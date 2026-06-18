import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authService } from '../services/api';
import type { AuthUser } from '../types';

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  role: string;
  isAdmin: boolean;
  isDev: boolean;
  permissions: string[];
  hasPermission: (permissionName: string) => boolean;
  canAccessUsers: boolean;
  canAccessServices: boolean;
  login: () => void;
  refreshSession: () => Promise<void>;
  logout: () => Promise<void>;
};

const DEFAULT_AUTH_USER: AuthUser = {
  authenticated: false,
  permissions: [],
  allowedServices: [],
  assignedServices: [],
  canManageUsers: false,
  canManageServices: false
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function normalizeAuthPayload(payload: any): AuthUser {
  if (!payload) {
    return DEFAULT_AUTH_USER;
  }

  const email = payload.email || '';
  const authenticated = payload.authenticated !== undefined ? Boolean(payload.authenticated) : Boolean(email);
  const roles: string[] = Array.isArray(payload.roles) ? payload.roles : [];
  const permissions: string[] = Array.isArray(payload.permissions) ? payload.permissions : [];
  const services: string[] = Array.isArray(payload.services) ? payload.services : [];
  const profileImageUrl = payload.profileImageUrl || '';

  const isAdmin = roles.includes('ADMIN');
  const role = payload.role || (roles.length > 0 ? roles[0] : 'DEV');

  return {
    id: payload.userId || '',
    authenticated,
    name: payload.name || email,
    email,
    role,
    permissions,
    allowedServices: Array.isArray(payload.allowedServices) ? payload.allowedServices : services,
    assignedServices: Array.isArray(payload.assignedServices)
      ? payload.assignedServices
      : (Array.isArray(payload.allowedServices) ? payload.allowedServices : services),
    canManageUsers: Boolean(payload.canManageUsers) || isAdmin,
    canManageServices: Boolean(payload.canManageServices) || isAdmin,
    profileImageUrl,
    activeOrganizationId: payload.activeOrganizationId || ''
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);

  const refreshSession = useCallback(async () => {
    const token = localStorage.getItem('token');
    if (!token) {
      setUser(null);
      setStatus('unauthenticated');
      return;
    }

    setStatus('loading');
    try {
      const payload = await authService.getSession();
      const normalized = normalizeAuthPayload(payload);
      if (normalized.authenticated) {
        setUser(normalized);
        setStatus('authenticated');
      } else {
        setUser(null);
        setStatus('unauthenticated');
      }
    } catch {
      setUser(null);
      setStatus('unauthenticated');
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authService.logout();
    } finally {
      localStorage.removeItem('token');
      setUser(null);
      setStatus('unauthenticated');
    }
  }, []);

  const login = useCallback(() => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/azure';
  }, []);

  useEffect(() => {
    void refreshSession();
  }, [refreshSession]);

  const role = String(user?.role || '').toUpperCase();
  const isAdmin = role.includes('ADMIN') || role.includes('OWNER') || Boolean(user?.canManageUsers) || Boolean(user?.canManageServices);
  const isDev = role.includes('DEV') || (!isAdmin && status === 'authenticated');
  const permissions = useMemo(() => {
    return Array.isArray(user?.permissions) ? user.permissions.map((item) => String(item).toLowerCase()) : [];
  }, [user]);
  const canReadServices = permissions.includes('services:read') || permissions.includes('logs:read');

  const hasPermission = useCallback((permissionName: string) => {
    return permissions.includes(permissionName.toLowerCase());
  }, [permissions]);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    role,
    isAdmin,
    isDev,
    permissions,
    hasPermission,
    canAccessUsers: isAdmin || Boolean(user?.canManageUsers) || hasPermission('users:manage'),
    canAccessServices: isAdmin || Boolean(user?.canManageServices) || canReadServices || hasPermission('services:manage'),
    login,
    refreshSession,
    logout
  }), [status, user, role, isAdmin, isDev, permissions, hasPermission, canReadServices, login, refreshSession, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
}
