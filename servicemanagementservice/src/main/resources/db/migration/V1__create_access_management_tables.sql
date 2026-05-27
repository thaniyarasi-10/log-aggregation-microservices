CREATE TABLE IF NOT EXISTS app_user (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS app_role (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS app_permission (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    resource VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS app_service (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS service_access_request (
    id UUID PRIMARY KEY,
    requested_by VARCHAR(255) NOT NULL,
    service_name VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_role_mapping (
    id UUID PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES app_user(id),
    role_id UUID NOT NULL REFERENCES app_role(id),
    assigned_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS user_service_mapping (
    id UUID PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL REFERENCES app_user(id),
    service_id UUID NOT NULL REFERENCES app_service(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS role_permission_mapping (
    id UUID PRIMARY KEY,
    role_id UUID NOT NULL REFERENCES app_role(id),
    permission_id UUID NOT NULL REFERENCES app_permission(id),
    assigned_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_service_access_request_requested_by
    ON service_access_request (requested_by);
CREATE INDEX IF NOT EXISTS idx_service_access_request_status
    ON service_access_request (status);
CREATE INDEX IF NOT EXISTS idx_app_service_name
    ON app_service (name);
