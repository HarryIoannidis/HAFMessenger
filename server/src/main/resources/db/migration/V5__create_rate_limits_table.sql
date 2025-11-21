CREATE TABLE rate_limits (
    user_id VARCHAR(64) NOT NULL,
    message_count INT DEFAULT 0,
    window_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    lockout_until TIMESTAMP NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_rate_limits_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_rate_limits_window (window_start),
    INDEX idx_rate_limits_lockout (lockout_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

