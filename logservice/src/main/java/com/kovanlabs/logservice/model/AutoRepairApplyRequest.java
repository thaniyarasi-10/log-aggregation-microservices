package com.kovanlabs.logservice.model;

public class AutoRepairApplyRequest {
    private String filePath;
    private String originalCode;
    private String fixedCode;
    private ApplyMode applyMode;

    public AutoRepairApplyRequest() {}

    public AutoRepairApplyRequest(String filePath, String originalCode, String fixedCode, ApplyMode applyMode) {
        this.filePath = filePath;
        this.originalCode = originalCode;
        this.fixedCode = fixedCode;
        this.applyMode = applyMode;
    }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getOriginalCode() { return originalCode; }
    public void setOriginalCode(String originalCode) { this.originalCode = originalCode; }

    public String getFixedCode() { return fixedCode; }
    public void setFixedCode(String fixedCode) { this.fixedCode = fixedCode; }

    public ApplyMode getApplyMode() { return applyMode; }
    public void setApplyMode(ApplyMode applyMode) { this.applyMode = applyMode; }
}
