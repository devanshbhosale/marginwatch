import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api, { apiError } from '../api';
import { Pager } from '../components/bits';
import { FileStatusChip, fmtDateTime } from '../format';

const STATUSES = ['', 'UPLOADED', 'PARSING', 'PARSED', 'FAILED'];
const PAGE_SIZE = 20;

export default function Files() {
  const nav = useNavigate();
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [status, setStatus] = useState('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    setPage(0);
  }, [status]);

  useEffect(() => {
    let live = true;
    const params = { page, size: PAGE_SIZE };
    if (status) params.status = status;
    api
      .get('/files', { params })
      .then((r) => {
        if (live) {
          setData(r.data);
          setErr('');
        }
      })
      .catch((e) => {
        if (live) setErr(apiError(e));
      });
    return () => {
      live = false;
    };
  }, [page, status, reload]);

  // Refresh while any file on this page is still processing.
  const inflight = data && (data.content || []).some((f) => f.status === 'UPLOADED' || f.status === 'PARSING');
  useEffect(() => {
    if (!inflight) return undefined;
    const t = setInterval(() => setReload((x) => x + 1), 3000);
    return () => clearInterval(t);
  }, [inflight, page, status]);

  if (err) return <div className="callout callout-red">{err}</div>;
  const content = data ? data.content || [] : null;

  return (
    <div>
      <div className="toolbar">
        <div className="field">
          <label className="lbl" htmlFor="f-status">Status</label>
          <select id="f-status" className="input" value={status} onChange={(e) => setStatus(e.target.value)}>
            {STATUSES.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || 'All statuses'}
              </option>
            ))}
          </select>
        </div>
        <div className="spacer" />
        {data && <span className="note">{data.totalElements} files</span>}
      </div>

      <div className="card">
        <div className="card-b card-b-flush">
          {!content ? (
            <div className="empty">Loading files...</div>
          ) : content.length === 0 ? (
            <div className="empty">
              <div className="empty-title">No price files yet</div>
              <p>Upload a vendor price file to see its diff preview here.</p>
            </div>
          ) : (
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>Filename</th>
                    <th>Vendor</th>
                    <th>Status</th>
                    <th>Uploaded</th>
                    <th>By</th>
                    <th>Rows (total / updated / errors)</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((f) => (
                    <tr key={f.id} className="rowlink" onClick={() => nav(`/files/${f.id}`)}>
                      <td className="mono">{f.filename}</td>
                      <td>
                        <span className="chip chip-wh">{f.vendorCode}</span>
                      </td>
                      <td>
                        <FileStatusChip status={f.status} />
                      </td>
                      <td className="nowrap">{fmtDateTime(f.uploadedAt)}</td>
                      <td>{f.uploadedByName}</td>
                      <td className="mono nowrap">
                        {f.totalRows ?? 0} / {f.updatedRows ?? 0} / {f.errorRows ?? 0}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {data && (
          <div style={{ padding: '0 14px 12px' }}>
            <Pager page={data.number} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} label="files" />
          </div>
        )}
      </div>
    </div>
  );
}
