import { useEffect, useState, useMemo } from 'react';
import Modal from '../components/Modal';
import { apiService, extractApiErrorMessage } from '../services/api';
import { PageHeader, MetricCard, EmptyState } from '../components/UI';
import type { UserRecord } from '../types';

type UserForm = {
  id: string;
  username: string;
  email: string;
  roles: string;
  services: string;
};

const emptyForm: UserForm = {
  id: '',
  username: '',
  email: '',
  roles: 'DEV',
  services: ''
};

function parseCsv(value: string): string[] {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

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

export default function UsersPage() {
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');
  const [submitting, setSubmitting] = useState<boolean>(false);

  // Search Filter State
  const [searchQuery, setSearchQuery] = useState<string>('');

  // Modals visibility states
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null);

  // Forms states
  const [createForm, setCreateForm] = useState<UserForm>(emptyForm);
  const [editForm, setEditForm] = useState<UserForm>(emptyForm);

  const loadUsers = async () => {
    try {
      setLoading(true);
      const data = await apiService.getUsers();
      setUsers(data);
      setError('');
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to load users'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadUsers();
  }, []);

  const createUser = async () => {
    if (!createForm.id.trim()) {
      setError('User ID is required');
      return;
    }
    if (!createForm.email.trim()) {
      setError('Email is required');
      return;
    }

    try {
      setSubmitting(true);
      await apiService.createUser({
        id: createForm.id.trim().toUpperCase(),
        username: createForm.username.trim(),
        email: createForm.email.trim(),
        roles: parseCsv(createForm.roles),
        services: parseCsv(createForm.services)
      });
      setCreateForm(emptyForm);
      setShowCreateModal(false);
      setError('');
      await loadUsers();
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to create user'));
    } finally {
      setSubmitting(false);
    }
  };

  const openEdit = (user: UserRecord) => {
    setEditingUser(user);
    setEditForm({
      id: user.id,
      username: user.username || user.name || '',
      email: user.email || '',
      roles: (user.roles || (user.role ? [user.role] : [])).join(', '),
      services: (user.services || []).join(', ')
    });
  };

  const saveEdit = async () => {
    if (!editingUser) return;
    if (!editForm.email.trim()) {
      setError('Email is required');
      return;
    }

    try {
      setSubmitting(true);
      await apiService.updateUser(editingUser.id, {
        username: editForm.username.trim(),
        email: editForm.email.trim(),
        roles: parseCsv(editForm.roles),
        services: parseCsv(editForm.services)
      });
      setEditingUser(null);
      setError('');
      await loadUsers();
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to update user'));
    } finally {
      setSubmitting(false);
    }
  };

  const removeUser = async (userId: string) => {
    const confirmed = window.confirm('Delete this user?');
    if (!confirmed) return;

    try {
      setSubmitting(true);
      await apiService.deleteUser(userId);
      setError('');
      await loadUsers();
    } catch (err) {
      setError(extractApiErrorMessage(err, 'Failed to delete user'));
    } finally {
      setSubmitting(false);
    }
  };

  // Directory Statistics
  const totalCount = users.length;
  const adminCount = useMemo(() => {
    return users.filter(u => 
      u.roles?.some(r => r.toUpperCase() === 'ADMIN') || 
      String(u.role).toUpperCase() === 'ADMIN'
    ).length;
  }, [users]);
  const devCount = totalCount - adminCount;

  // Filter list
  const filteredUsers = useMemo(() => {
    return users.filter((u) => {
      const name = u.name || u.username || '';
      const email = u.email || '';
      const id = u.id || '';
      return name.toLowerCase().includes(searchQuery.toLowerCase()) ||
             email.toLowerCase().includes(searchQuery.toLowerCase()) ||
             id.toLowerCase().includes(searchQuery.toLowerCase());
    });
  }, [users, searchQuery]);

  return (
    <main className="page-container">
      {/* Statistics Row */}
      <div className="obs-metrics-grid">
        <MetricCard title="Total Users" value={totalCount} />
        <MetricCard title="Administrators" value={adminCount} />
        <MetricCard title="Developers" value={devCount} />
      </div>

      {/* Table workspace panel (docking toolbar and directory table) */}
      <div className="obs-table-workspace-panel">
        <div className="obs-filter-toolbar">
          <div className="obs-search-wrapper" style={{ flex: 1 }}>
            <span className="obs-search-icon">🔍</span>
            <input
              type="text"
              className="form-control obs-search-input"
              placeholder="Search directory by name, email, or ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
            />
          </div>
          <button
            className="btn"
            style={{ background: 'var(--accent)', color: '#fff', border: 'none', height: '31px', padding: '0 16px' }}
            onClick={() => setShowCreateModal(true)}
          >
            Add User
          </button>
        </div>

        {error && (
          <div className="error" style={{ margin: '12px', background: 'var(--level-error-bg)', border: '1px solid var(--level-error)', padding: '12px', borderRadius: '4px' }}>
            {error}
          </div>
        )}

        {loading ? (
          <div className="obs-empty-state" style={{ border: 'none', background: 'transparent' }}>
            <span className="upload-spinner" style={{ width: '24px', height: '24px', marginBottom: '12px' }} />
            <h4 className="obs-empty-title">Loading Directory</h4>
            <p className="obs-empty-desc">Fetching registered developers and service mappings...</p>
          </div>
        ) : filteredUsers.length === 0 ? (
          <EmptyState
            title="No users found"
            description="Adjust your search criteria or register a new user in the directory."
            icon="👥"
            action={{
              label: 'Register User',
              onClick: () => setShowCreateModal(true)
            }}
          />
        ) : (
          /* Users Directory Table */
          <div className="table-scroll-area">
            <table className="log-table" style={{ width: '100%' }}>
              <thead>
                <tr>
                  <th style={{ width: '110px' }}>User ID</th>
                  <th>User Name</th>
                  <th>Email</th>
                  <th style={{ width: '100px' }}>Role</th>
                  <th>Assigned Services</th>
                  <th style={{ width: '140px', textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filteredUsers.map((item) => {
                  const roleString = (item.roles && item.roles.join(', ')) || item.role || 'DEV';
                  const servicesList = item.services || [];

                  return (
                    <tr key={item.id} className="clickable-row">
                      <td style={{ fontFamily: 'var(--font-mono)', fontWeight: 600 }}>{item.id}</td>
                      <td style={{ fontWeight: 600 }}>{item.name || item.username || '—'}</td>
                      <td>{item.email}</td>
                      <td>
                        <span className={`tag ${roleString.toUpperCase().includes('ADMIN') ? 'tag-error' : 'tag-info'}`}>
                          {roleString}
                        </span>
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
                           {servicesList.map((svc) => (
                             <span key={svc} className="tag tag-debug" style={{ fontFamily: 'var(--font-mono)', fontSize: '0.68rem', padding: '1px 6px' }}>
                               {svc}
                             </span>
                           ))}
                          {servicesList.length === 0 && (
                            <span className="tag tag-unassigned" style={{ fontSize: '0.68rem', padding: '1px 6px' }}>Unassigned</span>
                          )}
                        </div>
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div style={{ display: 'inline-flex', gap: '6px' }}>
                          <button className="btn" style={{ padding: '2px 8px', fontSize: '0.72rem' }} onClick={() => openEdit(item)}>
                            Edit
                          </button>
                          <button className="btn btn-danger" style={{ padding: '2px 8px', fontSize: '0.72rem' }} onClick={() => void removeUser(item.id)}>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* CREATE USER MODAL */}
      <Modal
        open={showCreateModal}
        title="Add New User"
        onClose={() => { setShowCreateModal(false); setError(''); }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '4px 0' }}>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>User ID (Username/Unique Key)</label>
            <input
              className="form-control"
              placeholder="e.g. KL10004"
              value={createForm.id}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, id: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Full Name</label>
            <input
              className="form-control"
              placeholder="e.g. Jane Doe"
              value={createForm.username}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, username: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Email Address</label>
            <input
              className="form-control"
              type="email"
              placeholder="e.g. jane.doe@company.com"
              value={createForm.email}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, email: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Role Permissions (comma separated)</label>
            <input
              className="form-control"
              placeholder="e.g. DEV, ADMIN"
              value={createForm.roles}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, roles: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Assigned Services (comma separated)</label>
            <input
              className="form-control"
              placeholder="e.g. auth-service, gateway-service"
              value={createForm.services}
              onChange={(e) => setCreateForm((prev) => ({ ...prev, services: e.target.value }))}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px', marginTop: '8px' }}>
            <button className="btn" type="button" onClick={() => setShowCreateModal(false)} disabled={submitting}>
              Cancel
            </button>
            <button
              className="btn"
              style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
              disabled={submitting}
              onClick={() => void createUser()}
            >
              {submitting ? 'Saving...' : 'Add User'}
            </button>
          </div>
        </div>
      </Modal>

      {/* EDIT USER MODAL */}
      <Modal
        open={Boolean(editingUser)}
        title="Edit User Directory Details"
        onClose={() => { setEditingUser(null); setError(''); }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', padding: '4px 0' }}>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Full Name</label>
            <input
              className="form-control"
              placeholder="Full Name"
              value={editForm.username}
              onChange={(e) => setEditForm((prev) => ({ ...prev, username: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Email Address</label>
            <input
              className="form-control"
              placeholder="Email"
              value={editForm.email}
              onChange={(e) => setEditForm((prev) => ({ ...prev, email: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Roles (comma separated)</label>
            <input
              className="form-control"
              placeholder="Roles"
              value={editForm.roles}
              onChange={(e) => setEditForm((prev) => ({ ...prev, roles: e.target.value }))}
            />
          </div>
          <div className="service-form-row">
            <label style={{ fontSize: '0.75rem', fontWeight: '600', color: 'var(--text-secondary)' }}>Assigned Services (comma separated)</label>
            <input
              className="form-control"
              placeholder="Services"
              value={editForm.services}
              onChange={(e) => setEditForm((prev) => ({ ...prev, services: e.target.value }))}
            />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', borderTop: '1px solid var(--border-subtle)', paddingTop: '12px', marginTop: '8px' }}>
            <button className="btn" type="button" onClick={() => setEditingUser(null)} disabled={submitting}>
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
