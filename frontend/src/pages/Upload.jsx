import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import api, { apiError } from '../api';
import { useAuth } from '../AuthContext';
import { MappingModal, Toast, useToast } from '../components/bits';
import { FileStatusChip } from '../format';

function sizeText(bytes) {
  const kb = bytes / 1024;
  return kb > 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(kb))} KB`;
}

export default function Upload() {
  const { user } = useAuth();
  const { toast, show } = useToast();
  const [vendors, setVendors] = useState([]);
  const [vendorCode, setVendorCode] = useState('');
  const [file, setFile] = useState(null);
  const [drag, setDrag] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const [upload, setUpload] = useState(null); // fileRes of the last upload, polled
  const [mappingVendor, setMappingVendor] = useState(null);
  const inputRef = useRef(null);

  const viewer = user.role === 'VIEWER';

  useEffect(() => {
    api.get('/vendors').then((r) => setVendors(r.data || [])).catch((e) => setErr(apiError(e)));
  }, []);

  // Poll the uploaded file every 1.5s until processing finishes.
  useEffect(() => {
    if (!upload || (upload.status !== 'UPLOADED' && upload.status !== 'PARSING')) return undefined;
    const t = setInterval(() => {
      api.get(`/files/${upload.id}`).then((r) => setUpload(r.data)).catch(() => {});
    }, 1500);
    return () => clearInterval(t);
  }, [upload]);

  const vendor = vendors.find((v) => v.code === vendorCode);

  function pickFile(f) {
    if (!f) return;
    setErr('');
    setUpload(null);
    setFile(f);
  }

  function onDrop(e) {
    e.preventDefault();
    setDrag(false);
    if (!viewer) pickFile(e.dataTransfer.files && e.dataTransfer.files[0]);
  }

  async function refreshVendors() {
    try {
      const r = await api.get('/vendors');
      setVendors(r.data || []);
    } catch {
      /* non-critical */
    }
  }

  function onMappingSaved() {
    setMappingVendor(null);
    setErr('');
    refreshVendors();
    show('Mapping saved — upload again');
  }

  async function submit(e) {
    e.preventDefault();
    if (!vendorCode || !file) return;
    setBusy(true);
    setErr('');
    try {
      const fd = new FormData();
      fd.append('vendorCode', vendorCode);
      fd.append('file', file);
      const r = await api.post('/files', fd);
      setUpload(r.data);
      setFile(null);
      if (inputRef.current) inputRef.current.value = '';
    } catch (e2) {
      const msg = apiError(e2);
      setErr(msg);
      // 422 = vendor has no column mapping yet — open the mapping editor.
      if (e2.response && e2.response.status === 422) setMappingVendor(vendorCode);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <Toast toast={toast} />

      {viewer && (
        <div className="callout callout-amber" style={{ marginBottom: 14 }}>
          Your role is read-only. Uploading price files requires the Analyst or Admin role.
        </div>
      )}

      {upload && (
        <div className="card" style={{ marginBottom: 14 }}>
          <div className="card-h">
            <span className="card-title">Upload progress</span>
            <FileStatusChip status={upload.status} />
          </div>
          <div className="card-b">
            <dl className="kv" style={{ gridTemplateColumns: '110px 1fr' }}>
              <dt>File</dt>
              <dd className="mono">{upload.filename}</dd>
              <dt>Vendor</dt>
              <dd className="mono">{upload.vendorCode}</dd>
            </dl>
            {upload.status === 'UPLOADED' && (
              <p className="note" style={{ marginTop: 10 }}>
                <span className="spin" />
                Queued — waiting for the parser to pick it up...
              </p>
            )}
            {upload.status === 'PARSING' && (
              <p className="note" style={{ marginTop: 10 }}>
                <span className="spin" />
                Parsing rows and diffing against the catalog...
              </p>
            )}
            {upload.status === 'PARSED' && (
              <div className="callout callout-green" style={{ marginTop: 10 }}>
                <span className="callout-title">Parsed {upload.totalRows ?? 0} rows.</span>{' '}
                <Link to={`/files/${upload.id}`} className="btn btn-sm btn-primary" style={{ marginLeft: 8 }}>
                  Open diff preview
                </Link>
              </div>
            )}
            {upload.status === 'FAILED' && (
              <div className="callout callout-red" style={{ marginTop: 10 }}>
                <div className="callout-title">Parsing failed</div>
                <div style={{ marginTop: 4 }}>{upload.errorReport || 'The file could not be parsed.'}</div>
                <button className="btn btn-sm" style={{ marginTop: 8 }} onClick={() => setMappingVendor(upload.vendorCode)}>
                  Open mapping editor
                </button>
              </div>
            )}
          </div>
        </div>
      )}

      {err && !mappingVendor && (
        <div className="callout callout-red" style={{ marginBottom: 14 }}>
          {err}
        </div>
      )}

      <form onSubmit={submit}>
        <div className="auth-field" style={{ maxWidth: 480 }}>
          <label className="lbl" htmlFor="vendor">Vendor</label>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
            <select
              id="vendor"
              className="input"
              style={{ maxWidth: 320 }}
              value={vendorCode}
              disabled={viewer}
              onChange={(e) => {
                setVendorCode(e.target.value);
                setUpload(null);
                setErr('');
              }}
            >
              <option value="">Select a vendor...</option>
              {vendors.map((v) => (
                <option key={v.code} value={v.code}>
                  {v.name} ({v.code}){v.mapped ? '' : ' — no mapping'}
                </option>
              ))}
            </select>
            {vendor && !vendor.mapped && <span className="chip chip-amber">No mapping saved</span>}
            {vendor && vendor.mapped && <span className="chip">{vendor.mappingCount} columns mapped</span>}
          </div>
        </div>

        <div className="auth-field" style={{ maxWidth: 640 }}>
          <span className="lbl">Price file</span>
          <div
            className={'dropzone' + (drag ? ' drag' : '')}
            onClick={() => !viewer && inputRef.current && inputRef.current.click()}
            onDragOver={(e) => {
              e.preventDefault();
              if (!viewer) setDrag(true);
            }}
            onDragLeave={() => setDrag(false)}
            onDrop={onDrop}
            role="button"
            tabIndex={0}
            aria-disabled={viewer}
          >
            {file ? (
              <div>
                <div className="dropzone-title mono">{file.name}</div>
                <div className="small" style={{ marginTop: 4 }}>{sizeText(file.size)} — click to choose a different file</div>
              </div>
            ) : (
              <div>
                <div className="dropzone-title">Drag &amp; drop the vendor price file here</div>
                <div className="small" style={{ marginTop: 4 }}>or click to browse — .csv and .xlsx accepted</div>
              </div>
            )}
          </div>
          <input
            ref={inputRef}
            type="file"
            accept=".csv,.xlsx"
            style={{ display: 'none' }}
            onChange={(e) => pickFile(e.target.files && e.target.files[0])}
          />
        </div>

        <button className="btn btn-primary" disabled={busy || viewer || !vendorCode || !file}>
          {busy ? 'Uploading...' : 'Upload file'}
        </button>
        {vendor && !vendor.mapped && !viewer && (
          <button type="button" className="btn" style={{ marginLeft: 8 }} onClick={() => setMappingVendor(vendorCode)}>
            Open mapping editor
          </button>
        )}
      </form>

      {mappingVendor && (
        <MappingModal vendorCode={mappingVendor} onClose={() => setMappingVendor(null)} onSaved={onMappingSaved} />
      )}
    </div>
  );
}
