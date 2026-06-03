DROP TABLE service_jira_mapping;

CREATE TABLE service_jira_mapping (
      id UUID PRIMARY KEY,
      service_name VARCHAR(255) NOT NULL UNIQUE,
      jira_account_id VARCHAR(255) NOT NULL,
      jira_display_name VARCHAR(255) NOT NULL,
      active BOOLEAN NOT NULL DEFAULT TRUE,
      created_at TIMESTAMP NOT NULL,
      updated_at TIMESTAMP NOT NULL
);

CREATE TABLE jira_configuration (
    id UUID PRIMARY KEY,
    jira_base_url VARCHAR(255) NOT NULL,
    jira_email VARCHAR(255) NOT NULL,
    jira_api_token VARCHAR(255) NOT NULL,
    jira_project_key VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);