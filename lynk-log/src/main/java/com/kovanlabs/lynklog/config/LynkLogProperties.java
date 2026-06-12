package com.kovanlabs.lynklog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lynklog")
public class LynkLogProperties {

    private boolean enabled = true;
    private String serviceSecret;
    private String apiKey;
    private String logFilePath = "logs/application.log";
    private String kafkaBootstrapServers = "localhost:9092";
    private String kafkaTopic = "lynk-logs";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceSecret() {
        return serviceSecret;
    }

    public void setServiceSecret(String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    public String getLogFilePath() {
        return logFilePath;
    }

    public void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public String getKafkaBootstrapServers() {
        return kafkaBootstrapServers;
    }

    public void setKafkaBootstrapServers(String kafkaBootstrapServers) {
        this.kafkaBootstrapServers = kafkaBootstrapServers;
    }

    public String getKafkaTopic() {
        return kafkaTopic;
    }

    public void setKafkaTopic(String kafkaTopic) {
        this.kafkaTopic = kafkaTopic;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
