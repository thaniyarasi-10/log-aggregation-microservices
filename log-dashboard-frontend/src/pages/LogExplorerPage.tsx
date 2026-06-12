import React, { useState, useEffect, useMemo, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useRealtimeLogs } from '../hooks/useRealtimeLogs';
import { useAuth } from '../context/AuthContext';
import { apiService } from '../services/api';
import { PageHeader, FilterToolbar, StatusBadge, EmptyState } from '../components/UI';
import type { LogEvent, LogFilters } from '../types';
import { getLogFingerprint } from '../utils/time';
import LogDrawer from '../components/LogDrawer';

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

export default function LogExplorerPage() {
  const { user, isAdmin } = useAuth();
  const location = useLocation();

  // Parse query params (e.g. search from Ctrl+K, or redirection from dashboard)
  const searchParams = new URLSearchParams(location.search);
  const initialSearch = searchParams.get('search') || '';
  const initialServicesStr = searchParams.get('services') || '';
  const initialLevelsStr = searchParams.get('levels') || '';
  const initialTimeRange = (searchParams.get('timeRange') as LogFilters['timeRange']) || '24h';

  const initialServices = initialServicesStr ? initialServicesStr.split(',').filter(Boolean) : [];
  const initialLevels = initialLevelsStr ? initialLevelsStr.split(',').filter(Boolean) : [];

  const [filters, setFilters] = useState<LogFilters>({
    timeRange: initialTimeRange,
    services: initialServices,
    levels: initialLevels,
    search: initialSearch,
    service: '',
    level: '',
  });

  const [serviceOptions, setServiceOptions] = useState<string[]>([]);
  const [selectedLog, setSelectedLog] = useState<LogEvent | null>(null);

  // Sorting State
  const [sortField, setSortField] = useState<'timestamp' | 'service' | 'level' | 'responseTime'>('timestamp');
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('desc');

  // Pagination State
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);

  const allowedServices = useMemo(
    () => (Array.isArray(user?.allowedServices) ? user.allowedServices : []),
    [user?.allowedServices]
  );

  const { logs, loading, error } = useRealtimeLogs(filters, true, 8000, allowedServices, isAdmin);

  // Load Services Dropdown
  useEffect(() => {
    let active = true;
    const loadServices = async () => {
      try {
        const servicesData = await apiService.fetchServices();
        if (!active) return;

        let names = servicesData
          .map((item) => item.name)
          .filter((name): name is string => typeof name === 'string' && name.trim().length > 0);

        if (!isAdmin) {
          const allowed = new Set(allowedServices.map((s) => s.trim().toLowerCase()));
          if (!allowed.has('*')) {
            names = names.filter((name) => allowed.has(name.toLowerCase()));
          }
        }
        names.sort((a, b) => a.localeCompare(b));
        setServiceOptions(names);
      } catch {
        if (active) setServiceOptions([]);
      }
    };
    void loadServices();
    return () => { active = false; };
  }, [allowedServices, isAdmin]);

  // Sync filters if URL query params change
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const search = params.get('search') || '';
    const servicesStr = params.get('services') || '';
    const levelsStr = params.get('levels') || '';
    const timeRange = (params.get('timeRange') as LogFilters['timeRange']) || '24h';

    const services = servicesStr ? servicesStr.split(',').filter(Boolean) : [];
    const levels = levelsStr ? levelsStr.split(',').filter(Boolean) : [];

    setFilters((prev) => {
      // Only update if something changed
      const isServicesSame = prev.services.length === services.length && prev.services.every((v, i) => v === services[i]);
      const isLevelsSame = prev.levels.length === levels.length && prev.levels.every((v, i) => v === levels[i]);
      if (
        prev.search === search &&
        prev.timeRange === timeRange &&
        isServicesSame &&
        isLevelsSame
      ) {
        return prev;
      }

      return {
        ...prev,
        search,
        services,
        levels,
        timeRange,
      };
    });
  }, [location.search]);

  // Reset pagination on filter change
  useEffect(() => {
    setCurrentPage(1);
    setSelectedLog(null);
  }, [filters, sortField, sortOrder]);

  // 1. Client-Side Sorting
  const sortedLogs = useMemo(() => {
    return [...logs].sort((a, b) => {
      let valA: any = '';
      let valB: any = '';

      if (sortField === 'timestamp') {
        valA = a['@timestamp'] ? new Date(a['@timestamp']).getTime() : 0;
        valB = b['@timestamp'] ? new Date(b['@timestamp']).getTime() : 0;
      } else if (sortField === 'service') {
        valA = a.service || '';
        valB = b.service || '';
      } else if (sortField === 'level') {
        valA = a.level || '';
        valB = b.level || '';
      } else if (sortField === 'responseTime') {
        valA = a.responseTime || 0;
        valB = b.responseTime || 0;
      }

      if (typeof valA === 'string') {
        return sortOrder === 'asc' ? valA.localeCompare(valB) : valB.localeCompare(valA);
      }
      return sortOrder === 'asc' ? valA - valB : valB - valA;
    });
  }, [logs, sortField, sortOrder]);

  // 2. Client-Side Pagination
  const totalLogsCount = sortedLogs.length;
  const totalPagesCount = Math.max(1, Math.ceil(totalLogsCount / pageSize));
  const paginatedLogs = useMemo(() => {
    const start = (currentPage - 1) * pageSize;
    return sortedLogs.slice(start, start + pageSize);
  }, [sortedLogs, currentPage, pageSize]);

  const handleSort = (field: typeof sortField) => {
    if (sortField === field) {
      setSortOrder((prev) => (prev === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortField(field);
      setSortOrder('desc');
    }
  };

  const getLogUniqueId = (log: LogEvent, index: number) => {
    return log.id || `${log['@timestamp'] || ''}-${log.service}-${log.level}-${index}`;
  };

  // Handle auto-focus and highlight of log from URL
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const highlight = params.get('highlight') || '';
    if (!highlight || logs.length === 0) return;

    // Find the log that matches the fingerprint
    const matchedIndex = sortedLogs.findIndex((log) => getLogFingerprint(log) === highlight);
    if (matchedIndex !== -1) {
      const matchedLog = sortedLogs[matchedIndex];
      const targetPage = Math.floor(matchedIndex / pageSize) + 1;
      
      // Calculate unique ID for expansion (relative index within the page)
      const pageRelativeIndex = matchedIndex % pageSize;
      const uniqueId = getLogUniqueId(matchedLog, pageRelativeIndex);

      // Set page and expand row
      setCurrentPage(targetPage);
      setSelectedLog(matchedLog);

      // Scroll to row and highlight after DOM renders the page change
      const timer = setTimeout(() => {
        const element = document.getElementById(`log-row-${uniqueId}`);
        if (element) {
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          element.classList.add('log-row-highlighted');
        }
      }, 300);
      return () => clearTimeout(timer);
    }
  }, [location.search, logs, sortedLogs, pageSize]);

  const getLevelClass = (level: string) => {
    const l = level.toUpperCase();
    if (l === 'ERROR' || l === 'CRITICAL' || l === 'FATAL') return 'tag-error';
    if (l === 'WARN' || l === 'WARNING') return 'tag-warn';
    if (l === 'INFO') return 'tag-info';
    return 'tag-debug';
  };

  return (
    <main className="page-container" style={{ height: 'calc(100vh - 44px)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>


      <FilterToolbar
        filters={filters}
        services={serviceOptions}
        onChange={setFilters}
      />

      {loading && logs.length === 0 ? (
        <div className="obs-empty-state">
          <span className="upload-spinner" style={{ width: '24px', height: '24px', marginBottom: '12px' }} />
          <h4 className="obs-empty-title">Loading Logs</h4>
          <p className="obs-empty-desc">Fetching log documents from Elasticsearch database...</p>
        </div>
      ) : error ? (
        <EmptyState
          title="Elasticsearch Query Failed"
          description={error}
          icon="⚠️"
        />
      ) : logs.length === 0 ? (
        <EmptyState
          title="No logs found"
          description="No logs were ingested during the selected time window. Confirm that Filebeat or log services are pushing documents."
          icon="📂"
        />
      ) : (
        <div className="obs-table-workspace-panel" style={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
          <div className="table-scroll-area" style={{ flex: 1, overflowY: 'auto', minHeight: 0 }}>
            <table className="log-table explorer-table" style={{ width: '100%' }}>
              <thead style={{ position: 'sticky', top: 0, zIndex: 5, background: 'var(--table-header-bg)' }}>
                <tr>
                  <th className="sortable-header col-timestamp" onClick={() => handleSort('timestamp')} style={{ cursor: 'pointer' }}>
                    Timestamp {sortField === 'timestamp' && (sortOrder === 'asc' ? '▲' : '▼')}
                  </th>
                  <th className="sortable-header col-service" onClick={() => handleSort('service')} style={{ cursor: 'pointer' }}>
                    Service {sortField === 'service' && (sortOrder === 'asc' ? '▲' : '▼')}
                  </th>
                  <th className="sortable-header col-level" onClick={() => handleSort('level')} style={{ cursor: 'pointer' }}>
                    Level {sortField === 'level' && (sortOrder === 'asc' ? '▲' : '▼')}
                  </th>
                  <th className="col-message">Message</th>
                  <th className="col-status">Status</th>
                  <th className="sortable-header col-latency" onClick={() => handleSort('responseTime')} style={{ cursor: 'pointer' }}>
                    Latency {sortField === 'responseTime' && (sortOrder === 'asc' ? '▲' : '▼')}
                  </th>
                </tr>
              </thead>
              <tbody>
                {paginatedLogs.map((log, index) => {
                  const uniqueId = getLogUniqueId(log, index);
                  const isSelected = selectedLog && getLogFingerprint(selectedLog) === getLogFingerprint(log);

                  return (
                    <tr
                      key={uniqueId}
                      id={`log-row-${uniqueId}`}
                      className={`clickable-row ${log.responseTime && log.responseTime > 1000 ? 'slow-log' : ''} ${isSelected ? 'log-row-active' : ''}`}
                      onClick={() => setSelectedLog(log)}
                      style={{ cursor: 'pointer' }}
                    >
                      <td className="col-timestamp" style={{ fontSize: '0.75rem' }}>
                        {log['@timestamp']
                          ? new Date(log['@timestamp']).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' })
                          : 'N/A'}
                      </td>
                      <td className="col-service">{log.service}</td>
                      <td className="col-level">
                        <span className={`tag ${getLevelClass(String(log.level))}`}>
                          {log.level}
                        </span>
                      </td>
                      <td className="col-message">
                        <div className="message-cell">
                          {log.message}
                        </div>
                      </td>
                      <td className="col-status" style={{ fontWeight: 500 }}>{log.statusCode ?? '-'}</td>
                      <td className="col-latency">
                        {log.responseTime !== undefined && log.responseTime !== null ? `${Math.round(log.responseTime)}ms` : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination Toolbar (embedded with border-top) */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              background: 'var(--surface-raised)',
              borderTop: '1px solid var(--border)',
              padding: '8px 16px',
            }}
          >
            <div style={{ fontSize: '0.8rem', color: 'var(--text-secondary)', paddingLeft: '44px' }}>
              Showing <strong>{Math.min(totalLogsCount, (currentPage - 1) * pageSize + 1)}</strong> to{' '}
              <strong>{Math.min(totalLogsCount, currentPage * pageSize)}</strong> of{' '}
              <strong>{totalLogsCount}</strong> logs
            </div>

            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>Page Size:</span>
                <select
                  className="form-control"
                  style={{ width: '70px', height: '28px', padding: '0 4px' }}
                  value={pageSize}
                  onChange={(e) => { setPageSize(Number(e.target.value)); setCurrentPage(1); }}
                >
                  <option value={20}>20</option>
                  <option value={50}>50</option>
                  <option value={100}>100</option>
                  <option value={200}>200</option>
                </select>
              </div>

              <div style={{ display: 'flex', gap: '4px' }}>
                <button
                  className="btn"
                  style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                  disabled={currentPage === 1}
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                >
                  Previous
                </button>
                <span style={{ display: 'flex', alignItems: 'center', padding: '0 8px', fontSize: '0.8rem', color: 'var(--text-primary)' }}>
                  Page {currentPage} of {totalPagesCount}
                </span>
                <button
                  className="btn"
                  style={{ padding: '4px 10px', fontSize: '0.75rem' }}
                  disabled={currentPage === totalPagesCount}
                  onClick={() => setCurrentPage((p) => Math.min(totalPagesCount, p + 1))}
                >
                  Next
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
      {selectedLog && (
        <LogDrawer log={selectedLog} onClose={() => setSelectedLog(null)} />
      )}
    </main>
  );
}
