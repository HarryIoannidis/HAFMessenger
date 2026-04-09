CREATE TABLE api_rate_limits (
    throttle_key CHAR(64) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lockout_until TIMESTAMP NULL,
    PRIMARY KEY (throttle_key),
    INDEX idx_api_rate_limits_window (window_start),
    INDEX idx_api_rate_limits_lockout (lockout_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
