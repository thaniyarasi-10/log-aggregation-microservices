package com.kovanlabs.notificationservice.service;

import com.kovanlabs.notificationservice.repository.JiraConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Validates Jira configuration health at startup.
 * If active connection is invalid, logs warning instead of crashing boot flow.
 */
@Component
public class  JiraStartupHealthValidator implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(JiraStartupHealthValidator.class);

    private final JiraConfigurationRepository repository;
    private final JiraClient client;

    public JiraStartupHealthValidator(JiraConfigurationRepository repository, JiraClient client) {
        this.repository = repository;
        this.client = client;
    }

    @Override
    public void run(String... args) {
        repository.findFirstByActiveTrue().ifPresent(config -> {
            try {
                LOGGER.info("Verifying Jira connection settings at startup for URL: {}", config.getJiraBaseUrl());
                client.testConnection(
                        config.getJiraBaseUrl(),
                        config.getJiraApiToken(),
                        config.getJiraEmail(),
                        config.getJiraProjectKey()
                );
                LOGGER.info("Jira startup health check succeeded.");
            } catch (Exception e) {
                LOGGER.warn("Jira configuration is invalid at startup. Reason: {}", e.getMessage());
            }
        });
    }
}
