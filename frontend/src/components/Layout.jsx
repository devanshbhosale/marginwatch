import { useState } from 'react';
import { NavLink, Link, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';

const TITLES = {
  '/': 'Dashboard',
  '/upload': 'Upload price file',
  '/files': 'Price files',
  '/products': 'Products',
  '/policies': 'Margin policies',
  '/audit': 'Audit trail',
  '/account': 'Account',
};

const NAV = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/upload', label: 'Upload' },
  { to: '/files', label: 'Files' },
  { to: '/products', label: 'Products' },
  { to: '/policies', label: 'Policies', admin: true },
  { to: '/audit', label: 'Audit' },
];

function initials(user) {
  const src = (user && (user.fullName || user.email)) || '?';
  const words = src.trim().split(/\s+/);
  const out = words.length > 1 ? words[0][0] + words[words.length - 1][0] : src.slice(0, 2);
  return out.toUpperCase();
}

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const loc = useLocation();

  const isAdmin = user && user.role === 'ADMIN';
  const title =
    TITLES[loc.pathname] || (/^\/files\/\d+$/.test(loc.pathname) ? 'File diff preview' : 'MarginWatch');

  const [sideClosed, setSideClosed] = useState(
    () => localStorage.getItem('mw-sidebar-closed') === '1'
  );
  const toggleSidebar = () =>
    setSideClosed((closed) => {
      localStorage.setItem('mw-sidebar-closed', closed ? '0' : '1');
      return !closed;
    });

  return (
    <div className={'app' + (sideClosed ? ' side-closed' : '')}>
      <aside className="sidebar" id="app-sidebar">
        <div className="logo">
          <span className="logo-mark" />
          MarginWatch
        </div>
        <nav className="nav">
          <div className="nav-group">Pricing ops</div>
          {NAV.filter((n) => !n.admin || isAdmin).map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              className={({ isActive }) => 'nav-link' + (isActive ? ' active' : '')}
            >
              <span>{n.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <div className="side-user">
            <div className="avatar">{initials(user)}</div>
            <div style={{ minWidth: 0 }}>
              <div className="side-name">{user.fullName || user.email}</div>
              <div className="side-email">{user.email}</div>
            </div>
          </div>
          <button className="side-logout" onClick={logout}>Sign out</button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="topbar-left">
            <button
              type="button"
              className="side-toggle"
              onClick={toggleSidebar}
              aria-expanded={!sideClosed}
              aria-controls="app-sidebar"
              title={sideClosed ? 'Open sidebar' : 'Close sidebar'}
            >
              <svg width="15" height="15" viewBox="0 0 16 16" aria-hidden="true">
                <rect
                  x="1.5" y="2.5" width="13" height="11" rx="1.6"
                  fill="none" stroke="currentColor" strokeWidth="1.3"
                />
                <rect x="3" y="4" width="2.6" height="8" rx="0.8" fill="currentColor" />
              </svg>
            </button>
            <h1>{title}</h1>
          </div>
          <div className="topbar-right">
            {user.isDemo && <span className="chip chip-amber">Demo</span>}
            <Link to="/account" className="topbar-account">
              <span className={'role-chip' + (isAdmin ? ' role-admin' : '')}>{user.role}</span>
              <div className="avatar avatar-light">{initials(user)}</div>
            </Link>
          </div>
        </header>

        {user.isDemo && (
          <div className="demo-banner">Demo workspace — sample data. Every action you take is written to the audit trail.</div>
        )}

        <main className="content">{children}</main>
      </div>
    </div>
  );
}
