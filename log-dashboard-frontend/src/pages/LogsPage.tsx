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
import { useOrganization } from '../context/OrganizationContext';
import { apiService } from '../services/api';
import { PageHeader, MetricCard, FilterToolbar, StatusBadge, EmptyState } from '../components/UI';
import type { LogEvent, LogFilters, MetricsResponse, ServiceHealth } from '../types';
import { getLogFingerprint } from '../utils/time';
import SidebarFilters from '../components/SidebarFilters';

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
  const { activeOrganization } = useOrganization();
  const navigate = useNavigate();

  const handleLogClick = (log: LogEvent) => {
    const fingerprint = getLogFingerprint(log);
    navigate(`/explorer?highlight=${encodeURIComponent(fingerprint)}&timeRange=24h`);
  };

  const handleTopErrorClick = (errorItem: { service: string; message: string }) => {
    navigate(`/explorer?service=${encodeURIComponent(errorItem.service)}&search=${encodeURIComponent(errorItem.message)}&timeRange=24h`);
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
  }, [allowedServices, isAdmin, activeOrganization?.id]);

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
  }, [metricsKey, activeOrganization?.id]);

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
  }, [activeOrganization?.id]);

  // Compute Throughput average
  const throughput = useMemo(() => {
    return metrics.throughputOverTime.length
      ? metrics.throughputOverTime.reduce((sum, item) => sum + (Number(item.throughputPerSecond) || 0), 0) /
          metrics.throughputOverTime.length
      : 0;
  }, [metrics.throughputOverTime]);

  // Compute Top Errors (grouped by service and message, sorted by frequency count descending)
  const topErrors = useMemo(() => {
    const errorMap = new Map<string, { service: string; message: string; level: string; count: number; lastSeen: string }>();
    
    logs.forEach((log) => {
      const isError = ['ERROR', 'CRITICAL', 'FATAL'].includes(String(log.level).toUpperCase()) || (log.statusCode && log.statusCode >= 500);
      if (!isError) return;
      
      const key = `${log.service || 'unknown'}|${log.message || ''}`;
      const existing = errorMap.get(key);
      const timestamp = log['@timestamp'] || '';
      
      if (existing) {
        existing.count += 1;
        if (new Date(timestamp).getTime() > new Date(existing.lastSeen).getTime()) {
          existing.lastSeen = timestamp;
        }
      } else {
        errorMap.set(key, {
          service: log.service || 'unknown',
          message: log.message || '',
          level: log.level || 'ERROR',
          count: 1,
          lastSeen: timestamp
        });
      }
    });
    
    return Array.from(errorMap.values())
      .sort((a, b) => b.count - a.count)
      .slice(0, 10);
  }, [logs]);

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
    
    // Exact color mapping to match standard log level aesthetics:
    // ERROR -> red (#fc4444)
    // WARN -> orange (#f5a524)
    // INFO -> blue (#60a5fa)
    // DEBUG -> green (#11ab3a)
    // others -> gray (#94a3b8)
    const colorMap: Record<string, string> = {
      ERROR: '#fc4444',
      WARN: '#f5a524',
      INFO: '#60a5fa',
      DEBUG: '#11ab3a',
    };

    const backgroundColors = sortedLevels.map(
      (l) => colorMap[l.level.toUpperCase()] || '#94a3b8'
    );

    return {
      labels: sortedLevels.map((l) => `${l.level.toLowerCase()} (${l.count})`),
      datasets: [
        {
          data: sortedLevels.map((l) => l.count),
          backgroundColor: backgroundColors,
          borderWidth: 1,
          borderColor: '#262f3a'
        }
      ]
    };
  }, [metrics.levelDistribution]);

  const levelDistributionOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'top' as const,
        labels: {
          color: 'rgb(148, 163, 184)',
          font: { size: 9 },
          boxWidth: 12,
          padding: 8
        }
      }
    }
  };

  const responseTimeDistributionData = useMemo(() => {
    return {
      labels: chartLabels,
      datasets: [
        {
          label: 'Response Time (ms)',
          data: timeline.map((p) => p.avgResponseTime || 0),
          backgroundColor: '#3b82f6',
          borderRadius: 2,
        }
      ]
    };
  }, [timeline, chartLabels]);

  const throughputTrendsData = useMemo(() => {
    return {
      labels: chartLabels,
      datasets: [
        {
          label: 'Logs/sec',
          data: timeline.map((p) => p.throughputPerSecond || 0),
          borderColor: '#10b981',
          backgroundColor: 'rgba(16, 185, 129, 0.1)',
          fill: true,
          tension: 0.35
        }
      ]
    };
  }, [timeline, chartLabels]);

  return (
    <div className="dashboard-grid">
      <SidebarFilters
        filters={filters}
        services={serviceOptions}
        onChange={setFilters}
        hideLevels={true}
      />

      <div className="dashboard-main animate-fade-in">
        {/* KPI metrics row at the top */}
        <section className="dashboard-analytics">
          <div className="metrics-row">
            <div className="metric-card">
              <span className="metric-title">Error Rate</span>
              <span className="metric-value">{`${metrics.errorRate.toFixed(2)}%`}</span>
            </div>
            <div className="metric-card">
              <span className="metric-title">Avg Response Time</span>
              <span className="metric-value">{`${Math.round(metrics.avgResponseTime)}ms`}</span>
            </div>
            <div className="metric-card">
              <span className="metric-title">Throughput</span>
              <span className="metric-value">{`${throughput.toFixed(2)} req/s`}</span>
            </div>
            <div className="metric-card">
              <span className="metric-title">P95 Latency</span>
              <span className="metric-value">{`${Math.round(metrics.p95Latency)}ms`}</span>
            </div>
          </div>

          {/* First row of charts */}
          <div className="charts-row">
            <div className="chart-container">
              <h3>Error Rate Over Time</h3>
              <div className="chart-wrapper">
                {timeline.length > 0 ? (
                  <Line data={errorTrendsData} options={chartOptions} />
                ) : (
                  <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                    No trend metrics available
                  </div>
                )}
              </div>
            </div>
            <div className="chart-container">
              <h3>Response Time Distribution</h3>
              <div className="chart-wrapper">
                {timeline.length > 0 ? (
                  <Bar data={responseTimeDistributionData} options={chartOptions} />
                ) : (
                  <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                    No distribution metrics available
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* Second row of charts */}
          <div className="charts-row">
            <div className="chart-container">
              <h3>Throughput Over Time</h3>
              <div className="chart-wrapper">
                {timeline.length > 0 ? (
                  <Line data={throughputTrendsData} options={chartOptions} />
                ) : (
                  <div style={{ display: 'flex', height: '100%', alignItems: 'center', justifyContent: 'center', color: 'var(--text-dim)', fontSize: '0.8rem' }}>
                    No throughput metrics available
                  </div>
                )}
              </div>
            </div>
            <div className="chart-container">
              <h3>Level Distribution</h3>
              <div className="chart-wrapper" style={{ display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
                {metrics.levelDistribution.length > 0 ? (
                  <div style={{ height: '160px', width: '100%' }}>
                    <Doughnut data={levelDistributionData} options={levelDistributionOptions} />
                  </div>
                ) : (
                  <div style={{ color: 'var(--text-dim)', fontSize: '0.8rem' }}>No log distributions found</div>
                )}
              </div>
            </div>
          </div>
        </section>

        {/* Top Errors table panel at the bottom */}
        <section className="dashboard-logs">
          <div className="table-container">
            <div className="table-header">
              <h2>Top Errors (by Frequency)</h2>
            </div>
            <div className="table-scroll-area">
              {topErrors.length > 0 ? (
                <table className="log-table top-errors-table">
                  <thead>
                    <tr>
                      <th style={{ width: '20%' }}>Service</th>
                      <th style={{ width: '10%' }}>Level</th>
                      <th style={{ width: '45%' }}>Error Message</th>
                      <th style={{ width: '10%', textAlign: 'center' }}>Occurrences</th>
                      <th style={{ width: '15%' }}>Last Occurred</th>
                    </tr>
                  </thead>
                  <tbody>
                    {topErrors.map((errorItem, i) => {
                      const isError = ['ERROR', 'CRITICAL', 'FATAL'].includes(String(errorItem.level).toUpperCase());
                      const isWarn = String(errorItem.level).toUpperCase() === 'WARN';
                      const isInfo = String(errorItem.level).toUpperCase() === 'INFO';
                      const isDebug = String(errorItem.level).toUpperCase() === 'DEBUG';

                      let tagClass = 'tag';
                      if (isError) tagClass += ' tag-error';
                      else if (isWarn) tagClass += ' tag-warn';
                      else if (isInfo) tagClass += ' tag-info';
                      else if (isDebug) tagClass += ' tag-debug';

                      return (
                        <tr
                          key={i}
                          onClick={() => handleTopErrorClick(errorItem)}
                          title="Click to search in Log Explorer"
                        >
                          <td style={{ fontWeight: 600 }}>{errorItem.service}</td>
                          <td>
                            <span className={tagClass}>{errorItem.level}</span>
                          </td>
                          <td className="message-cell" title={errorItem.message}>
                            {errorItem.message}
                          </td>
                          <td style={{ textAlign: 'center', fontWeight: 600, color: '#fc4444' }}>
                            {errorItem.count}
                          </td>
                          <td style={{ fontFamily: 'var(--font-mono)', fontSize: '0.85rem' }}>
                            {errorItem.lastSeen
                              ? new Date(errorItem.lastSeen).toLocaleString('en-IN', {
                                  timeZone: 'Asia/Kolkata',
                                  hour12: true,
                                })
                              : 'N/A'}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              ) : (
                <div className="state-message">
                  No error logs matching the current filters.
                </div>
              )}
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
