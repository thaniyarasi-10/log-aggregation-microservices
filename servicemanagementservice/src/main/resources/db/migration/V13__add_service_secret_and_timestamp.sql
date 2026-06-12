ALTER TABLE app_service ADD COLUMN service_secret VARCHAR(255) UNIQUE;
ALTER TABLE app_service ADD COLUMN secret_generated_at TIMESTAMP;
