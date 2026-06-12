package com.kovanlabs.servicemanagementservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_service")
public class AppService {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "service_secret", unique = true)
    private String serviceSecret;

    @Column(name = "secret_generated_at")
    private LocalDateTime secretGeneratedAt;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "api_key", unique = true)
    private String apiKey;

    @Column(name = "api_key_generated_at")
    private LocalDateTime apiKeyGeneratedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getServiceSecret() {
        return serviceSecret;
    }

    public void setServiceSecret(String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    public LocalDateTime getSecretGeneratedAt() {
        return secretGeneratedAt;
    }

    public void setSecretGeneratedAt(LocalDateTime secretGeneratedAt) {
        this.secretGeneratedAt = secretGeneratedAt;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public LocalDateTime getApiKeyGeneratedAt() {
        return apiKeyGeneratedAt;
    }

    public void setApiKeyGeneratedAt(LocalDateTime apiKeyGeneratedAt) {
        this.apiKeyGeneratedAt = apiKeyGeneratedAt;
    }
}
