import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './AuthContext';
import Layout from './components/Layout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Upload from './pages/Upload';
import Files from './pages/Files';
import FileDetail from './pages/FileDetail';
import Products from './pages/Products';
import Policies from './pages/Policies';
import Audit from './pages/Audit';
import Account from './pages/Account';
import NotFound from './pages/NotFound';

function Guard({ role, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (role && user.role !== role) return <Navigate to="/" replace />;
  return <Layout>{children}</Layout>;
}

function PublicOnly({ children }) {
  const { user } = useAuth();
  if (user) return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<PublicOnly><Login /></PublicOnly>} />

          <Route path="/" element={<Guard><Dashboard /></Guard>} />
          <Route path="/upload" element={<Guard><Upload /></Guard>} />
          <Route path="/files" element={<Guard><Files /></Guard>} />
          <Route path="/files/:id" element={<Guard><FileDetail /></Guard>} />
          <Route path="/products" element={<Guard><Products /></Guard>} />
          <Route path="/policies" element={<Guard role="ADMIN"><Policies /></Guard>} />
          <Route path="/audit" element={<Guard><Audit /></Guard>} />
          <Route path="/account" element={<Guard><Account /></Guard>} />

          <Route path="*" element={<Guard><NotFound /></Guard>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
