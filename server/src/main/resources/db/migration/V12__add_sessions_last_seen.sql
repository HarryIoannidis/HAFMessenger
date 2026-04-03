ALTER TABLE sessions
    ADD COLUMN last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER issued_at;

CREATE INDEX idx_sessions_user_recent
    ON sessions (user_id, revoked, expires_at, last_seen_at);
