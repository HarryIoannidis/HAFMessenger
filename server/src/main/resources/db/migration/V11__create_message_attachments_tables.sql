CREATE TABLE message_attachments (
    attachment_id VARCHAR(64) NOT NULL,
    sender_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    expected_size BIGINT NOT NULL,
    expected_chunks INT NOT NULL,
    status ENUM('INIT', 'UPLOADING', 'COMPLETE', 'BOUND') NOT NULL DEFAULT 'INIT',
    envelope_id VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    bound_at TIMESTAMP NULL,
    PRIMARY KEY (attachment_id),
    CONSTRAINT fk_msg_attachments_sender FOREIGN KEY (sender_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_attachments_recipient FOREIGN KEY (recipient_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_msg_attachments_envelope FOREIGN KEY (envelope_id) REFERENCES message_envelopes (envelope_id) ON DELETE SET NULL,
    INDEX idx_msg_attachments_sender (sender_id),
    INDEX idx_msg_attachments_recipient_status (recipient_id, status),
    INDEX idx_msg_attachments_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE message_attachment_chunks (
    attachment_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    chunk_data LONGBLOB NOT NULL,
    chunk_size INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attachment_id, chunk_index),
    CONSTRAINT fk_msg_attachment_chunks_attachment FOREIGN KEY (attachment_id) REFERENCES message_attachments (attachment_id) ON DELETE CASCADE,
    INDEX idx_msg_attachment_chunks_attachment (attachment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
