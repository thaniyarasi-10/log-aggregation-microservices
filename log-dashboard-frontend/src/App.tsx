import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useEffect } from 'react';
import Navbar from './components/Navbar';
import LoginOverlay from './components/LoginOverlay';
import FloatingAgent from './components/FloatingAgent.jsx';
import CommandSearch from './components/CommandSearch';
import { useAuth } from './context/AuthContext';
import { useOrganization } from './context/OrganizationContext';
import LogsPage from './pages/LogsPage';
import LogExplorerPage from './pages/LogExplorerPage';
import AlertsPage from './pages/AlertsPage';
import ServicesPage from './pages/ServicesPage';
import UsersPage from './pages/UsersPage';
import LoginCallback from './pages/LoginCallback';
import SettingsPage from './pages/SettingsPage';
import OnboardingPage from './pages/OnboardingPage';
import OrganizationMembersPage from './pages/OrganizationMembersPage';
import OrganizationJoinRequestsPage from './pages/OrganizationJoinRequestsPage';
import OrganizationSettingsPage from './pages/OrganizationSettingsPage';

function RouteLogger() {
  const location = useLocation();

  useEffect(() => {
    console.log('Route changed:', location.pathname);
  }, [location.pathname]);

  return null;
}

export default function App() {
  const { status, login, canAccessUsers, canAccessServices, isAdmin, isDev } = useAuth();
  const { organizations, isLoading: orgLoading } = useOrganization();
  const location = useLocation();

  if (status !== 'authenticated' && location.pathname !== '/login/callback') {
    return (
      <LoginOverlay
        login={login}
        statusMessage={status === 'loading' ? 'Checking existing session...' : 'Not signed in. Use Microsoft Entra ID to continue.'}
      />
    );
  }

  if (status === 'authenticated' && orgLoading) {
    return (
      <div style={{
        display: 'flex',
        height: '100vh',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: '1.2rem',
        color: 'var(--text-color, #ffffff)',
        backgroundColor: 'var(--bg-color, #0f172a)'
      }}>
        Loading organization context...
      </div>
    );
  }

  if (status === 'authenticated' && organizations.length === 0 && location.pathname !== '/login/callback') {
    return <OnboardingPage />;
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
        
        {/* Multi-Tenant Organization Pages */}
        <Route path="/organization/members" element={<OrganizationMembersPage />} />
        <Route path="/organization/join-requests" element={<OrganizationJoinRequestsPage />} />
        <Route path="/organization/settings" element={<OrganizationSettingsPage />} />

        <Route path="*" element={<Navigate to="/logs" replace />} />
      </Routes>
      {location.pathname !== '/login/callback' && <FloatingAgent />}
      {location.pathname !== '/login/callback' && <CommandSearch />}
    </div>
  );
}
