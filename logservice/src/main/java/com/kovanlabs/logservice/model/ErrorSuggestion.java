package com.kovanlabs.logservice.model;

import java.util.List;

public class ErrorSuggestion {

    private String errorType;
    private List<String> possibleCauses;
    private List<String> suggestedFixes;
    private String severity;

    private String rootCause;
    private int confidence;
    private String suggestionSource;

    public ErrorSuggestion() {}

    public ErrorSuggestion(String errorType, List<String> possibleCauses, List<String> suggestedFixes, String severity) {
        this.errorType = errorType;
        this.possibleCauses = possibleCauses;
        this.suggestedFixes = suggestedFixes;
        this.severity = severity;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public List<String> getPossibleCauses() {
        return possibleCauses;
    }

    public void setPossibleCauses(List<String> possibleCauses) {
        this.possibleCauses = possibleCauses;
    }

    public List<String> getSuggestedFixes() {
        return suggestedFixes;
    }

    public void setSuggestedFixes(List<String> suggestedFixes) {
        this.suggestedFixes = suggestedFixes;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public int getConfidence() {
        return confidence;
    }

    public void setConfidence(int confidence) {
        this.confidence = confidence;
    }

    public String getSuggestionSource() {
        return suggestionSource;
    }

    public void setSuggestionSource(String suggestionSource) {
        this.suggestionSource = suggestionSource;
    }

    @Override
    public String toString() {
        return "ErrorSuggestion{" +
                "errorType='" + errorType + '\'' +
                ", possibleCauses=" + possibleCauses +
                ", suggestedFixes=" + suggestedFixes +
                ", severity='" + severity + '\'' +
                ", rootCause='" + rootCause + '\'' +
                ", confidence=" + confidence +
                ", suggestionSource='" + suggestionSource + '\'' +
                '}';
    }
}
