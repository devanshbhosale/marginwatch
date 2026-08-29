import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import api, { apiError, downloadBlob } from '../api';
import { useAuth } from '../AuthContext';
import { MappingModal, Modal, Pager, Toast, useToast } from '../components/bits';
import {
  FileStatusChip,
  FlagChip,
  RowStatusChip,
  flagTone,
  rowStatusTone,
  fmtDateTime,
  fmtINR,
  fmtPct,
} from '../format';

const FLAGS = ['OK', 'BREACH', 'NEW_SKU', 'NOT_FOUND', 'DUPLICATE'];
const STATUSES = ['PENDING', 'APPLIED', 'SKIPPED'];
const PAGE_SIZE = 100;

function MarginCell({ row }) {
  if (row.resultingMarginPct == null) return <span className="muted">—</span>;
  const below = row.policyFloorPct != null && row.resultingMarginPct < row.policyFloorPct;
  return (
    <span className={below ? 'below-floor' : undefined} title={below ? 'Below the margin floor' : undefined}>
      {fmtPct(row.resultingMarginPct)}%
      {row.policyFloorPct != null && <span className="muted"> vs {fmtPct(row.policyFloorPct)}% floor</span>}
    </span>
  );
}

function CostCell({ row }) {
  const pct = row.changePct;
  const cls = pct > 0 ? 'delta-up' : pct < 0 ? 'delta-down' : 'muted';
  return (
    <span className="mono nowrap">
      {fmtINR(row.oldCost)} → {fmtINR(row.newCost)}{' '}
      <span className={cls}>
        ({pct > 0 ? '+' : ''}
        {fmtPct(pct)}%)
      </span>
    </span>
  );
}

