import { createContext, useContext, useState, useEffect, useCallback, useMemo, type ReactNode } from 'react';
import { apiService } from '../services/api';
import { Organization, OrganizationContextValue } from '../types';
import { useAuth } from './AuthContext';

const OrganizationContext = createContext<OrganizationContextValue | undefined>(undefined);

export function OrganizationProvider({ children }: { children: ReactNode }) {
  const { user, status, refreshSession } = useAuth();
  const [activeOrganization, setActiveOrganization] = useState<Organization | null>(null);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const currentRole = useMemo(() => {
    return String(user?.role || '').toUpperCase() || 'DEV';
  }, [user]);

  const permissions = useMemo(() => {
    return Array.isArray(user?.permissions) ? user.permissions.map((p) => String(p).toLowerCase()) : [];
  }, [user]);

  const refreshOrgs = useCallback(async () => {
    if (status !== 'authenticated') {
      setOrganizations([]);
      setActiveOrganization(null);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    try {
      const orgs = await apiService.getOrganizations();
      setOrganizations(orgs);

      const activeId = user?.activeOrganizationId;
      if (activeId) {
        const found = orgs.find((o) => o.id === activeId);
        if (found) {
          setActiveOrganization(found);
        } else if (orgs.length > 0) {
          const activeDetails = await apiService.getOrganization(activeId);
          setActiveOrganization(activeDetails);
        } else {
          setActiveOrganization(null);
        }
      } else if (orgs.length > 0) {
        setActiveOrganization(orgs[0]);
      } else {
        setActiveOrganization(null);
      }
    } catch (err) {
      console.error('Failed to load organizations:', err);
    } finally {
      setIsLoading(false);
    }
  }, [status, user]);

  const switchOrg = useCallback(async (orgId: string) => {
    setIsLoading(true);
    try {
      const res = await apiService.switchOrganization(orgId);
      if (res && res.accessToken) {
        localStorage.setItem('token', res.accessToken);
        await refreshSession();
      }
    } catch (err) {
      console.error('Failed to switch organization:', err);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, [refreshSession]);

  const createOrg = useCallback(async (name: string, type: 'BUSINESS' | 'PERSONAL') => {
    setIsLoading(true);
    try {
      const newOrg = await apiService.createOrganization(name, type);
      await switchOrg(newOrg.id);
      return newOrg;
    } catch (err) {
      console.error('Failed to create organization:', err);
      throw err;
    } finally {
      setIsLoading(false);
    }
  }, [switchOrg]);

  useEffect(() => {
    if (status === 'authenticated') {
      void refreshOrgs();
    } else {
      setOrganizations([]);
      setActiveOrganization(null);
      setIsLoading(false);
    }
  }, [status, user?.activeOrganizationId, refreshOrgs]);

  const value = useMemo<OrganizationContextValue>(() => ({
    activeOrganization,
    organizations,
    currentRole,
    permissions,
    isLoading,
    switchOrg,
    createOrg,
    refreshOrgs
  }), [activeOrganization, organizations, currentRole, permissions, isLoading, switchOrg, createOrg, refreshOrgs]);

  return (
    <OrganizationContext.Provider value={value}>
      {children}
    </OrganizationContext.Provider>
  );
}

export function useOrganization() {
  const context = useContext(OrganizationContext);
  if (!context) {
    throw new Error('useOrganization must be used within OrganizationProvider');
  }
  return context;
}
