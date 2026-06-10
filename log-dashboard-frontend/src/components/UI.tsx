import React, { useState, useEffect, useRef } from 'react';
import type { LogFilters } from '../types';

// ==========================================
// 1. PAGE HEADER
// ==========================================
type PageHeaderProps = {
  title: string;
  description?: string;
  actions?: React.ReactNode;
};

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <div className="obs-header">
      <div className="obs-header-text">
        <h2>{title}</h2>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="obs-header-actions">{actions}</div>}
    </div>
  );
}

// ==========================================
// 2. METRIC CARD
// ==========================================
type MetricCardProps = {
  title: string;
  value: string | number;
  status?: 'healthy' | 'warning' | 'critical' | 'neutral';
  trend?: string;
};

export function MetricCard({ title, value, status = 'neutral', trend }: MetricCardProps) {
  const getStatusColor = () => {
    switch (status) {
      case 'healthy': return 'var(--level-debug)';
      case 'warning': return 'var(--level-warn)';
      case 'critical': return 'var(--level-error)';
      default: return 'var(--text-primary)';
    }
  };

  return (
    <div className="obs-metric-card">
      <span className="obs-metric-title">{title}</span>
      <div className="obs-metric-value-container">
        <strong className="obs-metric-value" style={{ color: getStatusColor() }}>
          {value}
        </strong>
        {trend && (
          <span style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', marginLeft: '8px' }}>
            {trend}
          </span>
        )}
      </div>
    </div>
  );
}

// ==========================================
// 3. STATUS BADGE
// ==========================================
type StatusBadgeProps = {
  status: 'OK' | 'WARNING' | 'ERROR' | 'CRITICAL' | 'NO_DATA' | string;
  label?: string;
};

export function StatusBadge({ status, label }: StatusBadgeProps) {
  const s = String(status).toUpperCase();
  const displayLabel = label || status;

  if (s === 'OK' || s === 'HEALTHY' || s === 'ACTIVE') {
    return (
      <span className="obs-badge obs-badge-healthy">
        <span className="obs-badge-indicator" />
        {displayLabel}
      </span>
    );
  }
  if (s === 'WARNING' || s === 'WARN') {
    return (
      <span className="obs-badge obs-badge-warning">
        <span className="obs-badge-indicator" />
        {displayLabel}
      </span>
    );
  }
  if (s === 'ERROR' || s === 'CRITICAL' || s === 'HIGH') {
    return (
      <span className="obs-badge obs-badge-critical">
        <span className="obs-badge-indicator" />
        {displayLabel}
      </span>
    );
  }
  return (
    <span className="obs-badge obs-badge-nodata">
      <span className="obs-badge-indicator" />
      {displayLabel}
    </span>
  );
}

// ==========================================
// 4. EMPTY STATE
// ==========================================
type EmptyStateProps = {
  title: string;
  description: string;
  icon?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
};

export function EmptyState({ title, description, icon = '🔍', action }: EmptyStateProps) {
  return (
    <div className="obs-empty-state">
      <div className="obs-empty-icon">{icon}</div>
      <h4 className="obs-empty-title">{title}</h4>
      <p className="obs-empty-desc">{description}</p>
      {action && (
        <button className="btn" style={{ background: 'var(--accent)', color: '#fff', border: 'none' }} onClick={action.onClick}>
          {action.label}
        </button>
      )}
    </div>
  );
}

// ==========================================
// 5. COMPACT FILTER TOOLBAR WITH POPOVERS
// ==========================================
type FilterToolbarProps = {
  filters: LogFilters;
  services: string[];
  onChange: (next: LogFilters) => void;
};

