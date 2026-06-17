-- V17__alter_alert_organization_id_default.sql
-- Add organization_id column if it does not exist (safety check)
ALTER TABLE alert ADD COLUMN IF NOT EXISTS organization_id UUID;

-- Set default value to the column for future inserts
ALTER TABLE alert ALTER COLUMN organization_id SET DEFAULT '00000000-0000-0000-0000-000000000000'::uuid;

-- Update existing null rows
UPDATE alert SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;

-- Set column to NOT NULL
ALTER TABLE alert ALTER COLUMN organization_id SET NOT NULL;
