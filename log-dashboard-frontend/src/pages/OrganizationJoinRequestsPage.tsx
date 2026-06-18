import React, { useEffect, useState } from 'react';
import { useOrganization } from '../context/OrganizationContext';
import { apiService, extractApiErrorMessage } from '../services/api';
import { PageHeader, StatusBadge } from '../components/UI';
import { JoinRequest } from '../types';
import { Navigate } from 'react-router-dom';

export default function OrganizationJoinRequestsPage() {
  const { activeOrganization, currentRole } = useOrganization();

  const [requests, setRequests] = useState<JoinRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [successMsg, setSuccessMsg] = useState('');

  const isOwnerOrAdmin = currentRole === 'OWNER' || currentRole === 'ADMIN';

  const loadRequests = async () => {
    if (!activeOrganization) return;
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await apiService.getJoinRequests(activeOrganization.id);
      setRequests(data);
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to load join requests.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isOwnerOrAdmin) {
      void loadRequests();
    }
  }, [activeOrganization, currentRole]);

  // Enforce access control on frontend
  if (!isOwnerOrAdmin) {
    return <Navigate to="/logs" replace />;
  }

  const handleApprove = async (requestId: string, email: string) => {
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.approveJoinRequest(requestId);
      setSuccessMsg(`Approved join request from ${email}`);
      void loadRequests();
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to approve join request.'));
    }
  };

  const handleReject = async (requestId: string, email: string) => {
    setErrorMsg('');
    setSuccessMsg('');
    try {
      await apiService.rejectJoinRequest(requestId);
      setSuccessMsg(`Rejected join request from ${email}`);
      void loadRequests();
    } catch (err) {
      setErrorMsg(extractApiErrorMessage(err, 'Failed to reject join request.'));
    }
  };

  return (
    <main className="obs-content">
      <PageHeader
        title="Organization Join Requests"
        description="Review and approve requests from users wishing to join this business organization."
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
        <div style={{ color: 'var(--text-dim)', textAlign: 'center', padding: '24px' }}>Loading requests...</div>
      ) : (
        <div className="glass-panel" style={{ overflowX: 'auto', padding: 0 }}>
          <table className="logs-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '1px solid var(--border-color)', textAlign: 'left' }}>
                <th style={{ padding: '12px 16px' }}>Requesting User</th>
                <th style={{ padding: '12px 16px' }}>Email Address</th>
                <th style={{ padding: '12px 16px' }}>Requested At</th>
                <th style={{ padding: '12px 16px', textAlign: 'right' }}>Actions</th>
              </tr>
            </thead>
            <tbody>
              {requests.map((req) => (
                <tr key={req.id} style={{ borderBottom: '1px solid var(--border-color)', fontSize: '0.875rem' }}>
                  <td style={{ padding: '12px 16px', fontWeight: 600 }}>{req.username}</td>
                  <td style={{ padding: '12px 16px' }}>{req.email}</td>
                  <td style={{ padding: '12px 16px' }}>{new Date(req.requestedAt).toLocaleString()}</td>
                  <td style={{ padding: '12px 16px', textAlign: 'right' }}>
                    <div style={{ display: 'inline-flex', gap: '8px' }}>
                      <button
                        className="btn btn-sm"
                        onClick={() => handleApprove(req.id, req.email)}
                        style={{ padding: '4px 10px', fontSize: '0.75rem', border: 'none', background: 'var(--accent, #3b82f6)', color: '#fff' }}
                      >
                        Approve
                      </button>
                      <button
                        className="btn btn-sm"
                        onClick={() => handleReject(req.id, req.email)}
                        style={{ padding: '4px 10px', fontSize: '0.75rem', border: '1px solid #f87171', color: '#f87171', background: 'transparent' }}
                      >
                        Reject
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {requests.length === 0 && (
                <tr>
                  <td colSpan={4} style={{ padding: '24px', textAlign: 'center', color: 'var(--text-dim)' }}>
                    No pending join requests found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </main>
  );
}
