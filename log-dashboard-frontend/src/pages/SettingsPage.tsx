import { useEffect, useState } from 'react';
import { apiService, extractApiErrorMessage } from '../services/api';
import { PageHeader, StatusBadge } from '../components/UI';
import Modal from '../components/Modal';
import { useAuth } from '../context/AuthContext';
import type { JiraConfiguration, UserJiraMapping, JiraUser, NotificationPreference } from '../types';

export function ServerIcon() {
  return (
    <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
      <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
      <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
      <line x1="6" y1="6" x2="6.01" y2="6" />
      <line x1="6" y1="18" x2="6.01" y2="18" />
    </svg>
  );
}

export default function SettingsPage() {
  const { isAdmin } = useAuth();
  const [activeTab, setActiveTab] = useState<'jira' | 'notifications'>('jira');

  // ==========================================
  // JIRA INTEGRATION STATES
  // ==========================================
  const [config, setConfig] = useState<JiraConfiguration>({
    jiraBaseUrl: '',
    jiraEmail: '',
    jiraApiToken: '',
    jiraProjectKey: '',
    active: true
  });
  const [configLoading, setConfigLoading] = useState<boolean>(true);
  const [configSubmitting, setConfigSubmitting] = useState<boolean>(false);
  const [configTesting, setConfigTesting] = useState<boolean>(false);
  const [configError, setConfigError] = useState<string>('');
  const [configSuccess, setConfigSuccess] = useState<string>('');
  const [showConfigModal, setShowConfigModal] = useState<boolean>(false);

  // Mappings & Jira Users
  const [mappings, setMappings] = useState<UserJiraMapping[]>([]);
  const [jiraUsers, setJiraUsers] = useState<JiraUser[]>([]);
  const [mappingsLoading, setMappingsLoading] = useState<boolean>(true);
  const [mappingsError, setMappingsError] = useState<string>('');
  const [mappingsSuccess, setMappingsSuccess] = useState<string>('');
  const [mappingUpdatingUserId, setMappingUpdatingUserId] = useState<string | null>(null);

  // Developer Mapping Form
  const [isManual, setIsManual] = useState<boolean>(false);
  const [manualAccountId, setManualAccountId] = useState<string>('');
  const [manualDisplayName, setManualDisplayName] = useState<string>('');

  // ==========================================
  // NOTIFICATION PREFERENCES STATES
  // ==========================================
  const [notificationPref, setNotificationPref] = useState<NotificationPreference>({
    emailEnabled: false,
    createdAt: null,
    updatedAt: null
  });
  const [prefLoading, setPrefLoading] = useState<boolean>(true);
  const [prefSubmitting, setPrefSubmitting] = useState<boolean>(false);
  const [prefError, setPrefError] = useState<string>('');
  const [prefSuccess, setPrefSuccess] = useState<string>('');

  // ==========================================
  // DATA LOADERS
  // ==========================================
  const loadJiraConfig = async () => {
    try {
      setConfigLoading(true);
      const data = await apiService.getJiraConfiguration();
      if (data) {
        setConfig({
          id: data.id,
          jiraBaseUrl: data.jiraBaseUrl || '',
          jiraEmail: data.jiraEmail || '',
          jiraApiToken: data.jiraApiToken || '',
          jiraProjectKey: data.jiraProjectKey || '',
          active: data.active ?? true
        });
      }
      setConfigError('');
    } catch (err) {
      console.log('Failed to fetch Jira configuration, might not exist yet.', err);
    } finally {
      setConfigLoading(false);
    }
  };

  const loadJiraMappingsAndUsers = async () => {
    try {
      setMappingsLoading(true);
      setMappingsError('');
      setMappingsSuccess('');

      const mappingData = await apiService.getUserJiraMappings();
      setMappings(mappingData);

      if (!isAdmin && mappingData.length > 0) {
        const myMapping = mappingData[0];
        setManualAccountId(myMapping.jiraAccountId || '');
        setManualDisplayName(myMapping.jiraDisplayName || '');
      }

      try {
        const userData = await apiService.getJiraUsers();
        setJiraUsers(userData);
        if (userData.length === 0) {
          setIsManual(true);
        }
      } catch (userErr) {
        console.error('Failed to fetch Jira users:', userErr);
        setIsManual(true);
      }
    } catch (err) {
      setMappingsError(extractApiErrorMessage(err, 'Failed to load mappings'));
    } finally {
      setMappingsLoading(false);
    }
  };

  const loadNotificationPreferences = async () => {
    try {
      setPrefLoading(true);
      const data = await apiService.getNotificationPreferences();
      setNotificationPref(data);
      setPrefError('');
    } catch (err) {
      setPrefError(extractApiErrorMessage(err, 'Failed to load preferences'));
    } finally {
      setPrefLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'jira') {
      void loadJiraConfig();
      void loadJiraMappingsAndUsers();
    } else {
      void loadNotificationPreferences();
    }
  }, [activeTab]);

  // ==========================================
  // JIRA ACTIONS
  // ==========================================
  const handleTestConnection = async () => {
    if (!config.jiraBaseUrl.trim() || !config.jiraEmail.trim() || !config.jiraApiToken?.trim() || !config.jiraProjectKey.trim()) {
      setConfigError('All fields (including API Token) are required to test connection.');
      setConfigSuccess('');
      return;
    }

    try {
      setConfigTesting(true);
      setConfigError('');
      setConfigSuccess('');

      const payload = { ...config };
      if (payload.jiraApiToken && payload.jiraApiToken.startsWith('***')) {
        setConfigError('Please re-enter your API Token to test connection, as the existing token is masked.');
        setConfigTesting(false);
        return;
      }

      const res = await apiService.testJiraConnection(payload);
      setConfigSuccess(res.message || 'Connection test succeeded!');
    } catch (err) {
      setConfigError(extractApiErrorMessage(err, 'Connection test failed'));
    } finally {
      setConfigTesting(false);
    }
  };

  const handleSaveConfig = async () => {
    if (!config.jiraBaseUrl.trim()) {
      setConfigError('Jira Base URL is required');
      return;
    }
    if (!config.jiraEmail.trim()) {
      setConfigError('Jira Email is required');
      return;
    }
    if (!config.jiraProjectKey.trim()) {
      setConfigError('Jira Project Key is required');
      return;
    }

    try {
      setConfigSubmitting(true);
      setConfigError('');
      setConfigSuccess('');

      const payload = { ...config };
      if (payload.jiraApiToken && payload.jiraApiToken.startsWith('***')) {
        delete payload.jiraApiToken;
      } else if (!payload.jiraApiToken || !payload.jiraApiToken.trim()) {
        setConfigError('Jira API Token is required');
        setConfigSubmitting(false);
        return;
      }

      const saved = await apiService.saveJiraConfiguration(payload);
      setConfig({
        id: saved.id,
        jiraBaseUrl: saved.jiraBaseUrl || '',
        jiraEmail: saved.jiraEmail || '',
        jiraApiToken: saved.jiraApiToken || '',
        jiraProjectKey: saved.jiraProjectKey || '',
        active: saved.active ?? true
      });
      setConfigSuccess('Configuration saved successfully!');
      setShowConfigModal(false);
      // Reload mappings and users
      void loadJiraMappingsAndUsers();
    } catch (err) {
      setConfigError(extractApiErrorMessage(err, 'Failed to save configuration'));
    } finally {
      setConfigSubmitting(false);
    }
  };

  const handleSaveDeveloperMapping = async () => {
    const myMapping = mappings[0];
    if (!myMapping) return;

    let accountId = '';
    let displayName = '';

    if (isManual) {
      if (!manualAccountId.trim() || !manualDisplayName.trim()) {
        setMappingsError('Both Jira Account ID and Display Name are required for manual mapping.');
        return;
      }
      accountId = manualAccountId.trim();
      displayName = manualDisplayName.trim();
    } else {
      const selected = jiraUsers.find(u => u.accountId === manualAccountId);
      if (!selected) {
        setMappingsError('Please select a Jira User.');
        return;
      }
      accountId = selected.accountId;
      displayName = selected.displayName;
    }

    try {
      setMappingUpdatingUserId(myMapping.userId);
      setMappingsError('');
      setMappingsSuccess('');

      if (myMapping.id) {
        await apiService.updateUserJiraMapping(myMapping.id, {
          userId: myMapping.userId,
          jiraAccountId: accountId,
          jiraDisplayName: displayName,
          active: true
        });
      } else {
        await apiService.createUserJiraMapping({
          userId: myMapping.userId,
          jiraAccountId: accountId,
          jiraDisplayName: displayName,
          active: true
        });
      }

      setMappingsSuccess('Mapping saved successfully!');
      const mappingData = await apiService.getUserJiraMappings();
      setMappings(mappingData);
    } catch (err) {
      setMappingsError(extractApiErrorMessage(err, 'Failed to save mapping'));
    } finally {
      setMappingUpdatingUserId(null);
    }
  };

  const handleMappingChange = async (userId: string, mappingId: string | undefined, newAccountId: string) => {
    try {
      setMappingUpdatingUserId(userId);
      setMappingsError('');

      if (!newAccountId) {
        if (mappingId) {
          await apiService.deleteUserJiraMapping(mappingId);
        }
      } else {
        const selectedUser = jiraUsers.find(u => u.accountId === newAccountId);
        if (!selectedUser) return;

        if (mappingId) {
          await apiService.updateUserJiraMapping(mappingId, {
            userId,
            jiraAccountId: selectedUser.accountId,
            jiraDisplayName: selectedUser.displayName,
            active: true
          });
        } else {
          await apiService.createUserJiraMapping({
            userId,
            jiraAccountId: selectedUser.accountId,
            jiraDisplayName: selectedUser.displayName,
            active: true
          });
        }
      }

      const mappingData = await apiService.getUserJiraMappings();
      setMappings(mappingData);
    } catch (err) {
      setMappingsError(extractApiErrorMessage(err, 'Failed to save user mapping'));
    } finally {
      setMappingUpdatingUserId(null);
    }
  };

  // ==========================================
  // NOTIFICATION ACTIONS
  // ==========================================
  const handleSaveNotificationPref = async () => {
    try {
      setPrefSubmitting(true);
      setPrefError('');
      setPrefSuccess('');
      const data = await apiService.updateNotificationPreferences({
        emailEnabled: notificationPref.emailEnabled
      });
      setNotificationPref(data);
      setPrefSuccess('Notification preferences saved successfully!');
    } catch (err) {
      setPrefError(extractApiErrorMessage(err, 'Failed to update preferences'));
    } finally {
      setPrefSubmitting(false);
    }
  };

  // Jira configured status logic
  const isJiraConfigured = config.jiraBaseUrl && config.jiraProjectKey && config.active;

  return (
    <main className="page-container">
      {/* Tabs Menu */}
      <div className="obs-tabs-container" style={{ padding: '8px 16px 0 16px' }}>
        <button
          className={`obs-tab-btn ${activeTab === 'jira' ? 'active' : ''}`}
          onClick={() => setActiveTab('jira')}
        >
          Jira Integration
        </button>
        <button
          className={`obs-tab-btn ${activeTab === 'notifications' ? 'active' : ''}`}
          onClick={() => setActiveTab('notifications')}
        >
          Notification Preferences
        </button>
      </div>

      {/* JIRA INTEGRATION TAB */}
      {activeTab === 'jira' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {/* Connection Overview Section */}
          <div className="obs-section-panel">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '12px' }}>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <h3 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 600 }}>Jira Connection Status</h3>
                  <StatusBadge status={isJiraConfigured ? 'OK' : 'NO_DATA'} label={isJiraConfigured ? 'Connected' : 'Not Configured'} />
                </div>
                {isJiraConfigured ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px', fontSize: '0.78rem', color: 'var(--text-secondary)', marginTop: '4px' }}>
                    <span>Workspace: <strong style={{ color: 'var(--text-primary)' }}>{config.jiraBaseUrl}</strong></span>
                    <span>Project Target Key: <strong style={{ color: 'var(--text-primary)' }}>{config.jiraProjectKey}</strong></span>
                    <span>Integration Email: <strong style={{ color: 'var(--text-primary)' }}>{config.jiraEmail}</strong></span>
                  </div>
                ) : (
                  <p style={{ margin: 0, fontSize: '0.8rem', color: 'var(--text-dim)' }}>
                    Integrate LogFlow with Jira to automatically file tickets and assign stories directly to developers.
                  </p>
                )}
              </div>
              {isAdmin && (
                <button
                  className="btn"
                  style={{ background: 'var(--btn-bg)', border: '1px solid var(--border)' }}
                  onClick={() => {
                    setShowConfigModal(true);
                    setConfigError('');
                    setConfigSuccess('');
                  }}
                >
                  Configure Jira
                </button>
              )}
            </div>
          </div>

          {/* User Mappings Directory */}
          <div className="obs-table-workspace-panel">
            <div style={{ borderBottom: '1px solid var(--border)', padding: '14px 16px' }}>
              <h3 style={{ margin: 0, fontSize: '0.9rem', fontWeight: 600 }}>
                {isAdmin ? 'Developer Jira Account Mappings' : 'My Jira Mapping'}
              </h3>
              <p style={{ margin: '4px 0 0 0', fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                {isAdmin 
                  ? 'Map local developers to their Atlassian Jira accounts to assign stories filed from alerts.'
                  : 'Link your workspace account to your Jira account so alert stories are correctly assigned to you.'}
              </p>
            </div>

            {mappingsError && (
              <div style={{ padding: '12px 16px 0 16px' }}>
                <p className="error" style={{ margin: 0 }}>{mappingsError}</p>
              </div>
            )}

            {mappingsLoading ? (
              <div style={{ padding: '24px', textAlign: 'center', fontSize: '0.82rem', color: 'var(--text-dim)' }}>
                <span className="upload-spinner" style={{ width: '16px', height: '16px', display: 'inline-block', marginRight: '8px' }} />
                Loading account mappings...
              </div>
            ) : (
              <div style={{ padding: '16px' }}>
                {!isAdmin ? (
                  /* Developer Form */
                  <div style={{ maxWidth: '500px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    <div className="service-form-row">
                      <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Local Profile Account</label>
                      <input
                        className="form-control"
                        value={mappings[0]?.username || mappings[0]?.userId || ''}
                        disabled
                        style={{ background: 'var(--surface)' }}
                      />
                    </div>

                    {isManual ? (
                      <>
                        <div className="service-form-row">
                          <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira Account ID</label>
                          <input
                            className="form-control"
                            placeholder="e.g. 5b10ac8d14e1f72a39e8e2d4"
                            value={manualAccountId}
                            onChange={(e) => setManualAccountId(e.target.value)}
                          />
                        </div>
                        <div className="service-form-row">
                          <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira Display Name</label>
                          <input
                            className="form-control"
                            placeholder="e.g. John Doe"
                            value={manualDisplayName}
                            onChange={(e) => setManualDisplayName(e.target.value)}
                          />
                        </div>
                      </>
                    ) : (
                      <div className="service-form-row">
                        <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira Account Match</label>
                        <select
                          className="form-control"
                          value={manualAccountId}
                          onChange={(e) => setManualAccountId(e.target.value)}
                        >
                          <option value="">— Select Jira Account —</option>
                          {jiraUsers.map(user => (
                            <option key={user.accountId} value={user.accountId}>
                              {user.displayName}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    {jiraUsers.length > 0 && (
                      <div style={{ display: 'flex' }}>
                        <button
                          type="button"
                          onClick={() => {
                            setIsManual(!isManual);
                            setMappingsError('');
                          }}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: 'var(--accent)',
                            cursor: 'pointer',
                            fontSize: '0.78rem',
                            padding: 0,
                            textDecoration: 'underline'
                          }}
                        >
                          {isManual ? 'Select from active Jira user list' : 'Enter Jira Account ID manually'}
                        </button>
                      </div>
                    )}

                    {mappingsSuccess && (
                      <p style={{ color: 'var(--level-debug)', margin: 0, fontSize: '0.8rem', fontWeight: 600 }}>
                        {mappingsSuccess}
                      </p>
                    )}

                    <div style={{ marginTop: '4px' }}>
                      <button
                        className="btn"
                        onClick={() => void handleSaveDeveloperMapping()}
                        disabled={mappingUpdatingUserId !== null}
                        style={{ backgroundColor: 'var(--accent)', color: '#fff', border: 'none' }}
                      >
                        {mappingUpdatingUserId !== null ? 'Saving...' : 'Save Mapping'}
                      </button>
                    </div>
                  </div>
                ) : (
                  /* Admin Mappings Table */
                  <div className="table-scroll-area">
                    <table className="log-table mappings-table" style={{ width: '100%', margin: 0 }}>
                      <thead>
                        <tr>
                          <th>User Name</th>
                          <th>Owned Services</th>
                          <th>Jira User Assignee Mapping</th>
                        </tr>
                      </thead>
                      <tbody>
                        {mappings.map((mapping) => (
                          <tr key={mapping.userId}>
                            <td style={{ fontWeight: 600 }}>{mapping.username || mapping.userId}</td>
                            <td>
                              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                                {(mapping.ownedServices || '').split(',').map(s => s.trim()).filter(Boolean).map(s => (
                                  <span key={s} className="tag tag-debug" style={{ fontFamily: 'var(--font-mono)', fontSize: '0.68rem', padding: '1px 6px' }}>{s}</span>
                                ))}
                                {!(mapping.ownedServices || '').trim() && (
                                  <span className="tag tag-unassigned" style={{ fontSize: '0.68rem', padding: '1px 6px' }}>None</span>
                                )}
                              </div>
                            </td>
                            <td>
                              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                <select
                                  className="form-control"
                                  value={mapping.jiraAccountId || ''}
                                  onChange={(e) => void handleMappingChange(mapping.userId, mapping.id, e.target.value)}
                                  disabled={mappingUpdatingUserId === mapping.userId}
                                  style={{ maxWidth: '300px', height: '30px', padding: '0 8px' }}
                                >
                                  <option value="">— Unmapped —</option>
                                  {jiraUsers.map(user => (
                                    <option key={user.accountId} value={user.accountId}>
                                      {user.displayName}
                                    </option>
                                  ))}
                                </select>
                                {mappingUpdatingUserId === mapping.userId && (
                                  <span className="upload-spinner" style={{ width: '14px', height: '14px' }} />
                                )}
                              </div>
                            </td>
                          </tr>
                        ))}
                        {!mappings.length && (
                          <tr>
                            <td colSpan={3} style={{ textAlign: 'center', color: 'var(--text-dim)' }}>
                              No users own any services. Assign services to directory users first.
                            </td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* NOTIFICATION PREFERENCES TAB */}
      {activeTab === 'notifications' && (
        <div className="obs-section-panel">
          <h3 style={{ margin: '0 0 6px 0', fontSize: '0.95rem', fontWeight: 600 }}>Email Alerts Settings</h3>
          <p style={{ margin: '0 0 16px 0', fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
            Configure how and when you receive email alerts for system events.
          </p>

          {prefError && (
            <div className="error" style={{ marginBottom: '12px' }}>{prefError}</div>
          )}
          {prefSuccess && (
            <div style={{ color: 'var(--level-debug)', fontWeight: 600, fontSize: '0.8rem', marginBottom: '12px' }}>{prefSuccess}</div>
          )}

          {prefLoading ? (
            <div style={{ fontSize: '0.82rem', color: 'var(--text-dim)' }}>
              <span className="upload-spinner" style={{ width: '14px', height: '14px', display: 'inline-block', marginRight: '6px' }} />
              Loading preferences...
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px', maxWidth: '500px' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: '10px' }}>
                <input
                  type="checkbox"
                  id="email-alerts-checkbox"
                  checked={notificationPref.emailEnabled}
                  onChange={(e) => setNotificationPref(prev => ({ ...prev, emailEnabled: e.target.checked }))}
                  style={{ marginTop: '3px' }}
                />
                <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                  <label htmlFor="email-alerts-checkbox" style={{ fontSize: '0.85rem', fontWeight: 600, cursor: 'pointer', color: 'var(--text-primary)' }}>
                    Enable Email Alerts
                  </label>
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                    Receive email notifications immediately when critical error thresholds are breached.
                  </span>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                <button
                  className="btn"
                  style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
                  disabled={prefSubmitting}
                  onClick={() => void handleSaveNotificationPref()}
                >
                  {prefSubmitting ? 'Saving...' : 'Save Preferences'}
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* JIRA CONFIGURATION MODAL (ADMIN ONLY) */}
      <Modal
        open={showConfigModal}
        title="Jira Connection Configuration"
        onClose={() => setShowConfigModal(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '4px 0' }}>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira Base URL</label>
            <input
              className="form-control"
              placeholder="e.g. https://your-domain.atlassian.net"
              value={config.jiraBaseUrl}
              onChange={(e) => setConfig(prev => ({ ...prev, jiraBaseUrl: e.target.value }))}
            />
          </div>

          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira Email Address</label>
            <input
              className="form-control"
              type="email"
              placeholder="e.g. admin@company.com"
              value={config.jiraEmail}
              onChange={(e) => setConfig(prev => ({ ...prev, jiraEmail: e.target.value }))}
            />
          </div>

          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira API Token</label>
            <input
              className="form-control"
              type="password"
              placeholder={config.jiraApiToken ? "••••••••••••••••" : "Enter API token"}
              value={config.jiraApiToken}
              onChange={(e) => setConfig(prev => ({ ...prev, jiraApiToken: e.target.value }))}
              onFocus={() => {
                if (config.jiraApiToken?.startsWith('***')) {
                  setConfig(prev => ({ ...prev, jiraApiToken: '' }));
                }
              }}
            />
          </div>

          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)' }}>Jira Project Key</label>
            <input
              className="form-control"
              placeholder="e.g. PROJ"
              value={config.jiraProjectKey}
              onChange={(e) => setConfig(prev => ({ ...prev, jiraProjectKey: e.target.value }))}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '4px 0' }}>
            <input
              type="checkbox"
              id="modal-config-active"
              checked={config.active}
              onChange={(e) => setConfig(prev => ({ ...prev, active: e.target.checked }))}
            />
            <label htmlFor="modal-config-active" style={{ fontSize: '0.8rem', cursor: 'pointer', color: 'var(--text-primary)' }}>
              Active Configuration
            </label>
          </div>

          {configError && <p className="error" style={{ margin: 0 }}>{configError}</p>}
          {configSuccess && <p style={{ color: 'var(--level-debug)', margin: 0, fontWeight: 500 }}>{configSuccess}</p>}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px', marginTop: '8px' }}>
            <button
              className="btn"
              type="button"
              disabled={configTesting || configSubmitting}
              onClick={() => void handleTestConnection()}
            >
              {configTesting ? 'Testing...' : 'Test Connection'}
            </button>
            <button
              className="btn"
              style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
              disabled={configTesting || configSubmitting}
              onClick={() => void handleSaveConfig()}
            >
              {configSubmitting ? 'Saving...' : 'Save Configuration'}
            </button>
          </div>
        </div>
      </Modal>
    </main>
  );
}
