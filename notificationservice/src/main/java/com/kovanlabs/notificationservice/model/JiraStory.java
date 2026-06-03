package com.kovanlabs.notificationservice.model;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "jira_story")
public class JiraStory {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "alert_id", nullable = false, length = 255)
    private String alertId;

    @Column(name = "service_name", nullable = false, length = 255)
    private String serviceName;

    @Column(name = "jira_issue_id", length = 255)
    private String jiraIssueId;

    @Column(name = "jira_issue_key", length = 255)
    private String jiraIssueKey;

    @Column(name = "jira_issue_url", length = 2048)
    private String jiraIssueUrl;

    @Column(name = "jira_assignee_account_id", length = 255)
    private String jiraAssigneeAccountId;

    @Column(name = "jira_assignee_name", length = 255)
    private String jiraAssigneeName;

    @Column(name = "priority", nullable = false, length = 50)
    private String priority;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getJiraIssueId() {
        return jiraIssueId;
    }

    public void setJiraIssueId(String jiraIssueId) {
        this.jiraIssueId = jiraIssueId;
    }

    public String getJiraIssueKey() {
        return jiraIssueKey;
    }

    public void setJiraIssueKey(String jiraIssueKey) {
        this.jiraIssueKey = jiraIssueKey;
    }

    public String getJiraIssueUrl() {
        return jiraIssueUrl;
    }

    public void setJiraIssueUrl(String jiraIssueUrl) {
        this.jiraIssueUrl = jiraIssueUrl;
    }

    public String getJiraAssigneeAccountId() {
        return jiraAssigneeAccountId;
    }

    public void setJiraAssigneeAccountId(String jiraAssigneeAccountId) {
        this.jiraAssigneeAccountId = jiraAssigneeAccountId;
    }

    public String getJiraAssigneeName() {
        return jiraAssigneeName;
    }

    public void setJiraAssigneeName(String jiraAssigneeName) {
        this.jiraAssigneeName = jiraAssigneeName;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
