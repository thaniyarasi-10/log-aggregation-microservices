import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from './components/Navbar';
import LoginOverlay from './components/LoginOverlay';
import FloatingAgent from './components/FloatingAgent.jsx';
import { useAuth } from './context/AuthContext';
import LogsPage from './pages/LogsPage';
import ServicesPage from './pages/ServicesPage';
import UsersPage from './pages/UsersPage';
import LoginCallback from './pages/LoginCallback';
import JiraIntegrationPage from './pages/JiraIntegrationPage';

function RouteLogger() {
  const location = useLocation();

  useEffect(() => {
    console.log('Route changed:', location.pathname);
  }, [location.pathname]);

  return null;
}

export default function App() {
  const { status, login, canAccessUsers, canAccessServices, isAdmin } = useAuth();
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
        <Route path="/users" element={canAccessUsers ? <UsersPage /> : <Navigate to="/logs" replace />} />
        <Route path="/services" element={canAccessServices ? <ServicesPage /> : <Navigate to="/logs" replace />} />
        <Route path="/settings/jira" element={isAdmin ? <JiraIntegrationPage /> : <Navigate to="/logs" replace />} />
        <Route path="*" element={<Navigate to="/logs" replace />} />
      </Routes>
      {location.pathname !== '/login/callback' && <FloatingAgent />}
    </div>
  );
}