export function FilterToolbar({ filters, services, onChange }: FilterToolbarProps) {
  const [searchVal, setSearchVal] = useState(filters.search);
  const [serviceOpen, setServiceOpen] = useState(false);
  const [levelOpen, setLevelOpen] = useState(false);
  const [serviceSearch, setServiceSearch] = useState('');

  const serviceRef = useRef<HTMLDivElement>(null);
  const levelRef = useRef<HTMLDivElement>(null);

  // Sync state if prop changes
  useEffect(() => {
    setSearchVal(filters.search);
  }, [filters.search]);

  // Debounced search
  useEffect(() => {
    const timer = setTimeout(() => {
      if (searchVal !== filters.search) {
        onChange({ ...filters, search: searchVal });
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchVal, filters, onChange]);

  // Click outside listener to close dropdowns
  useEffect(() => {
    function clickOutside(e: MouseEvent) {
      if (serviceRef.current && !serviceRef.current.contains(e.target as Node)) {
        setServiceOpen(false);
      }
      if (levelRef.current && !levelRef.current.contains(e.target as Node)) {
        setLevelOpen(false);
      }
    }
    document.addEventListener('mousedown', clickOutside);
    return () => document.removeEventListener('mousedown', clickOutside);
  }, []);

  const handleServiceToggle = (svc: string) => {
    const isSelected = filters.services.includes(svc);
    const nextServices = isSelected
      ? filters.services.filter((s) => s !== svc)
      : [...filters.services, svc];
    
    onChange({
      ...filters,
      services: nextServices,
      service: nextServices.length === 1 ? nextServices[0] : '',
    });
  };

  const handleLevelToggle = (lvl: string) => {
    const isSelected = filters.levels.includes(lvl);
    const nextLevels = isSelected
      ? filters.levels.filter((l) => l !== lvl)
      : [...filters.levels, lvl];
    
    onChange({
      ...filters,
      levels: nextLevels,
      level: nextLevels.length === 1 ? nextLevels[0] : '',
    });
  };

  const handleTimeRangeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    onChange({ ...filters, timeRange: e.target.value as LogFilters['timeRange'] });
  };

  const clearAll = () => {
    onChange({ ...filters, services: [], levels: [], service: '', level: '', search: '' });
    setSearchVal('');
    setServiceSearch('');
  };

  const hasActiveFilters =
    filters.services.length > 0 || filters.levels.length > 0 || filters.search.trim().length > 0;

  const filteredServicesList = services.filter((s) =>
    s.toLowerCase().includes(serviceSearch.toLowerCase())
  );

  return (
    <div className="obs-filter-toolbar">
      {/* ── Search Bar ── */}
      <div className="obs-search-wrapper">
        <span className="obs-search-icon">🔍</span>
        <input
          className="form-control obs-search-input"
          type="text"
          placeholder="Search logs by message..."
          value={searchVal}
          onChange={(e) => setSearchVal(e.target.value)}
        />
      </div>

      {/* ── Service Popover Dropdown ── */}
      <div className="obs-dropdown-popover-wrapper" ref={serviceRef}>
        <button
          className="btn obs-popover-trigger"
          type="button"
          onClick={() => { setServiceOpen(!serviceOpen); setLevelOpen(false); }}
        >
          <span>Services ({filters.services.length || 'All'})</span>
          <span>▾</span>
        </button>
        {serviceOpen && (
          <div className="obs-popover-dropdown">
            <input
              type="text"
              className="obs-popover-search"
              placeholder="Filter services..."
              value={serviceSearch}
              onChange={(e) => setServiceSearch(e.target.value)}
              onClick={(e) => e.stopPropagation()}
            />
            <ul className="obs-popover-list">
              {filteredServicesList.map((svc) => (
                <li key={svc} className="obs-popover-item" onClick={() => handleServiceToggle(svc)}>
                  <input
                    type="checkbox"
                    checked={filters.services.includes(svc)}
                    onChange={() => {}} // handled by click
                  />
                  <span>{svc}</span>
                </li>
              ))}
              {filteredServicesList.length === 0 && (
                <li style={{ padding: '6px', fontSize: '0.75rem', color: 'var(--text-dim)' }}>
                  No services found
                </li>
              )}
            </ul>
          </div>
        )}
      </div>

      {/* ── Level Popover Dropdown ── */}
      <div className="obs-dropdown-popover-wrapper" ref={levelRef}>
        <button
          className="btn obs-popover-trigger"
          type="button"
          onClick={() => { setLevelOpen(!levelOpen); setServiceOpen(false); }}
        >
          <span>Levels ({filters.levels.length || 'All'})</span>
          <span>▾</span>
        </button>
        {levelOpen && (
          <div className="obs-popover-dropdown">
            <ul className="obs-popover-list">
              {['ERROR', 'WARN', 'INFO', 'DEBUG'].map((lvl) => (
                <li key={lvl} className="obs-popover-item" onClick={() => handleLevelToggle(lvl)}>
                  <input
                    type="checkbox"
                    checked={filters.levels.includes(lvl)}
                    onChange={() => {}} // handled by click
                  />
                  <span>{lvl}</span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {/* ── Time Preset Selector ── */}
      <div>
        <select
          className="form-control"
          style={{ height: '31px', padding: '0 8px' }}
          value={filters.timeRange}
          onChange={handleTimeRangeChange}
        >
          <option value="5m">Last 5m</option>
          <option value="15m">Last 15m</option>
          <option value="1h">Last 1h</option>
          <option value="24h">Last 24h</option>
          <option value="7d">Last 7d</option>
          <option value="15d">Last 15d</option>
        </select>
      </div>

      {/* ── Clear All Filters button ── */}
      {hasActiveFilters && (
        <button
          className="btn"
          style={{ background: 'transparent', border: 'none', textDecoration: 'underline', color: 'var(--accent)' }}
          onClick={clearAll}
        >
          Clear
        </button>
      )}

      {/* ── Active Chips Row ── */}
      {hasActiveFilters && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', width: '100%', marginTop: '4px' }}>
          {filters.services.map((svc) => (
            <span key={svc} className="filter-chip filter-chip-service">
              {svc}
              <button className="filter-chip-remove" onClick={() => handleServiceToggle(svc)}>×</button>
            </span>
          ))}
          {filters.levels.map((lvl) => (
            <span key={lvl} className={`filter-chip filter-chip-level filter-chip-level-${lvl.toLowerCase()}`}>
              {lvl}
              <button className="filter-chip-remove" onClick={() => handleLevelToggle(lvl)}>×</button>
            </span>
          ))}
          {filters.search.trim() && (
            <span className="filter-chip filter-chip-search">
              "{filters.search.trim()}"
              <button className="filter-chip-remove" onClick={() => { onChange({ ...filters, search: '' }); setSearchVal(''); }}>×</button>
            </span>
          )}
        </div>
      )}
    </div>
  );
}