function ApplyAllModal({ fileId, isAdmin, onClose, onDone }) {
  const [includeBreaches, setIncludeBreaches] = useState(false);
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [result, setResult] = useState(null);

  const needReason = isAdmin && includeBreaches && !reason.trim();

  async function submit() {
    setBusy(true);
    setErr('');
    try {
      const r = await api.post(`/files/${fileId}/apply-all`, {
        includeBreaches: isAdmin && includeBreaches,
        overrideReason: isAdmin && includeBreaches ? reason.trim() : null,
      });
      setResult(r.data);
      onDone();
    } catch (e) {
      setErr(apiError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title="Apply all pending rows" onClose={onClose}>
      {result ? (
        <div>
          <div className="callout callout-green">
            <div className="callout-title">Done.</div>
            <div className="err-nums">
              Applied {result.applied} · Breach overrides {result.breachApplied} · Skipped {result.skipped}
            </div>
          </div>
          <div className="modal-actions">
            <button className="btn btn-primary" onClick={onClose}>Close</button>
          </div>
        </div>
      ) : (
        <div>
          {isAdmin ? (
            <div style={{ marginBottom: 12 }}>
              <label className="check">
                <input
                  type="checkbox"
                  checked={includeBreaches}
                  onChange={(e) => setIncludeBreaches(e.target.checked)}
                />
                Include margin breaches
              </label>
              <p className="note" style={{ marginTop: 6 }}>
                Includes rows whose resulting margin falls below the category or global floor.
              </p>
            </div>
          ) : (
            <div className="callout callout-amber" style={{ marginBottom: 12 }}>
              Breach overrides need an admin — rows flagged BREACH will be skipped.
            </div>
          )}
          <div className="auth-field">
            <label className="lbl" htmlFor="apply-all-reason">Override reason</label>
            <textarea
              id="apply-all-reason"
              className="input"
              placeholder={isAdmin && includeBreaches ? 'Required when including breaches — e.g. "Supplier agreed promo pricing for Q3"' : 'Optional note for the audit trail'}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </div>
          {err && (
            <div className="callout callout-red" style={{ marginBottom: 12 }}>
              {err}
            </div>
          )}
          <div className="modal-actions">
            <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
            <button className="btn btn-primary" onClick={submit} disabled={busy || needReason}>
              {busy ? 'Applying...' : 'Apply all pending'}
            </button>
          </div>
        </div>
      )}
    </Modal>
  );
}

function BreachModal({ row, onClose, onApplied }) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit() {
    setBusy(true);
    setErr('');
    try {
      const res = await api.post(`/rows/${row.id}/apply`, { overrideReason: reason.trim() });
      onApplied(res.data);
    } catch (e) {
      setErr(apiError(e));
      setBusy(false);
    }
  }

  return (
    <Modal title="Override margin breach" onClose={onClose} width={480}>
      <div className="callout callout-red" style={{ marginBottom: 12 }}>
        <div className="callout-title">Row {row.rowNumber} — {row.sku}</div>
        <div style={{ marginTop: 4 }}>
          Resulting margin {fmtPct(row.resultingMarginPct)}% falls below the {fmtPct(row.policyFloorPct)}% floor.
          Applying needs an admin override with a reason.
        </div>
      </div>
      <div className="auth-field">
        <label className="lbl" htmlFor="breach-reason">Override reason</label>
        <textarea
          id="breach-reason"
          className="input"
          placeholder="Type a reason — it is written to the audit trail"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>
      {err && (
        <div className="callout callout-red" style={{ marginBottom: 12 }}>
          {err}
        </div>
      )}
      <div className="modal-actions">
        <button className="btn" onClick={onClose} disabled={busy}>Cancel</button>
        <button className="btn btn-primary" onClick={submit} disabled={busy || !reason.trim()}>
          {busy ? 'Applying...' : 'Apply with override'}
        </button>
      </div>
    </Modal>
  );
}

export default function FileDetail() {
  const { id } = useParams();
  const { user } = useAuth();
  const { toast, show } = useToast();
  const [file, setFile] = useState(null);
  const [rows, setRows] = useState([]);
  const [rowsMeta, setRowsMeta] = useState(null);
  const [page, setPage] = useState(0);
  const [flag, setFlag] = useState('');
  const [status, setStatus] = useState('');
  const [err, setErr] = useState('');
  const [busyRow, setBusyRow] = useState(null);
  const [applyAllOpen, setApplyAllOpen] = useState(false);
  const [breachRow, setBreachRow] = useState(null);
  const [mappingOpen, setMappingOpen] = useState(false);
  const [exporting, setExporting] = useState(false);

  const isAdmin = user.role === 'ADMIN';
  const canAct = user.role === 'ADMIN' || user.role === 'ANALYST';

  const loadFile = useCallback(
    () =>
      api
        .get(`/files/${id}`)
        .then((r) => setFile(r.data))
        .catch((e) => setErr(apiError(e))),
    [id]
  );

  const loadRows = useCallback(() => {
    const params = { page, size: PAGE_SIZE };
    if (flag) params.flag = flag;
    if (status) params.status = status;
    return api
      .get(`/files/${id}/rows`, { params })
      .then((r) => {
        setRows(r.data.content || []);
        setRowsMeta(r.data);
      })
      .catch((e) => setErr(apiError(e)));
  }, [id, page, flag, status]);

  useEffect(() => {
    setPage(0);
  }, [flag, status]);

  useEffect(() => {
    loadFile();
  }, [loadFile]);

  useEffect(() => {
    loadRows();
  }, [loadRows]);

  // Poll while the file is still being parsed, then pull the rows.
  useEffect(() => {
    if (!file || (file.status !== 'UPLOADED' && file.status !== 'PARSING')) return undefined;
    const t = setInterval(() => {
      api
        .get(`/files/${id}`)
        .then((r) => {
          setFile(r.data);
          if (r.data.status === 'PARSED' || r.data.status === 'FAILED') loadRows();
        })
        .catch(() => {});
    }, 1500);
    return () => clearInterval(t);
  }, [file, id, loadRows]);

  async function exportReport() {
    setExporting(true);
    try {
      await downloadBlob(`/files/${id}/export`, `marginwatch-file-${id}-report.xlsx`);
    } catch (e) {
      show(apiError(e), 'red');
    } finally {
      setExporting(false);
    }
  }

  async function applyRow(row, overrideReason) {
    setBusyRow(row.id);
    try {
      const res = await api.post(`/rows/${row.id}/apply`, overrideReason ? { overrideReason } : {});
      setRows((prev) => prev.map((x) => (x.id === row.id ? res.data : x)));
      loadFile();
      show(`Row ${row.rowNumber} applied`);
    } catch (e) {
      show(apiError(e), 'red');
    } finally {
      setBusyRow(null);
    }
  }

  async function skipRow(row) {
    setBusyRow(row.id);
    try {
      const res = await api.post(`/rows/${row.id}/skip`, {});
      setRows((prev) => prev.map((x) => (x.id === row.id ? res.data : x)));
      loadFile();
      show(`Row ${row.rowNumber} skipped`);
    } catch (e) {
      show(apiError(e), 'red');
    } finally {
      setBusyRow(null);
    }
  }

  function rowActions(r) {
    if (r.status !== 'PENDING') return <span className="muted small">—</span>;
    const skipOnly = r.flag === 'NEW_SKU' || r.flag === 'NOT_FOUND' || r.flag === 'DUPLICATE';
    const showApply = r.flag === 'OK' || (r.flag === 'BREACH' && isAdmin);
    return (
      <span style={{ display: 'inline-flex', gap: 6, justifyContent: 'flex-end' }}>
        {showApply && (
          <button
            className="btn btn-sm btn-primary"
            disabled={busyRow === r.id}
            onClick={() => (r.flag === 'BREACH' ? setBreachRow(r) : applyRow(r, ''))}
          >
            Apply
          </button>
        )}
        {r.flag === 'BREACH' && !isAdmin && (
          <span className="muted small" title="Admin override required" style={{ cursor: 'help' }}>
            Admin only
          </span>
        )}
        <button className="btn btn-sm" disabled={busyRow === r.id} onClick={() => skipRow(r)}>
          Skip
        </button>
      </span>
    );
  }

  if (err && !file) return <div className="callout callout-red">{err}</div>;
  if (!file) return <p className="muted">Loading file...</p>;

  const flagCounts = file.flagCounts || {};
  const statusCounts = file.statusCounts || {};

  return (
    <div>
      <Toast toast={toast} />

      {file.status === 'FAILED' && (
        <div className="callout callout-red" style={{ marginBottom: 14 }}>
          <div className="callout-title">Parsing failed</div>
          <div style={{ marginTop: 4 }}>{file.errorReport || 'The file could not be parsed.'}</div>
          {canAct && (
            <button className="btn btn-sm" style={{ marginTop: 8 }} onClick={() => setMappingOpen(true)}>
              Open mapping editor
            </button>
          )}
        </div>
      )}

      <div className="card">
        <div className="card-h">
          <span className="card-title">
            File #{file.id} — {file.filename}
          </span>
          <FileStatusChip status={file.status} />
        </div>
        <div className="card-b">
          <dl className="kv" style={{ gridTemplateColumns: '130px 1fr', marginBottom: 14 }}>
            <dt>Vendor</dt>
            <dd>
              <span className="chip chip-wh">{file.vendorCode}</span>
            </dd>
            <dt>Uploaded</dt>
            <dd>
              {fmtDateTime(file.uploadedAt)} by {file.uploadedByName}
            </dd>
            {file.processedAt && (
              <>
                <dt>Processed</dt>
                <dd>{fmtDateTime(file.processedAt)}</dd>
              </>
            )}
            <dt>Rows</dt>
            <dd className="mono">
              {file.totalRows ?? 0} parsed · {file.updatedRows ?? 0} updated · {file.errorRows ?? 0} errors
            </dd>
          </dl>

          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
            <span className="lbl" style={{ marginBottom: 0 }}>Flags</span>
            {FLAGS.map((f) => (
              <span
                key={f}
                className={`chip chip-${flagTone(f)} chip-click${flag === f ? ' chip-active' : ''}`}
                onClick={() => setFlag(flag === f ? '' : f)}
                title={`Filter rows by ${f}`}
              >
                {f} · {flagCounts[f] ?? 0}
              </span>
            ))}
          </div>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
            <span className="lbl" style={{ marginBottom: 0 }}>Status</span>
            {STATUSES.map((s) => (
              <span
                key={s}
                className={`chip chip-${rowStatusTone(s)} chip-click${status === s ? ' chip-active' : ''}`}
                onClick={() => setStatus(status === s ? '' : s)}
                title={`Filter rows by ${s}`}
              >
                {s} · {statusCounts[s] ?? 0}
              </span>
            ))}
          </div>
        </div>
      </div>

      {canAct && (
        <div className="flex-between" style={{ marginTop: 14, flexWrap: 'wrap', gap: 10 }}>
          <span className="note">
            Applying writes the new cost onto the product and logs the change to the audit trail.
          </span>
          <span style={{ display: 'flex', gap: 8 }}>
            <button className="btn" onClick={exportReport} disabled={exporting}>
              {exporting ? 'Exporting...' : 'Export report'}
            </button>
            <button className="btn btn-primary" onClick={() => setApplyAllOpen(true)} disabled={file.status !== 'PARSED'}>
              Apply all
            </button>
          </span>
        </div>
      )}

      <div className="card section-gap">
        <div className="card-h">
          <span className="card-title">
            Rows{flag ? ` — flag ${flag}` : ''}
            {status ? ` — status ${status}` : ''}
          </span>
          <span className="note">{rowsMeta ? `${rowsMeta.totalElements} rows` : ''}</span>
        </div>
        <div className="table-scroll">
          {rows.length === 0 ? (
            <div className="empty">
              {flag || status ? 'No rows match this filter.' : 'No rows parsed for this file.'}
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>SKU</th>
                  <th>Product</th>
                  <th>Old cost → New cost</th>
                  <th className="right">Resulting margin</th>
                  <th>Flag</th>
                  <th>Status</th>
                  {canAct && <th className="right">Actions</th>}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td className="mono muted">{r.rowNumber}</td>
                    <td className="mono">{r.sku}</td>
                    <td>{r.productName || <span className="muted">—</span>}</td>
                    <td>
                      <CostCell row={r} />
                    </td>
                    <td className="right">
                      <MarginCell row={r} />
                    </td>
                    <td>
                      <FlagChip flag={r.flag} />
                    </td>
                    <td>
                      <RowStatusChip status={r.status} />
                    </td>
                    {canAct && <td className="right">{rowActions(r)}</td>}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        {rowsMeta && (
          <div style={{ padding: '0 14px 12px' }}>
            <Pager page={rowsMeta.number} totalPages={rowsMeta.totalPages} total={rowsMeta.totalElements} onPage={setPage} />
          </div>
        )}
      </div>

      {applyAllOpen && (
        <ApplyAllModal
          fileId={file.id}
          isAdmin={isAdmin}
          onClose={() => setApplyAllOpen(false)}
          onDone={() => {
            loadFile();
            loadRows();
          }}
        />
      )}

      {breachRow && (
        <BreachModal
          row={breachRow}
          onClose={() => setBreachRow(null)}
          onApplied={(updated) => {
            setRows((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
            setBreachRow(null);
            loadFile();
            show(`Row ${updated.rowNumber} applied with override`);
          }}
        />
      )}

      {mappingOpen && (
        <MappingModal
          vendorCode={file.vendorCode}
          onClose={() => setMappingOpen(false)}
          onSaved={() => {
            setMappingOpen(false);
            show('Mapping saved — upload again');
          }}
        />
      )}
    </div>
  );
}
