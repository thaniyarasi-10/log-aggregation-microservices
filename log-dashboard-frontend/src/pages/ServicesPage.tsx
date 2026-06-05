import { useEffect, useRef, useState } from 'react';
import Modal from '../components/Modal';
import { useAuth } from '../context/AuthContext';
import { apiService, extractApiErrorMessage } from '../services/api';
import type { ServiceAccessRequest, ServiceRecord } from '../types';

type ServiceForm = {
  name: string;
  description: string;
};

const emptyServiceForm: ServiceForm = {
  name: '',
  description: ''
};

export default function ServicesPage() {
  const { isAdmin } = useAuth();
  const addNameInputRef = useRef<HTMLInputElement | null>(null);
  const [services, setServices] = useState<ServiceRecord[]>([]);
  const [requests, setRequests] = useState<ServiceAccessRequest[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadError, setLoadError] = useState<string>('');
  const [requestsLoading, setRequestsLoading] = useState<boolean>(true);
  const [requestsError, setRequestsError] = useState<string>('');
  const [actionError, setActionError] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);
  const [showCreateInline, setShowCreateInline] = useState<boolean>(false);
  const [createForm, setCreateForm] = useState<ServiceForm>(emptyServiceForm);
  const [editService, setEditService] = useState<ServiceRecord | null>(null);
  const [editForm, setEditForm] = useState<ServiceForm>(emptyServiceForm);

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
        if (showLoader && active) setLoading(false);
        if (showLoader && active) setRequestsLoading(false);
      }
    };

    void boot(true);
    const timer = window.setInterval(() => {
      void boot(false);
    }, 5000);

    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [isAdmin]);

  const normalizedRequestStatus = (status?: string) => String(status || '').trim().toUpperCase();
  const pendingRequests = requests.filter((request) => normalizedRequestStatus(request.status) === 'PENDING');
  const rejectedRequests = requests.filter((request) => normalizedRequestStatus(request.status) === 'REJECTED');

  const getStatusBadgeClass = (status?: string) => {
    const normalized = normalizedRequestStatus(status);
    if (normalized === 'APPROVED') return 'tag tag-info';
    if (normalized === 'REJECTED') return 'tag tag-error';
    return 'tag tag-warn';
  };

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
      setShowCreateInline(false);
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

  const openCreateInline = () => {
    setShowCreateInline(true);
    window.setTimeout(() => {
      addNameInputRef.current?.focus();
    }, 0);
  };

  // Statistics calculations
  const totalServices = services.length;
  const activeServices = services.filter((s) => s.active !== false && s.status !== 'INACTIVE').length;
  const pendingRequestsCount = pendingRequests.length;
  const rejectedRequestsCount = rejectedRequests.length;

  return (
    <main className="dashboard-grid">
      <section className="dashboard-main" style={{ padding: '24px', gap: '24px' }}>
        {/* Page Header */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginBottom: '8px' }}>
          <h1 style={{ fontSize: '1.4rem', fontWeight: 700, color: 'var(--text-primary)' }}>Services Management</h1>
          <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
            Monitor service health, define system ownership, and approve access delegation requests.
          </p>
        </div>

        {/* Summary Statistics Cards */}
        <div className="services-stats-row">
          <div className="services-stat-card">
            <span className="services-stat-title">Total Services</span>
            <span className="services-stat-value">{totalServices}</span>
          </div>
          <div className="services-stat-card stat-active">
            <span className="services-stat-title">Active Services</span>
            <span className="services-stat-value">{activeServices}</span>
          </div>
          <div className="services-stat-card stat-pending">
            <span className="services-stat-title">Pending Requests</span>
            <span className="services-stat-value">{pendingRequestsCount}</span>
          </div>
          <div className="services-stat-card stat-rejected">
            <span className="services-stat-title">Rejected Requests</span>
            <span className="services-stat-value">{rejectedRequestsCount}</span>
          </div>
        </div>

        {/* Service Form Card for Creation/Request */}
        {(showCreateInline || !isAdmin) && (
          <div className="service-form-card">
            <h3 className="service-form-title">
              {isAdmin ? 'Add New Service' : 'Request Access to Service'}
            </h3>
            <div className="service-form-grid-fields">
              <div className="service-form-row">
                <label htmlFor="service-name-input">Service Name</label>
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
                <label htmlFor="service-desc-input">Description</label>
                <input
                  id="service-desc-input"
                  className="form-control"
                  placeholder="Provide a brief description of the service"
                  value={createForm.description}
                  onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
                />
              </div>
            </div>
            {actionError && <p className="error" style={{ marginTop: '8px' }}>{actionError}</p>}
            <div className="service-form-actions">
              {isAdmin && (
                <button
                  className="btn"
                  disabled={submitting}
                  onClick={() => {
                    setShowCreateInline(false);
                    setCreateForm(emptyServiceForm);
                    setActionError('');
                  }}
                >
                  Cancel
                </button>
              )}
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
        )}

        {/* Services Directory Section */}
        <section className="glass-panel" style={{ border: 'none', background: 'transparent' }}>
          <div className="services-section-header" style={{ borderBottom: '1px solid var(--border)', paddingBottom: '8px', marginBottom: '16px' }}>
            <h2>Approved Services Directory</h2>
            {isAdmin && !showCreateInline && (
              <button className="btn" disabled={submitting} onClick={openCreateInline}>
                Add Service
              </button>
            )}
          </div>

          {actionError && !showCreateInline && (
            <div style={{ marginBottom: '16px' }}>
              <p className="error">{actionError}</p>
            </div>
          )}

          {loading && <div className="state-message" style={{ padding: '2rem 0' }}>Loading services...</div>}
          {!loading && loadError && (
            <div className="state-message" style={{ padding: '2rem 0' }}>
              <p className="error">{loadError}</p>
            </div>
          )}

          {!loading && !loadError && (
            <>
              <div className="services-grid">
                {services.map((service) => {
                  const isActive = service.active !== false && service.status !== 'INACTIVE';
                  const firstLetter = service.name ? service.name.charAt(0) : 'S';
                  return (
                    <div key={service.id || service.name} className="service-card">
                      <div className="service-card-body">
                        <div className="service-card-header">
                          <div className="service-card-identity">
                            <div className="service-card-avatar">{firstLetter}</div>
                            <div className="service-card-name-wrapper">
                              <span className="service-card-name" title={service.name}>
                                {service.name}
                              </span>
                              <div>
                                <span className={`tag ${isActive ? 'tag-debug' : 'tag-unassigned'}`}>
                                  {service.status || (isActive ? 'ACTIVE' : 'INACTIVE')}
                                </span>
                              </div>
                            </div>
                          </div>
                        </div>

                        <p className="service-card-description">
                          {service.description || 'No description provided.'}
                        </p>

                        <div className="service-card-owners-section">
                          <h4 className="service-card-owners-title">Owners (Primary Owner)</h4>
                          <div className="service-card-owners-list">
                            {service.owners && service.owners.map((owner) => (
                              <label
                                key={owner.userId}
                                className={`service-owner-item ${owner.primary ? 'primary-owner' : ''} ${
                                  isAdmin ? 'owner-editable' : ''
                                }`}
                              >
                                <input
                                  type="radio"
                                  className="service-owner-radio"
                                  name={`primary-owner-${service.name}`}
                                  checked={owner.primary}
                                  disabled={submitting || !isAdmin}
                                  onChange={() => void handleSetPrimaryOwner(service.name, owner.userId)}
                                />
                                <span>
                                  {owner.username} {owner.primary && '(Primary)'}
                                </span>
                              </label>
                            ))}
                            {(!service.owners || service.owners.length === 0) && (
                              <div>
                                <span className="tag tag-unassigned">Unassigned</span>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>

                      {isAdmin && (
                        <div className="service-card-footer">
                          <button
                            className="btn"
                            onClick={() => openEdit(service)}
                            disabled={!service.id}
                          >
                            Edit
                          </button>
                          <button
                            className="btn btn-danger"
                            onClick={() => void removeService(service.id)}
                            disabled={!service.id}
                          >
                            Delete
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              {!services.length && (
                <div className="state-message" style={{ padding: '2rem 0' }}>
                  No services available
                </div>
              )}
            </>
          )}
        </section>

        {/* Pending Requests Section */}
        <section className="glass-panel" style={{ border: 'none', background: 'transparent', marginTop: '16px' }}>
          <div className="services-section-header" style={{ borderBottom: '1px solid var(--border)', paddingBottom: '8px', marginBottom: '16px' }}>
            <h2>{isAdmin ? 'Pending Access Requests' : 'My Pending Access Requests'}</h2>
          </div>

          {requestsLoading && <div className="state-message" style={{ padding: '1rem 0' }}>Loading requests...</div>}
          {!requestsLoading && requestsError && (
            <div className="state-message" style={{ padding: '1rem 0' }}>
              <p className="error">{requestsError}</p>
            </div>
          )}

          {!requestsLoading && !requestsError && (
            <>
              <div className="requests-grid">
                {pendingRequests.map((request) => (
                  <div key={request.id} className="request-card">
                    <div className="request-card-body">
                      <div className="request-card-header">
                        <span className="request-card-title" title={request.serviceName}>
                          {request.serviceName}
                        </span>
                        <span className={getStatusBadgeClass(request.status)}>
                          {normalizedRequestStatus(request.status)}
                        </span>
                      </div>

                      <div className="request-card-meta">
                        <div className="request-card-meta-row">
                          <span className="request-card-meta-label">Requested by:</span>
                          <span className="request-card-meta-value" title={request.requestedByEmail}>
                            {request.requestedByEmail}
                          </span>
                        </div>
                      </div>

                      <div className="request-card-description">
                        {request.description || 'No reason provided.'}
                      </div>
                    </div>

                    {isAdmin && (
                      <div className="request-card-footer">
                        <button
                          className="btn btn-danger"
                          disabled={submitting}
                          onClick={() => void rejectRequest(request)}
                        >
                          Reject
                        </button>
                        <button
                          className="btn"
                          style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
                          disabled={submitting}
                          onClick={() => void approveRequest(request)}
                        >
                          Approve
                        </button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
              {!pendingRequests.length && (
                <div className="state-message" style={{ padding: '1rem 0' }}>
                  No pending requests found
                </div>
              )}
            </>
          )}
        </section>

        {/* Rejected Requests Section */}
        <section className="glass-panel" style={{ border: 'none', background: 'transparent', marginTop: '16px', marginBottom: '24px' }}>
          <div className="services-section-header" style={{ borderBottom: '1px solid var(--border)', paddingBottom: '8px', marginBottom: '16px' }}>
            <h2>{isAdmin ? 'Rejected Access Requests' : 'My Rejected Access Requests'}</h2>
          </div>

          {requestsLoading && <div className="state-message" style={{ padding: '1rem 0' }}>Loading requests...</div>}
          {!requestsLoading && requestsError && (
            <div className="state-message" style={{ padding: '1rem 0' }}>
              <p className="error">{requestsError}</p>
            </div>
          )}

          {!requestsLoading && !requestsError && (
            <>
              <div className="requests-grid">
                {rejectedRequests.map((request) => (
                  <div key={request.id} className="request-card">
                    <div className="request-card-body">
                      <div className="request-card-header">
                        <span className="request-card-title" title={request.serviceName}>
                          {request.serviceName}
                        </span>
                        <span className={getStatusBadgeClass(request.status)}>
                          {normalizedRequestStatus(request.status)}
                        </span>
                      </div>

                      <div className="request-card-meta">
                        <div className="request-card-meta-row">
                          <span className="request-card-meta-label">Requested by:</span>
                          <span className="request-card-meta-value" title={request.requestedByEmail}>
                            {request.requestedByEmail}
                          </span>
                        </div>
                      </div>

                      <div className="request-card-description" style={{ borderLeft: '2px solid var(--level-error)' }}>
                        <strong style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>Reason / Rejection Comment:</strong>
                        <div style={{ marginTop: '4px' }}>
                          {request.reviewComment || request.description || 'No comment provided.'}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
              {!rejectedRequests.length && (
                <div className="state-message" style={{ padding: '1rem 0' }}>
                  No rejected requests found
                </div>
              )}
            </>
          )}
        </section>
      </section>

      <Modal open={Boolean(editService)} title="Edit Service" onClose={() => setEditService(null)}>
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
    </main>
  );
}
