package com.kovanlabs.lynklog.startup;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import com.kovanlabs.lynklog.client.VerificationClient;
import com.kovanlabs.lynklog.config.LynkLogProperties;
import com.kovanlabs.lynklog.context.ServiceContext;
import com.kovanlabs.lynklog.model.VerifyResponse;
import com.kovanlabs.lynklog.watcher.LynkLogFileWatcher;

public class LynkLogStartupVerifier implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LynkLogStartupVerifier.class);

    private final LynkLogProperties properties;
    private final VerificationClient verificationClient;
    private final ServiceContext serviceContext;
    private final LynkLogFileWatcher fileWatcher;

    public LynkLogStartupVerifier(LynkLogProperties properties,
                                   VerificationClient verificationClient,
                                   ServiceContext serviceContext,
                                   LynkLogFileWatcher fileWatcher) {
        this.properties = properties;
        this.verificationClient = verificationClient;
        this.serviceContext = serviceContext;
        this.fileWatcher = fileWatcher;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            LOGGER.info("[LYNK] Log agent is disabled");
            serviceContext.setVerified(false);
            serviceContext.setStatus("DISABLED");
            return;
        }

        String secret = properties.getServiceSecret();
        String apiKey = properties.getApiKey();
        if (secret == null || secret.isBlank() || apiKey == null || apiKey.isBlank()) {
            LOGGER.error("[LYNK] Service verification failed");
            serviceContext.setVerified(false);
            serviceContext.setStatus("VERIFICATION_FAILED");
            return;
        }

        try {
            VerifyResponse response = verificationClient.verify(apiKey, secret);
            if (response != null && response.approved()) {
                serviceContext.setVerified(true);
                serviceContext.setServiceName(response.serviceName());
                serviceContext.setOrganizationId(response.organizationId());
                serviceContext.setStatus("VERIFIED");
                serviceContext.setVerifiedAt(Instant.now());

                LOGGER.info("[LYNK] Service verified successfully");
                LOGGER.info("[LYNK] Service Name: {}", response.serviceName());

                // Start the file watcher!
                fileWatcher.start();
            } else {
                LOGGER.error("[LYNK] Service verification failed");
                serviceContext.setVerified(false);
                serviceContext.setStatus("VERIFICATION_FAILED");
            }
        } catch (Exception e) {
            LOGGER.error("[LYNK] Service verification failed");
            serviceContext.setVerified(false);
            serviceContext.setStatus("VERIFICATION_ERROR");
        }
    }
}
