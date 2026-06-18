import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type {
  AgentQueryRequest,
  AgentQueryResponse,
  AlertItem,
  AlertsResponse,
  AuthUser,
  LogEvent,
  LogFilters,
  LogQueryParams,
  MetricsResponse,
  JiraConfiguration,
  UserJiraMapping,
  JiraUser,
  NotificationPreference,
  NotificationPreferenceUpdate,
  ServiceAccessRequest,
  ServiceRecord,
  ServiceHealth,
  UserRecord,
  Organization,
  OrganizationMember,
  JoinRequest
} from '../types';
import { buildLogQueryParams } from '../utils/time';

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

const directApi = axios.create({
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json'
  }
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  console.log('INTERCEPTOR TOKEN:', token);
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

directApi.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  console.log('INTERCEPTOR TOKEN:', token);
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

function isNotFound(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404;
}

function isUnauthorized(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

function isForbidden(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 403;
}

function sanitizeRequestConfig(config?: AxiosRequestConfig): AxiosRequestConfig | undefined {
  if (!config || !config.params || typeof config.params !== 'object') {
    return config;
  }

  const sanitizedParams = Object.entries(config.params as Record<string, unknown>).reduce<Record<string, unknown>>(
    (acc, [key, value]) => {
      if (value === undefined || value === null) {
        return acc;
      }

      if (typeof value === 'string') {
        const trimmed = value.trim();
        if (!trimmed || trimmed.toLowerCase() === 'undefined' || trimmed.toLowerCase() === 'null') {
          return acc;
        }
      }

      acc[key] = value;
      return acc;
    },
    {}
  );

  return {
    ...config,
    params: sanitizedParams
  };
}

export function extractApiErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (!axios.isAxiosError(error)) {
    return fallback;
  }

  const responseData = error.response?.data as { message?: unknown } | undefined;
  const responseMessage = typeof responseData?.message === 'string' ? responseData.message : '';
  if (responseMessage.trim()) {
    return responseMessage;
  }

  return fallback;
}

async function getWithFallback<T>(
  primary: string,
  fallback: string,
  config?: AxiosRequestConfig
): Promise<T> {
  const safeConfig = sanitizeRequestConfig(config);
  try {
    const response = await api.get<T>(primary, safeConfig);
    return response.data;
  } catch (error) {
    if (!isNotFound(error)) {
      throw error;
    }

    const response = await api.get<T>(fallback, safeConfig);
    return response.data;
  }
}

async function getByPaths<T>(paths: string[], config?: AxiosRequestConfig): Promise<T> {
  const safeConfig = sanitizeRequestConfig(config);
  let lastError: unknown;

  for (const path of paths) {
    try {
      const response = await directApi.get<T>(path, safeConfig);
      return response.data;
    } catch (error) {
      lastError = error;
      if (!isNotFound(error)) {
        throw error;
      }
    }
  }

  throw lastError ?? new Error('Request failed');
}

async function getByPathsAllowForbidden<T>(paths: string[], config?: AxiosRequestConfig): Promise<T> {
  const safeConfig = sanitizeRequestConfig(config);
  let lastError: unknown;

  for (const path of paths) {
    try {
      const response = await directApi.get<T>(path, safeConfig);
      return response.data;
    } catch (error) {
      lastError = error;
      if (!isNotFound(error) && !isForbidden(error)) {
        throw error;
      }
    }
  }

  throw lastError ?? new Error('Request failed');
}

async function postByPaths<TResponse, TBody = unknown>(
  paths: string[],
  body?: TBody,
  config?: AxiosRequestConfig
): Promise<TResponse> {
  let lastError: unknown;

  for (const path of paths) {
    try {
      const response = await directApi.post<TResponse>(path, body, config);
      return response.data;
    } catch (error) {
      lastError = error;
      if (!isNotFound(error)) {
        throw error;
      }
    }
  }

  throw lastError ?? new Error('Request failed');
}

async function deleteByPaths(paths: string[], config?: AxiosRequestConfig): Promise<void> {
  let lastError: unknown;

  for (const path of paths) {
    try {
      await directApi.delete(path, config);
      return;
    } catch (error) {
      lastError = error;
      if (!isNotFound(error)) {
        throw error;
      }
    }
  }

  throw lastError ?? new Error('Request failed');
}

function toMetricsResponse(data: unknown): MetricsResponse {
  const source = (data ?? {}) as Partial<MetricsResponse>;

  return {
    totalLogs: Number(source.totalLogs) || 0,
    errorCount: Number(source.errorCount) || 0,
    errorRate: Number(source.errorRate) || 0,
    avgResponseTime: Number(source.avgResponseTime) || 0,
    p95Latency: Number(source.p95Latency) || 0,
    bucketInterval: typeof source.bucketInterval === 'string' ? source.bucketInterval : '1m',
    throughputOverTime: Array.isArray(source.throughputOverTime)
      ? source.throughputOverTime.map((item) => ({
          time: String(item?.time || ''),
          count: Number(item?.count) || 0,
          intervalSeconds: Number(item?.intervalSeconds) || 0,
          throughputPerSecond: Number(item?.throughputPerSecond) || 0,
          errorCount: Number(item?.errorCount) || 0,
          errorRate: Number(item?.errorRate) || 0,
          avgResponseTime: Number(item?.avgResponseTime) || 0
        }))
      : [],
    levelDistribution: Array.isArray(source.levelDistribution)
      ? source.levelDistribution
          .map((item) => ({
            level: String(item?.level || ''),
            count: Number(item?.count) || 0
          }))
          .filter((item) => item.level.length > 0)
      : []
  };
}

const emptyMetrics: MetricsResponse = {
  totalLogs: 0,
  errorCount: 0,
  errorRate: 0,
  avgResponseTime: 0,
  p95Latency: 0,
  bucketInterval: '1m',
  throughputOverTime: [],
  levelDistribution: []
};

export const apiService = {
  async fetchLogs(filters: LogFilters): Promise<LogEvent[]> {
    const params: LogQueryParams = buildLogQueryParams(filters);

    const data = await getByPaths<LogEvent[]>(['/api/logs', '/logs'], { params });
    return Array.isArray(data) ? data : [];
  },

  async getUsers(): Promise<UserRecord[]> {
    const data = await getByPaths<UserRecord[]>(['/api/admin/users', '/admin/users', '/api/users', '/users']);
    return Array.isArray(data) ? data : [];
  },

  async fetchUsers(): Promise<UserRecord[]> {
    return apiService.getUsers();
  },

  async createUser(payload: {
    id?: string;
    username: string;
    email: string;
    roles: string[];
    services: string[];
  }): Promise<UserRecord> {
    const normalizedRoles = payload.roles || [];
    const normalizedServices = payload.services || [];
    const requestPayload = {
      id: payload.id,
      username: payload.username,
      email: payload.email,
      role: normalizedRoles[0] || '',
      serviceName: normalizedServices[0] || '',
      roles: normalizedRoles,
      services: normalizedServices
    };

    return postByPaths<UserRecord, typeof requestPayload>(['/api/admin/users', '/admin/users'], requestPayload);
  },

  async updateUser(
    userId: string,
    payload: {
      username: string;
      email: string;
      roles: string[];
      services: string[];
    }
  ): Promise<UserRecord> {
    return postByPaths<UserRecord, typeof payload>([`/api/admin/users/${userId}`, `/admin/users/${userId}`], payload);
  },

  async deleteUser(userId: string): Promise<void> {
    await deleteByPaths([`/api/admin/users/${userId}`, `/admin/users/${userId}`]);
  },

  async getServices(): Promise<ServiceRecord[]> {
    const data = await getByPaths<any[]>(['/api/services']);
    if (!Array.isArray(data)) {
      return [];
    }
    return data
      .map((item) => {
        if (typeof item === 'string') {
          return { name: item.trim().toLowerCase() };
        }
        if (item && typeof item === 'object') {
          return {
            id: item.id,
            name: (item.name || '').trim().toLowerCase(),
            description: item.description,
            active: item.active,
            owners: item.owners
          };
        }
        return null;
      })
      .filter((item): item is ServiceRecord => item !== null && item.name.length > 0);
  },

  async getAdminServices(): Promise<ServiceRecord[]> {
    const data = await getByPaths<ServiceRecord[]>(['/api/admin/services', '/admin/services']);
    return Array.isArray(data) ? data : [];
  },

  async fetchServices(): Promise<ServiceRecord[]> {
    return apiService.getServices();
  },

  async createService(payload: { name: string; description: string }): Promise<ServiceRecord> {
    return postByPaths<ServiceRecord, typeof payload>(['/api/admin/services', '/admin/services'], payload);
  },

  async updateService(serviceId: string, payload: { name: string; description: string }): Promise<ServiceRecord> {
    return postByPaths<ServiceRecord, typeof payload>([`/api/admin/services/${serviceId}`, `/admin/services/${serviceId}`], payload);
  },

  async deleteService(serviceId: string): Promise<void> {
    await deleteByPaths([`/api/admin/services/${serviceId}`, `/admin/services/${serviceId}`]);
  },

  async requestService(payload: { serviceName: string; description?: string }): Promise<ServiceAccessRequest> {
    return postByPaths<ServiceAccessRequest, typeof payload>(
      ['/api/services/request', '/api/services/requests', '/services/request', '/services/requests'],
      payload
    );
  },

  async approveService(
    requestId: string,
    payload?: { comment?: string; description?: string }
  ): Promise<ServiceAccessRequest> {
    return postByPaths<ServiceAccessRequest, typeof payload>(
      [
        `/api/services/${requestId}/approve`,
        `/api/admin/services/requests/${requestId}/approve`,
        `/admin/services/requests/${requestId}/approve`
      ],
      payload || {}
    );
  },

  async rejectService(requestId: string, payload?: { comment?: string }): Promise<ServiceAccessRequest> {
    return postByPaths<ServiceAccessRequest, typeof payload>(
      [
        `/api/services/${requestId}/reject`,
        `/api/admin/services/requests/${requestId}/reject`,
        `/admin/services/requests/${requestId}/reject`
      ],
      payload || {}
    );
  },

  async getServiceRequests(): Promise<ServiceAccessRequest[]> {
    try {
      const data = await getByPaths<ServiceAccessRequest[]>([
        '/api/services/requests',
        '/services/requests',
        '/api/admin/services/requests',
        '/admin/services/requests'
      ], {
        headers: {
          'Cache-Control': 'no-cache',
          Pragma: 'no-cache'
        }
      });
      return Array.isArray(data) ? data : [];
    } catch (error) {
      if (isForbidden(error) || isNotFound(error)) {
        const mine = await getByPaths<ServiceAccessRequest[]>([
          '/api/services/requests/mine',
          '/services/requests/mine'
        ]);
        return Array.isArray(mine) ? mine : [];
      }
      throw error;
    }
  },

  async fetchMetrics(filters: LogFilters): Promise<MetricsResponse> {
    const params = buildLogQueryParams(filters);
    try {
      const response = await getByPaths<unknown>(['/api/logs/metrics', '/logs/metrics'], { params });
      return toMetricsResponse(response);
    } catch {
      return emptyMetrics;
    }
  },

  async queryAgent(payload: AgentQueryRequest): Promise<AgentQueryResponse> {
    const response = await api.post<AgentQueryResponse>('/agent/query', payload);
    return response.data;
  },

  async fetchAlerts(): Promise<AlertItem[]> {
    try {
      const response = await api.get<AlertsResponse>('/alerts');
      const grouped = response.data;

      if (!grouped || typeof grouped !== 'object' || Array.isArray(grouped)) {
        return [];
      }

      // Backend returns { "service-name": [AlertItem, ...], ... }
      // Flatten all service groups into a single list sorted by severity then timestamp.
      return Object.values(grouped).flat().sort((a, b) => {
        const severityRank = (s: string) => (s === 'CRITICAL' ? 0 : s === 'WARNING' ? 1 : 2);
        const rankDiff = severityRank(a.severity) - severityRank(b.severity);
        if (rankDiff !== 0) return rankDiff;
        const ta = a.timestamp ? new Date(a.timestamp).getTime() : 0;
        const tb = b.timestamp ? new Date(b.timestamp).getTime() : 0;
        return tb - ta;
      });
    } catch {
      return [];
    }
  },

  async getNotificationPreferences(): Promise<NotificationPreference> {
    const response = await api.get<NotificationPreference>('/notifications/preferences');
    return response.data;
  },

  async updateNotificationPreferences(
    payload: NotificationPreferenceUpdate
  ): Promise<NotificationPreference> {
    const response = await api.put<NotificationPreference>('/notifications/preferences', payload);
    return response.data;
  },

  async uploadProfileImage(file: File): Promise<{ message: string; imageUrl: string }> {
    const formData = new FormData();
    formData.append('file', file);
    const response = await api.post<{ message: string; imageUrl: string }>('/users/upload-profile-image', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
    return response.data;
  },

  async getJiraConfiguration(): Promise<JiraConfiguration> {
    const response = await api.get<JiraConfiguration>('/jira/configuration');
    return response.data;
  },

  async saveJiraConfiguration(payload: JiraConfiguration): Promise<JiraConfiguration> {
    const hasConfig = !!payload.id;
    if (hasConfig) {
      const response = await api.put<JiraConfiguration>('/jira/configuration', payload);
      return response.data;
    } else {
      const response = await api.post<JiraConfiguration>('/jira/configuration', payload);
      return response.data;
    }
  },

  async testJiraConnection(payload: JiraConfiguration): Promise<{ message: string }> {
    const response = await api.post<{ message: string }>('/jira/test-connection', payload);
    return response.data;
  },

  async getJiraUsers(query?: string): Promise<JiraUser[]> {
    const response = await api.get<JiraUser[]>('/jira/users', { params: { query } });
    return response.data;
  },

  async getUserJiraMappings(): Promise<UserJiraMapping[]> {
    const response = await api.get<UserJiraMapping[]>('/jira/user-mappings');
    return response.data;
  },

  async createUserJiraMapping(payload: { userId: string; jiraAccountId: string; jiraDisplayName: string; active: boolean }): Promise<UserJiraMapping> {
    const response = await api.post<UserJiraMapping>('/jira/user-mappings', payload);
    return response.data;
  },

  async updateUserJiraMapping(id: string, payload: { userId: string; jiraAccountId: string; jiraDisplayName: string; active: boolean }): Promise<UserJiraMapping> {
    const response = await api.put<UserJiraMapping>(`/jira/user-mappings/${id}`, payload);
    return response.data;
  },

  async deleteUserJiraMapping(id: string): Promise<void> {
    await api.delete(`/jira/user-mappings/${id}`);
  },

  async setPrimaryOwner(serviceName: string, userId: string): Promise<void> {
    await api.post(`/services/${encodeURIComponent(serviceName)}/primary-owner`, null, {
      params: { userId }
    });
  },

  async getServiceSecret(serviceId: string): Promise<{ serviceSecret: string | null; hidden: boolean; secondsRemaining: number | null }> {
    const response = await api.get<{ serviceSecret: string | null; hidden: boolean; secondsRemaining: number | null }>(`/services/${serviceId}/secret`);
    return response.data;
  },

  async regenerateServiceSecret(serviceId: string): Promise<{ serviceId: string; serviceName: string; serviceSecret: string }> {
    const response = await api.post<{ serviceId: string; serviceName: string; serviceSecret: string }>(`/services/${serviceId}/regenerate-secret`);
    return response.data;
  },

  async getServiceApiKey(serviceId: string): Promise<{ serviceId: string; serviceName: string; apiKey: string }> {
    const response = await api.get<{ serviceId: string; serviceName: string; apiKey: string }>(`/services/${serviceId}/api-key`);
    return response.data;
  },

  async regenerateServiceApiKey(serviceId: string): Promise<{ serviceId: string; serviceName: string; apiKey: string }> {
    const response = await api.post<{ serviceId: string; serviceName: string; apiKey: string }>(`/services/${serviceId}/regenerate-api-key`);
    return response.data;
  },

  async fetchSourceCode(
    service: string,
    className?: string,
    fileName?: string,
    line?: number
  ): Promise<{
    service: string;
    fileName: string;
    filePath: string;
    lineNumber: number;
    fileContent: string;
    targetLine: number;
  }> {
    const response = await api.get('/logs/source-code', {
      params: {
        service,
        class: className,
        file: fileName,
        line
      }
    });
    return response.data;
  },

  async getServiceHealth(): Promise<ServiceHealth[]> {
    const data = await getByPaths<ServiceHealth[]>(['/api/services/health', '/services/health']);
    return Array.isArray(data) ? data : [];
  },

  async triggerJiraStory(payload: {
    alertId: string;
    alertName: string;
    serviceName: string;
    priority: string;
    triggeredAt: string;
    alertRule: string;
    observedValue: string;
    threshold: string;
    timeWindow: string;
    errorCount: number;
    topErrors: string;
    alertUrl: string;
  }): Promise<{ status: string; message: string; jiraIssueKey?: string; jiraIssueUrl?: string }> {
    const response = await api.post('/notifications/jira/stories', payload);
    return response.data;
  },

  async getLogsServiceHealth(windowMinutes = 15): Promise<any[]> {
    const data = await getByPaths<any[]>(['/api/logs/service-health', '/logs/service-health'], {
      params: { windowMinutes }
    });
    return Array.isArray(data) ? data : [];
  },

  async getOrganizations(): Promise<Organization[]> {
    const data = await getByPaths<Organization[]>(['/api/organizations', '/organizations']);
    return Array.isArray(data) ? data : [];
  },

  async getOrganization(orgId: string): Promise<Organization> {
    return getByPaths<Organization>([`/api/organizations/${orgId}`, `/organizations/${orgId}`]);
  },

  async updateOrganization(orgId: string, name: string): Promise<Organization> {
    // The backend uses PUT /api/organizations/{orgId}
    const token = localStorage.getItem('token');
    const response = await api.put<Organization>(`/organizations/${orgId}`, { name }, {
      headers: { Authorization: `Bearer ${token}` }
    });
    return response.data;
  },

  async deleteOrganization(orgId: string): Promise<void> {
    const token = localStorage.getItem('token');
    await api.delete(`/organizations/${orgId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async getOrganizationMembers(orgId: string): Promise<OrganizationMember[]> {
    const data = await getByPaths<OrganizationMember[]>([`/api/organizations/${orgId}/members`, `/organizations/${orgId}/members`]);
    return Array.isArray(data) ? data : [];
  },

  async getJoinRequests(orgId: string): Promise<JoinRequest[]> {
    const data = await getByPaths<JoinRequest[]>([`/api/organizations/${orgId}/join-requests`, `/organizations/${orgId}/join-requests`]);
    return Array.isArray(data) ? data : [];
  },

  async approveJoinRequest(requestId: string): Promise<void> {
    await postByPaths<void, void>([`/api/organizations/join-request/${requestId}/approve`, `/organizations/join-request/${requestId}/approve`], undefined);
  },

  async rejectJoinRequest(requestId: string): Promise<void> {
    await postByPaths<void, void>([`/api/organizations/join-request/${requestId}/reject`, `/organizations/join-request/${requestId}/reject`], undefined);
  },

  async transferOwnership(orgId: string, targetUserId: string): Promise<void> {
    const token = localStorage.getItem('token');
    await api.post<void>(`/organizations/${orgId}/transfer-ownership`, { targetUserId }, {
      headers: { Authorization: `Bearer ${token}` }
    });
  },

  async inviteUser(orgId: string, email: string): Promise<any> {
    return postByPaths<any, { organizationId: string; email: string }>(['/api/organizations/invite', '/organizations/invite'], { organizationId: orgId, email });
  },

  async acceptInvite(inviteId: string): Promise<void> {
    await postByPaths<void, void>(['/api/organizations/invite/accept'], undefined, { params: { inviteId } });
  },

  async removeMember(userId: string): Promise<void> {
    await deleteByPaths([`/api/users/${userId}`, `/users/${userId}`]);
  },

  async promoteMember(userId: string): Promise<void> {
    await postByPaths<void, void>([`/api/users/${userId}/promote`, `/users/${userId}/promote`], undefined);
  },

  async demoteMember(userId: string): Promise<void> {
    await postByPaths<void, void>([`/api/users/${userId}/demote`, `/users/${userId}/demote`], undefined);
  },

  async createOrganization(name: string, type: 'BUSINESS' | 'PERSONAL'): Promise<Organization> {
    return postByPaths<Organization, { name: string; type: string }>(['/api/organizations', '/organizations'], { name, type });
  },

  async switchOrganization(orgId: string): Promise<any> {
    return postByPaths<any, { organizationId: string }>(['/api/organizations/switch', '/organizations/switch'], { organizationId: orgId });
  }
};

export const authService = {
  async getSession(): Promise<Partial<AuthUser> | null> {
    try {
      const response = await api.get('/auth/me');
      return response.data as Partial<AuthUser>;
    } catch (error) {
      if (isUnauthorized(error)) {
        return null;
      }
      throw error;
    }
  },

  async logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } catch (error) {
      console.error('Logout request failed:', error);
    }
  }
};
