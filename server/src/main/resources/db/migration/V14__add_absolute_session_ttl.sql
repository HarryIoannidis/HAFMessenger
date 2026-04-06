ALTER TABLE sessions
    ADD COLUMN absolute_expires_at TIMESTAMP NULL AFTER refresh_expires_at;

UPDATE sessions
SET absolute_expires_at = COALESCE(refresh_expires_at, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 30 DAY))
WHERE absolute_expires_at IS NULL;

ALTER TABLE sessions
    MODIFY COLUMN absolute_expires_at TIMESTAMP NOT NULL;

CREATE INDEX idx_sessions_absolute_expires
    ON sessions (absolute_expires_at);
