CREATE TABLE sessions (
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    jwt_token VARCHAR(512) NOT NULL,
    refresh_token VARCHAR(512),
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    PRIMARY KEY (session_id),
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_sessions_user_active (user_id, revoked),
    INDEX idx_sessions_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

