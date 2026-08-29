import { useEffect, useState } from 'react';
import api from '../api';
import { useAuth } from '../AuthContext';

export default function Account() {
  const { user } = useAuth();
  const [acct, setAcct] = useState(user);

  useEffect(() => {
    api.get('/account').then((r) => setAcct(r.data)).catch(() => {});
  }, []);

  if (!acct) return <p className="muted">Loading account...</p>;

  return (
    <div className="card" style={{ maxWidth: 520 }}>
      <div className="card-h">
        <span className="card-title">Signed-in account</span>
      </div>
      <div className="card-b">
        <dl className="kv">
          <dt>Full name</dt>
          <dd>{acct.fullName}</dd>
          <dt>Email</dt>
          <dd>{acct.email}</dd>
          <dt>Role</dt>
          <dd>
            <span className={'role-chip' + (acct.role === 'ADMIN' ? ' role-admin' : '')}>{acct.role}</span>
          </dd>
          <dt>Mode</dt>
          <dd>{acct.isDemo ? <span className="chip chip-amber">Demo</span> : 'Live'}</dd>
        </dl>
        <p className="note" style={{ marginTop: 14 }}>
          Accounts are provisioned internally — there is no self-registration.
        </p>
      </div>
    </div>
  );
}
