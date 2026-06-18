import React, { useEffect, useState } from 'react';
import { useOrganization } from '../context/OrganizationContext';
import { apiService, extractApiErrorMessage } from '../services/api';
import { PageHeader, StatusBadge } from '../components/UI';
import Modal from '../components/Modal';
import { OrganizationMember } from '../types';

export default function OrganizationMembersPage() {
  const { activeOrganization, currentRole } = useOrganization();
  
  const [members, setMembers] = useState<OrganizationMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  // Invite Modal States
  const [showInviteModal, setShowInviteModal] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviting, setInviting] = useState(false);
  const [inviteError, setInviteError] = useState('');

  const isOwnerOrAdmin = currentRole === 'OWNER' || currentRole === 'ADMIN';
  const isOwner = currentRole === 'OWNER';

  const loadMembers = async () => {
    if (!activeOrganization) return;
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await apiService.getOrganizationMembers(activeOrganization.id);
      setMembers(data);
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to load organization members.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadMembers();
  }, [activeOrganization]);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim() || !activeOrganization) return;

    setInviting(true);
    setInviteError('');
    try {
      await apiService.inviteUser(activeOrganization.id, inviteEmail.trim());
      setSuccessMsg(`Successfully invited ${inviteEmail.trim()}`);
      setShowInviteModal(false);
      setInviteEmail('');
      void loadMembers();
    } catch (err) {
      setInviteError(extractApiErrorMessage(err, 'Failed to invite user.'));
    } finally {
      setInviting(false);
    }
  };

  const handlePromote = async (memberId: string, email: string) => {
    if (!window.confirm(`Are you sure you want to promote ${email} to ADMIN?`)) return;
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.promoteMember(memberId);
      setSuccessMsg(`Successfully promoted ${email} to ADMIN`);
      void loadMembers();
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to promote member.'));
    }
  };

  const handleDemote = async (memberId: string, email: string) => {
    if (!window.confirm(`Are you sure you want to demote ${email} to DEV?`)) return;
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.demoteMember(memberId);
      setSuccessMsg(`Successfully demoted ${email} to DEV`);
      void loadMembers();
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to demote member.'));
    }
  };

  const handleRemove = async (memberId: string, email: string) => {
    if (!window.confirm(`Are you sure you want to remove ${email} from the organization?`)) return;
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.removeMember(memberId);
      setSuccessMsg(`Successfully removed ${email}`);
      void loadMembers();
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to remove member.'));
    }
  };

  const handleTransfer = async (targetUserId: string, email: string) => {
    if (!activeOrganization) return;
    if (!window.confirm(`WARNING: Are you sure you want to transfer ownership to ${email}? You will be demoted to ADMIN.`)) return;
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.transferOwnership(activeOrganization.id, targetUserId);
      setSuccessMsg(`Successfully transferred ownership to ${email}`);
      window.location.reload(); // Reload context to fetch updated role
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to transfer ownership.'));
    }
  };

  return (
    <main className="obs-content">
      <PageHeader
        title="Organization Members"
        description="Manage members, role definitions, and invitations for your active workspace."
        actions={
          isOwnerOrAdmin && (
            <button
              className="btn btn-primary"
              onClick={() => setShowInviteModal(true)}
              style={{ background: 'var(--accent)', color: '#fff', border: 'none' }}
            >
              ✉️ Invite User
            </button>
          )
        }
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

      {loading ? (
        <div style={{ color: 'var(--text-dim)', textAlign: 'center', padding: '24px' }}>Loading members...</div>
      ) : (
        <div className="glass-panel" style={{ overflowX: 'auto', padding: 0 }}>
          <table className="logs-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-color)', textAlign: 'left' }}>
                <th style={{ padding: '12px 16px' }}>Name / Username</th>
                <th style={{ padding: '12px 16px' }}>Email Address</th>
                <th style={{ padding: '12px 16px' }}>Role</th>
                <th style={{ padding: '12px 16px' }}>Status</th>
                {isOwnerOrAdmin && <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.userId || member.email} style={{ borderBottom: '1px solid var(--border-color)', fontSize: '0.875rem' }}>
                  <td style={{ padding: '12px 16px', fontWeight: 600 }}>
                    {member.username || <span style={{ color: 'var(--text-dim)', fontStyle: 'italic' }}>Invited User</span>}
                  </td>
                  <td style={{ padding: '12px 16px' }}>{member.email}</td>
                  <td style={{ padding: '12px 16px' }}>
                    <span style={{
                      display: 'inline-block',
                      padding: '2px 6px',
                      borderRadius: '4px',
                      fontSize: '0.72rem',
                      fontWeight: 700,
                      backgroundColor: member.role === 'OWNER' ? 'rgba(168, 85, 247, 0.2)' : member.role === 'ADMIN' ? 'rgba(59, 130, 246, 0.2)' : 'rgba(100, 116, 139, 0.2)',
                      color: member.role === 'OWNER' ? '#c084fc' : member.role === 'ADMIN' ? '#60a5fa' : '#94a3b8'
                    }}>
                      {member.role}
                    </span>
                  </td>
                  <td style={{ padding: '12px 16px' }}>
                    <StatusBadge status={member.status} />
                  </td>
                  {isOwnerOrAdmin && (
                    <td style={{ padding: '12px 16px', textAlign: 'right' }}>
                      {member.status === 'ACTIVE' && (
                        <div style={{ display: 'inline-flex', gap: '8px' }}>
                          {member.role === 'DEV' && (
                            <button
                              className="btn btn-sm"
                              onClick={() => handlePromote(member.userId, member.email)}
                              style={{ padding: '4px 8px', fontSize: '0.75rem', border: '1px solid var(--border-color)' }}
                            >
                              Promote
                            </button>
                          )}
                          {member.role === 'ADMIN' && (
                            <button
                              className="btn btn-sm"
                              onClick={() => handleDemote(member.userId, member.email)}
                              style={{ padding: '4px 8px', fontSize: '0.75rem', border: '1px solid var(--border-color)' }}
                            >
                              Demote
                            </button>
                          )}
                          {isOwner && member.role !== 'OWNER' && (
                            <button
                              className="btn btn-sm"
                              onClick={() => handleTransfer(member.userId, member.email)}
                              style={{ padding: '4px 8px', fontSize: '0.75rem', border: '1px solid var(--accent)', color: 'var(--accent)', background: 'transparent' }}
                            >
                              🔑 Transfer
                            </button>
                          )}
                          {member.role !== 'OWNER' && (
                            <button
                              className="btn btn-sm"
                              onClick={() => handleRemove(member.userId, member.email)}
                              style={{ padding: '4px 8px', fontSize: '0.75rem', border: '1px solid #f87171', color: '#f87171', background: 'transparent' }}
                            >
                              Remove
                            </button>
                          )}
                        </div>
                      )}
                      {member.status === 'INVITED' && (
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>Pending acceptance</span>
                      )}
                      {member.status === 'PENDING' && (
                        <span style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>Join request pending</span>
                      )}
                    </td>
                  )}
                </tr>
              ))}
              {members.length === 0 && (
                <tr>
                  <td colSpan={isOwnerOrAdmin ? 5 : 4} style={{ padding: '24px', textAlign: 'center', color: 'var(--text-dim)' }}>
                    No members found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Invite Modal */}
      <Modal
        open={showInviteModal}
        title="Invite Member to Organization"
        onClose={() => { setShowInviteModal(false); setInviteEmail(''); setInviteError(''); }}
      >
        <form onSubmit={handleInvite}>
          {inviteError && (
            <div style={{ color: '#f87171', backgroundColor: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.4)', padding: '8px 12px', borderRadius: '4px', marginBottom: '12px', fontSize: '0.8rem' }}>
              {inviteError}
            </div>
          )}
          <div style={{ marginBottom: '16px' }}>
            <label style={{ display: 'block', fontSize: '0.75rem', fontWeight: 600, color: 'var(--text-dim)', marginBottom: '6px' }}>
              EMAIL ADDRESS
            </label>
            <input
              type="email"
              className="form-control"
              placeholder="user@company.com"
              value={inviteEmail}
              onChange={(e) => setInviteEmail(e.target.value)}
              required
              style={{ width: '100%', padding: '8px', boxSizing: 'border-box' }}
            />
          </div>
          <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end' }}>
            <button type="button" className="btn" onClick={() => setShowInviteModal(false)} style={{ background: 'none', border: '1px solid var(--border-color)', color: 'var(--text-color)' }}>Cancel</button>
            <button type="submit" className="btn btn-primary" style={{ background: 'var(--accent)', color: '#fff', border: 'none' }} disabled={inviting}>
              {inviting ? 'Inviting...' : 'Send Invitation'}
            </button>
          </div>
        </form>
      </Modal>
    </main>
  );
}
