package com.kovanlabs.logservice.model;

public class AutoRepairSuggestRequest {
    private String service;
    private String className;
    private String fileName;
    private Integer lineNumber;
    private String message;
    private String errorDetails;

    public AutoRepairSuggestRequest() {}

    public AutoRepairSuggestRequest(String service, String className, String fileName, Integer lineNumber, String message, String errorDetails) {
        this.service = service;
        this.className = className;
        this.fileName = fileName;
        this.lineNumber = lineNumber;
        this.message = message;
        this.errorDetails = errorDetails;
    }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Integer getLineNumber() { return lineNumber; }
    public void setLineNumber(Integer lineNumber) { this.lineNumber = lineNumber; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
}
