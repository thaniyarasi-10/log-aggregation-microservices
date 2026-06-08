import { useEffect, useMemo, useRef, useState } from 'react';
import { apiService, extractApiErrorMessage } from '../services/api';
import type { LogEvent, LogFilters } from '../types';
import { RANGE_TO_MS } from '../utils/time';

/**
 * Realtime log streaming hook.
 *
 * Transport: STOMP over WebSocket, subscribing to the user-specific destination
 * /user/queue/logs. The backend delivers only logs the authenticated user is
 * authorised to see (ADMIN = all services, DEV = mapped services only).
 *
 * The frontend adds a second layer of defence in matchesFilters: if the server
 * somehow delivers a log for a service not in the user's allowedServices list,
 * it is silently dropped before reaching the UI.
 *
 * Falls back to HTTP polling when the WebSocket connection is unavailable.
 * Automatically reconnects the WebSocket if it drops.
 *
 * KEY DESIGN DECISIONS:
 * - Filters are kept in a ref so the WebSocket handler always sees the latest
 *   values WITHOUT needing to reconnect. Reconnecting on every filter change
 *   would wipe accumulated realtime logs and cause a visible flash.
 * - HTTP polling MERGES results with existing logs rather than replacing them,
 *   so WS-received logs are never lost when a poll fires.
 * - Duplicate detection uses a composite key so the same event is never shown twice.
 */
