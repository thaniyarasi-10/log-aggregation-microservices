package com.kovanlabs.logservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorKnowledgeBaseEntry {
    private String id;
    private String errorPattern;
    private String errorType;
    private String rootCause;
    private List<String> possibleCauses;
    private List<String> suggestedFixes;
    private String severity;
    private int confidence;
    private String source;
    private String createdAt;
    private String signatureHash;

    public ErrorKnowledgeBaseEntry() {}

    public ErrorKnowledgeBaseEntry(String errorPattern, String errorType, String rootCause,
                                    List<String> possibleCauses, List<String> suggestedFixes,
                                    String severity, int confidence, String source, String createdAt) {
        this.errorPattern = errorPattern;
        this.errorType = errorType;
        this.rootCause = rootCause;
        this.possibleCauses = possibleCauses;
        this.suggestedFixes = suggestedFixes;
        this.severity = severity;
        this.confidence = confidence;
        this.source = source;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getErrorPattern() { return errorPattern; }
    public void setErrorPattern(String errorPattern) { this.errorPattern = errorPattern; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }

    public List<String> getPossibleCauses() { return possibleCauses; }
    public void setPossibleCauses(List<String> possibleCauses) { this.possibleCauses = possibleCauses; }

    public List<String> getSuggestedFixes() { return suggestedFixes; }
    public void setSuggestedFixes(List<String> suggestedFixes) { this.suggestedFixes = suggestedFixes; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getSignatureHash() { return signatureHash; }
    public void setSignatureHash(String signatureHash) { this.signatureHash = signatureHash; }
}
