CREATE TABLE IF NOT EXISTS service_jira_mapping (
    id UUID PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL UNIQUE,
    jira_account_id VARCHAR(255) NOT NULL,
    jira_display_name VARCHAR(255) NOT NULL,
    jira_email VARCHAR(255) NOT NULL,
    jira_base_url VARCHAR(255) NOT NULL,
    jira_project_key VARCHAR(255) NOT NULL,
    jira_api_token VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS jira_story (
    id UUID PRIMARY KEY,
    alert_id VARCHAR(255) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    jira_issue_id VARCHAR(255),
    jira_issue_key VARCHAR(255),
    jira_issue_url VARCHAR(2048),
    jira_assignee_account_id VARCHAR(255),
    jira_assignee_name VARCHAR(255),
    priority VARCHAR(50) NOT NULL,
    due_date TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_service_jira_mapping_service_name
    ON service_jira_mapping (service_name);

CREATE INDEX IF NOT EXISTS idx_jira_story_alert_id
    ON jira_story (alert_id);
