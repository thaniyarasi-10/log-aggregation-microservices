import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { useTheme } from '../context/ThemeContext';
import { useOrganization } from '../context/OrganizationContext';
import { apiService, extractApiErrorMessage } from '../services/api';
import type { AlertItem } from '../types';
import NotificationSettings from './NotificationSettings';
import Modal from './Modal';

const getClassName = ({ isActive }: { isActive: boolean }) =>
  isActive ? 'header-nav-link active' : 'header-nav-link';

// ── Moon icon (light mode → click to go dark)
function MoonIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
    </svg>
  );
}

// ── Sun icon (dark mode → click to go light)
function SunIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="5" />
      <line x1="12" y1="1" x2="12" y2="3" />
      <line x1="12" y1="21" x2="12" y2="23" />
      <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
      <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
      <line x1="1" y1="12" x2="3" y2="12" />
      <line x1="21" y1="12" x2="23" y2="12" />
      <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
      <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
    </svg>
  );
}

// ── Person icon SVG
function PersonIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  );
}

// ── DashboardIcon SVG
function DashboardIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="3" y="3" width="7" height="9" />
      <rect x="14" y="3" width="7" height="5" />
      <rect x="14" y="12" width="7" height="9" />
      <rect x="3" y="16" width="7" height="5" />
    </svg>
  );
}

// ── ExplorerIcon SVG (Terminal/Log Prompt Icon)
function ExplorerIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polyline points="4 17 10 11 4 5" />
      <line x1="12" y1="19" x2="20" y2="19" />
    </svg>
  );
}

// ── AlertsIcon SVG
function AlertsIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
      <line x1="12" y1="9" x2="12" y2="13" />
      <line x1="12" y1="17" x2="12.01" y2="17" />
    </svg>
  );
}

// ── ServicesIcon SVG
function ServicesIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
      <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
      <line x1="6" y1="6" x2="6.01" y2="6" />
      <line x1="6" y1="18" x2="6.01" y2="18" />
    </svg>
  );
}

// ── UsersIcon SVG
function UsersIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
      <circle cx="9" cy="7" r="4" />
      <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
    </svg>
  );
}

// ── SettingsIcon SVG
function SettingsIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  );
}

