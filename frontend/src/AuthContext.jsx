import { createContext, useContext, useMemo, useState } from 'react';
import api from './api';

const AuthCtx = createContext(null);

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('mw_user') || 'null');
  } catch {
    return null;
  }
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(readUser);

  const value = useMemo(
    () => ({
      user,
      async login(email, password) {
        const r = await api.post('/auth/login', { email, password });
        localStorage.setItem('mw_token', r.data.token);
        localStorage.setItem('mw_user', JSON.stringify(r.data.user));
        setUser(r.data.user);
        return r.data.user;
      },
      logout() {
        localStorage.removeItem('mw_token');
        localStorage.removeItem('mw_user');
        setUser(null);
      },
    }),
    [user]
  );

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  return useContext(AuthCtx);
}
