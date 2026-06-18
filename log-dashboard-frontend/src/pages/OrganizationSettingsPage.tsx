import React, { useEffect, useState } from 'react';
import { useOrganization } from '../context/OrganizationContext';
import { apiService, extractApiErrorMessage } from '../services/api';
import { PageHeader } from '../components/UI';
import { OrganizationMember } from '../types';

export default function OrganizationSettingsPage() {
  const { activeOrganization, currentRole, refreshOrgs } = useOrganization();

  const [newName, setNewName] = useState('');
  const [members, setMembers] = useState<OrganizationMember[]>([]);
  const [targetOwnerId, setTargetOwnerId] = useState('');
  
  const [loading, setLoading] = useState(false);
  const [updating, setUpdating] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [transferring, setTransferring] = useState(false);

  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const isOwner = currentRole === 'OWNER';
  const isOwnerOrAdmin = currentRole === 'OWNER' || currentRole === 'ADMIN';

  useEffect(() => {
    if (activeOrganization) {
      setNewName(activeOrganization.name);
      
      // Load active members for ownership transfer dropdown
      if (isOwner) {
        apiService.getOrganizationMembers(activeOrganization.id)
          .then((data) => {
            const activeMembers = data.filter((m) => m.status === 'ACTIVE' && m.role !== 'OWNER');
            setMembers(activeMembers);
            if (activeMembers.length > 0) {
              setTargetOwnerId(activeMembers[0].userId);
            }
          })
          .catch((err) => console.error('Failed to load members for transfer dropdown:', err));
      }
    }
  }, [activeOrganization, currentRole]);

  if (!activeOrganization) {
    return <div style={{ color: 'var(--text-dim)', padding: '24px', textAlign: 'center' }}>No active organization context found.</div>;
  }

  const handleUpdateDetails = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) return;

    setUpdating(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.updateOrganization(activeOrganization.id, newName.trim());
      setSuccessMsg('Organization name updated successfully!');
      await refreshOrgs();
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to update organization details.'));
    } finally {
      setUpdating(false);
    }
  };

  const handleTransferOwnership = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!targetOwnerId) return;

    const chosenMember = members.find((m) => m.userId === targetOwnerId);
    if (!chosenMember) return;

    if (!window.confirm(`WARNING: Are you sure you want to transfer organization ownership to ${chosenMember.email}? You will lose owner permissions and become an ADMIN.`)) {
      return;
    }

    setTransferring(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.transferOwnership(activeOrganization.id, targetOwnerId);
      setSuccessMsg(`Successfully transferred ownership to ${chosenMember.email}. Reloading session...`);
      setTimeout(() => {
        window.location.reload();
      }, 1500);
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to transfer ownership.'));
    } finally {
      setTransferring(false);
    }
  };

  const handleDeleteOrganization = async () => {
    if (!window.confirm('CRITICAL WARNING: Are you sure you want to DELETE this organization? This action is permanent and will delete all mapped users, API keys, logs, and services.')) {
      return;
    }

    setDeleting(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.deleteOrganization(activeOrganization.id);
      setSuccessMsg('Organization deleted successfully. Redirecting...');
      setTimeout(() => {
        window.location.replace('/');
      }, 1500);
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to delete organization.'));
      setDeleting(false);
    }
  };

  return (
    <main className="obs-content">
      <PageHeader
        title="Organization Settings"
        description="Configure your workspace details, domains, and manage ownership settings."
      />

      {errorMsg && (
        <div style={{ color: '#f87171', backgroundColor: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.4)', padding: '12px', borderRadius: '6px', marginBottom: '16px' }}>
          ⚠️ {errorMsg}
        </div>
      )}

      {successMsg && (
        <div style={{ color: '#4ade80', backgroundColor: 'rgba(34,197,94,0.15)', border: '1px solid rgba(34,197,94,0.4)', padding: '12px', borderRadius: '6px', marginBottom: '16px' }}>
          ✅ {successMsg}
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
        
        {/* Info Card */}
        <div className="glass-panel" style={{ padding: '24px' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 600, margin: '0 0 16px 0', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
            Workspace Profile
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '0.875rem' }}>
            <div>
              <span style={{ color: 'var(--text-dim)', display: 'block', fontSize: '0.75rem', fontWeight: 600 }}>WORKSPACE NAME</span>
              <span style={{ fontSize: '1.05rem', fontWeight: 600 }}>{activeOrganization.name}</span>
            </div>
            <div>
              <span style={{ color: 'var(--text-dim)', display: 'block', fontSize: '0.75rem', fontWeight: 600 }}>TYPE</span>
              <span style={{ fontSize: '0.9rem', display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                {activeOrganization.organizationType === 'BUSINESS' ? '🏢 Business Organization' : '👤 Personal Workspace'}
              </span>
            </div>
            {activeOrganization.organizationType === 'BUSINESS' && (
              <div>
                <span style={{ color: 'var(--text-dim)', display: 'block', fontSize: '0.75rem', fontWeight: 600 }}>VERIFIED DOMAIN</span>
                <span style={{ fontSize: '0.9rem', fontFamily: 'monospace' }}>{activeOrganization.domain || 'Not verified'}</span>
              </div>
            )}
            <div>
              <span style={{ color: 'var(--text-dim)', display: 'block', fontSize: '0.75rem', fontWeight: 600 }}>CREATED DATE</span>
              <span style={{ fontSize: '0.9rem' }}>{new Date(activeOrganization.createdAt).toLocaleDateString()}</span>
            </div>
          </div>
        </div>

        {/* Update Form (ADMIN or OWNER) */}
        {isOwnerOrAdmin && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '1.2rem', fontWeight: 600, margin: '0 0 16px 0', borderBottom: '1px solid var(--border-color)', paddingBottom: '8px' }}>
              Rename Workspace
            </h3>
            <form onSubmit={handleUpdateDetails}>
              <div style={{ marginBottom: '16px' }}>
                <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '6px' }}>
                  NEW NAME
                </label>
                <input
                  type="text"
                  className="form-control"
                  value={newName}
                  onChange={(e) => setNewName(e.target.value)}
                  required
                  disabled={updating}
                  style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
                />
              </div>
              <button
                type="submit"
                className="btn btn-primary"
                style={{ background: 'var(--accent)', color: '#fff', border: 'none', width: '100%', padding: '10px' }}
                disabled={updating}
              >
                {updating ? 'Updating...' : 'Save Workspace Name'}
              </button>
            </form>
          </div>
        )}
      </div>

      {/* Danger Zone (OWNER only) */}
      {isOwner && (
        <div style={{ marginTop: '24px' }}>
          <h3 style={{ fontSize: '1.2rem', fontWeight: 600, color: '#f87171', margin: '0 0 16px 0' }}>
            Danger Zone
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '24px' }}>
            
            {/* Transfer Ownership */}
            <div className="glass-panel" style={{ padding: '24px', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
              <h4 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 8px 0', color: 'var(--text-color)' }}>
                Transfer Ownership
              </h4>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-dim)', margin: '0 0 16px 0' }}>
                Give ownership of this organization to another active member. You will lose owner privileges.
              </p>
              {members.length === 0 ? (
                <div style={{ fontSize: '0.85rem', color: 'var(--text-dim)', fontStyle: 'italic' }}>
                  No other active members found to transfer ownership to.
                </div>
              ) : (
                <form onSubmit={handleTransferOwnership}>
                  <div style={{ marginBottom: '16px' }}>
                    <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '6px' }}>
                      SELECT NEW OWNER
                    </label>
                    <select
                      className="form-control"
                      value={targetOwnerId}
                      onChange={(e) => setTargetOwnerId(e.target.value)}
                      disabled={transferring}
                      style={{ width: '100%', padding: '8px' }}
                    >
                      {members.map((member) => (
                        <option key={member.userId} value={member.userId}>
                          {member.username} ({member.email})
                        </option>
                      ))}
                    </select>
                  </div>
                  <button
                    type="submit"
                    className="btn"
                    style={{ border: '1px solid var(--accent)', color: 'var(--accent)', background: 'transparent', width: '100%', padding: '10px' }}
                    disabled={transferring}
                  >
                    {transferring ? 'Transferring...' : 'Transfer Ownership'}
                  </button>
                </form>
              )}
            </div>

            {/* Delete Organization */}
            <div className="glass-panel" style={{ padding: '24px', border: '1px solid rgba(239, 68, 68, 0.2)' }}>
              <h4 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 8px 0', color: '#f87171' }}>
                Delete Organization
              </h4>
              <p style={{ fontSize: '0.8rem', color: 'var(--text-dim)', margin: '0 0 16px 0' }}>
                Permanently delete this organization, including all API keys, services, logged metrics, and database mappings.
              </p>
              <button
                type="button"
                className="btn"
                onClick={handleDeleteOrganization}
                style={{ background: '#ef4444', color: '#fff', border: 'none', width: '100%', padding: '10px', fontWeight: 600 }}
                disabled={deleting}
              >
                {deleting ? 'Deleting...' : 'Delete Organization'}
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
