CREATE TABLE audit_logs (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64),
    action VARCHAR(100) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    metadata JSON,
    severity ENUM('INFO', 'WARNING', 'ERROR', 'CRITICAL') DEFAULT 'INFO',
    PRIMARY KEY (log_id),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE SET NULL,
    INDEX idx_audit_logs_timestamp (timestamp),
    INDEX idx_audit_logs_action (action),
    INDEX idx_audit_logs_user_action (user_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

