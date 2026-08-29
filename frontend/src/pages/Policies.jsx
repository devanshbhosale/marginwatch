import { useEffect, useState } from 'react';
import api, { apiError } from '../api';
import { Modal, Toast, useToast } from '../components/bits';
import { fmtPct } from '../format';

function AddPolicyModal({ onClose, onSaved }) {
  const [category, setCategory] = useState('');
  const [margin, setMargin] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit(e) {
    e.preventDefault();
    const n = Number(margin);
    if (!Number.isFinite(n) || n < 0 || n > 100) {
      setErr('Enter a valid minimum margin percentage (0-100).');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.post('/policies', { category: category.trim() || null, minMarginPct: n });
      onSaved();
    } catch (e2) {
      setErr(apiError(e2));
      setBusy(false);
    }
  }

  return (
    <Modal title="Add margin policy" onClose={onClose} width={420}>
      {err && (
        <div className="callout callout-red" style={{ marginBottom: 12 }}>
          {err}
        </div>
      )}
      <form onSubmit={submit}>
        <div className="auth-field">
          <label className="lbl" htmlFor="pol-category">Category</label>
          <input
            id="pol-category"
            className="input"
            placeholder="Leave blank for the global floor"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
        </div>
        <div className="auth-field">
          <label className="lbl" htmlFor="pol-margin">Min margin %</label>
          <input
            id="pol-margin"
            className="input input-mono"
            type="number"
            min="0"
            max="100"
            step="0.1"
            value={margin}
            onChange={(e) => setMargin(e.target.value)}
          />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving...' : 'Add policy'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function Policies() {
  const { toast, show } = useToast();
  const [policies, setPolicies] = useState(null);
  const [err, setErr] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editVal, setEditVal] = useState('');
  const [busyId, setBusyId] = useState(null);
  const [addOpen, setAddOpen] = useState(false);

  function load() {
    api
      .get('/policies')
      .then((r) => {
        const body = r.data;
        const list = Array.isArray(body) ? body : body.content || [];
        // Global floor (category null) first, then categories alphabetically.
        list.sort((a, b) => {
          if ((a.category == null) !== (b.category == null)) return a.category == null ? -1 : 1;
          return String(a.category || '').localeCompare(String(b.category || ''));
        });
        setPolicies(list);
        setErr('');
      })
      .catch((e) => setErr(apiError(e)));
  }

  useEffect(() => {
    load();
  }, []);

  function startEdit(p) {
    setEditingId(p.id);
    setEditVal(String(p.minMarginPct));
  }

  async function saveEdit(p) {
    const n = Number(editVal);
    if (!Number.isFinite(n) || n < 0 || n > 100) {
      show('Enter a valid minimum margin percentage (0-100).', 'red');
      return;
    }
    setBusyId(p.id);
    try {
      await api.put(`/policies/${p.id}`, { category: p.category ?? null, minMarginPct: n });
      setEditingId(null);
      load();
      show(p.category ? `Floor for ${p.category} updated` : 'Global floor updated');
    } catch (e) {
      show(apiError(e), 'red');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <Toast toast={toast} />

      {err && (
        <div className="callout callout-red" style={{ marginBottom: 14 }}>
          {err}
        </div>
      )}

      <div className="callout" style={{ marginBottom: 14 }}>
        Category floors override the global floor. Rows falling below the floor are flagged{' '}
        <span className="chip chip-red">BREACH</span> and need an admin override with a reason.
      </div>

      <div className="card">
        <div className="card-h">
          <span className="card-title">Margin policies</span>
          <button className="btn btn-sm btn-primary" onClick={() => setAddOpen(true)}>Add policy</button>
        </div>
        <div className="table-scroll">
          {!policies ? (
            <div className="empty">Loading policies...</div>
          ) : policies.length === 0 ? (
            <div className="empty">
              <div className="empty-title">No policies yet</div>
              <p>Add a global floor or per-category minimums to start flagging breaches.</p>
            </div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Category</th>
                  <th className="right">Min margin</th>
                  <th className="right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {policies.map((p) => (
                  <tr key={p.id}>
                    <td>
                      {p.category ? (
                        p.category
                      ) : (
                        <span>
                          Global floor <span className="muted small">— applies where no category policy exists</span>
                        </span>
                      )}
                    </td>
                    <td className="right mono">
                      {editingId === p.id ? (
                        <input
                          className="input input-mono"
                          type="number"
                          min="0"
                          max="100"
                          step="0.1"
                          style={{ width: 90, textAlign: 'right' }}
                          value={editVal}
                          onChange={(e) => setEditVal(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') saveEdit(p);
                          }}
                          autoFocus
                        />
                      ) : (
                        <>{fmtPct(p.minMarginPct)}%</>
                      )}
                    </td>
                    <td className="right nowrap">
                      {editingId === p.id ? (
                        <span style={{ display: 'inline-flex', gap: 6 }}>
                          <button className="btn btn-sm btn-primary" disabled={busyId === p.id} onClick={() => saveEdit(p)}>
                            Save
                          </button>
                          <button className="btn btn-sm" disabled={busyId === p.id} onClick={() => setEditingId(null)}>
                            Cancel
                          </button>
                        </span>
                      ) : (
                        <button className="btn btn-sm" onClick={() => startEdit(p)}>Edit</button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {addOpen && (
        <AddPolicyModal
          onClose={() => setAddOpen(false)}
          onSaved={() => {
            setAddOpen(false);
            load();
            show('Policy added');
          }}
        />
      )}
    </div>
  );
}
