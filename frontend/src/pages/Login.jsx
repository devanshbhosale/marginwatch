import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { apiError } from '../api';

const DEMO_ANALYST = { email: 'analyst.demo@marginwatch.in', password: 'Demo@Analyst2026' };
const DEMO_ADMIN = { email: 'admin.demo@marginwatch.in', password: 'Demo@Admin2026' };

export default function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  async function doLogin(em, pw) {
    setErr('');
    setBusy(true);
    try {
      await login(em, pw);
      nav('/');
    } catch (e) {
      setErr(apiError(e));
    } finally {
      setBusy(false);
    }
  }

  function submit(e) {
    e.preventDefault();
    doLogin(email.trim(), password);
  }

  function tryDemo(d) {
    setEmail(d.email);
    setPassword(d.password);
    doLogin(d.email, d.password);
  }

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        <div className="auth-logo">
          <span className="logo-mark" />
          MarginWatch
        </div>
        <p className="auth-sub">Vendor price-file sync &amp; margin guard</p>

        {err && (
          <div className="callout callout-red" style={{ marginBottom: 14 }}>
            {err}
          </div>
        )}

        <form onSubmit={submit}>
          <div className="auth-field">
            <label className="lbl" htmlFor="email">Email</label>
            <input
              id="email"
              className="input"
              type="email"
              autoComplete="username"
              placeholder="you@company.in"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
          </div>
          <div className="auth-field">
            <label className="lbl" htmlFor="password">Password</label>
            <input
              id="password"
              className="input"
              type="password"
              autoComplete="current-password"
              placeholder="Your password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          <button className="btn btn-primary" style={{ width: '100%' }} disabled={busy}>
            {busy ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <div className="auth-divider">Demo accounts</div>
        <div className="auth-demo">
          <button className="btn" disabled={busy} onClick={() => tryDemo(DEMO_ANALYST)}>
            Analyst demo
          </button>
          <button className="btn" disabled={busy} onClick={() => tryDemo(DEMO_ADMIN)}>
            Admin demo
          </button>
        </div>
        <p className="auth-demo-note">
          Read-only viewer: viewer.demo@marginwatch.in / Demo@Viewer2026
        </p>

        <div className="auth-foot">Internal tool — accounts are provisioned by the ops team.</div>
      </div>
    </div>
  );
}
