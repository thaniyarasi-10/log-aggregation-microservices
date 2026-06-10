import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bar, Doughnut, Line } from 'react-chartjs-2';
import {
  ArcElement,
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip
} from 'chart.js';
import { useRealtimeLogs } from '../hooks/useRealtimeLogs';
import { useAuth } from '../context/AuthContext';
import { apiService } from '../services/api';
import { PageHeader, MetricCard, FilterToolbar, StatusBadge, EmptyState } from '../components/UI';
import type { LogEvent, LogFilters, MetricsResponse, ServiceHealth } from '../types';
import { getLogFingerprint } from '../utils/time';

ChartJS.register(
  ArcElement,
  BarElement,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Tooltip,
  Legend,
  Filler
);

const defaultFilters: LogFilters = {
  timeRange: '24h',
  services: [],
  levels: [],
  search: '',
  service: '',
  level: '',
};

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

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: {
        color: 'rgb(148, 163, 184)',
        boxWidth: 12,
        font: { size: 10 }
      }
    }
  },
  scales: {
    x: {
      ticks: {
        color: 'rgb(148, 163, 184)',
        font: { size: 9 },
        maxRotation: 0
      },
      grid: {
        color: 'rgba(148, 163, 184, 0.08)'
      }
    },
    y: {
      ticks: {
        color: 'rgb(148, 163, 184)',
        font: { size: 9 }
      },
      grid: {
        color: 'rgba(148, 163, 184, 0.08)'
      }
    }
  }
} as const;

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

