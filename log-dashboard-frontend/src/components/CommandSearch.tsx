import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiService } from '../services/api';
import type { ServiceRecord, UserRecord, AlertItem, LogEvent } from '../types';

type SearchResultItem = {
  id: string;
  type: 'service' | 'user' | 'alert' | 'log';
  title: string;
  subtitle: string;
  targetUrl: string;
};

export default function CommandSearch() {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [selectedIndex, setSelectedIndex] = useState(0);

  // Cached data lists for fast local filtering
  const [services, setServices] = useState<ServiceRecord[]>([]);
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [alerts, setAlerts] = useState<AlertItem[]>([]);

  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();

  // Listen for Ctrl+K / Cmd+K
  useEffect(() => {
    const handleGlobalKeys = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setIsOpen((prev) => !prev);
      }
    };
    window.addEventListener('keydown', handleGlobalKeys);
    return () => window.removeEventListener('keydown', handleGlobalKeys);
  }, []);

  // Fetch search lists on modal open
  useEffect(() => {
    if (!isOpen) return;

    // Reset state
    setQuery('');
    setResults([]);
    setSelectedIndex(0);

    // Focus input shortly
    setTimeout(() => inputRef.current?.focus(), 50);

    // Load data
    const loadSearchData = async () => {
      try {
        const [servicesData, usersData, alertsData] = await Promise.all([
          apiService.fetchServices().catch(() => []),
          apiService.fetchUsers().catch(() => []),
          apiService.fetchAlerts().catch(() => []),
        ]);
        setServices(servicesData);
        setUsers(usersData);
        setAlerts(alertsData);
      } catch (err) {
        console.error('Failed to load command search indexes', err);
      }
    };
    void loadSearchData();
  }, [isOpen]);

  // Run search when query changes
  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      return;
    }

    const lowerQuery = query.toLowerCase().trim();
    const matches: SearchResultItem[] = [];

    // 1. Filter Services
    services.forEach((s) => {
      if (s.name.toLowerCase().includes(lowerQuery) || (s.description && s.description.toLowerCase().includes(lowerQuery))) {
        matches.push({
          id: `svc-${s.name}`,
          type: 'service',
          title: s.name,
          subtitle: s.description || 'Service Profile',
          targetUrl: `/services?selected=${encodeURIComponent(s.name)}`,
        });
      }
    });

    // 2. Filter Users
    users.forEach((u) => {
      const name = u.name || u.username || '';
      if (name.toLowerCase().includes(lowerQuery) || u.email.toLowerCase().includes(lowerQuery) || u.id.toLowerCase().includes(lowerQuery)) {
        matches.push({
          id: `usr-${u.id}`,
          type: 'user',
          title: name,
          subtitle: `${u.role || 'DEV'} • ${u.email}`,
          targetUrl: `/users?search=${encodeURIComponent(u.id)}`,
        });
      }
    });

    // 3. Filter Alerts
    alerts.forEach((a, idx) => {
      if (a.message.toLowerCase().includes(lowerQuery) || a.service.toLowerCase().includes(lowerQuery)) {
        matches.push({
          id: `alt-${idx}`,
          type: 'alert',
          title: a.message,
          subtitle: `Alert in ${a.service} • Severity: ${a.severity}`,
          targetUrl: `/alerts?search=${encodeURIComponent(a.message)}`,
        });
      }
    });

    setResults(matches.slice(0, 12)); // Cap at 12 results
    setSelectedIndex(0);

    // 4. Async Fetch matching Logs (if query has a few characters)
    if (lowerQuery.length >= 3) {
      const fetchMatchedLogs = async () => {
        try {
          const logsData = await apiService.fetchLogs({
            timeRange: '24h',
            services: [],
            levels: [],
            search: query,
            service: '',
            level: '',
          });
          const logMatches = logsData.slice(0, 5).map((l, idx) => ({
            id: `log-${idx}-${l['@timestamp'] || ''}`,
            type: 'log' as const,
            title: l.message,
            subtitle: `${l.service} • ${l.level} • ${l['@timestamp'] ? new Date(l['@timestamp']).toLocaleTimeString() : ''}`,
            targetUrl: `/explorer?search=${encodeURIComponent(l.message)}`,
          }));
          setResults((prev) => {
            const nonLogs = prev.filter((r) => r.type !== 'log');
            return [...nonLogs, ...logMatches].slice(0, 15);
          });
        } catch {
          // ignore log fetch failure in command search
        }
      };
      const debounceTimer = setTimeout(() => {
        void fetchMatchedLogs();
      }, 250);
      return () => clearTimeout(debounceTimer);
    }
  }, [query, services, users, alerts]);

  // Handle keyboard navigation inside search
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      setIsOpen(false);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex((prev) => (results.length > 0 ? (prev + 1) % results.length : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex((prev) => (results.length > 0 ? (prev - 1 + results.length) % results.length : 0));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (results[selectedIndex]) {
        handleSelect(results[selectedIndex]);
      }
    }
  };

  const handleSelect = (item: SearchResultItem) => {
    setIsOpen(false);
    navigate(item.targetUrl);
  };

  if (!isOpen) return null;

  return (
    <div className="obs-command-search-backdrop" onClick={() => setIsOpen(false)}>
      <div
        className="obs-command-search-modal"
        onClick={(e) => e.stopPropagation()}
        onKeyDown={handleKeyDown}
      >
        <div className="obs-command-search-header">
          <span className="obs-command-search-icon-left">🔍</span>
          <input
            ref={inputRef}
            type="text"
            className="obs-command-search-input"
            placeholder="Search services, users, alerts, or logs... (Esc to close)"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
          />
        </div>

        <div className="obs-command-search-body">
          {results.length > 0 ? (
            <div className="obs-command-search-section">
              <div className="obs-command-search-section-title">Search Results</div>
              {results.map((item, index) => (
                <div
                  key={item.id}
                  className={`obs-command-search-option ${index === selectedIndex ? 'selected' : ''}`}
                  onClick={() => handleSelect(item)}
                >
                  <div>
                    <div className="obs-command-search-option-text">{item.title}</div>
                    <div className="obs-command-search-option-subtext">{item.subtitle}</div>
                  </div>
                  <span className="obs-command-search-option-badge">{item.type}</span>
                </div>
              ))}
            </div>
          ) : query.trim() ? (
            <div style={{ textAlign: 'center', padding: '24px', color: 'var(--text-dim)', fontSize: '0.82rem' }}>
              No results found for "{query}"
            </div>
          ) : (
            <div style={{ padding: '16px 20px', color: 'var(--text-secondary)' }}>
              <div style={{ fontSize: '0.8rem', fontWeight: 600, marginBottom: '6px', color: 'var(--text-dim)' }}>
                SUGGESTIONS
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '0.78rem' }}>
                <div>Type service name to inspect status (e.g., <span className="obs-command-search-key">auth-service</span>)</div>
                <div>Search users by role or email (e.g., <span className="obs-command-search-key">admin</span>)</div>
                <div>Filter critical alerts (e.g., <span className="obs-command-search-key">critical</span>)</div>
              </div>
            </div>
          )}
        </div>

        <div className="obs-command-search-footer">
          <div className="obs-command-search-help">
            <span><span className="obs-command-search-key">↑↓</span> to navigate</span>
            <span><span className="obs-command-search-key">Enter</span> to select</span>
            <span><span className="obs-command-search-key">Esc</span> to close</span>
          </div>
          <div>LogFlow Observability</div>
        </div>
      </div>
    </div>
  );
}
