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

  return (
    <div className="app">
      <aside className="sidebar">
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
          <h1>{title}</h1>
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
