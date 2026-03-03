-- Create contacts table
CREATE TABLE contacts (
    user_id VARCHAR(64) NOT NULL,
    contact_id VARCHAR(64) NOT NULL,
    `status` ENUM('PENDING', 'ACCEPTED', 'BLOCKED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, contact_id),
    CONSTRAINT fk_contacts_user FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE,
    CONSTRAINT fk_contacts_friend FOREIGN KEY (contact_id) REFERENCES users (user_id) ON DELETE CASCADE,
    INDEX idx_contacts_user_status (user_id, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add encryption metadata columns to file_uploads for E2E-encrypted photo storage.
-- The server stores these fields opaquely and cannot decrypt without the Admin's private key.
ALTER TABLE file_uploads
    ADD COLUMN iv_b64              TEXT          NULL COMMENT 'Base64-encoded AES-GCM IV (12 bytes)'          AFTER encrypted_chunks,
    ADD COLUMN tag_b64             TEXT          NULL COMMENT 'Base64-encoded AES-GCM authentication tag'     AFTER iv_b64,
    ADD COLUMN ephemeral_public_b64 TEXT         NULL COMMENT 'Base64-encoded ephemeral X25519 public key'   AFTER tag_b64,
    ADD COLUMN sha256_hash_hex     VARCHAR(64)   NULL COMMENT 'Hex-encoded SHA-256 of the plaintext file'    AFTER ephemeral_public_b64;
