package com.kovanlabs.lynklog.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CallerInfo {
    @JsonProperty("class")
    private String clazz;
    private String method;
    private String file;
    private Integer line;

    public CallerInfo() {}

    public CallerInfo(String clazz, String method, String file, Integer line) {
        this.clazz = clazz;
        this.method = method;
        this.file = file;
        this.line = line;
    }

    public String getClazz() {
        return clazz;
    }

    public void setClazz(String clazz) {
        this.clazz = clazz;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }
}
