package com.kovanlabs.logservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponse {
    private String errorType;
    private String severity;
    private String rootCause;
    private List<String> possibleCauses;
    private List<String> suggestedFixes;
    private int confidence;

    public GeminiResponse() {}

    public GeminiResponse(String errorType, String severity, String rootCause,
                          List<String> possibleCauses, List<String> suggestedFixes, int confidence) {
        this.errorType = errorType;
        this.severity = severity;
        this.rootCause = rootCause;
        this.possibleCauses = possibleCauses;
        this.suggestedFixes = suggestedFixes;
        this.confidence = confidence;
    }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public List<String> getPossibleCauses() { return possibleCauses; }
    public void setPossibleCauses(List<String> possibleCauses) { this.possibleCauses = possibleCauses; }

    public List<String> getSuggestedFixes() { return suggestedFixes; }
    public void setSuggestedFixes(List<String> suggestedFixes) { this.suggestedFixes = suggestedFixes; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }
}
