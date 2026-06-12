package com.kovanlabs.lynklog.client;

import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import com.kovanlabs.lynklog.model.VerifyRequest;
import com.kovanlabs.lynklog.model.VerifyResponse;

public class VerificationClient {

    private static final String DEFAULT_VERIFY_URL = "http://localhost:8082/api/services/verify";
    private final RestClient restClient;

    public VerificationClient() {
        this.restClient = RestClient.builder().build();
    }

    public VerifyResponse verify(String secret) {
        return verify(null, secret);
    }

    public VerifyResponse verify(String apiKey, String secret) {
        return restClient.post()
                .uri(DEFAULT_VERIFY_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VerifyRequest(apiKey, secret))
                .retrieve()
                .body(VerifyResponse.class);
    }
}
