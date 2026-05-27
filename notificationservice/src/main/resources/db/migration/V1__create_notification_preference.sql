CREATE TABLE IF NOT EXISTS notification_preference (
    id UUID PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL UNIQUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    push_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_preference_user_email
    ON notification_preference (user_email);
