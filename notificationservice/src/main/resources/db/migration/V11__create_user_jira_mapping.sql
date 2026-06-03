CREATE TABLE IF NOT EXISTS user_jira_mapping (
                                                 id UUID PRIMARY KEY,
                                                 user_id VARCHAR(50) NOT NULL UNIQUE,
    jira_account_id VARCHAR(255) NOT NULL,
    jira_display_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
    );