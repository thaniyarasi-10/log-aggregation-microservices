-- Add organization_id to alert table
ALTER TABLE alert ADD COLUMN IF NOT EXISTS organization_id UUID;
UPDATE alert SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;
ALTER TABLE alert ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE alert ADD CONSTRAINT fk_alert_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE;

-- Add organization_id to jira_configuration table
ALTER TABLE jira_configuration ADD COLUMN IF NOT EXISTS organization_id UUID;
UPDATE jira_configuration SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;
ALTER TABLE jira_configuration ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE jira_configuration ADD CONSTRAINT fk_jira_configuration_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE;
