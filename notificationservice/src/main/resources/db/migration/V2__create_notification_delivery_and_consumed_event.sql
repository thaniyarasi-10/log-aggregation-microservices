CREATE TABLE IF NOT EXISTS consumed_event (
    id UUID PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    topic VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS notification_delivery (
    id UUID PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_delivery_event_id
    ON notification_delivery (event_id);
