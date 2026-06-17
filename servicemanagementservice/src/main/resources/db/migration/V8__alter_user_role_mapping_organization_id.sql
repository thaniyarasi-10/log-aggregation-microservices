-- V8__alter_user_role_mapping_organization_id.sql
-- Add organization_id column if it somehow does not exist (safety check), but normally we alter its default and update.
ALTER TABLE user_role_mapping ADD COLUMN IF NOT EXISTS organization_id UUID;

-- Set default value to the column for future inserts
ALTER TABLE user_role_mapping ALTER COLUMN organization_id SET DEFAULT '00000000-0000-0000-0000-000000000000'::uuid;

-- Update existing null rows
UPDATE user_role_mapping SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;

-- Set column to NOT NULL if it isn't already
ALTER TABLE user_role_mapping ALTER COLUMN organization_id SET NOT NULL;
