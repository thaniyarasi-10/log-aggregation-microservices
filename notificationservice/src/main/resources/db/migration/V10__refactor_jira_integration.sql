-- Drop obsolete service_jira_mapping table
DROP TABLE IF EXISTS service_jira_mapping CASCADE;

-- Re-verify / create jira_configuration table (matches V4/V5 but ensures consistency)
CREATE TABLE IF NOT EXISTS jira_configuration (
    id UUID PRIMARY KEY,
    jira_base_url VARCHAR(255) NOT NULL,
    jira_email VARCHAR(255) NOT NULL,
    jira_api_token VARCHAR(255) NOT NULL,
    jira_project_key VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create new user_jira_mapping table
CREATE TABLE IF NOT EXISTS user_jira_mapping (
    id UUID PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL UNIQUE REFERENCES app_user(id) ON DELETE CASCADE,
    jira_account_id VARCHAR(255) NOT NULL,
    jira_display_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Add column is_primary to user_service_mapping if it doesn't exist
ALTER TABLE user_service_mapping ADD COLUMN IF NOT EXISTS is_primary BOOLEAN DEFAULT FALSE;

-- Initialize is_primary for existing service mappings. 
-- The first user assigned to each service becomes the primary owner.
WITH ranked_mappings AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY service_id ORDER BY created_at ASC) as rn
    FROM user_service_mapping
)
UPDATE user_service_mapping
SET is_primary = TRUE
WHERE id IN (SELECT id FROM ranked_mappings WHERE rn = 1);

-- Set NOT NULL constraint on is_primary
ALTER TABLE user_service_mapping ALTER COLUMN is_primary SET NOT NULL;

-- Enforce at most one primary owner per service at database level
CREATE UNIQUE INDEX IF NOT EXISTS idx_single_primary_owner_per_service 
ON user_service_mapping (service_id) 
WHERE (is_primary = TRUE);