export default function Navbar() {
  const { user, isAdmin, isDev, canAccessUsers, canAccessServices, logout, refreshSession } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const { activeOrganization, organizations, currentRole, switchOrg, createOrg } = useOrganization();

  const [profileOpen, setProfileOpen] = useState(false);
  const [notifPanelOpen, setNotifPanelOpen] = useState(false);
  const [orgSwitcherOpen, setOrgSwitcherOpen] = useState(false);
  
  // Organization Creation Modal States
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createName, setCreateName] = useState('');
  const [createType, setCreateType] = useState<'BUSINESS' | 'PERSONAL'>('BUSINESS');
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');

  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const profileRef = useRef<HTMLDivElement>(null);
  const orgRef = useRef<HTMLDivElement>(null);

  const handleImageUpload = async (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setUploadError(null);

    try {
      await apiService.uploadProfileImage(file);
      await refreshSession();
    } catch (err) {
      console.error('Failed to upload image:', err);
      setUploadError(extractApiErrorMessage(err, 'Failed to upload profile image'));
    } finally {
      setUploading(false);
    }
  };

  // Close dropdowns when clicking outside
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) {
        setProfileOpen(false);
      }
      if (orgRef.current && !orgRef.current.contains(e.target as Node)) {
        setOrgSwitcherOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const displayName = user?.name || user?.email || 'User';
  const displayRole = currentRole || 'DEV';

  return (
    <>
      <header className="glass-panel dashboard-header">
        <div className="header-logo" style={{ display: 'flex', alignItems: 'center' }}>
          <span className="logo-icon">◷</span>
          <h1 className="header-title">LogFlow Observability</h1>

          {/* Global Organization Switcher */}
          {activeOrganization && (
            <div className="org-switcher-container" ref={orgRef} style={{ position: 'relative', marginLeft: '24px' }}>
              <button
                onClick={() => setOrgSwitcherOpen(!orgSwitcherOpen)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                  padding: '6px 12px',
                  backgroundColor: 'var(--card-bg, #1e293b)',
                  border: '1px solid var(--border-color, #334155)',
                  borderRadius: '6px',
                  color: 'var(--text-color, #ffffff)',
                  cursor: 'pointer',
                  fontWeight: 600,
                  fontSize: '0.85rem',
                  outline: 'none',
                  transition: 'border-color 0.2s'
                }}
              >
                <span>🏢 {activeOrganization.name}</span>
                <span style={{ fontSize: '0.65rem', color: 'var(--text-dim, #94a3b8)' }}>▼</span>
              </button>

              {orgSwitcherOpen && (
                <div
                  style={{
                    position: 'absolute',
                    top: '100%',
                    left: 0,
                    marginTop: '8px',
                    width: '240px',
                    backgroundColor: 'var(--card-bg, #1e293b)',
                    border: '1px solid var(--border-color, #334155)',
                    borderRadius: '8px',
                    boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.3)',
                    zIndex: 100,
                    padding: '8px 0'
                  }}
                >
                  <div style={{ padding: '4px 12px', fontSize: '0.7rem', fontWeight: 600, color: 'var(--text-dim, #94a3b8)', textTransform: 'uppercase', borderBottom: '1px solid var(--border-color, #334155)', paddingBottom: '6px', marginBottom: '4px' }}>
                    Switch Organization
                  </div>
                  <ul style={{ listStyle: 'none', padding: 0, margin: 0, maxHeight: '200px', overflowY: 'auto' }}>
                    {organizations.map((org) => (
                      <li key={org.id}>
                        <button
                          onClick={async () => {
                            setOrgSwitcherOpen(false);
                            await switchOrg(org.id);
                          }}
                          style={{
                            width: '100%',
                            textAlign: 'left',
                            padding: '8px 12px',
                            background: 'none',
                            border: 'none',
                            color: org.id === activeOrganization.id ? 'var(--accent, #3b82f6)' : 'var(--text-color, #ffffff)',
                            fontWeight: org.id === activeOrganization.id ? 700 : 500,
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            fontSize: '0.85rem'
                          }}
                        >
                          <span>{org.name}</span>
                          {org.id === activeOrganization.id && <span style={{ fontSize: '0.75rem' }}>✓</span>}
                        </button>
                      </li>
                    ))}
                  </ul>
                  <div style={{ borderTop: '1px solid var(--border-color, #334155)', marginTop: '4px', paddingTop: '4px' }} />
                  <button
                    onClick={() => {
                      setOrgSwitcherOpen(false);
                      setShowCreateModal(true);
                    }}
                    style={{
                      width: '100%',
                      textAlign: 'left',
                      padding: '8px 12px',
                      background: 'none',
                      border: 'none',
                      color: 'var(--accent, #3b82f6)',
                      fontWeight: 600,
                      cursor: 'pointer',
                      fontSize: '0.85rem',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '6px'
                    }}
                  >
                    <span>➕ Create Organization</span>
                  </button>
                </div>
              )}
            </div>
          )}
        </div>

        <div className="header-actions">
          <nav className="header-nav">
             <NavLink to="/logs" className={getClassName} title="Dashboard"><DashboardIcon /></NavLink>
             <NavLink to="/explorer" className={getClassName} title="Log Explorer"><ExplorerIcon /></NavLink>
             <NavLink to="/alerts" className={getClassName} title="Alerts"><AlertsIcon /></NavLink>
             {canAccessServices && <NavLink to="/services" className={getClassName} title="Services"><ServicesIcon /></NavLink>}
             {canAccessUsers && <NavLink to="/users" className={getClassName} title="Users"><UsersIcon /></NavLink>}
             {(isAdmin || isDev) && <NavLink to="/settings" className={getClassName} title="Settings"><SettingsIcon /></NavLink>}
          </nav>

          {/* Theme toggle — SVG moon/sun */}
          <button
            className="btn header-icon-btn"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
            title={theme === 'dark' ? 'Light mode' : 'Dark mode'}
          >
            {theme === 'dark' ? <SunIcon /> : <MoonIcon />}
          </button>
        </div>
      </header>

      {/* Profile button — fixed bottom-left, outside the header flow */}
      <div className="profile-anchor" ref={profileRef}>
        <button
          className={`profile-trigger ${profileOpen ? 'active' : ''}`}
          onClick={() => { setProfileOpen((o) => !o); setNotifPanelOpen(false); }}
          aria-label="Profile"
          title="Profile"
          style={user?.profileImageUrl ? { padding: 0, overflow: 'hidden' } : {}}
        >
          {user?.profileImageUrl ? (
            <img src={user.profileImageUrl} alt="Avatar" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <PersonIcon />
          )}
        </button>

        {profileOpen && (
          <div className="profile-dropdown">
            <div className="header-profile-avatar-wrap">
              {user?.profileImageUrl ? (
                <img src={user.profileImageUrl} alt="Avatar" className="header-profile-avatar" />
              ) : (
                <div className="header-profile-avatar-placeholder">
                  <PersonIcon />
                </div>
              )}
              <label className="header-profile-upload-label" title="Upload new profile picture">
                {uploading ? (
                  <span className="upload-spinner" />
                ) : (
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                    <polyline points="17 8 12 3 7 8" />
                    <line x1="12" y1="3" x2="12" y2="15" />
                  </svg>
                )}
                <input
                  type="file"
                  accept="image/png, image/jpeg, image/gif"
                  onChange={handleImageUpload}
                  style={{ display: 'none' }}
                  disabled={uploading}
                />
              </label>
            </div>
            {uploadError && (
              <div className="profile-upload-error">{uploadError}</div>
            )}
            <div className="header-profile-info">
              <div className="header-profile-name">{displayName}</div>
              {user?.email && user.email !== displayName && (
                <div className="header-profile-email">{user.email}</div>
              )}
              {activeOrganization && (
                <div className="header-profile-role" style={{ color: 'var(--accent, #3b82f6)', fontWeight: 600, fontSize: '0.75rem', marginTop: '4px' }}>
                  🏢 {activeOrganization.name} ({displayRole})
                </div>
              )}
            </div>
            <div className="header-dropdown-divider" />
            <NavLink
              to="/organization/settings"
              className="header-profile-action"
              onClick={() => setProfileOpen(false)}
              style={{ textDecoration: 'none', display: 'block' }}
            >
              ⚙️ Organization Settings
            </NavLink>
            <NavLink
              to="/organization/members"
              className="header-profile-action"
              onClick={() => setProfileOpen(false)}
              style={{ textDecoration: 'none', display: 'block' }}
            >
              👥 Members
            </NavLink>
            {(currentRole === 'OWNER' || currentRole === 'ADMIN') && (
              <NavLink
                to="/organization/join-requests"
                className="header-profile-action"
                onClick={() => setProfileOpen(false)}
                style={{ textDecoration: 'none', display: 'block' }}
              >
                📥 Join Requests
              </NavLink>
            )}
            <div className="header-dropdown-divider" />
            <button
              className="header-profile-action"
              onClick={() => { setProfileOpen(false); setNotifPanelOpen(true); }}
            >
              🔔 Notification Settings
            </button>
            <div className="header-dropdown-divider" />
            <button
              className="header-profile-logout"
              onClick={() => { setProfileOpen(false); void logout(); }}
            >
              Sign out
            </button>
          </div>
        )}

        {/* Notification settings side panel — slides in above the profile anchor */}
        {notifPanelOpen && (
          <>
            <div
              className="ns-panel-backdrop"
              onClick={() => setNotifPanelOpen(false)}
              aria-hidden="true"
            />
            <div className="ns-panel" role="dialog" aria-label="Notification Settings">
              <div className="ns-panel-header">
                <span className="ns-panel-title">Notification Settings</span>
                <button
                  className="ns-panel-close"
                  onClick={() => setNotifPanelOpen(false)}
                  aria-label="Close"
                >
                  <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                    <path d="M3 3l10 10M13 3L3 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
                  </svg>
                </button>
              </div>
              <NotificationSettings onSaved={() => setNotifPanelOpen(false)} />
            </div>
          </>
        )}
      </div>

      {/* Create Org Modal */}
      <Modal
        open={showCreateModal}
        title="Create New Organization"
        onClose={() => { setShowCreateModal(false); setCreateName(''); setCreateError(''); }}
      >
        <form onSubmit={async (e) => {
          e.preventDefault();
          if (!createName.trim()) return;
          setCreating(true);
          setCreateError('');
          try {
            await createOrg(createName, createType);
            setShowCreateModal(false);
            setCreateName('');
          } catch (err) {
            setCreateError(extractApiErrorMessage(err, 'Failed to create organization. Note: business domains require non-personal email.'));
          } finally {
            setCreating(false);
          }
        }}>
          {createError && (
            <div style={{ color: '#f87171', backgroundColor: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.4)', padding: '8px 12px', borderRadius: '4px', marginBottom: '12px', fontSize: '0.8rem' }}>
              {createError}
            </div>
          )}
          <div style={{ marginBottom: '12px' }}>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>ORGANIZATION NAME</label>
            <input
              type="text"
              className="form-control"
              placeholder="e.g. Acme Corp"
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              required
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          </div>
          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '4px' }}>ORGANIZATION TYPE</label>
            <select
              className="form-control"
              value={createType}
              onChange={(e) => setCreateType(e.target.value as 'BUSINESS' | 'PERSONAL')}
              style={{ width: '100%', padding: '8px' }}
            >
              <option value="BUSINESS">Business (Domain derived from email)</option>
              <option value="PERSONAL">Personal Workspace</option>
            </select>
          </div>
          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <button type="button" className="btn" onClick={() => setShowCreateModal(false)} style={{ background: 'none', border: '1px solid var(--border-color)', color: 'var(--text-color)' }}>Cancel</button>
            <button type="submit" className="btn btn-primary" style={{ background: 'var(--accent)', color: '#fff', border: 'none' }} disabled={creating}>
              {creating ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </Modal>
    </>
  );
}
