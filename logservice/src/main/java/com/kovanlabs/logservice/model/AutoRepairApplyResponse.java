package com.kovanlabs.logservice.model;

public class AutoRepairApplyResponse {
    private String status;
    private String message;
    private String commitUrl;
    private String commitSha;

    public AutoRepairApplyResponse() {}

    public AutoRepairApplyResponse(String status, String message, String commitUrl, String commitSha) {
        this.status = status;
        this.message = message;
        this.commitUrl = commitUrl;
        this.commitSha = commitSha;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCommitUrl() { return commitUrl; }
    public void setCommitUrl(String commitUrl) { this.commitUrl = commitUrl; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }
}
