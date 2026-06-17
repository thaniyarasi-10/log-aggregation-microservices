package com.kovanlabs.notificationservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert")
public class Alert {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Column(name = "service", nullable = false, length = 100)
    private String service;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "count", nullable = false)
    private int count;

    @Column(name = "severity", nullable = false, length = 30)
    private String severity;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "normalized_message")
    private String normalizedMessage;

    @Column(name = "signature_hash", length = 64)
    private String signatureHash;

    @Column(name = "last_notification_sent_at")
    private LocalDateTime lastNotificationSentAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public void setNormalizedMessage(String normalizedMessage) {
        this.normalizedMessage = normalizedMessage;
    }

    public String getSignatureHash() {
        return signatureHash;
    }

    public void setSignatureHash(String signatureHash) {
        this.signatureHash = signatureHash;
    }

    public LocalDateTime getLastNotificationSentAt() {
        return lastNotificationSentAt;
    }

    public void setLastNotificationSentAt(LocalDateTime lastNotificationSentAt) {
        this.lastNotificationSentAt = lastNotificationSentAt;
    }
}
