package com.kovanlabs.logservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LogDto {

    @JsonProperty("@timestamp")
    private String timestamp;
    private String level;
    private String service;
    private String instance;
    private String environment;
    private String message;
    private String traceId;
    private String spanId;
    private String userId;
    private String endpoint;
    private String method;
    private Integer statusCode;
    private Double responseTime;
    private String errorCode;
    private String errorDetails;
    private Object tags;
    private LogCaller caller;
    private String organizationId;

    public LogDto() {}

    public LogDto(LogEvent event) {
        if (event != null) {
            this.timestamp = event.getTimestamp();
            this.level = event.getLevel();
            this.service = event.getService();
            this.instance = event.getInstance();
            this.environment = event.getEnvironment();
            this.message = event.getMessage();
            this.traceId = event.getTraceId();
            this.spanId = event.getSpanId();
            this.userId = event.getUserId();
            this.endpoint = event.getEndpoint();
            this.method = event.getMethod();
            this.statusCode = event.getStatusCode();
            this.responseTime = event.getResponseTime();
            this.errorCode = event.getErrorCode();
            this.errorDetails = event.getErrorDetails();
            this.tags = event.getTags();
            this.caller = event.getCaller();
            this.organizationId = event.getOrganizationId();
        }
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public Double getResponseTime() { return responseTime; }
    public void setResponseTime(Double responseTime) { this.responseTime = responseTime; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }

    public Object getTags() { return tags; }
    public void setTags(Object tags) { this.tags = tags; }

    public LogCaller getCaller() { return caller; }
    public void setCaller(LogCaller caller) { this.caller = caller; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
}
