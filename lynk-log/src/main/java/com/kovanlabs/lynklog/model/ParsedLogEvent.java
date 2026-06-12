package com.kovanlabs.lynklog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ParsedLogEvent {
    @JsonProperty("@timestamp")
    private String timestamp;
    private String level;
    private String message;
    private String service;
    private String environment;
    private String organizationId;
    private CallerInfo caller;

    public ParsedLogEvent() {}

    public ParsedLogEvent(String timestamp, String level, String message, String service, String environment) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.service = service;
        this.environment = environment;
        this.organizationId = null;
        this.caller = null;
    }

    public ParsedLogEvent(String timestamp, String level, String message, String service, String environment, CallerInfo caller) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.service = service;
        this.environment = environment;
        this.organizationId = null;
        this.caller = caller;
    }

    public ParsedLogEvent(String timestamp, String level, String message, String service, String environment, String organizationId, CallerInfo caller) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.service = service;
        this.environment = environment;
        this.organizationId = organizationId;
        this.caller = caller;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public CallerInfo getCaller() {
        return caller;
    }

    public void setCaller(CallerInfo caller) {
        this.caller = caller;
    }
}
