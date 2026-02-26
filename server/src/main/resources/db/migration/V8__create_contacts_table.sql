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
