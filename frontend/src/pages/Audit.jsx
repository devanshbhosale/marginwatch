import { useEffect, useState } from 'react';
import api, { apiError } from '../api';
import { Pager } from '../components/bits';
import { fmtDateTime } from '../format';

const PAGE_SIZE = 20;

// The five audited actions. Filter is server-side via ?action=.
const ACTIONS = ['FILE_UPLOADED', 'MAPPING_UPDATED', 'ROWS_APPLIED', 'ROW_SKIPPED', 'POLICY_UPDATED'];

function actionTone(action) {
  switch (action) {
    case 'ROWS_APPLIED':
      return 'green';
    case 'ROW_SKIPPED':
    case 'POLICY_UPDATED':
      return 'amber';
    default:
      return 'grey';
  }
}

export default function Audit() {
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [actor, setActor] = useState('');
  const [actorDeb, setActorDeb] = useState('');
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => setActorDeb(actor.trim()), 300);
    return () => clearTimeout(t);
  }, [actor]);

  useEffect(() => {
    setPage(0);
  }, [actorDeb, action]);

  useEffect(() => {
    let live = true;
    const params = { page, size: PAGE_SIZE };
    if (actorDeb) params.actor = actorDeb;
    if (action) params.action = action;
    api
      .get('/audit', { params })
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
  }, [page, actorDeb, action]);

  if (err) return <div className="callout callout-red">{err}</div>;
  const content = data ? data.content || [] : null;

  return (
    <div>
      <p className="note" style={{ marginBottom: 14 }}>
        Insert-only — entries cannot be edited or deleted.
      </p>

      <div className="toolbar">
        <div className="field-q">
          <label className="lbl" htmlFor="a-actor">Actor</label>
          <input
            id="a-actor"
            className="input"
            placeholder="Filter by actor email..."
            value={actor}
            onChange={(e) => setActor(e.target.value)}
          />
        </div>
        <div className="field">
          <label className="lbl" htmlFor="a-action">Action</label>
          <select id="a-action" className="input" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>{a}</option>
            ))}
          </select>
        </div>
        <div className="spacer" />
        {data && <span className="note">{data.totalElements} entries</span>}
      </div>

      <div className="card">
        <div className="card-b card-b-flush">
          {!content ? (
            <div className="empty">Loading audit trail...</div>
          ) : content.length === 0 ? (
            <div className="empty">No audit entries match these filters.</div>
          ) : (
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Actor</th>
                    <th>Action</th>
                    <th>Entity ref</th>
                    <th>Details</th>
                  </tr>
                </thead>
                <tbody>
                  {content.map((a) => (
                    <tr key={a.id}>
                      <td className="nowrap">{fmtDateTime(a.createdAt)}</td>
                      <td>{a.actorEmail}</td>
                      <td>
                        <span className={`chip chip-${actionTone(a.action)} chip-wh`}>{a.action}</span>
                      </td>
                      <td className="mono">{a.entityRef}</td>
                      <td className="small" style={{ maxWidth: 380, wordBreak: 'break-word' }}>
                        {a.details}
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
            <Pager page={data.number} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} label="entries" />
          </div>
        )}
      </div>
    </div>
  );
}
