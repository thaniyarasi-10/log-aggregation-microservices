package com.kovanlabs.logservice.model;

import java.time.Instant;

public class ServiceLogMetrics {
    private String service;
    private long errorCount;
    private long warnCount;
    private Instant lastSeen;

    public ServiceLogMetrics() {}

    public ServiceLogMetrics(String service, long errorCount, long warnCount, Instant lastSeen) {
        this.service = service;
        this.errorCount = errorCount;
        this.warnCount = warnCount;
        this.lastSeen = lastSeen;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public long getWarnCount() {
        return warnCount;
    }

    public void setWarnCount(long warnCount) {
        this.warnCount = warnCount;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
