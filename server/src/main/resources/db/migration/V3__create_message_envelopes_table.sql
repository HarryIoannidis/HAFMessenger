CREATE TABLE message_envelopes (
    envelope_id VARCHAR(64) NOT NULL,
    sender_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    encrypted_payload LONGBLOB NOT NULL,
    wrapped_key BLOB NOT NULL,
    iv VARBINARY(12) NOT NULL,
    auth_tag VARBINARY(16) NOT NULL,
    aad_hash VARCHAR(64) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    content_length INT NOT NULL,
    timestamp BIGINT NOT NULL,
    ttl INT NOT NULL,
    delivered BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    PRIMARY KEY (envelope_id),
    CONSTRAINT fk_envelopes_sender FOREIGN KEY (sender_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_envelopes_recipient FOREIGN KEY (recipient_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_envelopes_recipient_delivered (recipient_id, delivered),
    INDEX idx_envelopes_expires (expires_at),
    INDEX idx_envelopes_sender_timestamp (sender_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

