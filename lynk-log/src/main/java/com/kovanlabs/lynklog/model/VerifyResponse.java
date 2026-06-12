package com.kovanlabs.lynklog.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyResponse(
    boolean approved,
    String serviceName,
    String organizationId
) {}
