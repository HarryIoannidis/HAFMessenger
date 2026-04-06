ALTER TABLE sessions
    ADD COLUMN access_jti VARCHAR(64) NULL AFTER session_id,
    ADD COLUMN refresh_token_hash CHAR(64) NULL AFTER user_id,
    ADD COLUMN access_expires_at TIMESTAMP NULL AFTER issued_at,
    ADD COLUMN refresh_expires_at TIMESTAMP NULL AFTER access_expires_at,
    ADD COLUMN refresh_last_rotated_at TIMESTAMP NULL AFTER refresh_expires_at;

UPDATE sessions
SET access_jti = session_id,
    refresh_token_hash = SHA2(CONCAT('legacy:', session_id), 256),
    access_expires_at = expires_at,
    refresh_expires_at = DATE_ADD(expires_at, INTERVAL 30 DAY),
    refresh_last_rotated_at = COALESCE(last_seen_at, issued_at, CURRENT_TIMESTAMP)
WHERE access_jti IS NULL
   OR refresh_token_hash IS NULL
   OR access_expires_at IS NULL
   OR refresh_expires_at IS NULL
   OR refresh_last_rotated_at IS NULL;

ALTER TABLE sessions
    MODIFY COLUMN access_jti VARCHAR(64) NOT NULL,
    MODIFY COLUMN refresh_token_hash CHAR(64) NOT NULL,
    MODIFY COLUMN access_expires_at TIMESTAMP NOT NULL,
    MODIFY COLUMN refresh_expires_at TIMESTAMP NOT NULL,
    MODIFY COLUMN refresh_last_rotated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE sessions
    DROP COLUMN jwt_token,
    DROP COLUMN refresh_token,
    DROP COLUMN expires_at;

CREATE UNIQUE INDEX uq_sessions_access_jti
    ON sessions (access_jti);

CREATE UNIQUE INDEX uq_sessions_refresh_token_hash
    ON sessions (refresh_token_hash);

CREATE INDEX idx_sessions_access_expires
    ON sessions (access_expires_at);

CREATE INDEX idx_sessions_refresh_expires
    ON sessions (refresh_expires_at);

CREATE TABLE login_rate_limits (
    throttle_key CHAR(64) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lockout_until TIMESTAMP NULL,
    PRIMARY KEY (throttle_key),
    INDEX idx_login_rate_limits_window (window_start),
    INDEX idx_login_rate_limits_lockout (lockout_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
