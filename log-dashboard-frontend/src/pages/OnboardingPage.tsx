import React, { useState } from 'react';
import { useOrganization } from '../context/OrganizationContext';
import { apiService, extractApiErrorMessage } from '../services/api';

export default function OnboardingPage() {
  const { createOrg, switchOrg } = useOrganization();
  const [activeTab, setActiveTab] = useState<'business' | 'personal' | 'invite'>('business');
  
  // States for forms
  const [businessName, setBusinessName] = useState('');
  const [personalName, setPersonalName] = useState('');
  const [inviteId, setInviteId] = useState('');
  
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const handleCreateBusiness = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!businessName.trim()) {
      setErrorMsg('Organization name is required');
      return;
    }
    
    setIsSubmitting(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await createOrg(businessName, 'BUSINESS');
      setSuccessMsg('Business organization created successfully! Redirecting...');
      window.location.replace('/');
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to create business organization. Ensure you are not using a personal email domain (e.g. gmail.com).'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCreatePersonal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!personalName.trim()) {
      setErrorMsg('Workspace name is required');
      return;
    }
    
    setIsSubmitting(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await createOrg(personalName, 'PERSONAL');
      setSuccessMsg('Personal workspace created successfully! Redirecting...');
      window.location.replace('/');
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to create personal workspace.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleAcceptInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteId.trim()) {
      setErrorMsg('Invitation code/ID is required');
      return;
    }
    
    setIsSubmitting(true);
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.acceptInvite(inviteId.trim());
      setSuccessMsg('Invitation accepted successfully! Fetching organizations...');
      
      // Load organizations again
      const updatedOrgs = await apiService.getOrganizations();
      if (updatedOrgs.length > 0) {
        // Switch to the first active organization (which is likely the one we just joined)
        const matchedOrg = updatedOrgs[updatedOrgs.length - 1];
        await switchOrg(matchedOrg.id);
        window.location.replace('/');
      } else {
        window.location.reload();
      }
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to accept invitation. Please verify the code/ID is correct and was sent to your email.'));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: 'var(--bg-color, #0f172a)',
      color: 'var(--text-color, #f8fafc)',
      padding: '2rem'
    }}>
      <div style={{
        maxWidth: '480px',
        width: '100%',
        backgroundColor: 'var(--card-bg, #1e293b)',
        border: '1px solid var(--border-color, #334155)',
        borderRadius: '12px',
        boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.3), 0 8px 10px -6px rgba(0, 0, 0, 0.3)',
        padding: '2.5rem'
      }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '48px',
            height: '48px',
            borderRadius: '10px',
            backgroundColor: 'var(--accent, #3b82f6)',
            color: '#ffffff',
            fontSize: '1.5rem',
            marginBottom: '1rem'
          }}>
            🎛️
          </div>
          <h2 style={{ fontSize: '1.75rem', fontWeight: 700, margin: '0 0 0.5rem 0' }}>Welcome to LynkLog</h2>
          <p style={{ color: 'var(--text-dim, #94a3b8)', margin: 0, fontSize: '0.9rem' }}>
            To get started, create a new workspace or join an existing organization.
          </p>
        </div>

        {/* Tab switcher */}
        <div style={{
          display: 'flex',
          borderBottom: '1px solid var(--border-color, #334155)',
          marginBottom: '1.5rem'
        }}>
          <button
            onClick={() => { setActiveTab('business'); setErrorMsg(''); setSuccessMsg(''); }}
            style={{
              flex: 1,
              padding: '0.75rem',
              background: 'none',
              border: 'none',
              color: activeTab === 'business' ? 'var(--accent, #3b82f6)' : 'var(--text-dim, #94a3b8)',
              borderBottom: activeTab === 'business' ? '2px solid var(--accent, #3b82f6)' : 'none',
              fontWeight: 600,
              cursor: 'pointer',
              fontSize: '0.875rem'
            }}
          >
            🏢 Business Org
          </button>
          <button
            onClick={() => { setActiveTab('personal'); setErrorMsg(''); setSuccessMsg(''); }}
            style={{
              flex: 1,
              padding: '0.75rem',
              background: 'none',
              border: 'none',
              color: activeTab === 'personal' ? 'var(--accent, #3b82f6)' : 'var(--text-dim, #94a3b8)',
              borderBottom: activeTab === 'personal' ? '2px solid var(--accent, #3b82f6)' : 'none',
              fontWeight: 600,
              cursor: 'pointer',
              fontSize: '0.875rem'
            }}
          >
            👤 Personal Space
          </button>
          <button
            onClick={() => { setActiveTab('invite'); setErrorMsg(''); setSuccessMsg(''); }}
            style={{
              flex: 1,
              padding: '0.75rem',
              background: 'none',
              border: 'none',
              color: activeTab === 'invite' ? 'var(--accent, #3b82f6)' : 'var(--text-dim, #94a3b8)',
              borderBottom: activeTab === 'invite' ? '2px solid var(--accent, #3b82f6)' : 'none',
              fontWeight: 600,
              cursor: 'pointer',
              fontSize: '0.875rem'
            }}
          >
            ✉️ Join Invite
          </button>
        </div>

        {errorMsg && (
          <div style={{
            backgroundColor: 'rgba(239, 68, 68, 0.15)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '6px',
            color: '#f87171',
            padding: '0.75rem 1rem',
            marginBottom: '1.5rem',
            fontSize: '0.85rem'
          }}>
            ⚠️ {errorMsg}
          </div>
        )}

        {successMsg && (
          <div style={{
            backgroundColor: 'rgba(34, 197, 94, 0.15)',
            border: '1px solid rgba(34, 197, 94, 0.4)',
            borderRadius: '6px',
            color: '#4ade80',
            padding: '0.75rem 1rem',
            marginBottom: '1.5rem',
            fontSize: '0.85rem'
          }}>
            ✅ {successMsg}
          </div>
        )}

        {activeTab === 'business' && (
          <form onSubmit={handleCreateBusiness}>
            <div style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--text-dim, #94a3b8)' }}>
                ORGANIZATION NAME
              </label>
              <input
                className="form-control"
                type="text"
                placeholder="e.g. Acme Corporation"
                value={businessName}
                onChange={(e) => setBusinessName(e.target.value)}
                style={{ width: '100%', padding: '0.75rem', boxSizing: 'border-box' }}
                disabled={isSubmitting}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim, #94a3b8)', marginTop: '0.5rem', display: 'block' }}>
                Note: Domain name will be derived automatically from your email address. Creation will fail if using a personal email (like Gmail).
              </span>
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', padding: '0.8rem', background: 'var(--accent, #3b82f6)', color: '#fff', border: 'none', fontWeight: 600, borderRadius: '6px', cursor: 'pointer' }}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Creating...' : 'Create Business Organization'}
            </button>
          </form>
        )}

        {activeTab === 'personal' && (
          <form onSubmit={handleCreatePersonal}>
            <div style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--text-dim, #94a3b8)' }}>
                WORKSPACE NAME
              </label>
              <input
                className="form-control"
                type="text"
                placeholder="e.g. My Workspace"
                value={personalName}
                onChange={(e) => setPersonalName(e.target.value)}
                style={{ width: '100%', padding: '0.75rem', boxSizing: 'border-box' }}
                disabled={isSubmitting}
              />
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', padding: '0.8rem', background: 'var(--accent, #3b82f6)', color: '#fff', border: 'none', fontWeight: 600, borderRadius: '6px', cursor: 'pointer' }}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Creating...' : 'Create Personal Workspace'}
            </button>
          </form>
        )}

        {activeTab === 'invite' && (
          <form onSubmit={handleAcceptInvite}>
            <div style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'block', fontSize: '0.8rem', fontWeight: 600, marginBottom: '0.5rem', color: 'var(--text-dim, #94a3b8)' }}>
                INVITATION ID / CODE
              </label>
              <input
                className="form-control"
                type="text"
                placeholder="e.g. a1b2c3d4-e5f6-7a8b-9c0d-e1f2a3b4c5d6"
                value={inviteId}
                onChange={(e) => setInviteId(e.target.value)}
                style={{ width: '100%', padding: '0.75rem', boxSizing: 'border-box' }}
                disabled={isSubmitting}
              />
              <span style={{ fontSize: '0.75rem', color: 'var(--text-dim, #94a3b8)', marginTop: '0.5rem', display: 'block' }}>
                Paste the invitation code/UUID sent by your organization administrator.
              </span>
            </div>
            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: '100%', padding: '0.8rem', background: 'var(--accent, #3b82f6)', color: '#fff', border: 'none', fontWeight: 600, borderRadius: '6px', cursor: 'pointer' }}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Accepting...' : 'Accept Invitation'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
