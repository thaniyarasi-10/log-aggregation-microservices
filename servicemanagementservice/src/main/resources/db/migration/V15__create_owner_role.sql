-- Seed new permissions if not already present
INSERT INTO app_permission (id, name, resource, description, created_at)
VALUES 
  ('55555555-5555-5555-5555-555555555555', 'USERS:MANAGE', 'users', 'Manage users and role assignments', CURRENT_TIMESTAMP),
  ('66666666-6666-6666-6666-666666666666', 'SERVICES:MANAGE', 'services', 'Manage service registration and approval', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Seed OWNER role if not already present
INSERT INTO app_role (id, name, description, created_at)
VALUES ('77777777-7777-7777-7777-777777777777', 'OWNER', 'Organization Owner role', CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- Map ADMIN role and new permissions
INSERT INTO role_permission_mapping (id, role_id, permission_id, assigned_at, updated_at)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM app_role r, app_permission p
WHERE r.name = 'ADMIN' AND p.name IN ('USERS:MANAGE', 'SERVICES:MANAGE')
ON CONFLICT DO NOTHING;

-- Map OWNER role and all permissions
INSERT INTO role_permission_mapping (id, role_id, permission_id, assigned_at, updated_at)
SELECT gen_random_uuid(), r.id, p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM app_role r, app_permission p
WHERE r.name = 'OWNER' AND p.name IN ('ORGANIZATION:MANAGE', 'API-KEYS:MANAGE', 'JIRA:MANAGE', 'USERS:MANAGE', 'SERVICES:MANAGE')
ON CONFLICT DO NOTHING;
