CREATE TABLE file_uploads (
    file_id VARCHAR(64) NOT NULL,
    uploader_id VARCHAR(64) NOT NULL,
    encrypted_chunks LONGBLOB NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    chunk_count INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    PRIMARY KEY (file_id),
    CONSTRAINT fk_file_uploads_uploader FOREIGN KEY (uploader_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_file_uploads_uploader (uploader_id),
    INDEX idx_file_uploads_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

