package com.kovanlabs.logservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AutoRepairResponse {
    private String explanation;
    private String targetFile;
    private String originalCode;
    private String fixedCode;
    private String diff;
    private boolean githubConfigured;

    public AutoRepairResponse() {}

    public AutoRepairResponse(String explanation, String targetFile, String originalCode, String fixedCode, String diff, boolean githubConfigured) {
        this.explanation = explanation;
        this.targetFile = targetFile;
        this.originalCode = originalCode;
        this.fixedCode = fixedCode;
        this.diff = diff;
        this.githubConfigured = githubConfigured;
    }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getTargetFile() { return targetFile; }
    public void setTargetFile(String targetFile) { this.targetFile = targetFile; }

    public String getOriginalCode() { return originalCode; }
    public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }

    public String getFixedCode() { return fixedCode; }
    public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }

    public String getDiff() { return diff; }
    public void setDiff(String diff) { this.diff = diff; }

    public boolean isGithubConfigured() { return githubConfigured; }
    public void setGithubConfigured(boolean githubConfigured) { this.githubConfigured = githubConfigured; }
}
