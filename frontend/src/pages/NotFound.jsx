import { Link } from 'react-router-dom';

export default function NotFound() {
  return (
    <div className="empty" style={{ padding: '80px 20px' }}>
      <div className="mono" style={{ fontSize: 40, fontWeight: 600, color: 'var(--line-strong)' }}>404</div>
      <div className="empty-title">Page not found</div>
      <p>The page you are looking for does not exist or has moved.</p>
      <Link to="/" className="btn btn-primary">Back to dashboard</Link>
    </div>
  );
}
