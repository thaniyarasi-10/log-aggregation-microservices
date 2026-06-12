package com.kovanlabs.lynklog.model;

public record VerifyRequest(String apiKey, String serviceSecret) {
    public VerifyRequest(String serviceSecret) {
        this(null, serviceSecret);
    }
}
