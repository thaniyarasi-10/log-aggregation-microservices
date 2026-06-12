import { useState, useEffect, useMemo } from 'react';
import { apiService } from '../services/api';
import { PageHeader, StatusBadge, EmptyState } from '../components/UI';
import type { AlertItem, UserJiraMapping, ServiceRecord } from '../types';

// Deterministic UUID generator to map alert service/message to a stable alertId
function generateDeterministicUuid(service: string, message: string): string {
  const str = `${service}:${message}`;
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  const part1 = Math.abs(hash).toString(16).padStart(8, '0');
  const part2 = Math.abs(hash * 3).toString(16).padStart(4, '0');
  const part3 = '4' + Math.abs(hash * 7).toString(16).substring(0, 3).padStart(3, '0');
  const part4 = 'a' + Math.abs(hash * 11).toString(16).substring(0, 3).padStart(3, '0');
  const part5 = Math.abs(hash * 13).toString(16).padStart(12, '0').substring(0, 12);
  return `${part1}-${part2}-${part3}-${part4}-${part5}`;
}

export function ServerIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0, color: 'var(--text-secondary)' }}>
      <rect x="2" y="2" width="20" height="8" rx="2" ry="2" />
      <rect x="2" y="14" width="20" height="8" rx="2" ry="2" />
      <line x1="6" y1="6" x2="6.01" y2="6" />
      <line x1="6" y1="18" x2="6.01" y2="18" />
    </svg>
  );
}

