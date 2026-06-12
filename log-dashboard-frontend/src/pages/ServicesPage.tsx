import { useEffect, useRef, useState, useMemo } from 'react';
import Modal from '../components/Modal';
import { useAuth } from '../context/AuthContext';
import { apiService, extractApiErrorMessage } from '../services/api';
import { PageHeader, MetricCard, StatusBadge, EmptyState } from '../components/UI';
import type { ServiceAccessRequest, ServiceRecord, ServiceHealth, MetricsResponse, LogEvent, AlertItem } from '../types';

type ServiceForm = {
  name: string;
  description: string;
};

const emptyServiceForm: ServiceForm = {
  name: '',
  description: ''
};

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

export default function ServicesPage() {
  const { isAdmin } = useAuth();
  const addNameInputRef = useRef<HTMLInputElement | null>(null);

  // Core data states
  const [services, setServices] = useState<ServiceRecord[]>([]);
  const [requests, setRequests] = useState<ServiceAccessRequest[]>([]);
  const [healthData, setHealthData] = useState<ServiceHealth[]>([]);
  const [logsHealth24h, setLogsHealth24h] = useState<any[]>([]);
  const [enrichedMetrics, setEnrichedMetrics] = useState<Record<string, { avgResponseTime: number; errorRate: number }>>({});

  // Loading and error states
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string>('');
  const [requestsLoading, setRequestsLoading] = useState<boolean>(true);
  const [requestsError, setRequestsError] = useState<string>('');
  const [healthLoading, setHealthLoading] = useState<boolean>(true);
  const [healthError, setHealthError] = useState<string>('');
  const [actionError, setActionError] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Tab & Search state
  const [activeTab, setActiveTab] = useState<'health' | 'ownership' | 'requests'>('health');
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Service drawer state
  const [selectedService, setSelectedService] = useState<ServiceRecord | null>(null);
  const [drawerMetrics, setDrawerMetrics] = useState<MetricsResponse | null>(null);
  const [drawerMetricsLoading, setDrawerMetricsLoading] = useState<boolean>(false);
  const [drawerLogs, setDrawerLogs] = useState<LogEvent[]>([]);
  const [drawerLogsLoading, setDrawerLogsLoading] = useState<boolean>(false);
  const [drawerAlerts, setDrawerAlerts] = useState<AlertItem[]>([]);
  const [drawerAlertsLoading, setDrawerAlertsLoading] = useState<boolean>(false);

  // Service secret states
  const [serviceSecret, setServiceSecret] = useState<string | null>(null);
  const [loadingSecret, setLoadingSecret] = useState<boolean>(false);
  const [revealSecret, setRevealSecret] = useState<boolean>(false);
  const [showRegenerateConfirm, setShowRegenerateConfirm] = useState<boolean>(false);
  const [copiedSecret, setCopiedSecret] = useState<boolean>(false);

  // Modal states
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [createForm, setCreateForm] = useState<ServiceForm>(emptyServiceForm);
  const [editService, setEditService] = useState<ServiceRecord | null>(null);
  const [editForm, setEditForm] = useState<ServiceForm>(emptyServiceForm);

  // Clean error helpers
  const toCleanLoadError = (err: unknown) => {
    const message = extractApiErrorMessage(err, 'Failed to load services').trim();
    if (!message || message.toLowerCase() === 'invalid request data') {
      return 'Failed to load services';
    }
    return message;
  };

  const toCleanRequestsError = (err: unknown) => {
    const message = extractApiErrorMessage(err, 'Failed to load requests').trim();
    if (!message || message.toLowerCase() === 'invalid request data') {
      return 'Failed to load requests';
    }
    return message;
  };

  // Loaders
  const loadServices = async () => {
    const data = isAdmin ? await apiService.getAdminServices() : await apiService.getServices();
    setServices(data);
  };

  const loadRequests = async () => {
    try {
      const data = await apiService.getServiceRequests();
      setRequests(data);
      setRequestsError('');
    } catch (err) {
      setRequestsError(toCleanRequestsError(err));
      setRequests([]);
    }
  };

  const loadHealthData = async (showLoader: boolean) => {
    try {
      if (showLoader) setHealthLoading(true);
      const data = await apiService.getServiceHealth();
      setHealthData(data);
      setHealthError('');
    } catch (err) {
      setHealthError(extractApiErrorMessage(err, 'Failed to load service health'));
    } finally {
      if (showLoader) setHealthLoading(false);
    }
  };

  const loadLogsHealth24h = async () => {
    try {
      const data = await apiService.getLogsServiceHealth(1440);
      setLogsHealth24h(data);
    } catch (err) {
      console.error('Failed to load 24h logs health', err);
    }
  };

  // Initial and polling data setup
  useEffect(() => {
    let active = true;

    const boot = async (showLoader: boolean) => {
      try {
        if (showLoader) {
          setLoading(true);
        }
        const data = isAdmin ? await apiService.getAdminServices() : await apiService.getServices();
        if (!active) return;
        setServices(data);
        setLoadError('');
      } catch (err) {
        if (!active) return;
        setLoadError(toCleanLoadError(err));
      }

      try {
        const pendingRequests = await apiService.getServiceRequests();
        if (active) {
          setRequests(pendingRequests);
          setRequestsError('');
        }
      } catch (err) {
        if (active) {
          setRequestsError(toCleanRequestsError(err));
          setRequests([]);
        }
      } finally {
        if (showLoader && active) {
          setLoading(false);
          setRequestsLoading(false);
        }
      }
    };

    void boot(true);
    void loadHealthData(true);
    void loadLogsHealth24h();

    const timer = window.setInterval(() => {
      void boot(false);
      void loadHealthData(false);
      void loadLogsHealth24h();
    }, 15000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [isAdmin]);

  // Enrichment of 24h metrics
  useEffect(() => {
    if (services.length === 0) return;

    const fetchMetricsForAll = async () => {
      const result: Record<string, { avgResponseTime: number; errorRate: number }> = {};
      await Promise.all(
        services.map(async (svc) => {
          try {
            const m = await apiService.fetchMetrics({
              timeRange: '24h',
              services: [svc.name],
              levels: [],
              search: '',
              service: svc.name,
              level: ''
            });
            result[svc.name] = {
              avgResponseTime: m.avgResponseTime || 0,
              errorRate: m.errorRate || 0
            };
          } catch {
            result[svc.name] = { avgResponseTime: 0, errorRate: 0 };
          }
        })
      );
      setEnrichedMetrics(result);
    };

    void fetchMetricsForAll();
  }, [services]);



  // Handle drawer data loading
  useEffect(() => {
    if (!selectedService) {
      setDrawerMetrics(null);
      setDrawerLogs([]);
      setDrawerAlerts([]);
      setServiceSecret(null);
      setRevealSecret(false);
      setCopiedSecret(false);
      return;
    }

    setServiceSecret(null);
    setRevealSecret(false);
    setCopiedSecret(false);

    const loadDrawerData = async () => {
      const serviceName = selectedService.name;

      // Load Metrics
      try {
        setDrawerMetricsLoading(true);
        const metrics = await apiService.fetchMetrics({
          timeRange: '24h',
          services: [serviceName],
          levels: [],
          search: '',
          service: serviceName,
          level: ''
        });
        setDrawerMetrics(metrics);
      } catch (err) {
        console.error('Failed to load drawer metrics', err);
      } finally {
        setDrawerMetricsLoading(false);
      }

      // Load Logs
      try {
        setDrawerLogsLoading(true);
        const logs = await apiService.fetchLogs({
          timeRange: '1h',
          services: [serviceName],
          levels: [],
          search: '',
          service: serviceName,
          level: ''
        });
        setDrawerLogs(logs.slice(0, 5));
      } catch (err) {
        console.error('Failed to load drawer logs', err);
      } finally {
        setDrawerLogsLoading(false);
      }

      // Load Alerts
      try {
        setDrawerAlertsLoading(true);
        const alerts = await apiService.fetchAlerts();
        const filtered = alerts.filter(a => a.service.toLowerCase() === serviceName.toLowerCase());
        setDrawerAlerts(filtered);
      } catch (err) {
        console.error('Failed to load drawer alerts', err);
      } finally {
        setDrawerAlertsLoading(false);
      }

      // Load Secret
      if (selectedService.id) {
        try {
          setLoadingSecret(true);
          const data = await apiService.getServiceSecret(selectedService.id);
          setServiceSecret(data.serviceSecret);
        } catch (err) {
          console.warn('Could not load service secret (unauthorized or error)', err);
          setServiceSecret(null);
        } finally {
          setLoadingSecret(false);
        }
      }
    };

    void loadDrawerData();
  }, [selectedService]);

  // Request & Creation handlers
  const createService = async () => {
    if (!createForm.name.trim()) {
      setActionError('Service name is required');
      return;
    }

    try {
      setSubmitting(true);
      if (isAdmin) {
        await apiService.createService({
          name: createForm.name.trim(),
          description: createForm.description.trim()
        });
      } else {
        await apiService.requestService({
          serviceName: createForm.name.trim(),
          description: createForm.description.trim()
        });
      }
      setCreateForm(emptyServiceForm);
      setShowCreateModal(false);
      await loadServices();
      await loadRequests();
      setActionError('');
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to submit service'));
    } finally {
      setSubmitting(false);
    }
  };

  const openEdit = (service: ServiceRecord) => {
    setEditService(service);
    setEditForm({
      name: service.name,
      description: service.description || ''
    });
  };

  const saveEdit = async () => {
    if (!editService?.id) return;
    if (!editForm.name.trim()) {
      setActionError('Service name is required');
      return;
    }

    try {
      setSubmitting(true);
      await apiService.updateService(editService.id, {
        name: editForm.name.trim(),
        description: editForm.description.trim()
      });
      setEditService(null);
      await loadServices();
      setActionError('');
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to update service'));
    } finally {
      setSubmitting(false);
    }
  };

  const removeService = async (serviceId?: string) => {
    if (!serviceId) return;
    if (!window.confirm('Delete this service?')) return;

    try {
      setSubmitting(true);
      await apiService.deleteService(serviceId);
      await loadServices();
      setActionError('');
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to delete service'));
    } finally {
      setSubmitting(false);
    }
  };

  // Request workflow handlers
  const approveRequest = async (request: ServiceAccessRequest) => {
    try {
      setSubmitting(true);
      await apiService.approveService(request.id, {
        description: request.description || '',
        comment: 'Approved via services page'
      });
      await loadServices();
      await loadRequests();
      setActionError('');
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to approve request'));
    } finally {
      setSubmitting(false);
    }
  };

  const rejectRequest = async (request: ServiceAccessRequest) => {
    try {
      setSubmitting(true);
      await apiService.rejectService(request.id, { comment: 'Rejected via services page' });
      await loadRequests();
      setActionError('');
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to reject request'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleSetPrimaryOwner = async (serviceName: string, userId: string) => {
    try {
      setSubmitting(true);
      setActionError('');
      await apiService.setPrimaryOwner(serviceName, userId);
      await loadServices();
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to update primary owner'));
    } finally {
      setSubmitting(false);
    }
  };

  const copyToClipboard = async () => {
    if (!serviceSecret) return;
    try {
      await navigator.clipboard.writeText(serviceSecret);
      setCopiedSecret(true);
      setTimeout(() => setCopiedSecret(false), 2000);
    } catch (err) {
      console.error('Failed to copy secret', err);
    }
  };

  const handleRegenerateSecret = async () => {
    if (!selectedService?.id) return;
    try {
      setSubmitting(true);
      const data = await apiService.regenerateServiceSecret(selectedService.id);
      setServiceSecret(data.serviceSecret);
      setRevealSecret(false);
      setShowRegenerateConfirm(false);
      setActionError('');
    } catch (err) {
      setActionError(extractApiErrorMessage(err, 'Failed to regenerate secret'));
    } finally {
      setSubmitting(false);
    }
  };

  const normalizedRequestStatus = (status?: string) => String(status || '').trim().toUpperCase();
  const pendingRequests = requests.filter((request) => normalizedRequestStatus(request.status) === 'PENDING');
  const rejectedRequests = requests.filter((request) => normalizedRequestStatus(request.status) === 'REJECTED');
  const pendingRequestsCount = pendingRequests.length;

  // Enriched service data with health status
  const enrichedServices = useMemo(() => {
    return services.map(service => {
      const health = healthData.find(h => h.service.toLowerCase() === service.name.toLowerCase());
      const status = health ? health.status : 'NO_DATA';
      const lastSeen = health ? health.lastSeen : null;
      return {
        ...service,
        status,
        lastSeen,
      };
    });
  }, [services, healthData]);

  // Statistics calculation for health tab
  const healthyCount = enrichedServices.filter(s => s.status === 'OK').length;
  const warningCount = enrichedServices.filter(s => s.status === 'WARNING').length;
  const criticalCount = enrichedServices.filter(s => s.status === 'ERROR').length;
  const nodataCount = enrichedServices.filter(s => s.status === 'NO_DATA').length;

  // Filtered health list
  const filteredServices = useMemo(() => {
    return enrichedServices.filter((s) =>
      s.name.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [enrichedServices, searchQuery]);

  return (
    <main className="page-container">
      {/* Tabs Menu */}
      <div className="obs-tabs-container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 16px 0 16px' }}>
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            className={`obs-tab-btn ${activeTab === 'health' ? 'active' : ''}`}
            onClick={() => setActiveTab('health')}
          >
            Health
          </button>
          <button
            className={`obs-tab-btn ${activeTab === 'ownership' ? 'active' : ''}`}
            onClick={() => setActiveTab('ownership')}
          >
            Ownership
          </button>
          <button
            className={`obs-tab-btn ${activeTab === 'requests' ? 'active' : ''}`}
            onClick={() => setActiveTab('requests')}
          >
            Requests {pendingRequestsCount > 0 && `(${pendingRequestsCount})`}
          </button>
        </div>
        <div>
          {isAdmin ? (
            <button
              className="btn"
              style={{ background: 'var(--accent)', color: '#fff', border: 'none', height: '28px', padding: '0 12px', fontSize: '0.75rem' }}
              onClick={() => setShowCreateModal(true)}
            >
              Add Service
            </button>
          ) : (
            <button
              className="btn"
              style={{ background: 'var(--accent)', color: '#fff', border: 'none', height: '28px', padding: '0 12px', fontSize: '0.75rem' }}
              onClick={() => setShowCreateModal(true)}
            >
              Request Access
            </button>
          )}
        </div>
      </div>

      {actionError && (
        <div className="error" style={{ marginBottom: '12px', background: 'var(--level-error-bg)', border: '1px solid var(--level-error)', padding: '10px', borderRadius: '4px' }}>
          {actionError}
        </div>
      )}

      {/* Layout Split Grid: Main Content & Sidebar Details Drawer */}
      <div className="obs-split-layout">
        <div className="obs-split-main">
          {/* HEALTH TAB VIEW */}
          {activeTab === 'health' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {/* Summary Cards */}
              <div className="obs-metrics-grid">
                <MetricCard title="Healthy Services" value={healthyCount} status="healthy" />
                <MetricCard title="Warning Services" value={warningCount} status="warning" />
                <MetricCard title="Critical Services" value={criticalCount} status="critical" />
                <MetricCard title="No Data Services" value={nodataCount} status="neutral" />
              </div>

              {/* Table workspace panel (docking toolbar and health table) */}
              <div className="obs-table-workspace-panel">
                <div className="obs-filter-toolbar">
                  <div className="obs-search-wrapper" style={{ width: '100%' }}>
                    <span className="obs-search-icon">🔍</span>
                    <input
                      type="text"
                      className="form-control obs-search-input"
                      placeholder="Search services health directory..."
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />
                  </div>
                </div>

                {loading && services.length === 0 ? (
                  <div className="obs-empty-state" style={{ border: 'none', background: 'transparent' }}>
                    <span className="upload-spinner" style={{ width: '24px', height: '24px', marginBottom: '12px' }} />
                    <h4 className="obs-empty-title">Loading Health Directory</h4>
                    <p className="obs-empty-desc">Fetching active service connections and telemetry...</p>
                  </div>
                ) : filteredServices.length === 0 ? (
                  <EmptyState
                    title="No services found"
                    description="Verify service configuration or check your filters."
                    icon="🖥️"
                  />
                ) : (
                  <div className="table-scroll-area">
                    <table className="log-table" style={{ width: '100%' }}>
                      <thead>
                        <tr>
                          <th style={{ width: '120px' }}>Status</th>
                          <th>Service Name</th>
                          <th>Error Count (24h)</th>
                          <th>Avg Response Time</th>
                          <th>Health %</th>
                        </tr>
                      </thead>
                      <tbody>
                        {filteredServices.map((svc) => {
                          const svcMetrics = enrichedMetrics[svc.name];
                          const errorCount24h = logsHealth24h.find((lh) => lh.service.toLowerCase() === svc.name.toLowerCase())?.errorCount ?? 0;
                          const isSelected = selectedService?.name === svc.name;

                          return (
                            <tr
                              key={svc.name}
                              className={`clickable-row ${isSelected ? 'row-selected' : ''}`}
                              style={{ cursor: 'pointer', background: isSelected ? 'rgba(59, 130, 246, 0.08)' : undefined }}
                              onClick={() => setSelectedService(svc)}
                            >
                              <td>
                                <StatusBadge status={svc.status} />
                              </td>
                              <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{svc.name}</td>
                              <td>
                                <span className={errorCount24h > 0 ? 'text-danger' : ''} style={{ fontWeight: errorCount24h > 0 ? 600 : 400 }}>
                                  {errorCount24h}
                                </span>
                              </td>
                              <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.75rem' }}>
                                {svcMetrics ? `${svcMetrics.avgResponseTime.toFixed(0)} ms` : '—'}
                              </td>
                              <td>
                                <span style={{ fontWeight: 600, color: svcMetrics ? (svcMetrics.errorRate > 5 ? 'var(--level-error)' : svcMetrics.errorRate > 1 ? 'var(--level-warn)' : 'var(--level-debug)') : 'inherit' }}>
                                  {svcMetrics ? `${(100 - svcMetrics.errorRate).toFixed(1)}%` : '—'}
                                </span>
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* OWNERSHIP TAB VIEW */}
          {activeTab === 'ownership' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {loading && services.length === 0 ? (
                <div className="obs-empty-state">
                  <span className="upload-spinner" style={{ width: '24px', height: '24px', marginBottom: '12px' }} />
                  <h4 className="obs-empty-title">Loading Ownership details</h4>
                  <p className="obs-empty-desc">Fetching system owner matrix...</p>
                </div>
              ) : services.length === 0 ? (
                <EmptyState
                  title="No services created"
                  description="Register your first system service component."
                  icon="🔑"
                  action={isAdmin ? { label: 'Register Service', onClick: () => setShowCreateModal(true) } : undefined}
                />
              ) : (
                <div className="obs-table-workspace-panel">
                  <div className="table-scroll-area">
                    <table className="log-table" style={{ width: '100%' }}>
                      <thead>
                        <tr>
                          <th>Service Name</th>
                          <th>Description</th>
                          <th>Owners Assignment (Select Primary)</th>
                          {isAdmin && <th style={{ width: '140px', textAlign: 'right' }}>Actions</th>}
                        </tr>
                      </thead>
                      <tbody>
                        {services.map((service) => {
                          const firstLetter = service.name ? service.name.charAt(0) : 'S';
                          return (
                            <tr key={service.id || service.name}>
                              <td style={{ fontWeight: 600, verticalAlign: 'top', fontFamily: 'var(--font-mono)' }}>{service.name}</td>
                              <td style={{ verticalAlign: 'top', color: 'var(--text-secondary)' }}>
                                {service.description || 'No description provided.'}
                              </td>
                              <td>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                                  {service.owners && service.owners.map((owner) => (
                                    <label
                                      key={owner.userId}
                                      className={`service-owner-item ${owner.primary ? 'primary-owner' : ''} ${
                                        isAdmin ? 'owner-editable' : ''
                                      }`}
                                      style={{
                                        display: 'inline-flex',
                                        alignItems: 'center',
                                        gap: '6px',
                                        cursor: isAdmin ? 'pointer' : 'default',
                                        padding: '4px 8px',
                                        background: owner.primary ? 'rgba(59, 130, 246, 0.12)' : 'var(--surface-raised)',
                                        border: owner.primary ? '1px solid var(--accent)' : '1px solid var(--border)',
                                        borderRadius: '3px',
                                        fontSize: '0.8rem'
                                      }}
                                    >
                                      <input
                                        type="radio"
                                        className="service-owner-radio"
                                        name={`primary-owner-${service.name}`}
                                        checked={owner.primary}
                                        disabled={submitting || !isAdmin}
                                        onChange={() => void handleSetPrimaryOwner(service.name, owner.userId)}
                                        style={{ margin: 0 }}
                                      />
                                      <span>
                                        {owner.username} {owner.primary && <span style={{ color: 'var(--accent)', fontWeight: 600 }}>(Primary)</span>}
                                      </span>
                                    </label>
                                  ))}
                                  {(!service.owners || service.owners.length === 0) && (
                                    <div>
                                      <span className="tag tag-unassigned">Unassigned</span>
                                    </div>
                                  )}
                                </div>
                              </td>
                              {isAdmin && (
                                <td style={{ textAlign: 'right', verticalAlign: 'top' }}>
                                  <div style={{ display: 'inline-flex', gap: '6px' }}>
                                    <button
                                      className="btn"
                                      style={{ padding: '2px 8px', fontSize: '0.72rem' }}
                                      onClick={() => openEdit(service)}
                                      disabled={!service.id}
                                    >
                                      Edit
                                    </button>
                                    <button
                                      className="btn btn-danger"
                                      style={{ padding: '2px 8px', fontSize: '0.72rem' }}
                                      onClick={() => void removeService(service.id)}
                                      disabled={!service.id}
                                    >
                                      Delete
                                    </button>
                                  </div>
                                </td>
                              )}
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* REQUESTS TAB VIEW */}
          {activeTab === 'requests' && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              {/* Pending Requests */}
              <div>
                <h3 style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Pending Access Requests
                </h3>
                {requestsLoading && requests.length === 0 ? (
                  <div className="state-message" style={{ padding: '1rem 0' }}>Loading requests...</div>
                ) : requestsError ? (
                  <div className="state-message" style={{ padding: '1rem 0' }}>
                    <p className="error">{requestsError}</p>
                  </div>
                ) : pendingRequests.length === 0 ? (
                  <div className="obs-section-panel" style={{ padding: '16px', textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.82rem' }}>
                    No pending access requests.
                  </div>
                ) : (
                  <div className="obs-table-workspace-panel">
                    <div className="table-scroll-area">
                      <table className="log-table" style={{ width: '100%' }}>
                        <thead>
                          <tr>
                            <th>Service</th>
                            <th>Requested By</th>
                            <th>Reason / Description</th>
                            {isAdmin && <th style={{ width: '180px', textAlign: 'right' }}>Actions</th>}
                          </tr>
                        </thead>
                        <tbody>
                          {pendingRequests.map((req) => (
                            <tr key={req.id}>
                              <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{req.serviceName}</td>
                              <td>{req.requestedByEmail}</td>
                              <td>{req.description || 'No reason provided.'}</td>
                              {isAdmin && (
                                <td style={{ textAlign: 'right' }}>
                                  <div style={{ display: 'inline-flex', gap: '6px' }}>
                                    <button
                                      className="btn btn-danger"
                                      style={{ padding: '2px 8px', fontSize: '0.72rem' }}
                                      disabled={submitting}
                                      onClick={() => void rejectRequest(req)}
                                    >
                                      Reject
                                    </button>
                                    <button
                                      className="btn"
                                      style={{ padding: '2px 8px', fontSize: '0.72rem', background: 'var(--accent)', color: '#fff', border: 'none' }}
                                      disabled={submitting}
                                      onClick={() => void approveRequest(req)}
                                    >
                                      Approve
                                    </button>
                                  </div>
                                </td>
                              )}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>

              {/* Rejected Requests */}
              <div>
                <h3 style={{ fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-secondary)', marginBottom: '6px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Rejected Access Requests
                </h3>
                {requestsLoading && requests.length === 0 ? (
                  <div className="state-message" style={{ padding: '1rem 0' }}>Loading requests...</div>
                ) : rejectedRequests.length === 0 ? (
                  <div className="obs-section-panel" style={{ padding: '16px', textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.82rem' }}>
                    No rejected access requests.
                  </div>
                ) : (
                  <div className="obs-table-workspace-panel">
                    <div className="table-scroll-area">
                      <table className="log-table" style={{ width: '100%' }}>
                        <thead>
                          <tr>
                            <th>Service</th>
                            <th>Requested By</th>
                            <th>Reason / Comment</th>
                          </tr>
                        </thead>
                        <tbody>
                          {rejectedRequests.map((req) => (
                            <tr key={req.id}>
                              <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }}>{req.serviceName}</td>
                              <td>{req.requestedByEmail}</td>
                              <td style={{ color: 'var(--level-error)' }}>
                                {req.reviewComment || req.description || 'No comment provided.'}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* SIDE DRAWER DETAILS PANEL */}
        {selectedService && (
          <div className="obs-split-drawer">
            <div className="obs-drawer-header">
              <div className="obs-drawer-title">
                <h3>{selectedService.name} Details</h3>
              </div>
              <button className="obs-drawer-close" onClick={() => setSelectedService(null)}>
                ✕
              </button>
            </div>
            <div className="obs-drawer-body">
              {/* Overview */}
              <div className="obs-drawer-section">
                <span className="obs-drawer-section-title">Overview</span>
                <div className="obs-drawer-grid">
                  <div className="obs-drawer-item">
                    <span className="obs-drawer-label">Service Name</span>
                    <span className="obs-drawer-value" style={{ fontWeight: 600 }}>{selectedService.name}</span>
                  </div>
                  <div className="obs-drawer-item">
                    <span className="obs-drawer-label">Status</span>
                    <div style={{ marginTop: '2px' }}>
                      <StatusBadge status={selectedService.status || 'NO_DATA'} />
                    </div>
                  </div>
                </div>
                <div className="obs-drawer-item" style={{ marginTop: '6px' }}>
                  <span className="obs-drawer-label">Description</span>
                  <span className="obs-drawer-value" style={{ fontSize: '0.78rem', color: 'var(--text-secondary)' }}>
                    {selectedService.description || 'No description provided.'}
                  </span>
                </div>
              </div>

              {/* Ownership */}
              <div className="obs-drawer-section">
                <span className="obs-drawer-section-title">Ownership</span>
                <div className="obs-drawer-item">
                  <span className="obs-drawer-label">Primary Owner</span>
                  <span className="obs-drawer-value">
                    {selectedService.owners?.find(o => o.primary)?.username || 'Unassigned'}
                  </span>
                </div>
                <div className="obs-drawer-item" style={{ marginTop: '4px' }}>
                  <span className="obs-drawer-label">Secondary Owners</span>
                  <span className="obs-drawer-value" style={{ fontSize: '0.78rem' }}>
                    {selectedService.owners?.filter(o => !o.primary).map(o => o.username).join(', ') || 'None'}
                  </span>
                </div>
              </div>

              {/* Security & Credentials */}
              <div className="obs-drawer-section">
                <span className="obs-drawer-section-title">Security & Credentials</span>
                {loadingSecret ? (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span className="upload-spinner" style={{ width: '12px', height: '12px' }} />
                    Loading security credentials...
                  </div>
                ) : serviceSecret ? (
                  <div className="obs-drawer-item">
                    <span className="obs-drawer-label">Service Secret Key</span>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '6px' }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--surface-raised)', border: '1px solid var(--border)', borderRadius: '4px', padding: '6px 10px' }}>
                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {revealSecret ? serviceSecret : '••••••••••••••••••••••••••••••••'}
                        </span>
                        <button
                          className="btn"
                          type="button"
                          style={{ padding: '2px 8px', fontSize: '0.72rem', height: '24px', whiteSpace: 'nowrap' }}
                          onClick={() => setRevealSecret(!revealSecret)}
                        >
                          {revealSecret ? 'Hide' : 'Reveal'}
                        </button>
                        <button
                          className="btn"
                          type="button"
                          style={{ padding: '2px 8px', fontSize: '0.72rem', height: '24px', whiteSpace: 'nowrap' }}
                          onClick={() => void copyToClipboard()}
                        >
                          {copiedSecret ? 'Copied!' : 'Copy'}
                        </button>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'flex-start' }}>
                        <button
                          className="btn btn-danger"
                          type="button"
                          style={{ padding: '4px 10px', fontSize: '0.72rem', height: '26px' }}
                          onClick={() => setShowRegenerateConfirm(true)}
                        >
                          Regenerate Secret
                        </button>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                    No security credentials available for this service.
                  </div>
                )}
              </div>

              {/* Telemetry Metrics */}
              <div className="obs-drawer-section">
                <span className="obs-drawer-section-title">Health Metrics (24h)</span>
                {drawerMetricsLoading ? (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span className="upload-spinner" style={{ width: '12px', height: '12px' }} />
                    Loading metrics telemetry...
                  </div>
                ) : drawerMetrics ? (
                  <div className="obs-drawer-grid">
                    <div className="obs-drawer-item">
                      <span className="obs-drawer-label">Throughput</span>
                      <span className="obs-drawer-value">{drawerMetrics.totalLogs} logs</span>
                    </div>
                    <div className="obs-drawer-item">
                      <span className="obs-drawer-label">Error Rate</span>
                      <span className="obs-drawer-value" style={{ color: drawerMetrics.errorRate > 0 ? 'var(--level-error)' : 'inherit' }}>
                        {drawerMetrics.errorRate.toFixed(2)}%
                      </span>
                    </div>
                    <div className="obs-drawer-item">
                      <span className="obs-drawer-label">Avg Response Time</span>
                      <span className="obs-drawer-value">{drawerMetrics.avgResponseTime.toFixed(1)} ms</span>
                    </div>
                    <div className="obs-drawer-item">
                      <span className="obs-drawer-label">P95 Latency</span>
                      <span className="obs-drawer-value">{drawerMetrics.p95Latency.toFixed(1)} ms</span>
                    </div>
                  </div>
                ) : (
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>No metrics data available</span>
                )}
              </div>

              {/* Recent Alerts */}
              <div className="obs-drawer-section">
                <span className="obs-drawer-section-title">Recent Alerts</span>
                {drawerAlertsLoading ? (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span className="upload-spinner" style={{ width: '12px', height: '12px' }} />
                    Loading alerts...
                  </div>
                ) : drawerAlerts.length === 0 ? (
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>No recent alerts triggered</span>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    {drawerAlerts.map((alert, idx) => (
                      <div key={idx} style={{ padding: '6px 8px', background: 'var(--surface-raised)', borderRadius: '3px', borderLeft: `2px solid ${alert.severity === 'CRITICAL' ? 'var(--level-error)' : 'var(--level-warn)'}` }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', fontWeight: 600, marginBottom: '2px' }}>
                          <span style={{ color: alert.severity === 'CRITICAL' ? 'var(--level-error)' : 'var(--level-warn)' }}>{alert.severity}</span>
                          <span style={{ color: 'var(--text-dim)', fontFamily: 'var(--font-mono)' }}>
                            {alert.timestamp ? new Date(alert.timestamp).toLocaleTimeString() : '—'}
                          </span>
                        </div>
                        <div style={{ fontSize: '0.72rem', color: 'var(--text-primary)', wordBreak: 'break-all' }}>{alert.message}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Recent Logs */}
              <div className="obs-drawer-section">
                <span className="obs-drawer-section-title">Recent Logs (1h)</span>
                {drawerLogsLoading ? (
                  <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span className="upload-spinner" style={{ width: '12px', height: '12px' }} />
                    Loading trace logs...
                  </div>
                ) : drawerLogs.length === 0 ? (
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>No logs captured in last hour</span>
                ) : (
                  <div className="obs-drawer-logs-list">
                    {drawerLogs.map((log, idx) => (
                      <div key={log.id || idx} className="obs-drawer-log-row">
                        <span style={{ color: 'var(--text-dim)' }}>
                          [{log['@timestamp'] ? new Date(log['@timestamp']).toLocaleTimeString() : '—'}]
                        </span>{' '}
                        <span style={{ color: log.level === 'ERROR' ? 'var(--level-error)' : log.level === 'WARN' ? 'var(--level-warn)' : 'var(--text-secondary)', fontWeight: 600 }}>
                          {log.level}
                        </span>:{' '}
                        {log.message}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* CREATE SERVICE / REQUEST ACCESS MODAL */}
      <Modal
        open={showCreateModal}
        title={isAdmin ? 'Add New Service' : 'Request Access to Service'}
        onClose={() => {
          setShowCreateModal(false);
          setCreateForm(emptyServiceForm);
          setActionError('');
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '4px 0' }}>
          <div className="service-form-row">
            <label htmlFor="service-name-input" style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Service Name</label>
            <input
              id="service-name-input"
              ref={addNameInputRef}
              className="form-control"
              placeholder="e.g. auth-service"
              value={createForm.name}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label htmlFor="service-desc-input" style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Description</label>
            <input
              id="service-desc-input"
              className="form-control"
              placeholder="Provide a brief description of the service"
              value={createForm.description}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
            />
          </div>
          {actionError && <p className="error">{actionError}</p>}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px', marginTop: '4px' }}>
            <button className="btn" type="button" onClick={() => setShowCreateModal(false)} disabled={submitting}>
              Cancel
            </button>
            <button
              className="btn"
              style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
              disabled={submitting}
              onClick={() => void createService()}
            >
              {submitting ? 'Saving...' : (isAdmin ? 'Create Service' : 'Submit Request')}
            </button>
          </div>
        </div>
      </Modal>

      {/* EDIT SERVICE DETAILS MODAL (ADMIN ONLY) */}
      <Modal open={Boolean(editService)} title="Edit Service Details" onClose={() => setEditService(null)}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '4px 0' }}>
          <div className="service-form-row">
            <label htmlFor="edit-service-name-input" style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Service Name</label>
            <input
              id="edit-service-name-input"
              className="form-control"
              placeholder="Service name"
              value={editForm.name}
              onChange={(e) => setEditForm((prev) => ({ ...prev, name: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label htmlFor="edit-service-desc-input" style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>Description</label>
            <input
              id="edit-service-desc-input"
              className="form-control"
              placeholder="Description"
              value={editForm.description}
              onChange={(e) => setEditForm((prev) => ({ ...prev, description: e.target.value }))}
            />
          </div>
          {actionError && <p className="error">{actionError}</p>}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px', marginTop: '4px' }}>
            <button className="btn" type="button" onClick={() => setEditService(null)} disabled={submitting}>
              Cancel
            </button>
            <button
              className="btn"
              style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
              disabled={submitting}
              onClick={() => void saveEdit()}
            >
              {submitting ? 'Saving...' : 'Save Changes'}
            </button>
          </div>
        </div>
      </Modal>

      {/* REGENERATE SECRET CONFIRMATION MODAL */}
      <Modal
        open={showRegenerateConfirm}
        title="Regenerate Service Secret"
        onClose={() => setShowRegenerateConfirm(false)}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', padding: '4px 0' }}>
          <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', lineHeight: '1.4' }}>
            Regenerating the secret will invalidate all existing applications using the old secret. Applications must be updated with the new secret.
          </p>
          {actionError && <p className="error">{actionError}</p>}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px', marginTop: '4px' }}>
            <button className="btn" type="button" onClick={() => setShowRegenerateConfirm(false)} disabled={submitting}>
              Cancel
            </button>
            <button
              className="btn btn-danger"
              disabled={submitting}
              onClick={() => void handleRegenerateSecret()}
            >
              {submitting ? 'Regenerating...' : 'Regenerate Secret'}
            </button>
          </div>
        </div>
      </Modal>
    </main>
  );
}