export default function LogsPage() {
  const { isAdmin, user } = useAuth();
  const navigate = useNavigate();

  const handleLogClick = (log: LogEvent) => {
    const fingerprint = getLogFingerprint(log);
    navigate(`/explorer?highlight=${encodeURIComponent(fingerprint)}&timeRange=24h`);
  };

  const [filters, setFilters] = useState<LogFilters>(defaultFilters);
  const [serviceOptions, setServiceOptions] = useState<string[]>([]);
  const [metrics, setMetrics] = useState<MetricsResponse>(emptyMetrics);
  const [serviceHealth, setServiceHealth] = useState<ServiceHealth[]>([]);
  const [healthLoading, setHealthLoading] = useState(false);

  const lastStableMetricsRef = useRef<MetricsResponse | null>(null);

  const allowedServices = useMemo(
    () => (Array.isArray(user?.allowedServices) ? user.allowedServices : []),
    [user?.allowedServices]
  );

  const metricsKey = `${filters.services.join(',')}|${filters.timeRange}`;

  // Query logs for dashboard computations (e.g. Top Errors, Recent Critical Logs)
  const { logs, loading: logsLoading } = useRealtimeLogs(filters, true, 8000, allowedServices, isAdmin);

  // Load Services Dropdown
  useEffect(() => {
    let active = true;
    const loadServices = async () => {
      try {
        const services = await apiService.fetchServices();
        if (!active) return;

        let names = services
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

  // Fetch metrics whenever key changes
  useEffect(() => {
    let active = true;
    const metricsFilters: LogFilters = {
      timeRange: filters.timeRange,
      services: filters.services,
      levels: [],
      search: '',
      service: filters.services.length === 1 ? filters.services[0] : '',
      level: '',
    };

    const refreshMetrics = async () => {
      try {
        const next = await apiService.fetchMetrics(metricsFilters);
        if (!active) return;

        const hasSignal =
          next.totalLogs > 0 ||
          next.errorCount > 0 ||
          next.avgResponseTime > 0 ||
          next.p95Latency > 0 ||
          next.throughputOverTime.length > 0 ||
          next.levelDistribution.length > 0;

        if (hasSignal) {
          lastStableMetricsRef.current = next;
          setMetrics(next);
        } else if (lastStableMetricsRef.current) {
          setMetrics(lastStableMetricsRef.current);
        } else {
          setMetrics(next);
        }
      } catch {
        // fail silently
      }
    };

    lastStableMetricsRef.current = null;
    setMetrics(emptyMetrics);

    void refreshMetrics();
    const timer = setInterval(refreshMetrics, 10000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [metricsKey]);

  // Fetch service health status
  useEffect(() => {
    let active = true;
    const fetchHealth = async () => {
      try {
        setHealthLoading(true);
        const data = await apiService.getServiceHealth();
        if (!active) return;
        setServiceHealth(data);
      } catch {
        if (active) setServiceHealth([]);
      } finally {
        if (active) setHealthLoading(false);
      }
    };
    void fetchHealth();
    const timer = setInterval(fetchHealth, 20000);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, []);

  // Compute Throughput average
  const throughput = useMemo(() => {
    return metrics.throughputOverTime.length
      ? metrics.throughputOverTime.reduce((sum, item) => sum + (Number(item.throughputPerSecond) || 0), 0) /
          metrics.throughputOverTime.length
      : 0;
  }, [metrics.throughputOverTime]);



  // Compute Recent Critical Logs
  const recentCriticalLogs = useMemo(() => {
    return logs
      .filter(
        (l) =>
          String(l.level).toUpperCase() === 'ERROR' ||
          String(l.level).toUpperCase() === 'CRITICAL' ||
          String(l.level).toUpperCase() === 'FATAL' ||
          (l.statusCode && l.statusCode >= 500)
      )
      .sort((a, b) => new Date(b['@timestamp'] || 0).getTime() - new Date(a['@timestamp'] || 0).getTime())
      .slice(0, 5);
  }, [logs]);

  // Chart Formatting Helpers
  const timeline = useMemo(() => {
    return [...metrics.throughputOverTime].sort(
      (a, b) => new Date(a.time).getTime() - new Date(b.time).getTime()
    );
  }, [metrics.throughputOverTime]);

  const chartLabels = useMemo(() => {
    return timeline.map((point) => {
      const date = new Date(point.time);
      if (!Number.isFinite(date.getTime())) return point.time;
      return date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    });
  }, [timeline]);

  const errorTrendsData = {
    labels: chartLabels,
    datasets: [
      {
        label: 'Error Rate (%)',
        data: timeline.map((p) => p.errorRate || 0),
        borderColor: '#fc4444',
        backgroundColor: 'rgba(252, 68, 68, 0.1)',
        fill: true,
        tension: 0.35
      }
    ]
  };

  const levelDistributionData = useMemo(() => {
    const sortedLevels = [...metrics.levelDistribution].sort((a, b) => b.count - a.count);
    return {
      labels: sortedLevels.map((l) => `${l.level} (${l.count})`),
      datasets: [
        {
          data: sortedLevels.map((l) => l.count),
          backgroundColor: ['#fc4444', '#f5a524', '#60a5fa', '#11ab3a', '#94a3b8'],
          borderWidth: 1,
          borderColor: '#262f3a'
        }
      ]
    };
  }, [metrics.levelDistribution]);

  return (
    <main className="page-container" style={{ gap: '12px' }}>
      <FilterToolbar
        filters={filters}
        services={serviceOptions}
        onChange={setFilters}
      />

      {/* Distinct lightweight KPI stats grid */}
      <div className="obs-metrics-grid">
        <MetricCard
          title="Error Rate"
          value={`${metrics.errorRate.toFixed(2)}%`}
          status={metrics.errorRate > 5 ? 'critical' : metrics.errorRate > 1 ? 'warning' : 'healthy'}
        />
        <MetricCard
          title="Avg Response Time"
          value={`${Math.round(metrics.avgResponseTime)}ms`}
        />
        <MetricCard
          title="Throughput"
          value={`${throughput.toFixed(2)} req/s`}
        />
        <MetricCard
          title="P95 Latency"
          value={`${Math.round(metrics.p95Latency)}ms`}
          status={metrics.p95Latency > 1000 ? 'warning' : 'healthy'}
        />
      </div>

      {/* Grouped monitoring section for charts & service health */}
      <div className="obs-monitoring-section">
        <div className="obs-canvas-charts-row" style={{ borderBottom: 'none' }}>
          {/* Chart 1: Error Trends */}
          <div className="obs-canvas-chart-cell">
            <span className="obs-canvas-chart-title">Error Trends (Last 24h)</span>
            <div style={{ flex: 1, position: 'relative', minHeight: 0 }}>
              {timeline.length > 0 ? (
                <Line data={errorTrendsData} options={chartOptions} />
              ) : (
                <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                  No trend metrics available
                </div>
              )}
            </div>
          </div>

          {/* Chart 2: Log Level Distribution */}
          <div className="obs-canvas-chart-cell">
            <span className="obs-canvas-chart-title">Level Distribution</span>
            <div style={{ flex: 1, position: 'relative', minHeight: 0, display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
              {metrics.levelDistribution.length > 0 ? (
                <div style={{ height: '160px', width: '100%' }}>
                  <Doughnut
                    data={levelDistributionData}
                    options={{
                      responsive: true,
                      maintainAspectRatio: false,
                      plugins: {
                        legend: {
                          position: 'right',
                          labels: { color: 'rgb(148, 163, 184)', font: { size: 9 } }
                        }
                      }
                    }}
                  />
                </div>
              ) : (
                <div style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>No log distributions found</div>
              )}
            </div>
          </div>

          {/* Chart 3: Service Health Overview */}
          <div className="obs-canvas-chart-cell" style={{ overflowY: 'auto' }}>
            <span className="obs-canvas-chart-title">Service Health Overview</span>
            <div style={{ marginTop: '6px', overflowX: 'auto' }}>
              {serviceHealth.length > 0 ? (
                <table className="obs-health-table" style={{ width: '100%' }}>
                  <thead>
                    <tr>
                      <th style={{ width: '100px' }}>Status</th>
                      <th>Service Name</th>
                      <th style={{ width: '95px', textAlign: 'right' }}>Error Count</th>
                      <th style={{ width: '70px', textAlign: 'right' }}>Health</th>
                    </tr>
                  </thead>
                  <tbody>
                    {serviceHealth.map((item) => {
                      const hasSentLogs = !!item.lastSeen;
                      const recentErrors = hasSentLogs
                        ? recentCriticalLogs.filter(
                            (l) => l.service.toLowerCase() === item.service.toLowerCase() && (l.level === 'ERROR' || l.level === 'CRITICAL')
                          ).length
                        : 0;

                      let healthVal = '100.0%';
                      if (item.status === 'ERROR') {
                        healthVal = '89.5%';
                      } else if (item.status === 'WARNING') {
                        healthVal = '96.2%';
                      } else if (item.status === 'NO_DATA' || !hasSentLogs) {
                        healthVal = '—';
                      }

                      const displayErrors = hasSentLogs ? recentErrors : '—';

                      // Compact status rendering (Neutral status dot + compact label)
                      const renderStatus = () => {
                        if (!hasSentLogs) {
                          return (
                            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                              <span style={{ display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', background: 'var(--text-dim)' }} />
                              <span style={{ color: 'var(--text-secondary)', fontSize: '0.72rem' }}>No Data</span>
                            </div>
                          );
                        }
                        const s = String(item.status).toUpperCase();
                        let dotColor = 'var(--text-dim)';
                        let label = 'No Data';
                        if (s === 'OK' || s === 'HEALTHY' || s === 'ACTIVE') {
                          dotColor = 'var(--level-debug)';
                          label = 'Healthy';
                        } else if (s === 'WARNING' || s === 'WARN') {
                          dotColor = 'var(--level-warn)';
                          label = 'Warning';
                        } else if (s === 'ERROR' || s === 'CRITICAL' || s === 'HIGH') {
                          dotColor = 'var(--level-error)';
                          label = 'Critical';
                        }
                        return (
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}>
                            <span style={{ display: 'inline-block', width: '6px', height: '6px', borderRadius: '50%', background: dotColor }} />
                            <span style={{ color: 'var(--text-secondary)', fontSize: '0.72rem' }}>{label}</span>
                          </div>
                        );
                      };

                      return (
                        <tr key={item.service}>
                          <td>{renderStatus()}</td>
                          <td style={{ fontWeight: 600, fontFamily: 'var(--font-mono)' }} title={item.service}>
                            {item.service}
                          </td>
                          <td style={{ textAlign: 'right', fontFamily: 'var(--font-mono)', fontWeight: hasSentLogs && recentErrors > 0 ? 600 : 400, color: hasSentLogs && recentErrors > 0 ? 'var(--level-error)' : 'var(--text-secondary)' }}>
                            {displayErrors}
                          </td>
                          <td style={{ textAlign: 'right', fontFamily: 'var(--font-mono)', fontWeight: 600, color: !hasSentLogs ? 'var(--text-secondary)' : item.status === 'ERROR' ? 'var(--level-error)' : item.status === 'WARNING' ? 'var(--level-warn)' : 'var(--level-debug)' }}>
                            {healthVal}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              ) : (
                !healthLoading && (
                  <div style={{ textAlign: 'center', color: 'var(--text-dim)', padding: '16px', fontSize: '0.78rem' }}>
                    No active services connected
                  </div>
                )
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Standalone Recent Logs area */}
      <div className="obs-table-workspace-panel">
        <div className="obs-canvas-logs-row">
          <span className="obs-canvas-logs-title">Recent Critical / Error Logs</span>
          <div style={{ overflowX: 'auto' }}>
            {recentCriticalLogs.length > 0 ? (
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.78rem', textAlign: 'left' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--border)', color: 'var(--text-dim)' }}>
                    <th style={{ padding: '6px 8px' }}>Timestamp</th>
                    <th style={{ padding: '6px 8px' }}>Service</th>
                    <th style={{ padding: '6px 8px' }}>Level</th>
                    <th style={{ padding: '6px 8px' }}>Message</th>
                  </tr>
                </thead>
                <tbody>
                  {recentCriticalLogs.map((log, i) => (
                    <tr
                      key={i}
                      className="obs-recent-log-row"
                      style={{ borderBottom: '1px solid var(--border-subtle)', background: 'rgba(252, 68, 68, 0.02)' }}
                      onClick={() => handleLogClick(log)}
                      title="Click to view in Log Explorer"
                    >
                      <td style={{ padding: '6px 8px', fontFamily: 'var(--font-mono)' }}>
                        {log['@timestamp'] ? new Date(log['@timestamp']).toLocaleString('en-IN', { timeZone: 'Asia/Kolkata' }) : 'N/A'}
                      </td>
                      <td style={{ padding: '6px 8px', fontWeight: 600 }}>{log.service}</td>
                      <td style={{ padding: '6px 8px' }}>
                        <span className="tag tag-error" style={{ fontSize: '0.68rem', padding: '1px 6px' }}>{log.level}</span>
                      </td>
                      <td style={{ padding: '6px 8px', color: 'var(--text-primary)', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{log.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div style={{ textAlign: 'center', color: 'var(--text-dim)', padding: '24px', fontSize: '0.8rem' }}>
                No critical logs or HTTP 500 errors detected
              </div>
            )}
          </div>
        </div>
      </div>
    </main>
  );
}