export default function AlertsPage() {
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [services, setServices] = useState<ServiceRecord[]>([]);
  const [mappings, setMappings] = useState<UserJiraMapping[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Filtering State
  const [search, setSearch] = useState('');
  const [selectedService, setSelectedService] = useState('');
  const [selectedSeverity, setSelectedSeverity] = useState('');
  const [selectedStatus, setSelectedStatus] = useState(''); // OPEN, RESOLVED

  // Locally triggered Jira tickets cache to display immediately without page reload
  const [triggeredTickets, setTriggeredTickets] = useState<Record<string, { key: string; url: string; assignee: string }>>({});
  const [creatingTicketId, setCreatingTicketId] = useState<string | null>(null);

  // Load created tickets cache from local storage on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem('obs_triggered_tickets');
      if (stored) {
        setTriggeredTickets(JSON.parse(stored));
      }
    } catch {
      // ignore
    }
  }, []);

  const loadAlertsData = async () => {
    try {
      setLoading(true);
      const [alertsData, servicesData, mappingsData] = await Promise.all([
        apiService.fetchAlerts(),
        apiService.fetchServices(),
        apiService.getUserJiraMappings(),
      ]);
      setAlerts(alertsData);
      setServices(servicesData);
      setMappings(mappingsData);
      setError('');
    } catch {
      setError('Failed to retrieve active alerts from notification service.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadAlertsData();
  }, []);

  // Compute Assignee and Jira status details based on service ownership and mapping
  const alertsWithJira = useMemo(() => {
    return alerts.map((alert) => {
      const alertId = generateDeterministicUuid(alert.service, alert.message);
      
      // Check if ticket is in locally triggered cache
      const cached = triggeredTickets[alertId];
      
      // Determine service primary owner mapping
      const service = services.find((s) => s.name.toLowerCase() === alert.service.toLowerCase());
      const primaryOwner = service?.owners?.find((o) => o.primary);
      const ownerMapping = primaryOwner
        ? mappings.find((m) => m.userId.toLowerCase() === primaryOwner.userId.toLowerCase())
        : null;

      const defaultAssignee = ownerMapping?.jiraDisplayName || primaryOwner?.username || 'Unassigned';

      return {
        ...alert,
        alertId,
        jiraKey: cached?.key || null,
        jiraUrl: cached?.url || null,
        assigneeName: cached?.assignee || defaultAssignee,
        status: cached ? 'OPEN' : 'RESOLVED', // Resolve status from local mapping (mock resolves for simulation)
      };
    });
  }, [alerts, services, mappings, triggeredTickets]);

  // Handle Trigger Jira Story
  const handleCreateJiraTicket = async (item: typeof alertsWithJira[0]) => {
    try {
      setCreatingTicketId(item.alertId);
      const payload = {
        alertId: item.alertId,
        alertName: item.message,
        serviceName: item.service,
        priority: item.severity.toUpperCase(),
        triggeredAt: item.timestamp || new Date().toISOString(),
        alertRule: item.message,
        observedValue: 'N/A',
        threshold: 'N/A',
        timeWindow: '24h',
        errorCount: item.count,
        topErrors: item.message,
        alertUrl: window.location.href,
      };

      const res = await apiService.triggerJiraStory(payload);
      if (res.status === 'CREATED' || res.status === 'SUCCESS') {
        const nextTickets = {
          ...triggeredTickets,
          [item.alertId]: {
            key: res.jiraIssueKey || 'JIRA-TICKET',
            url: res.jiraIssueUrl || '#',
            assignee: item.assigneeName,
          },
        };
        setTriggeredTickets(nextTickets);
        localStorage.setItem('obs_triggered_tickets', JSON.stringify(nextTickets));
      } else {
        alert(`Failed to create ticket: ${res.message}`);
      }
    } catch (err) {
      alert('Failed to connect to Jira configuration endpoint.');
    } finally {
      setCreatingTicketId(null);
    }
  };

  // Filter alerts list
  const filteredAlerts = useMemo(() => {
    return alertsWithJira.filter((item) => {
      const matchesSearch = item.message.toLowerCase().includes(search.toLowerCase()) ||
                            item.service.toLowerCase().includes(search.toLowerCase());
      const matchesService = selectedService ? item.service.toLowerCase() === selectedService.toLowerCase() : true;
      const matchesSeverity = selectedSeverity ? item.severity.toUpperCase() === selectedSeverity.toUpperCase() : true;
      const matchesStatus = selectedStatus ? item.status === selectedStatus : true;

      return matchesSearch && matchesService && matchesSeverity && matchesStatus;
    });
  }, [alertsWithJira, search, selectedService, selectedSeverity, selectedStatus]);

  const uniqueServices = useMemo(() => {
    return Array.from(new Set(alerts.map((a) => a.service)));
  }, [alerts]);

  return (
    <main className="page-container">
      {loading ? (
        <div className="obs-empty-state">
          <span className="upload-spinner" style={{ width: '24px', height: '24px', marginBottom: '12px' }} />
          <h4 className="obs-empty-title">Loading Alerts</h4>
          <p className="obs-empty-desc">Fetching system logs and triggers from the notifications broker...</p>
        </div>
      ) : error ? (
        <EmptyState title="Notifications Offline" description={error} icon="⚠️" />
      ) : (
        /* Unified Table Workspace Panel for Filters and Alerts Table */
        <div className="obs-table-workspace-panel">
          {/* Filters Toolbar docked inside the table workspace */}
          <div className="obs-filter-toolbar">
            {/* Search */}
            <div className="obs-search-wrapper">
              <span className="obs-search-icon">🔍</span>
              <input
                type="text"
                className="form-control obs-search-input"
                placeholder="Search alerts by message or service..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>

            {/* Service */}
            <div>
              <select
                className="form-control"
                style={{ height: '31px', padding: '0 8px' }}
                value={selectedService}
                onChange={(e) => setSelectedService(e.target.value)}
              >
                <option value="">— All Services —</option>
                {uniqueServices.map((svc) => (
                  <option key={svc} value={svc}>{svc}</option>
                ))}
              </select>
            </div>

            {/* Severity */}
            <div>
              <select
                className="form-control"
                style={{ height: '31px', padding: '0 8px' }}
                value={selectedSeverity}
                onChange={(e) => setSelectedSeverity(e.target.value)}
              >
                <option value="">— All Severities —</option>
                <option value="CRITICAL">Critical</option>
                <option value="WARNING">Warning</option>
                <option value="INFO">Info</option>
              </select>
            </div>

            {/* Status */}
            <div>
              <select
                className="form-control"
                style={{ height: '31px', padding: '0 8px' }}
                value={selectedStatus}
                onChange={(e) => setSelectedStatus(e.target.value)}
              >
                <option value="">— All Statuses —</option>
                <option value="OPEN">Open (Jira Ticket)</option>
                <option value="RESOLVED">Resolved / Untracked</option>
              </select>
            </div>

            {(search || selectedService || selectedSeverity || selectedStatus) && (
              <button
                className="btn"
                style={{ background: 'transparent', border: 'none', textDecoration: 'underline', color: 'var(--accent)' }}
                onClick={() => {
                  setSearch('');
                  setSelectedService('');
                  setSelectedSeverity('');
                  setSelectedStatus('');
                }}
              >
                Clear
              </button>
            )}
          </div>

          {filteredAlerts.length === 0 ? (
            <EmptyState
              title="No alerts found"
              description="Adjust your severity, status, or search filters to find historical alerts."
              icon="🔔"
            />
          ) : (
            <div className="table-scroll-area">
              <table className="log-table alerts-table" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th>Timestamp</th>
                    <th>Service</th>
                    <th>Severity</th>
                    <th>Alert Message</th>
                    <th>Jira Ticket</th>
                    <th>Assigned User</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {filteredAlerts.map((item) => {
                    const isCreating = creatingTicketId === item.alertId;
                    const hasTicket = !!item.jiraKey;

                    return (
                      <tr key={item.alertId}>
                        <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
                          {item.timestamp ? new Date(item.timestamp).toLocaleString() : 'N/A'}
                        </td>
                        <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{item.service}</td>
                        <td>
                          <span className={`tag ${item.severity.toUpperCase() === 'CRITICAL' ? 'tag-error' : 'tag-warn'}`}>
                            {item.severity}
                          </span>
                        </td>
                        <td className="message-cell" title={item.message}>
                          {item.message}
                        </td>
                        <td>
                          {hasTicket ? (
                            <a
                              href={item.jiraUrl || '#'}
                              target="_blank"
                              rel="noopener noreferrer"
                              style={{ color: 'var(--accent)', fontWeight: 600, textDecoration: 'underline' }}
                            >
                              {item.jiraKey}
                            </a>
                          ) : (
                            <button
                              className="btn"
                              style={{ padding: '2px 8px', fontSize: '0.72rem', background: 'var(--surface-raised)' }}
                              disabled={isCreating}
                              onClick={() => void handleCreateJiraTicket(item)}
                            >
                              {isCreating ? 'Creating...' : 'Create Ticket'}
                            </button>
                          )}
                        </td>
                        <td style={{ fontWeight: 500 }}>{item.assigneeName}</td>
                        <td>
                          <StatusBadge status={item.status} label={item.status} />
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </main>
  );
}
