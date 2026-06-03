import { useEffect, useState } from 'react';
import { apiService, extractApiErrorMessage } from '../services/api';
import type { JiraConfiguration, UserJiraMapping, JiraUser } from '../types';

export default function JiraIntegrationPage() {
  const [activeTab, setActiveTab] = useState<'config' | 'mappings'>('config');

  // Configuration States
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

  // User Mappings States
  const [mappings, setMappings] = useState<UserJiraMapping[]>([]);
  const [jiraUsers, setJiraUsers] = useState<JiraUser[]>([]);
  const [mappingsLoading, setMappingsLoading] = useState<boolean>(true);
  const [mappingsError, setMappingsError] = useState<string>('');
  const [mappingUpdatingUserId, setMappingUpdatingUserId] = useState<string | null>(null);

  // Load Jira Configuration
  const loadConfig = async () => {
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
      // 204 or Not Found returns empty, which is fine
      console.log('Failed to fetch Jira configuration, might not exist yet.', err);
    } finally {
      setConfigLoading(false);
    }
  };

  // Load Mappings & Jira Users
  const loadMappingsAndUsers = async () => {
    try {
      setMappingsLoading(true);
      setMappingsError('');
      
      const mappingData = await apiService.getUserJiraMappings();
      setMappings(mappingData);

      // Load Jira users from active configuration
      try {
        const userData = await apiService.getJiraUsers();
        setJiraUsers(userData);
      } catch (userErr) {
        console.error('Failed to fetch Jira users (Jira config might be missing/invalid):', userErr);
        setMappingsError('Jira Connection is required to load Jira users. Please verify Tab 1 Configuration.');
      }
    } catch (err) {
      setMappingsError(extractApiErrorMessage(err, 'Failed to load mappings'));
    } finally {
      setMappingsLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'config') {
      void loadConfig();
    } else {
      void loadMappingsAndUsers();
    }
  }, [activeTab]);

  const handleTestConnection = async () => {
    if (!config.jiraBaseUrl.trim() || !config.jiraEmail.trim() || !config.jiraApiToken?.trim() || !config.jiraProjectKey.trim()) {
      setConfigError('All fields (including API Token) are required to test the connection.');
      setConfigSuccess('');
      return;
    }

    try {
      setConfigTesting(true);
      setConfigError('');
      setConfigSuccess('');
      
      const payload = { ...config };
      // If api token was loaded as masked and not changed, it starts with *
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
        // Omit API token if it is still masked so backend doesn't overwrite it with literal masks
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
    } catch (err) {
      setConfigError(extractApiErrorMessage(err, 'Failed to save configuration'));
    } finally {
      setConfigSubmitting(false);
    }
  };

  const handleMappingChange = async (userId: string, mappingId: string | undefined, newAccountId: string) => {
    try {
      setMappingUpdatingUserId(userId);
      setMappingsError('');

      if (!newAccountId) {
        // If unmapping and there's a mapping id, delete it
        if (mappingId) {
          await apiService.deleteUserJiraMapping(mappingId);
        }
      } else {
        const selectedUser = jiraUsers.find(u => u.accountId === newAccountId);
        if (!selectedUser) return;

        if (mappingId) {
          // Update mapping
          await apiService.updateUserJiraMapping(mappingId, {
            userId,
            jiraAccountId: selectedUser.accountId,
            jiraDisplayName: selectedUser.displayName,
            active: true
          });
        } else {
          // Create mapping
          await apiService.createUserJiraMapping({
            userId,
            jiraAccountId: selectedUser.accountId,
            jiraDisplayName: selectedUser.displayName,
            active: true
          });
        }
      }

      // Reload mappings list
      const mappingData = await apiService.getUserJiraMappings();
      setMappings(mappingData);
    } catch (err) {
      setMappingsError(extractApiErrorMessage(err, 'Failed to save user mapping'));
    } finally {
      setMappingUpdatingUserId(null);
    }
  };

  return (
    <main className="dashboard-grid">
      <section className="dashboard-main">
        {/* Tab Selection */}
        <section className="glass-panel" style={{ minHeight: 'auto', marginBottom: '1rem', padding: '0.5rem 1rem' }}>
          <div className="ld-tabs" role="tablist" style={{ borderBottom: 'none', margin: 0 }}>
            <button
              role="tab"
              aria-selected={activeTab === 'config'}
              className={`ld-tab${activeTab === 'config' ? ' ld-tab-active' : ''}`}
              onClick={() => setActiveTab('config')}
            >
              Jira Configuration
            </button>
            <button
              role="tab"
              aria-selected={activeTab === 'mappings'}
              className={`ld-tab${activeTab === 'mappings' ? ' ld-tab-active' : ''}`}
              onClick={() => setActiveTab('mappings')}
            >
              User Jira Mappings
            </button>
          </div>
        </section>

        {/* Tab 1: Configuration Form */}
        {activeTab === 'config' && (
          <section className="glass-panel table-container">
            <div className="table-header"><h2>Jira Connection Configuration</h2></div>
            {configLoading ? (
              <div className="table-scroll-area state-message">Loading configuration...</div>
            ) : (
              <div className="table-scroll-area" style={{ padding: '1.5rem' }}>
                <div style={{ maxWidth: '600px', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                  <div className="filter-group">
                    <label>Jira Base URL</label>
                    <input
                      className="form-control"
                      placeholder="e.g. https://your-domain.atlassian.net"
                      value={config.jiraBaseUrl}
                      onChange={(e) => setConfig(prev => ({ ...prev, jiraBaseUrl: e.target.value }))}
                    />
                  </div>

                  <div className="filter-group">
                    <label>Jira Email Address</label>
                    <input
                      className="form-control"
                      type="email"
                      placeholder="e.g. admin@company.com"
                      value={config.jiraEmail}
                      onChange={(e) => setConfig(prev => ({ ...prev, jiraEmail: e.target.value }))}
                    />
                  </div>

                  <div className="filter-group">
                    <label>Jira API Token</label>
                    <input
                      className="form-control"
                      type="password"
                      placeholder={config.jiraApiToken ? "••••••••••••••••" : "Enter API token"}
                      value={config.jiraApiToken}
                      onChange={(e) => setConfig(prev => ({ ...prev, jiraApiToken: e.target.value }))}
                      onFocus={(e) => {
                        // Clear masked token on focus so they can type a new one
                        if (config.jiraApiToken?.startsWith('***')) {
                          setConfig(prev => ({ ...prev, jiraApiToken: '' }));
                        }
                      }}
                    />
                  </div>

                  <div className="filter-group">
                    <label>Jira Project Key</label>
                    <input
                      className="form-control"
                      placeholder="e.g. PROJ"
                      value={config.jiraProjectKey}
                      onChange={(e) => setConfig(prev => ({ ...prev, jiraProjectKey: e.target.value }))}
                    />
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <input
                      type="checkbox"
                      id="config-active-checkbox"
                      checked={config.active ?? true}
                      onChange={(e) => setConfig(prev => ({ ...prev, active: e.target.checked }))}
                    />
                    <label htmlFor="config-active-checkbox" style={{ fontSize: '0.85rem', cursor: 'pointer' }}>
                      Active Configuration
                    </label>
                  </div>

                  {configError && <p className="error" style={{ margin: 0 }}>{configError}</p>}
                  {configSuccess && <p style={{ color: 'var(--level-debug)', margin: 0, fontWeight: 500 }}>{configSuccess}</p>}

                  <div style={{ display: 'flex', gap: '10px', marginTop: '0.5rem' }}>
                    <button
                      className="btn"
                      disabled={configTesting || configSubmitting}
                      onClick={() => void handleTestConnection()}
                    >
                      {configTesting ? 'Testing...' : 'Test Connection'}
                    </button>
                    <button
                      className="btn"
                      disabled={configTesting || configSubmitting}
                      onClick={() => void handleSaveConfig()}
                      style={{ backgroundColor: 'var(--accent)', color: '#fff' }}
                    >
                      {configSubmitting ? 'Saving...' : 'Save Configuration'}
                    </button>
                  </div>
                </div>
              </div>
            )}
          </section>
        )}

        {/* Tab 2: User Jira Mappings */}
        {activeTab === 'mappings' && (
          <section className="glass-panel table-container">
            <div className="table-header"><h2>User Jira Mappings</h2></div>
            {mappingsError && (
              <div className="table-scroll-area" style={{ padding: '1rem 1.5rem 0 1.5rem' }}>
                <p className="error">{mappingsError}</p>
              </div>
            )}
            {mappingsLoading ? (
              <div className="table-scroll-area state-message">Loading owners & mappings...</div>
            ) : (
              <div className="table-scroll-area">
                <table className="log-table">
                  <thead>
                    <tr>
                      <th>User Name</th>
                      <th>Owned Services</th>
                      <th>Jira User Assignee</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mappings.map((mapping) => (
                      <tr key={mapping.userId}>
                        <td>{mapping.username || mapping.userId}</td>
                        <td>{mapping.ownedServices || '—'}</td>
                        <td>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <select
                              className="form-control"
                              value={mapping.jiraAccountId || ''}
                              onChange={(e) => void handleMappingChange(mapping.userId, mapping.id, e.target.value)}
                              disabled={mappingUpdatingUserId === mapping.userId}
                              style={{ maxWidth: '300px' }}
                            >
                              <option value="">— Unmapped —</option>
                              {jiraUsers.map(user => (
                                <option key={user.accountId} value={user.accountId}>
                                  {user.displayName}
                                </option>
                              ))}
                            </select>
                            {mappingUpdatingUserId === mapping.userId && (
                              <span className="upload-spinner" style={{ borderTopColor: 'var(--accent)' }} />
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                    {!mappings.length && (
                      <tr>
                        <td colSpan={3}>No users own any services. Assign services to users first.</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        )}
      </section>
    </main>
  );
}
