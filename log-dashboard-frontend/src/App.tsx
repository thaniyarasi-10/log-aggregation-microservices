import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from './components/Navbar';
import LoginOverlay from './components/LoginOverlay';
import FloatingAgent from './components/FloatingAgent.jsx';
import CommandSearch from './components/CommandSearch';
import { useAuth } from './context/AuthContext';
import LogsPage from './pages/LogsPage';
import LogExplorerPage from './pages/LogExplorerPage';
import AlertsPage from './pages/AlertsPage';
import ServicesPage from './pages/ServicesPage';
import UsersPage from './pages/UsersPage';
import LoginCallback from './pages/LoginCallback';
import SettingsPage from './pages/SettingsPage';

function RouteLogger() {
  const location = useLocation();

  useEffect(() => {
    console.log('Route changed:', location.pathname);
  }, [location.pathname]);

  return null;
}

export default function App() {
  const { status, login, canAccessUsers, canAccessServices, isAdmin, isDev } = useAuth();
  const location = useLocation();

  if (status !== 'authenticated' && location.pathname !== '/login/callback') {
    return (
      <LoginOverlay
        login={login}
        statusMessage={status === 'loading' ? 'Checking existing session...' : 'Not signed in. Use Microsoft Entra ID to continue.'}
      />
    );
  }

  return (
    <div id="app-container">
      <RouteLogger />
      {location.pathname !== '/login/callback' && <Navbar />}
      <Routes>
        <Route path="/login/callback" element={<LoginCallback />} />
        <Route path="/logs" element={<LogsPage />} />
        <Route path="/explorer" element={<LogExplorerPage />} />
        <Route path="/alerts" element={<AlertsPage />} />
        <Route path="/users" element={canAccessUsers ? <UsersPage /> : <Navigate to="/logs" replace />} />
        <Route path="/services" element={canAccessServices ? <ServicesPage /> : <Navigate to="/logs" replace />} />
        <Route path="/settings/*" element={(isAdmin || isDev) ? <SettingsPage /> : <Navigate to="/logs" replace />} />
        <Route path="*" element={<Navigate to="/logs" replace />} />
      </Routes>
      {location.pathname !== '/login/callback' && <FloatingAgent />}
      {location.pathname !== '/login/callback' && <CommandSearch />}
    </div>
  );
}

