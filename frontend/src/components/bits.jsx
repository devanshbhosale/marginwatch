import { useEffect, useRef, useState } from 'react';
import api, { apiError } from '../api';

/* Small shared bits: modal, pager, toast hook and the vendor column-mapping
   modal (used by both the Upload screen and the file diff preview). */

export function Modal({ title, onClose, children, width }) {
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);

  return (
    <div
      className="modal-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal" style={width ? { width } : undefined}>
        <div className="card-h">
          <span className="card-title">{title}</span>
          <button className="btn btn-sm" onClick={onClose}>Close</button>
        </div>
        <div className="card-b">{children}</div>
      </div>
    </div>
  );
}

export function Pager({ page, totalPages, total, onPage, label }) {
  if (!totalPages) return null;
  return (
    <div className="pager">
      <button className="btn btn-sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Previous
      </button>
      <span className="pager-info">
        Page {page + 1} of {totalPages}
        {total != null ? ` · ${total} ${label || 'rows'}` : ''}
      </span>
      <button className="btn btn-sm" disabled={page >= totalPages - 1} onClick={() => onPage(page + 1)}>
        Next
      </button>
    </div>
  );
}

export function Toast({ toast }) {
  if (!toast) return null;
  return <div className={'toast' + (toast.tone === 'red' ? ' toast-red' : '')}>{toast.msg}</div>;
}

export function useToast() {
  const [toast, setToast] = useState(null);
  const timer = useRef(null);
  const show = (msg, tone) => {
    clearTimeout(timer.current);
    setToast({ msg, tone });
    timer.current = setTimeout(() => setToast(null), 4000);
  };
  useEffect(() => () => clearTimeout(timer.current), []);
  return { toast, show };
}

const MAPPING_TARGETS = [
  { field: 'SKU', label: 'SKU', hint: 'Column header holding the SKU code' },
  { field: 'DESCRIPTION', label: 'Description', hint: 'Column header holding the product description' },
  { field: 'COST_PRICE', label: 'Cost price', hint: 'Column header holding the unit cost in INR' },
];

export function MappingModal({ vendorCode, onClose, onSaved }) {
  const [vals, setVals] = useState({});
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let live = true;
    api
      .get(`/vendors/${vendorCode}/mapping`)
      .then((r) => {
        if (!live) return;
        const next = {};
        (r.data || []).forEach((m) => {
          next[m.targetField] = m.sourceHeader || '';
        });
        setVals(next);
      })
      .catch((e) => {
        if (live) setErr(apiError(e));
      });
    return () => {
      live = false;
    };
  }, [vendorCode]);

  async function save(e) {
    e.preventDefault();
    setBusy(true);
    setErr('');
    try {
      const payload = MAPPING_TARGETS.filter((t) => vals[t.field] && String(vals[t.field]).trim()).map((t) => ({
        sourceHeader: String(vals[t.field]).trim(),
        targetField: t.field,
      }));
      await api.put(`/vendors/${vendorCode}/mapping`, payload);
      onSaved();
    } catch (e2) {
      setErr(apiError(e2));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Column mapping — ${vendorCode}`} onClose={onClose}>
      <p className="note" style={{ marginBottom: 12 }}>
        Enter the exact column header text from this vendor's price file for each field. Blank removes a mapping.
      </p>
      {err && (
        <div className="callout callout-red" style={{ marginBottom: 12 }}>
          {err}
        </div>
      )}
      <form onSubmit={save}>
        {MAPPING_TARGETS.map((t) => (
          <div className="auth-field" key={t.field}>
            <label className="lbl" htmlFor={`map-${t.field}`}>
              {t.label} <span className="mono">({t.field})</span>
            </label>
            <input
              id={`map-${t.field}`}
              className="input input-mono"
              placeholder={t.hint}
              value={vals[t.field] || ''}
              onChange={(e) => setVals({ ...vals, [t.field]: e.target.value })}
            />
          </div>
        ))}
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving...' : 'Save mapping'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
