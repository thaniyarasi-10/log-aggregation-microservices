package com.kovanlabs.logservice.model;

public record AlertItemView(
    String service,
    String message,
    long count,
    String severity,
    String timestamp
) {}