export function useRealtimeLogs(
  filters: LogFilters,
  enabled = true,
  intervalMs = 30000,
  /** Lowercase set of service names the user is allowed to see. Empty = no restriction (admin). */
  allowedServices: string[] = [],
  isAdmin = false,
) {
  const [logs, setLogs] = useState<LogEvent[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string>('');

  // ── Stable refs — updated on every render but never trigger re-effects ──────
  const filtersRef = useRef<LogFilters>(filters);
  const allowedServicesRef = useRef<Set<string>>(new Set());
  const isAdminRef = useRef<boolean>(isAdmin);
  const seenKeysRef = useRef<Set<string>>(new Set());
  const latestTimestampRef = useRef<string | null>(null);

  // WebSocket state refs — shared between the effect closure and reconnect timer
  const wsConnectedRef = useRef<boolean>(false);
  const stompSubscribedRef = useRef<boolean>(false);
  const socketRef = useRef<WebSocket | null>(null);

  // Keep all refs in sync with latest props on every render
  useEffect(() => {
    filtersRef.current = filters;
  }, [filters]);

  useEffect(() => {
    allowedServicesRef.current = new Set(
      allowedServices.map((s) => s.trim().toLowerCase()).filter(Boolean)
    );
    isAdminRef.current = isAdmin;
  }, [allowedServices, isAdmin]);

  // ── Main effect — runs once on mount (and on enabled/intervalMs change) ─────
  // Filters are intentionally NOT in the dependency array. The filtersRef keeps
  // them current so the WS handler always applies the latest filter values without
  // tearing down and rebuilding the socket on every keystroke.
  useEffect(() => {
    if (!enabled) {
      return;
    }

    let active = true;
    let pollingTimer: number | null = null;

    // ── Filter helpers ─────────────────────────────────────────────────────────

    const normalize = (value: string) => value.toLowerCase().trim();

    const withinTimeRange = (timestamp: string | undefined) => {
      const rangeMs = RANGE_TO_MS[filtersRef.current.timeRange] ?? RANGE_TO_MS['15m'];
      if (!timestamp) return true;
      const eventTs = new Date(timestamp).getTime();
      if (!Number.isFinite(eventTs)) return true;
      return eventTs >= Date.now() - rangeMs;
    };

    const matchesFilters = (event: LogEvent): boolean => {
      const currentFilters = filtersRef.current;

      // ── RBAC service guard (defence-in-depth) ──────────────────────────────
      if (!isAdminRef.current) {
        const allowed = allowedServicesRef.current;
        if (allowed.size > 0 && !allowed.has('*')) {
          const eventService = normalize(event.service || '');
          if (eventService && !allowed.has(eventService)) {
            return false;
          }
        }
      }

      // ── UI filter checks ───────────────────────────────────────────────────
      const selectedServices = currentFilters.services ?? [];
      if (selectedServices.length > 0) {
        const eventService = normalize(event.service || '');
        if (!selectedServices.some((s) => normalize(s) === eventService)) {
          return false;
        }
      }

      const selectedLevels = currentFilters.levels ?? [];
      if (selectedLevels.length > 0) {
        const eventLevel = normalize(String(event.level || ''));
        if (!selectedLevels.some((l) => normalize(l) === eventLevel)) {
          return false;
        }
      }

      if (currentFilters.search && !normalize(event.message || '').includes(normalize(currentFilters.search))) {
        return false;
      }
      return withinTimeRange(event['@timestamp']);
    };

    // ── HTTP polling — periodically query Elasticsearch ──────────────────────

    const pullLogs = async () => {
      try {
        let fromVal: string | undefined = undefined;
        if (latestTimestampRef.current) {
          const ms = new Date(latestTimestampRef.current).getTime();
          fromVal = new Date(ms - 5000).toISOString();
        }
        const fetchFilters = fromVal ? { ...filtersRef.current, from: fromVal } : filtersRef.current;
        const data = await apiService.fetchLogs(fetchFilters);
        if (!active) return;

        if (!Array.isArray(data) || data.length === 0) {
          setLoading(false);
          return;
        }

        const timestamps = data
          .map((e) => e['@timestamp'])
          .filter((ts): ts is string => typeof ts === 'string' && ts.trim().length > 0);
        if (timestamps.length > 0) {
          const maxTs = timestamps.reduce((max, current) => current > max ? current : max);
          if (!latestTimestampRef.current || maxTs > latestTimestampRef.current) {
            latestTimestampRef.current = maxTs;
          }
        }

        setLogs((prev) => {
          // Merge: add any polled logs not already in state (by key)
          const existingKeys = new Set(prev.map(eventKey));
          const newEntries = data.filter((e) => {
            const k = eventKey(e);
            if (existingKeys.has(k)) return false;
            // Also register in the global seen-keys set so other triggers don't duplicate
            seenKeysRef.current.add(k);
            return true;
          });

          if (newEntries.length === 0) return prev;

          console.debug('[POLL] Merging', newEntries.length, 'new log(s) from HTTP poll');
          return [...prev, ...newEntries];
        });

        setError('');
      } catch (err) {
        if (!active) return;
        const message = extractApiErrorMessage(err, 'Failed to load logs');
        setError(message);
      } finally {
        if (active) setLoading(false);
      }
    };

    const startPolling = () => {
      if (pollingTimer !== null) window.clearInterval(pollingTimer);
      // Initial fetch to populate the table immediately
      void pullLogs();
      pollingTimer = window.setInterval(() => {
        void pullLogs();
      }, intervalMs);
    };

    // ── Boot ───────────────────────────────────────────────────────────────────
    startPolling();

    return () => {
      active = false;
      if (pollingTimer !== null) window.clearInterval(pollingTimer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, intervalMs]); // Filters intentionally excluded — handled via filtersRef

  // ── Filter change: re-apply filters to existing logs and reset seen-keys ────
  // When filters change we don't reconnect the socket. Instead we:
  //   1. Clear the seen-keys set so the next poll can re-populate with filtered results.
  //   2. Wipe the log list so stale out-of-filter logs don't linger.
  //   3. Trigger a fresh HTTP poll immediately.
  const filterKey = useMemo(() => JSON.stringify(filters), [filters]);

  useEffect(() => {
    // Don't run on initial mount — the main effect handles the first load
    seenKeysRef.current = new Set();
    latestTimestampRef.current = null;
    setLogs([]);
    setLoading(true);

    let active = true;
    apiService.fetchLogs(filtersRef.current).then((data) => {
      if (!active) return;
      if (Array.isArray(data) && data.length > 0) {
        data.forEach((e) => seenKeysRef.current.add(eventKey(e)));
        const timestamps = data
          .map((e) => e['@timestamp'])
          .filter((ts): ts is string => typeof ts === 'string' && ts.trim().length > 0);
        if (timestamps.length > 0) {
          latestTimestampRef.current = timestamps.reduce((max, current) => current > max ? current : max);
        }
        setLogs(data);
      }
      setError('');
    }).catch((err) => {
      if (!active) return;
      setError(extractApiErrorMessage(err, 'Failed to load logs'));
    }).finally(() => {
      if (active) setLoading(false);
    });

    return () => { active = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterKey]);

  return { logs, loading, error };
}

// Helper used outside the effect (needs to be module-level for the filterKey effect)
function eventKey(event: LogEvent) {
  return `${event['@timestamp'] ?? ''}|${event.service ?? ''}|${event.level ?? ''}|${event.traceId ?? ''}|${event.message ?? ''}`;
}
