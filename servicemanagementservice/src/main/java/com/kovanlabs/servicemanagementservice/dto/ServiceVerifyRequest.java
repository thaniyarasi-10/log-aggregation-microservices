package com.kovanlabs.servicemanagementservice.dto;

public record ServiceVerifyRequest(String apiKey, String serviceSecret) {
    public ServiceVerifyRequest(String serviceSecret) {
        this(null, serviceSecret);
    }
}
