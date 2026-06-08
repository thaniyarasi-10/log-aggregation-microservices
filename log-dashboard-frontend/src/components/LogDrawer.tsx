import { useEffect, useRef, useState, useCallback } from 'react';
import type { LogEvent } from '../types';
import { apiService } from '../services/api';

// ─── helpers ────────────────────────────────────────────────────────────────

function formatTimestamp(raw?: string): string {
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
      fractionalSecondDigits: 3,
    } as Intl.DateTimeFormatOptions);
  } catch {
    return raw;
  }
}

function levelClass(level: string): string {
  const n = level.toUpperCase();
  if (n === 'ERROR') return 'tag-error';
  if (n === 'WARN') return 'tag-warn';
  if (n === 'INFO') return 'tag-info';
  return 'tag-debug';
}

function statusClass(code?: number): string {
  if (!code) return '';
  if (code >= 500) return 'ld-status-5xx';
  if (code >= 400) return 'ld-status-4xx';
  if (code >= 300) return 'ld-status-3xx';
  return 'ld-status-2xx';
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

// ─── syntax highlighter ──────────────────────────────────────────────────────
const tokenize = (line: string, isJava: boolean): string => {
  const placeholders: string[] = [];
  let working = line
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');

  // 1. Strings
  working = working.replace(/(["'])(?:\\.|[^\\])*?\1/g, (match) => {
    placeholders.push(`<span class="code-str">${match}</span>`);
    return `___PH_${placeholders.length - 1}___`;
  });

  // 2. Comments
  const commentRegex = isJava ? /(\/\/.*)/g : /(#.*)/g;
  working = working.replace(commentRegex, (match) => {
    placeholders.push(`<span class="code-comment">${match}</span>`);
    return `___PH_${placeholders.length - 1}___`;
  });

  // 3. Keywords
  const kwRegex = isJava
    ? /\b(package|import|public|private|protected|class|interface|enum|extends|implements|new|this|super|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|throws|static|final|void|int|double|float|long|boolean|char|byte|short|null|true|false)\b/g
    : /\b(import|from|class|def|return|if|elif|else|for|while|break|continue|try|except|finally|raise|assert|and|or|not|in|is|lambda|None|True|False|self)\b/g;

  working = working.replace(kwRegex, '<span class="code-kw">$&</span>');

  // 4. Annotations
  if (isJava) {
    working = working.replace(/(@\w+)/g, '<span class="code-ann">$&</span>');
  }

  // 5. Restore placeholders
  for (let i = placeholders.length - 1; i >= 0; i--) {
    working = working.replace(`___PH_${i}___`, placeholders[i]);
  }

  return working;
};

// ─── tabs ────────────────────────────────────────────────────────────────────

type Tab = 'overview' | 'source' | 'json' | 'metadata' | 'raw';

// ─── main component ──────────────────────────────────────────────────────────

type Props = {
  log: LogEvent | null;
  onClose: () => void;
};

export default function LogDrawer({ log, onClose }: Props) {
  const [tab, setTab] = useState<Tab>('overview');
  const [jsonCopied, setJsonCopied] = useState(false);
  const drawerRef = useRef<HTMLDivElement>(null);
  const { copied, copy } = useCopy();

  // Source Code Viewer States
  const [sourceCode, setSourceCode] = useState<string | null>(null);
  const [sourceFilePath, setSourceFilePath] = useState<string | null>(null);
  const [sourceLoading, setSourceLoading] = useState(false);
  const [sourceError, setSourceError] = useState<string | null>(null);
  const targetLineRef = useRef<HTMLDivElement | null>(null);

  // ESC to close
  useEffect(() => {
    if (!log) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', handler);
    return () => document.removeEventListener('keydown', handler);
  }, [log, onClose]);

  // Reset tab when a new log is opened
  useEffect(() => {
    if (log) {
      setTab('overview');
      setSourceCode(null);
      setSourceFilePath(null);
      setSourceError(null);
    }
  }, [log]);

  // Trap focus inside drawer when open
  useEffect(() => {
    if (log && drawerRef.current) {
      drawerRef.current.focus();
    }
  }, [log]);

  // Fetch source code when 'source' tab is active
  const caller = log?.caller;
  useEffect(() => {
    if (tab !== 'source' || !log) return;
    
    if (!caller || !caller.file) {
      setSourceError('No source code location details found for this log event.');
      setSourceCode(null);
      setSourceFilePath(null);
      return;
    }

    setSourceLoading(true);
    setSourceError(null);

    apiService.fetchSourceCode(log.service, caller.class, caller.file, caller.line)
      .then((res) => {
        setSourceCode(res.fileContent);
        setSourceFilePath(res.filePath);
        setSourceLoading(false);
      })
      .catch((err) => {
        console.error('Failed to load source code:', err);
        const msg = err.response?.data?.message || 'Failed to retrieve source file from backend.';
        setSourceError(msg);
        setSourceLoading(false);
      });
  }, [tab, log, caller]);

  // Auto-scroll target line into view
  useEffect(() => {
    if (tab === 'source' && !sourceLoading && sourceCode && targetLineRef.current) {
      const timer = setTimeout(() => {
        targetLineRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 120);
      return () => clearTimeout(timer);
    }
  }, [tab, sourceLoading, sourceCode]);

  if (!log) return null;

  const ts = log['@timestamp'];
  const jsonPayload = JSON.stringify(
    Object.fromEntries(
      Object.entries(log).filter(([, v]) => v !== undefined && v !== null && v !== '')
    ),
    null,
    2
  );

  const copyJson = () => {
    navigator.clipboard.writeText(jsonPayload).then(() => {
      setJsonCopied(true);
      setTimeout(() => setJsonCopied(false), 1500);
    });
  };

  // Build metadata fields: everything that isn't the primary display fields
  const primaryKeys = new Set(['@timestamp', 'level', 'service', 'message', 'environment', 'instance']);
  const metaEntries = Object.entries(log).filter(
    ([k, v]) => !primaryKeys.has(k) && v !== undefined && v !== null && v !== ''
  );

  return (
    <>
      {/* Backdrop — click outside to close */}
      <div
        className="ld-backdrop"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Drawer panel */}
      <div
        ref={drawerRef}
        className="ld-drawer"
        role="dialog"
        aria-modal="true"
        aria-label="Log details"
        tabIndex={-1}
      >
        {/* ── Header ── */}
        <div className="ld-header">
          <div className="ld-header-meta">
            <span className={`tag ${levelClass(String(log.level))}`}>{log.level}</span>
            <span className="ld-header-service">{log.service}</span>
            {log.statusCode != null && (
              <span className={`ld-status-badge ${statusClass(log.statusCode)}`}>
                {log.statusCode}
              </span>
            )}
            {log.endpoint && (
              <span className="ld-header-endpoint ld-mono">{log.method ? `${log.method} ` : ''}{log.endpoint}</span>
            )}
          </div>
          <div className="ld-header-right">
            <span className="ld-header-ts ld-mono">{formatTimestamp(ts)}</span>
            <button className="ld-close-btn" onClick={onClose} aria-label="Close log details">
              <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                <path d="M3 3l10 10M13 3L3 13" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
              </svg>
            </button>
          </div>
        </div>

        {/* Trace ID strip — shown only when present */}
        {log.traceId && (
          <div className="ld-trace-strip">
            <span className="ld-trace-label">Trace</span>
            <span className="ld-trace-id ld-mono">{log.traceId}</span>
            <button
              className={`ld-copy-btn${copied === 'traceId' ? ' ld-copy-done' : ''}`}
              onClick={() => copy(log.traceId!, 'traceId')}
              title="Copy trace ID"
              aria-label="Copy trace ID"
            >
              {copied === 'traceId' ? (
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
        )}

        {/* ── Tabs ── */}
        <div className="ld-tabs" role="tablist">
          {(['overview', 'source', 'json', 'metadata', 'raw'] as Tab[]).map(t => {
            if (t === 'source' && !log.caller) return null;
            return (
              <button
                key={t}
                role="tab"
                aria-selected={tab === t}
                className={`ld-tab${tab === t ? ' ld-tab-active' : ''}`}
                onClick={() => setTab(t)}
              >
                {t === 'source' ? 'Source Code' : t.charAt(0).toUpperCase() + t.slice(1)}
              </button>
            );
          })}
        </div>

        {/* ── Tab content ── */}
        <div className="ld-body">

          {/* OVERVIEW */}
          {tab === 'overview' && (
            <div className="ld-overview">
              {/* Full message */}
              <section className="ld-section">
                <h4 className="ld-section-title">Message</h4>
                <div className="ld-message-block ld-mono">{log.message}</div>
              </section>

              {/* Identity */}
              <section className="ld-section">
                <h4 className="ld-section-title">Identity</h4>
                <div className="ld-fields">
                  <Field label="Service"     value={log.service}     copied={copied} onCopy={copy} />
                  <Field label="Environment" value={log.environment} copied={copied} onCopy={copy} />
                  <Field label="Instance"    value={log.instance}    copied={copied} onCopy={copy} />
                  <Field label="Timestamp"   value={formatTimestamp(ts)} mono copied={copied} onCopy={copy} copyKey="timestamp" />
                  {log.caller && log.caller.file && (
                    <div className="ld-field">
                      <span className="ld-field-label">Source Location</span>
                      <button 
                        className="ld-caller-badge"
                        onClick={() => setTab('source')}
                        title="View exact source code location"
                      >
                        <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
                          <path d="M10.478 1.647a.5.5 0 1 0-.956-.294l-4 13a.5.5 0 0 0 .956.294l4-13zM4.854 4.146a.5.5 0 0 1 0 .708L1.707 8l3.147 3.146a.5.5 0 0 1-.708.708l-3.5-3.5a.5.5 0 0 1 0-.708l3.5-3.5a.5.5 0 0 1 .708 0zm6.292 0a.5.5 0 0 0 0 .708L14.293 8l-3.147 3.146a.5.5 0 0 0 .708.708l3.5-3.5a.5.5 0 0 0 0-.708l-3.5-3.5a.5.5 0 0 0-.708 0z"/>
                        </svg>
                        {log.caller.file}:{log.caller.line}
                      </button>
                    </div>
                  )}
                </div>
              </section>

              {/* Request */}
              {(log.method || log.endpoint || log.statusCode != null || log.responseTime != null) && (
                <section className="ld-section">
                  <h4 className="ld-section-title">Request</h4>
                  <div className="ld-fields">
                    <Field label="Method"        value={log.method}                                  copied={copied} onCopy={copy} />
                    <Field label="Endpoint"      value={log.endpoint}      mono                      copied={copied} onCopy={copy} />
                    <Field label="Status"        value={log.statusCode}                              copied={copied} onCopy={copy} />
                    <Field label="Response time" value={log.responseTime != null ? `${Math.round(log.responseTime)}ms` : undefined} mono copied={copied} onCopy={copy} copyKey="responseTime" />
                  </div>
                </section>
              )}

              {/* Tracing */}
              {(log.traceId || log.spanId) && (
                <section className="ld-section">
                  <h4 className="ld-section-title">Tracing</h4>
                  <div className="ld-fields">
                    <Field label="Trace ID" value={log.traceId} mono copied={copied} onCopy={copy} copyKey="traceId" />
                    <Field label="Span ID"  value={log.spanId}  mono copied={copied} onCopy={copy} copyKey="spanId" />
                    <Field label="User ID"  value={log.userId}  mono copied={copied} onCopy={copy} copyKey="userId" />
                  </div>
                </section>
              )}

              {/* Error details */}
              {(log.errorCode || log.errorDetails) && (
                <section className="ld-section">
                  <h4 className="ld-section-title">Error</h4>
                  <div className="ld-fields">
                    <Field label="Error code" value={log.errorCode} mono copied={copied} onCopy={copy} copyKey="errorCode" />
                  </div>
                  {log.errorDetails && (
                    <pre className="ld-stack-trace">{log.errorDetails}</pre>
                  )}
                </section>
              )}
            </div>
          )}

          {/* SOURCE CODE */}
          {tab === 'source' && (
            <div className="ld-code-tab animate-fade-in">
              <div className="ld-code-header">
                <div className="ld-code-title">
                  <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" style={{color: 'var(--accent-color)'}}>
                    <path d="M10.478 1.647a.5.5 0 1 0-.956-.294l-4 13a.5.5 0 0 0 .956.294l4-13zM4.854 4.146a.5.5 0 0 1 0 .708L1.707 8l3.147 3.146a.5.5 0 0 1-.708.708l-3.5-3.5a.5.5 0 0 1 0-.708l3.5-3.5a.5.5 0 0 1 .708 0zm6.292 0a.5.5 0 0 0 0 .708L14.293 8l-3.147 3.146a.5.5 0 0 0 .708.708l3.5-3.5a.5.5 0 0 0 0-.708l-3.5-3.5a.5.5 0 0 0-.708 0z"/>
                  </svg>
                  <span>Source Location</span>
                </div>
                <div className="ld-code-filepath">
                  {sourceFilePath ? sourceFilePath : (caller?.file ? `${log.service}/${caller.file}` : '')}
                </div>
              </div>

              <div className="ld-code-viewer-container">
                {sourceLoading && (
                  <div className="ld-code-loading">
                    <div className="ns-spinner" />
                    <span>Fetching source file...</span>
                  </div>
                )}

                {sourceError && (
                  <div className="ld-code-error-container">
                    <svg className="ld-code-error-icon" width="48" height="48" viewBox="0 0 16 16" fill="currentColor">
                      <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14zm0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16z"/>
                      <path d="M7.002 11a1 1 0 1 1 2 0 1 1 0 0 1-2 0zM7.1 4.995a.905.905 0 1 1 1.8 0l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 4.995z"/>
                    </svg>
                    <div className="ld-code-error-title">Source Code Not Available</div>
                    <div className="ld-code-error-desc">{sourceError}</div>
                  </div>
                )}

                {!sourceLoading && !sourceError && sourceCode && (
                  <div className="ld-code-scroller">
                    <div className="ld-code-table">
                      {sourceCode.split(/\r?\n/).map((line, idx) => {
                        const lineNum = idx + 1;
                        const isTarget = lineNum === caller?.line;
                        const isJava = caller?.file?.endsWith('.java') || false;
                        const highlightedHtml = tokenize(line, isJava);

                        return (
                          <div 
                            key={lineNum} 
                            ref={isTarget ? targetLineRef : undefined}
                            className={`ld-code-line${isTarget ? ' highlighted' : ''}`}
                          >
                            <div className="ld-code-ln">{lineNum}</div>
                            <div 
                              className="ld-code-text"
                              dangerouslySetInnerHTML={{ __html: highlightedHtml }}
                            />
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* JSON */}
          {tab === 'json' && (
            <div className="ld-json-tab">
              <div className="ld-json-toolbar">
                <span className="ld-json-toolbar-label">Structured log</span>
                <button
                  className={`ld-copy-json-btn${jsonCopied ? ' ld-copy-done' : ''}`}
                  onClick={copyJson}
                >
                  {jsonCopied ? (
                    <>
                      <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                        <path d="M3 8l4 4 6-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                      Copied
                    </>
                  ) : (
                    <>
                      <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                        <rect x="5" y="5" width="8" height="8" rx="1" stroke="currentColor" strokeWidth="1.5" />
                        <path d="M3 11V3h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                      </svg>
                      Copy JSON
                    </>
                  )}
                </button>
              </div>
              <div className="ld-json-tree ld-mono">
                <JsonNode value={JSON.parse(jsonPayload) as JsonValue} depth={0} />
              </div>
            </div>
          )}

          {/* METADATA */}
          {tab === 'metadata' && (
            <div className="ld-metadata-tab">
              {metaEntries.length === 0 ? (
                <p className="ld-empty">No additional metadata fields.</p>
              ) : (
                <div className="ld-fields ld-fields-wide">
                  {metaEntries.map(([k, v]) => (
                    <Field
                      key={k}
                      label={k}
                      value={typeof v === 'object' ? JSON.stringify(v) : String(v)}
                      mono
                      copyKey={k}
                      copied={copied}
                      onCopy={copy}
                    />
                  ))}
                </div>
              )}
            </div>
          )}

          {/* RAW */}
          {tab === 'raw' && (
            <div className="ld-raw-tab">
              <div className="ld-json-toolbar">
                <span className="ld-json-toolbar-label">Raw JSON</span>
                <button
                  className={`ld-copy-json-btn${jsonCopied ? ' ld-copy-done' : ''}`}
                  onClick={copyJson}
                >
                  {jsonCopied ? (
                    <>
                      <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                        <path d="M3 8l4 4 6-7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                      Copied
                    </>
                  ) : (
                    <>
                      <svg width="12" height="12" viewBox="0 0 16 16" fill="none" aria-hidden="true">
                        <rect x="5" y="5" width="8" height="8" rx="1" stroke="currentColor" strokeWidth="1.5" />
                        <path d="M3 11V3h8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
                      </svg>
                      Copy
                    </>
                  )}
                </button>
              </div>
              <pre className="ld-raw-pre ld-mono">{jsonPayload}</pre>
            </div>
          )}

        </div>
      </div>
    </>
  );
}
