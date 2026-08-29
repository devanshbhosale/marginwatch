import { useEffect, useState } from 'react';
import api, { apiError } from '../api';
import { useAuth } from '../AuthContext';
import { Modal, Pager, Toast, useToast } from '../components/bits';
import { fmtINR, fmtPct } from '../format';

const PAGE_SIZE = 20;

// <10% red, <12% amber, else default.
function marginClass(marginPct) {
  const n = Number(marginPct);
  if (!Number.isFinite(n)) return '';
  if (n < 10) return 'delta-up';
  if (n < 12) return 'below-floor';
  return '';
}

function PriceModal({ product, onClose, onSaved }) {
  const [price, setPrice] = useState(product.sellingPrice != null ? String(product.sellingPrice) : '');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');

  async function submit(e) {
    e.preventDefault();
    const n = Number(price);
    if (!Number.isFinite(n) || n <= 0) {
      setErr('Enter a valid selling price.');
      return;
    }
    setBusy(true);
    setErr('');
    try {
      await api.put(`/products/${product.id}/price`, { sellingPrice: n });
      onSaved();
    } catch (e2) {
      setErr(apiError(e2));
      setBusy(false);
    }
  }

  return (
    <Modal title={`Edit price — ${product.sku}`} onClose={onClose} width={420}>
      <p className="note" style={{ marginBottom: 12 }}>
        Current cost {fmtINR(product.currentCost)} · Margin {fmtPct(product.marginPct)}%. The margin recalculates
        from the new selling price.
      </p>
      {err && (
        <div className="callout callout-red" style={{ marginBottom: 12 }}>
          {err}
        </div>
      )}
      <form onSubmit={submit}>
        <div className="auth-field">
          <label className="lbl" htmlFor="selling-price">Selling price (INR)</label>
          <input
            id="selling-price"
            className="input input-mono"
            type="number"
            min="0"
            step="0.01"
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            autoFocus
          />
        </div>
        <div className="modal-actions">
          <button type="button" className="btn" onClick={onClose} disabled={busy}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving...' : 'Save price'}
          </button>
        </div>
      </form>
    </Modal>
  );
}

export default function Products() {
  const { user } = useAuth();
  const { toast, show } = useToast();
  const isAdmin = user.role === 'ADMIN';
  const [meta, setMeta] = useState({ categories: [], vendors: [] });
  const [vendor, setVendor] = useState('');
  const [category, setCategory] = useState('');
  const [q, setQ] = useState('');
  const [qDeb, setQDeb] = useState('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [data, setData] = useState(null);
  const [err, setErr] = useState('');
  const [edit, setEdit] = useState(null);

  useEffect(() => {
    api
      .get('/products/meta')
      .then((r) => setMeta(r.data || { categories: [], vendors: [] }))
      .catch(() => {});
  }, []);

  useEffect(() => {
    const t = setTimeout(() => setQDeb(q.trim()), 300);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    setPage(0);
  }, [vendor, category, qDeb, reload]);

  useEffect(() => {
    let live = true;
    const params = { page, size: PAGE_SIZE };
    if (vendor) params.vendor = vendor;
    if (category) params.category = category;
    if (qDeb) params.q = qDeb;
    api
      .get('/products', { params })
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
  }, [page, vendor, category, qDeb, reload]);

  function onSaved() {
    setEdit(null);
    setReload((x) => x + 1);
    show('Selling price updated');
  }

  const content = data ? data.content || [] : null;

  return (
    <div>
      <Toast toast={toast} />

      {err && (
        <div className="callout callout-red" style={{ marginBottom: 14 }}>
          {err}
        </div>
      )}

      <div className="toolbar">
        <div className="field">
          <label className="lbl" htmlFor="p-vendor">Vendor</label>
          <select id="p-vendor" className="input" value={vendor} onChange={(e) => setVendor(e.target.value)}>
            <option value="">All vendors</option>
            {(meta.vendors || []).map((v) => (
              <option key={v} value={v}>{v}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label className="lbl" htmlFor="p-category">Category</label>
          <select id="p-category" className="input" value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">All categories</option>
            {(meta.categories || []).map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>
        </div>
        <div className="field-q">
          <label className="lbl" htmlFor="p-q">Search</label>
          <input
            id="p-q"
            className="input"
            placeholder="Search SKU or name..."
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div className="spacer" />
        {data && <span className="note">{data.totalElements} products</span>}
      </div>

      <div className="card">
        <div className="card-b card-b-flush">
          {!content ? (
            <div className="empty">Loading products...</div>
          ) : content.length === 0 ? (
            <div className="empty">No products match these filters.</div>
          ) : (
            <div className="table-scroll">
              <table className="table">
                <thead>
                  <tr>
                    <th>SKU</th>
                    <th>Name</th>
                    <th>Vendor</th>
                    <th>Category</th>
                    <th className="right">Current cost</th>
                    <th className="right">Selling price</th>
                    <th className="right">Margin</th>
                    {isAdmin && <th className="right">Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {content.map((p) => (
                    <tr key={p.id}>
                      <td className="mono">{p.sku}</td>
                      <td>{p.name}</td>
                      <td>
                        <span className="chip chip-wh">{p.vendorCode}</span>
                      </td>
                      <td>{p.category}</td>
                      <td className="right mono">{fmtINR(p.currentCost)}</td>
                      <td className="right mono">{fmtINR(p.sellingPrice)}</td>
                      <td className={'right mono ' + marginClass(p.marginPct)}>{fmtPct(p.marginPct)}%</td>
                      {isAdmin && (
                        <td className="right">
                          <button className="btn btn-sm" onClick={() => setEdit(p)}>Edit price</button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
        {data && (
          <div style={{ padding: '0 14px 12px' }}>
            <Pager page={data.number} totalPages={data.totalPages} total={data.totalElements} onPage={setPage} label="products" />
          </div>
        )}
      </div>

      {edit && <PriceModal product={edit} onClose={() => setEdit(null)} onSaved={onSaved} />}
    </div>
  );
}
