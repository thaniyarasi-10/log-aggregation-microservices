package com.kovanlabs.logservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

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

    private String errorType;
    private List<String> possibleCauses;
    private List<String> suggestedFixes;
    private String severity;
    private String suggestionGeneratedAt;
    private String rootCause;
    private Integer confidence;
    private String suggestionSource;

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
<<<<<<< HEAD
            this.organizationId = event.getOrganizationId();
=======
            this.errorType = event.getErrorType();
            this.possibleCauses = event.getPossibleCauses();
            this.suggestedFixes = event.getSuggestedFixes();
            this.severity = event.getSeverity();
            this.suggestionGeneratedAt = event.getSuggestionGeneratedAt();
            this.rootCause = event.getRootCause();
            this.confidence = event.getConfidence();
            this.suggestionSource = event.getSuggestionSource();
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
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

<<<<<<< HEAD
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
=======
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public List<String> getPossibleCauses() { return possibleCauses; }
    public void setPossibleCauses(List<String> possibleCauses) { this.possibleCauses = possibleCauses; }

    public List<String> getSuggestedFixes() { return suggestedFixes; }
    public void setSuggestedFixes(List<String> suggestedFixes) { this.suggestedFixes = suggestedFixes; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSuggestionGeneratedAt() { return suggestionGeneratedAt; }
    public void setSuggestionGeneratedAt(String suggestionGeneratedAt) { this.suggestionGeneratedAt = suggestionGeneratedAt; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public String getSuggestionSource() { return suggestionSource; }
    public void setSuggestionSource(String suggestionSource) { this.suggestionSource = suggestionSource; }
>>>>>>> 6a01b900be15a6a689e602f89925f0c54101ef47
}
