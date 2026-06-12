-- Create organization table
CREATE TABLE IF NOT EXISTS organization (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    domain VARCHAR(100),
    organization_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Insert default organization
INSERT INTO organization (id, name, domain, organization_type, created_at, updated_at)
VALUES ('00000000-0000-0000-0000-000000000000', 'Default Organization', NULL, 'PERSONAL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Create user_organization_mapping table
CREATE TABLE IF NOT EXISTS user_organization_mapping (
    id UUID PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    assigned_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_user_org UNIQUE (user_id, organization_id)
);

-- Map existing users to default organization
INSERT INTO user_organization_mapping (id, user_id, organization_id, status, assigned_at, updated_at)
SELECT gen_random_uuid(), id, '00000000-0000-0000-0000-000000000000', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM app_user
ON CONFLICT DO NOTHING;

-- Modify user_role_mapping to add organization_id
ALTER TABLE user_role_mapping ADD COLUMN IF NOT EXISTS organization_id UUID;
UPDATE user_role_mapping SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;
ALTER TABLE user_role_mapping ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE user_role_mapping ADD CONSTRAINT fk_user_role_mapping_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE;

-- Modify app_service to add organization_id
ALTER TABLE app_service ADD COLUMN IF NOT EXISTS organization_id UUID;
UPDATE app_service SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;
ALTER TABLE app_service ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE app_service ADD CONSTRAINT fk_app_service_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE;

-- Modify service_access_request to add organization_id
ALTER TABLE service_access_request ADD COLUMN IF NOT EXISTS organization_id UUID;
UPDATE service_access_request SET organization_id = '00000000-0000-0000-0000-000000000000' WHERE organization_id IS NULL;
ALTER TABLE service_access_request ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE service_access_request ADD CONSTRAINT fk_service_access_request_organization FOREIGN KEY (organization_id) REFERENCES organization(id) ON DELETE CASCADE;

-- Create organization_invite table
CREATE TABLE IF NOT EXISTS organization_invite (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    invited_by VARCHAR(50) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create organization_join_request table
CREATE TABLE IF NOT EXISTS organization_join_request (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id VARCHAR(50) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

-- Create organization_api_key table
CREATE TABLE IF NOT EXISTS organization_api_key (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    created_by VARCHAR(50) NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Seed new permissions
INSERT INTO app_permission (id, name, resource, description, created_at)
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'ORGANIZATION:MANAGE', 'organization', 'Manage organization settings and members', CURRENT_TIMESTAMP),
  ('22222222-2222-2222-2222-222222222222', 'API-KEYS:MANAGE', 'api-keys', 'Manage organization API keys', CURRENT_TIMESTAMP),
  ('33333333-3333-3333-3333-333333333333', 'JIRA:MANAGE', 'jira', 'Manage Jira integration settings', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Map ADMIN role and new permissions
INSERT INTO app_role (id, name, description, created_at)
VALUES ('44444444-4444-4444-4444-444444444444', 'ADMIN', 'Administrator role', CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permission_mapping (id, role_id, permission_id, assigned_at, updated_at)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM app_role r, app_permission p
WHERE r.name = 'ADMIN' AND p.name IN ('ORGANIZATION:MANAGE', 'API-KEYS:MANAGE', 'JIRA:MANAGE')
ON CONFLICT DO NOTHING;
