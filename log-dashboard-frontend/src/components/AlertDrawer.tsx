import { useEffect, useRef, useState, useCallback } from 'react';
import type { AlertItem } from '../types';
import { parseAlertMessage, type ParsedAlert } from '../utils/errorParser';

// ─── helpers ────────────────────────────────────────────────────────────────

function formatTimestamp(raw?: string | null): string {
  if (!raw) return '—';
  try {
    return new Date(raw).toLocaleString('en-IN', {
      timeZone: 'Asia/Kolkata',
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return raw;
  }
}

function levelClass(level: string): string {
  const n = level.toUpperCase();
  if (n === 'CRITICAL' || n === 'ERROR') return 'tag-error';
  if (n === 'WARNING' || n === 'WARN') return 'tag-warn';
  return 'tag-info';
}

function categoryColor(category: ParsedAlert['category']): string {
  switch (category) {
    case 'AI Service Error': return '#a855f7'; // Purple
    case 'Jira Integration Error': return '#0052cc'; // Blue
    case 'Database Error': return '#eab308'; // Amber
    case 'Kafka Error': return '#f97316'; // Orange
    case 'Elasticsearch Error': return '#06b6d4'; // Cyan
    case 'Redis Error': return '#ef4444'; // Red
    case 'Network Error': return '#3b82f6'; // Blue
    default: return 'var(--text-secondary)';
  }
}

function useCopy(timeout = 1500) {
  const [copied, setCopied] = useState<string | null>(null);
  const copy = useCallback((text: string, key: string) => {
    navigator.clipboard.writeText(text).then(() => {
      setCopied(key);
      setTimeout(() => setCopied(null), timeout);
    });
  }, [timeout]);
  return { copied, copy };
}

// ─── JSON tree ───────────────────────────────────────────────────────────────

type JsonValue = string | number | boolean | null | JsonValue[] | { [k: string]: JsonValue };

function JsonNode({ value, depth = 0 }: { value: JsonValue; depth?: number }) {
  const [open, setOpen] = useState(depth < 2);

  if (value === null) return <span className="ld-json-null">null</span>;
  if (typeof value === 'boolean') return <span className="ld-json-bool">{String(value)}</span>;
  if (typeof value === 'number') return <span className="ld-json-num">{value}</span>;
  if (typeof value === 'string') return <span className="ld-json-str">"{value}"</span>;

  if (Array.isArray(value)) {
    if (value.length === 0) return <span className="ld-json-punct">[]</span>;
    return (
      <span>
        <button className="ld-json-toggle" onClick={() => setOpen(o => !o)} aria-label={open ? 'Collapse' : 'Expand'}>
          {open ? '▾' : '▸'}
        </button>
        <span className="ld-json-punct">[</span>
        {open ? (
          <span className="ld-json-block">
            {value.map((item, i) => (
              <span key={i} className="ld-json-line">
                <JsonNode value={item as JsonValue} depth={depth + 1} />
                {i < value.length - 1 && <span className="ld-json-punct">,</span>}
              </span>
            ))}
          </span>
        ) : (
          <span className="ld-json-ellipsis"> {value.length} items </span>
        )}
        <span className="ld-json-punct">]</span>
      </span>
    );
  }

  // object
  const entries = Object.entries(value as Record<string, JsonValue>);
  if (entries.length === 0) return <span className="ld-json-punct">{'{}'}</span>;
  return (
    <span>
      <button className="ld-json-toggle" onClick={() => setOpen(o => !o)} aria-label={open ? 'Collapse' : 'Expand'}>
        {open ? '▾' : '▸'}
      </button>
      <span className="ld-json-punct">{'{'}</span>
      {open ? (
        <span className="ld-json-block">
          {entries.map(([k, v], i) => (
            <span key={k} className="ld-json-line">
              <span className="ld-json-key">"{k}"</span>
              <span className="ld-json-punct">: </span>
              <JsonNode value={v} depth={depth + 1} />
              {i < entries.length - 1 && <span className="ld-json-punct">,</span>}
            </span>
          ))}
        </span>
      ) : (
        <span className="ld-json-ellipsis"> {entries.length} fields </span>
      )}
      <span className="ld-json-punct">{'}'}</span>
    </span>
  );
}

// ─── field row ───────────────────────────────────────────────────────────────

function Field({
  label,
  value,
  mono = false,
  copyKey,
  copied,
  onCopy,
}: {
  label: string;
  value: string | number | undefined | null;
  mono?: boolean;
  copyKey?: string;
  copied: string | null;
  onCopy: (text: string, key: string) => void;
}) {
  if (value === undefined || value === null || value === '') return null;
  const display = String(value);
  const key = copyKey ?? label;
  return (
    <div className="ld-field">
      <span className="ld-field-label">{label}</span>
      <span className={`ld-field-value${mono ? ' ld-mono' : ''}`}>{display}</span>
      <button
        className={`ld-copy-btn${copied === key ? ' ld-copy-done' : ''}`}
        onClick={() => onCopy(display, key)}
        title="Copy"
        aria-label={`Copy ${label}`}
      >
        {copied === key ? (
          <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <path d="M3 8l4 4 6-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        ) : (
          <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <rect x="5" y="5" width="8" height="8" rx="1" stroke="currentColor" strokeWidth="1.5" />
            <path d="M3 11V3h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        )}
      </button>
    </div>
  );
}

// ─── Component Props ───────────────────────────────────────────────────────

interface Props {
  alert: AlertItem | null;
  onClose: () => void;
}

export default function AlertDrawer({ alert, onClose }: Props) {
  const drawerRef = useRef<HTMLDivElement>(null);
  const { copied, copy } = useCopy();
  const [showStackTrace, setShowStackTrace] = useState(false);

  // ESC to close
  useEffect(() => {
    if (!alert) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [alert, onClose]);

  // Trap focus inside drawer when open
  useEffect(() => {
    if (alert && drawerRef.current) {
      drawerRef.current.focus();
    }
    setShowStackTrace(false); // Reset stack trace state on open
  }, [alert]);

  if (!alert) return null;

  // Run the error parser to extract summary/root-cause/resolutions/category
  const parsed = parseAlertMessage(alert.message, alert.service);

  // Trigger download report
  const handleDownloadReport = () => {
    const timeStr = formatTimestamp(alert.timestamp);
    const reportText = `ALERT REPORT
========================================
Title:       ${parsed.title}
Category:    ${parsed.category}
Service:     ${alert.service}
Severity:    ${alert.severity.toUpperCase()}
Time:        ${timeStr}
Count:       ${alert.count}
----------------------------------------
SUMMARY
${parsed.summary}

ROOT CAUSE
${parsed.rootCause}

SUGGESTED RESOLUTION
${parsed.resolution.map((res, i) => `${i + 1}. ${res}`).join('\n')}

----------------------------------------
FULL ERROR MESSAGE
${parsed.cleanMessage}
========================================`;

    const blob = new Blob([reportText], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `alert-report-${alert.service}-${parsed.category.toLowerCase().replace(/\s+/g, '-')}.txt`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <>
      {/* Backdrop — click outside to close */}
      <div
        className="ld-backdrop"
        onClick={onClose}
        aria-hidden="true"
        style={{ zIndex: 600 }} // Make sure alert drawer overlay is on top of any other UI
      />

      {/* Drawer panel */}
      <div
        ref={drawerRef}
        className="ld-drawer"
        role="dialog"
        aria-modal="true"
        aria-label="Alert details"
        tabIndex={-1}
        style={{ zIndex: 700 }}
      >
        {/* ── Header ── */}
        <div className="ld-header">
          <div className="ld-header-meta">
            <span className={`tag ${levelClass(String(alert.severity))}`}>{alert.severity}</span>
            <span className="ld-header-service" style={{ fontFamily: 'var(--font-mono)' }}>{alert.service}</span>
            <span
              className="tag"
              style={{
                background: 'transparent',
                border: `1px solid ${categoryColor(parsed.category)}`,
                color: categoryColor(parsed.category),
                fontWeight: 600,
                fontSize: '0.72rem',
                padding: '2px 6px'
              }}
            >
              {parsed.category}
            </span>
          </div>
          <div className="ld-header-right">
            <span className="ld-header-ts ld-mono">{formatTimestamp(alert.timestamp)}</span>
            <button className="ld-close-btn" onClick={onClose} aria-label="Close alert details">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M3 3l10 10M13 3L3 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>

        {/* ── Toolbar Action Bar ── */}
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '8px 14px',
            background: 'var(--surface-raised)',
            borderBottom: '1px solid var(--border)'
          }}
        >
          <div style={{ fontSize: '0.75rem', color: 'var(--text-dim)' }}>
            Occurrences count: <strong>{alert.count}</strong>
          </div>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              className="btn btn-secondary"
              style={{ padding: '4px 10px', fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: '6px' }}
              onClick={() => copy(parsed.cleanMessage, 'alertMessage')}
            >
              <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5">
                <rect x="5" y="5" width="8" height="8" rx="1" />
                <path d="M3 11V3h8" strokeLinecap="round" />
              </svg>
              {copied === 'alertMessage' ? 'Copied' : 'Copy Message'}
            </button>
            <button
              className="btn"
              style={{
                padding: '4px 10px',
                fontSize: '0.75rem',
                background: 'var(--accent)',
                color: '#fff',
                border: 'none',
                display: 'flex',
                alignItems: 'center',
                gap: '6px'
              }}
              onClick={handleDownloadReport}
            >
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                <polyline points="7 10 12 15 17 10" />
                <line x1="12" y1="15" x2="12" y2="3" />
              </svg>
              Download Report
            </button>
          </div>
        </div>

        {/* ── Content Body ── */}
        <div className="ld-body" style={{ flex: 1, overflowY: 'auto', padding: '16px 14px' }}>
          <div className="ld-overview" style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
            
            {/* Title Section */}
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 600, color: 'var(--text-primary)', marginBottom: '4px' }}>
                {parsed.title}
              </h2>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                {parsed.summary}
              </p>
            </div>

            {/* Root Cause Card */}
            <section className="ld-section ld-suggestion-section" style={{ border: 'none', padding: 0 }}>
              <div className="ld-suggestion-card" style={{ padding: '12px 14px', margin: 0 }}>
                <div className="ld-suggestion-block" style={{ border: 'none', padding: 0, margin: 0 }}>
                  <span className="ld-suggestion-subtitle" style={{ fontSize: '0.72rem', letterSpacing: '0.06em' }}>Root Cause</span>
                  <p className="ld-suggestion-rootcause-text" style={{ fontSize: '0.85rem', color: 'var(--text-primary)', marginTop: '4px' }}>
                    {parsed.rootCause}
                  </p>
                </div>
              </div>
            </section>

            {/* Suggested Resolution List */}
            <section className="ld-section" style={{ border: 'none', padding: 0 }}>
              <h4 className="ld-section-title" style={{ fontSize: '0.75rem', marginBottom: '8px' }}>Suggested Resolution</h4>
              <div className="ld-suggestion-card" style={{ padding: '12px 14px', margin: 0 }}>
                <ul className="ld-suggestion-list fixes-list" style={{ margin: 0, padding: 0 }}>
                  {parsed.resolution.map((item, i) => (
                    <li key={i} className="fix-item" style={{ display: 'flex', gap: '8px', fontSize: '0.85rem', marginBottom: i < parsed.resolution.length - 1 ? '8px' : 0 }}>
                      <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" className="fix-check-icon" style={{ flexShrink: 0, marginTop: '2px' }}>
                        <path d="M13.854 3.646a.5.5 0 0 1 0 .708l-7 7a.5.5 0 0 1-.708 0l-3.5-3.5a.5.5 0 1 1 .708-.708L6.5 10.293l6.646-6.647a.5.5 0 0 1 .708 0z"/>
                      </svg>
                      <span>{item}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </section>

            {/* Error Metadata fields */}
            {(parsed.exceptionClass || parsed.statusCode) && (
              <section className="ld-section" style={{ border: 'none', padding: 0 }}>
                <h4 className="ld-section-title" style={{ fontSize: '0.75rem', marginBottom: '8px' }}>Error Details</h4>
                <div className="ld-fields" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
                  <Field label="Exception Class" value={parsed.exceptionClass} mono copied={copied} onCopy={copy} />
                  <Field label="HTTP Status" value={parsed.statusCode} mono copied={copied} onCopy={copy} />
                </div>
              </section>
            )}

            {/* Display JSON structure nicely if available */}
            {parsed.isJson && parsed.jsonContent && (
              <section className="ld-section" style={{ border: 'none', padding: 0 }}>
                <h4 className="ld-section-title" style={{ fontSize: '0.75rem', marginBottom: '8px' }}>Parsed JSON Payload</h4>
                <div className="ld-json-tab" style={{ background: 'var(--surface-raised)', border: '1px solid var(--border)', borderRadius: '4px', padding: '12px' }}>
                  <div className="ld-json-tree ld-mono" style={{ fontSize: '0.78rem' }}>
                    <JsonNode value={parsed.jsonContent} depth={0} />
                  </div>
                </div>
              </section>
            )}

            {/* Full raw message / stack trace */}
            <section className="ld-section" style={{ border: 'none', padding: 0 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
                <h4 className="ld-section-title" style={{ fontSize: '0.75rem', margin: 0 }}>Full Error Message</h4>
                <button
                  className="filters-clear-btn"
                  style={{ fontSize: '0.75rem' }}
                  onClick={() => setShowStackTrace(!showStackTrace)}
                >
                  {showStackTrace ? 'Hide Raw Details' : 'Show Raw Details'}
                </button>
              </div>

              {showStackTrace ? (
                <pre
                  className="ld-stack-trace ld-mono"
                  style={{
                    fontSize: '0.75rem',
                    background: 'var(--surface-raised)',
                    border: '1px solid var(--border)',
                    borderRadius: '4px',
                    padding: '10px',
                    maxHeight: '300px',
                    overflow: 'auto',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all'
                  }}
                >
                  {parsed.cleanMessage}
                </pre>
              ) : (
                <div
                  className="ld-mono"
                  style={{
                    fontSize: '0.78rem',
                    background: 'var(--surface-raised)',
                    border: '1px solid var(--border)',
                    borderRadius: '4px',
                    padding: '8px 10px',
                    color: 'var(--text-secondary)',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    cursor: 'pointer'
                  }}
                  onClick={() => setShowStackTrace(true)}
                  title="Click to view full stack trace"
                >
                  {parsed.cleanMessage.substring(0, 120)}...
                </div>
              )}
            </section>

          </div>
        </div>
      </div>
    </>
  );
}
