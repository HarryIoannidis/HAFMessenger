# DATABASE

## Overview

The HAF Messenger database layer implements a military-grade persistence architecture for secure envelope routing, user management, session control, and audit logging. The design follows zero-knowledge principles where the server stores **envelope-only metadata** without decrypting message payloads, ensuring end-to-end encryption integrity.

The database serves as the central routing and mailbox infrastructure for Phase 5 (Server Ingress/Routing) and provides integration points for Phases 8 (Authentication/Authorization) and 9 (Telemetry/Monitoring).

---

## Database Selection & Requirements

### Technology Stack
- **Primary:** MySQL 8.0+ (production) / SQLite 3.35+ (development/testing)
- **Connection Pooling:** HikariCP 5.0+ with 20 max connections, 5 min idle
- **Migration Tool:** Flyway 9.0+ or Liquibase 4.0+ for schema versioning
- **JDBC Driver:** MySQL Connector/J 8.0.33+ with TLS 1.3 enforcement

### Core Requirements
1. **Zero-Knowledge Storage:** Server never decrypts message payloads; stores encrypted blobs with routing metadata only.
2. **TTL Enforcement:** Automatic expiration via `expires_at` indexed column with scheduled cleanup (ScheduledExecutorService every 5 minutes).
3. **ACID Transactions:** Full transactional integrity for message envelope insertion, session management, and audit logging.
4. **High Availability:** Connection pooling with automatic failover, read replicas for audit log queries (future Phase 9).
5. **Audit Trail:** Immutable log of all critical operations (LOGIN, SEND_MESSAGE, KEY_ROTATION, ADMIN_ACTION) with timestamp and metadata.
6. **Rate Limiting:** Per-user quotas enforced via `rate_limits` table with sliding window counters.
7. **Secure Credentials:** Environment variables only (`DB_USER`, `DB_PASS`), never hardcoded; support for AWS Secrets Manager/Vault integration.

---

## Schema Definition

### Core Tables

#### 1. `users` – Identity and Public Key Registry

```
CREATE TABLE users (
    user_id VARCHAR(64) PRIMARY KEY,                       -- UUID v4
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,                   -- Argon2id with salt
    rank VARCHAR(100),                                     -- Military rank
    reg_number VARCHAR(100),                               -- Service registration number
    full_name VARCHAR(255) NOT NULL,
    joined_date DATE,
    telephone VARCHAR(50),
    public_key_fingerprint VARCHAR(64) NOT NULL UNIQUE,    -- SHA-256 of X25519 public key
    public_key_pem TEXT NOT NULL,                          -- Base64-encoded SubjectPublicKeyInfo
    status ENUM('PENDING', 'APPROVED', 'REJECTED') DEFAULT 'PENDING',
    role ENUM('USER', 'ADMIN', 'MODERATOR') DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_username (username),
    INDEX idx_status (status),
    INDEX idx_fingerprint (public_key_fingerprint)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Rationale:**
- `public_key_fingerprint`: Enables fast lookup for recipient key retrieval during encryption.
- `public_key_pem`: Stored in SubjectPublicKeyInfo format for cross-platform compatibility (Java KeyFactory, BouncyCastle).
- `status`: Manual approval workflow for military environments (registration requires admin verification).
- `role`: RBAC integration point for Phase 8 authorization policies.

---

#### 2. `message_envelopes` – Encrypted Message Routing

```sql
CREATE TABLE message_envelopes (
    envelope_id VARCHAR(64) PRIMARY KEY,                   -- UUID v4
    sender_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    encrypted_payload BLOB NOT NULL,                       -- AES-256-GCM ciphertext (Base64-decoded)
    ephemeral_public BLOB NOT NULL,                        -- ephemeral X25519 public key
    iv VARBINARY(12) NOT NULL,                             -- 12-byte initialization vector
    auth_tag VARBINARY(16) NOT NULL,                       -- 128-bit GCM authentication tag
    aad_hash VARCHAR(64) NOT NULL,                         -- SHA-256 of AAD (header integrity)
    content_type VARCHAR(100) NOT NULL,                    -- MIME type (text/plain, image/jpeg, etc.)
    content_length INT NOT NULL,                           -- Original plaintext length
    timestamp BIGINT NOT NULL,                             -- Unix epoch milliseconds
    ttl INT NOT NULL,                                      -- Time-to-live in seconds
    delivered BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,                         -- Computed as created_at + ttl
    
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    INDEX idx_recipient_delivered (recipient_id, delivered),
    INDEX idx_expires (expires_at),
    INDEX idx_sender_timestamp (sender_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Rationale:**
- **Envelope-Only Storage:** Server nevers access to `PrivateKey`, cannot decrypt `encrypted_payload`.
- `aad_hash`: Tamper detection for immutable header fields (version, algorithm, sender, recipient, timestamp, ttl, contentType, contentLength).
- `idx_recipient_delivered`: Optimizes mailbox queries (`SELECT * WHERE recipient_id = ? AND delivered = FALSE`).
- `expires_at`: Indexed for efficient TTL cleanup via scheduled job.

---

#### 3. `sessions` – JWT Token Management

```sql
CREATE TABLE sessions (
    session_id VARCHAR(64) PRIMARY KEY,                    -- UUID v4
    user_id VARCHAR(64) NOT NULL,
    jwt_token VARCHAR(512) NOT NULL,                       -- Short-lived JWT (15 min)
    refresh_token VARCHAR(512),                            -- Long-lived refresh token (7 days)
    issued_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    ip_address VARCHAR(45),                                -- IPv4/IPv6 for audit
    user_agent VARCHAR(512),
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    INDEX idx_user_active (user_id, revoked),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Rationale:**
- `jwt_token`: Validated via HMAC-SHA256 signature (secret stored in environment variable).
- `revoked`: Enables immediate logout/session invalidation (checked on every API request).
- `expires_at`: Auto-cleanup via TTL job (delete sessions older than 7 days).

---

#### 4. `audit_logs` – Immutable Event Trail

```sql
CREATE TABLE audit_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64),
    action VARCHAR(100) NOT NULL,                          -- LOGIN, SEND_MESSAGE, KEY_ROTATION, etc.
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    metadata JSON,                                         -- Additional context (e.g., {"fileSize": 1048576})
    severity ENUM('INFO', 'WARNING', 'ERROR', 'CRITICAL') DEFAULT 'INFO',
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL,
    
    INDEX idx_timestamp (timestamp),
    INDEX idx_action (action),
    INDEX idx_user_action (user_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Rationale:**
- **Immutability:** No UPDATE or DELETE operations; append-only for forensic integrity]
- `metadata`: JSON field for flexible logging (e.g., `{"recipientId": "abc123", "messageSize": 2048}`).
- `severity`: Enables alert filtering in Phase 9 monitoring dashboards.

---

#### 5. `rate_limits` – Anti-Abuse Quotas

```sql
CREATE TABLE rate_limits (
    user_id VARCHAR(64) PRIMARY KEY,
    message_count INT DEFAULT 0,
    window_start TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    lockout_until TIMESTAMP,                               -- Non-NULL if user exceeded limits
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Rationale:**
- **Sliding Window:** Reset `message_count` and `window_start` every 60 seconds.
- **Lockouts:** If `message_count > 100` in window, set `lockout_until = NOW() + 15 minutes`.
- **Integration:** Checked before message ingress in `MessageHandler.handleIncomingMessage()`.

---

#### 6. `file_uploads` – Secure File Transfer Metadata

```sql
CREATE TABLE file_uploads (
    file_id VARCHAR(64) PRIMARY KEY,                       -- UUID v4
    uploader_id VARCHAR(64) NOT NULL,
    encrypted_chunks BLOB NOT NULL,                        -- AES-256-GCM encrypted file chunks
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,                             -- Original file size in bytes
    sha256_hash VARCHAR(64) NOT NULL,                      -- Integrity verification
    chunk_count INT NOT NULL,                              -- Number of 1MB chunks
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,                                  -- TTL for temporary uploads
    
    FOREIGN KEY (uploader_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    INDEX idx_uploader (uploader_id),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

**Rationale:**
- **Chunked Storage:** Files split into 1MB chunks, encrypted individually.
- `sha256_hash`: Client-side and server-side verification before download.
- `expires_at`: Temporary uploads (e.g., attachment pending send) auto-deleted after 24 hours.

---

## Server Schema

### Server Schema Overview
- MySQL 8.0+ required for JSON functions, window functions, and strict ACID compliance.
- All migrations managed via Flyway (`/db/migration/V*.sql`).
- No PII stored in plaintext; all message content is end-to-end encrypted.
- Phase 5 focuses on: `users`, `sessions`, `message_envelopes`, `audit_logs`, `rate_limits`, `file_uploads`.

---

### users

**Purpose:** Core user account table for authentication and authorization.

**Schema:**
```sql
CREATE TABLE users (
    user_id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    public_key_pem TEXT NOT NULL,
    key_fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_key_fingerprint (key_fingerprint)
);
```

**Columns:**
- `user_id`: Unique identifier (UUID or internal ID).
- `username`: Human-readable username (unique, case-sensitive).
- `password_hash`: Argon2id hash (never store plaintext passwords).
- `public_key_pem`: X25519 public key in PEM format for message encryption.
- `key_fingerprint`: SHA-256 fingerprint of public key for verification.

**Security:** Password hashes use Argon2id; public keys are write-once.

---

### sessions

**Purpose:** Track active authentication sessions for token-based auth.

**Schema:**
```sql
CREATE TABLE sessions (
    session_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_sessions (user_id),
    INDEX idx_expires (expires_at)
);
```

**Columns:**
- `session_id`: Unique session identifier (UUID).
- `user_id`: Foreign key to `users`.
- `token_hash`: SHA-256 hash of the session token.
- `expires_at`: Session expiry timestamp (typically 24 hours).

**Security:** Tokens hashed before storage; expired sessions cleaned via background task.

---

### message_envelopes

**Purpose:** Persistent storage for end-to-end encrypted message payloads.

**Schema:**
```sql
CREATE TABLE message_envelopes (
    envelope_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id VARCHAR(255) NOT NULL,
    recipient_id VARCHAR(255) NOT NULL,
    ciphertext_b64 TEXT NOT NULL,
    iv_b64 VARCHAR(255) NOT NULL,
    ephemeral_public_b64 TEXT NOT NULL,
    tag_b64 VARCHAR(255) NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'AES-256-GCM+X25519',
    content_type VARCHAR(100),
    client_timestamp_ms BIGINT NOT NULL,
    ttl_seconds INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_recipient_expires (recipient_id, expires_at),
    INDEX idx_expires (expires_at)
);
```

**Columns:**
- `envelope_id`: Auto-increment primary key.
- `sender_id` / `recipient_id`: User IDs.
- `ciphertext_b64`: Base64-encoded AES-GCM encrypted payload.
- `iv_b64`: Base64-encoded 12-byte IV.
- `ephemeral_public_b64`: Base64-encoded ephemeral X25519 public key.
- `tag_b64`: Base64-encoded 16-byte GCM tag.
- `expires_at`: Computed expiry (`FROM_UNIXTIME((client_timestamp_ms + ttl_seconds * 1000) / 1000)`).

**Security:** No plaintext content stored; server cannot decrypt messages; TTL enforced via cleanup task.

---

### audit_logs

**Purpose:** Structured audit trail for compliance and security monitoring.

**Schema:**
```sql
CREATE TABLE audit_logs (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    action VARCHAR(100) NOT NULL,
    user_id VARCHAR(255),
    request_id VARCHAR(255),
    status INT,
    details JSON,
    INDEX idx_action_timestamp (action, timestamp),
    INDEX idx_user_timestamp (user_id, timestamp)
);
```

**Columns:**
- `action`: Event type (`send_message`, `rate_limit`, `validation_failed`).
- `details`: JSON blob with event-specific fields.

**Security:** Never log sensitive data (ciphertext, keys, tokens); retention policy 90 days.

---

### rate_limits

**Purpose:** Per-user rate limiting state for sliding-window abuse prevention.

**Schema:**
```sql
CREATE TABLE rate_limits (
    user_id VARCHAR(255) PRIMARY KEY,
    message_count INT NOT NULL DEFAULT 0,
    window_start TIMESTAMP NOT NULL,
    lockout_until TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
```

**Behavior:** Sliding window (60 sec), threshold 100 messages → 15-min lockout; atomic UPSERT.

---

### file_uploads

**Purpose:** Metadata for large file uploads (reserved for Phase 6+).

**Schema:**
```sql  
CREATE TABLE file_uploads (
    upload_id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_user_uploads (user_id, uploaded_at)
);
```

**Note:** Table exists but not actively used in Phase 5.


---

## Database Manager Implementation

### Connection Pooling with HikariCP

```java
// File: server/src/main/java/com.haf.server/db/DatabaseManager.java

package com.haf.server.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * Production-grade database manager with HikariCP pooling.
 * 
 * Configuration via environment variables:
 * - DB_URL: JDBC connection string (e.g., jdbc:mysql://localhost:3306/haf_messenger)
 * - DB_USER: Database username
 * - DB_PASS: Database password (from AWS Secrets Manager in production)
 * - DB_POOL_SIZE: Maximum connection pool size (default: 20)
 */
public class DatabaseManager {
    
    private static final DataSource dataSource;
    
    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("DB_URL"));
        config.setUsername(System.getenv("DB_USER"));
        config.setPassword(System.getenv("DB_PASS"));
        
        // Connection pool tuning
        config.setMaximumPoolSize(getEnvInt("DB_POOL_SIZE", 20));
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);              // 30 seconds
        config.setIdleTimeout(600000);                   // 10 minutes
        config.setMaxLifetime(1800000);                  // 30 minutes
        config.setLeakDetectionThreshold(60000);         // 60 seconds (log leaks)
        
        // Security hardening
        config.addDataSourceProperty("useSSL", "true");
        config.addDataSourceProperty("requireSSL", "true");
        config.addDataSourceProperty("verifyServerCertificate", "true");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "false");
        
        // Performance optimization
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        dataSource = new HikariDataSource(config);
    }
    
    /**
     * Acquire connection from pool. Must be closed after use (try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
    /**
     * Store encrypted message envelope (Phase 5 ingress).
     */
    public static boolean storeEnvelope(EncryptedMessageDTO envelope) {
        String sql = """
            INSERT INTO message_envelopes 
            (envelope_id, sender_id, recipient_id, encrypted_payload, wrapped_key, 
             iv, auth_tag, aad_hash, content_type, content_length, timestamp, ttl, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, envelope.messageId);
            ps.setString(2, envelope.senderId);
            ps.setString(3, envelope.recipientId);
            ps.setBytes(4, Base64.getDecoder().decode(envelope.ciphertext));
            ps.setBytes(5, Base64.getDecoder().decode(envelope.ephemeralPublicB64));
            ps.setBytes(6, Base64.getDecoder().decode(envelope.iv));
            ps.setBytes(7, Base64.getDecoder().decode(envelope.authTag));
            ps.setString(8, computeAadHash(envelope));
            ps.setString(9, envelope.contentType);
            ps.setInt(10, envelope.contentLength);
            ps.setLong(11, envelope.timestamp);
            ps.setInt(12, envelope.ttl);
            ps.setTimestamp(13, new Timestamp(System.currentTimeMillis() + (envelope.ttl * 1000L)));
            
            int rows = ps.executeUpdate();
            
            if (rows > 0) {
                AuditLogger.log("MESSAGE_STORED", envelope.senderId, 
                    Map.of("envelopeId", envelope.messageId, "recipientId", envelope.recipientId));
            }
            
            return rows > 0;
            
        } catch (SQLException e) {
            AuditLogger.logError("DB_STORE_ENVELOPE_FAILED", e);
            return false;
        }
    }
    
    /**
     * Retrieve undelivered messages for recipient (mailbox pattern).
     */
    public static List<EncryptedMessageDTO> getMailbox(String recipientId) {
        String sql = """
            SELECT * FROM message_envelopes 
            WHERE recipient_id = ? AND delivered = FALSE AND expires_at > NOW()
            ORDER BY timestamp ASC
            LIMIT 100
        """;
        
        List<EncryptedMessageDTO> messages = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, recipientId);
            ResultSet rs = ps.executeQuery();
            
            while (rs.next()) {
                messages.add(mapToDTO(rs));
            }
            
            AuditLogger.log("MAILBOX_FETCHED", recipientId, 
                Map.of("count", messages.size()));
            
        } catch (SQLException e) {
            AuditLogger.logError("DB_MAILBOX_FETCH_FAILED", e);
        }
        
        return messages;
    }
    
    /**
     * Mark message as delivered (Phase 4 client ACK).
     */
    public static boolean markDelivered(String envelopeId) {
        String sql = "UPDATE message_envelopes SET delivered = TRUE WHERE envelope_id = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, envelopeId);
            return ps.executeUpdate() > 0;
            
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * TTL cleanup job (run every 5 minutes via ScheduledExecutorService).
     */
    public static int cleanupExpiredMessages() {
        String sql = "DELETE FROM message_envelopes WHERE expires_at < NOW()";
        
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            int deleted = stmt.executeUpdate(sql);
            
            if (deleted > 0) {
                AuditLogger.log("TTL_CLEANUP", "system", Map.of("deleted", deleted));
            }
            
            return deleted;
            
        } catch (SQLException e) {
            AuditLogger.logError("TTL_CLEANUP_FAILED", e);
            return 0;
        }
    }
    
    /**
     * Enforce rate limits (Phase 5 anti-abuse).
     */
    public static boolean checkRateLimit(String userId) {
        String sql = """
            INSERT INTO rate_limits (user_id, message_count, window_start)
            VALUES (?, 1, NOW())
            ON DUPLICATE KEY UPDATE
                message_count = IF(TIMESTAMPDIFF(SECOND, window_start, NOW()) > 60, 1, message_count + 1),
                window_start = IF(TIMESTAMPDIFF(SECOND, window_start, NOW()) > 60, NOW(), window_start),
                lockout_until = IF(message_count >= 100, DATE_ADD(NOW(), INTERVAL 15 MINUTE), lockout_until)
        """;
        
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, userId);
            ps.executeUpdate();
            
            // Check if user is locked out
            String checkSql = "SELECT lockout_until FROM rate_limits WHERE user_id = ?";
            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setString(1, userId);
                ResultSet rs = checkPs.executeQuery();
                
                if (rs.next()) {
                    Timestamp lockout = rs.getTimestamp("lockout_until");
                    if (lockout != null && lockout.after(new Timestamp(System.currentTimeMillis()))) {
                        AuditLogger.log("RATE_LIMIT_EXCEEDED", userId, Map.of("lockoutUntil", lockout));
                        return false;
                    }
                }
            }
            
            return true;
            
        } catch (SQLException e) {
            AuditLogger.logError("RATE_LIMIT_CHECK_FAILED", e);
            return false;
        }
    }
    
    // Helper methods
    private static String computeAadHash(EncryptedMessageDTO envelope) {
        // SHA-256 of immutable header fields (version, algorithm, senderId, recipientId, timestamp, ttl, contentType, contentLength)
        String aad = String.join("|", 
            envelope.version, envelope.algorithm, envelope.senderId, envelope.recipientId,
            String.valueOf(envelope.timestamp), String.valueOf(envelope.ttl),
            envelope.contentType, String.valueOf(envelope.contentLength)
        );
        return Hex.toHexString(MessageDigest.getInstance("SHA-256").digest(aad.getBytes()));
    }
    
    private static EncryptedMessageDTO mapToDTO(ResultSet rs) throws SQLException {
        EncryptedMessageDTO dto = new EncryptedMessageDTO();
        dto.messageId = rs.getString("envelope_id");
        dto.senderId = rs.getString("sender_id");
        dto.recipientId = rs.getString("recipient_id");
        dto.ciphertext = Base64.getEncoder().encodeToString(rs.getBytes("encrypted_payload"));
        dto.ephemeralPublicB64 = Base64.getEncoder().encodeToString(rs.getBytes("ephemeral_public"));
        dto.iv = Base64.getEncoder().encodeToString(rs.getBytes("iv"));
        dto.authTag = Base64.getEncoder().encodeToString(rs.getBytes("auth_tag"));
        dto.contentType = rs.getString("content_type");
        dto.contentLength = rs.getInt("content_length");
        dto.timestamp = rs.getLong("timestamp");
        dto.ttl = rs.getInt("ttl");
        return dto;
    }
    
    private static int getEnvInt(String key, int defaultValue) {
        String value = System.getenv(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }
}
```

---

## Repository Pattern

### UserRepository

```java
// File: server/src/main/java/com.haf.server/db/UserRepository.java

package com.haf.server.db;

import java.sql.*;
import java.util.Optional;

/**
 * User management repository (Phase 8 authentication integration).
 */
public class UserRepository {
    
    /**
     * Authenticate user with Argon2id verification.
     */
    public static Optional<UserDTO> authenticate(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND status = 'APPROVED'";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                
                // Constant-time Argon2id verification (Phase 2 crypto)
                if (PasswordHasher.verify(password, storedHash)) {
                    AuditLogger.log("LOGIN_SUCCESS", rs.getString("user_id"), Map.of("username", username));
                    return Optional.of(mapUserDTO(rs));
                }
            }
            
            AuditLogger.log("LOGIN_FAILED", null, Map.of("username", username, "reason", "INVALID_CREDENTIALS"));
            
        } catch (SQLException e) {
            AuditLogger.logError("AUTH_QUERY_FAILED", e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Create new user (Registration Phase 2 integration).
     */
    public static boolean createUser(RegistrationPayload payload) {
        String sql = """
            INSERT INTO users 
            (user_id, username, email, password_hash, rank, reg_number, full_name, 
             joined_date, telephone, public_key_fingerprint, public_key_pem, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
        """;
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, payload.username);
            ps.setString(3, payload.email);
            ps.setString(4, PasswordHasher.hash(payload.password));  // Argon2id
            ps.setString(5, payload.rank);
            ps.setString(6, payload.regNumber);
            ps.setString(7, payload.fullName);
            ps.setDate(8, Date.valueOf(payload.joinedDate));
            ps.setString(9, payload.telephone);
            ps.setString(10, payload.keyFingerprint);
            ps.setString(11, payload.publicKeyPem);
            
            int rows = ps.executeUpdate();
            
            if (rows > 0) {
                AuditLogger.log("USER_CREATED", null, Map.of("username", payload.username, "status", "PENDING"));
            }
            
            return rows > 0;
            
        } catch (SQLException e) {
            AuditLogger.logError("USER_CREATE_FAILED", e);
            return false;
        }
    }
    
    /**
     * Fetch user's public key PEM for encryption (Phase 2 integration).
     */
    public static Optional<String> getPublicKeyPem(String userId) {
        String sql = "SELECT public_key_pem FROM users WHERE user_id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("public_key_pem"));
            }
            
        } catch (SQLException e) {
            AuditLogger.logError("PUBLIC_KEY_FETCH_FAILED", e);
        }
        
        return Optional.empty();
    }
    
    private static UserDTO mapUserDTO(ResultSet rs) throws SQLException {
        UserDTO dto = new UserDTO();
        dto.userId = rs.getString("user_id");
        dto.username = rs.getString("username");
        dto.email = rs.getString("email");
        dto.rank = rs.getString("rank");
        dto.regNumber = rs.getString("reg_number");
        dto.fullName = rs.getString("full_name");
        dto.status = rs.getString("status");
        dto.role = rs.getString("role");
        return dto;
    }
}
```

---

## Relationships & Integrity

### Foreign Key Constraints

```
users
↓ (sender_id, recipient_id)
message_envelopes

users
↓ (user_id)
sessions

users
↓ (user_id)
audit_logs (SET NULL on delete)

users
↓ (user_id)
rate_limits

users
↓ (uploader_id)
file_uploads
```

**Referential Integrity:**
- **CASCADE:** Deleting a user removes all their messages, sessions, rate limits, and uploads
- **SET NULL:** Audit logs retain entries for deleted users (forensic preservation).

---

## Evolution Strategies

### Phase-by-Phase Schema Changes

#### Phase 7 – File Transfer Enhancements

**New Column:** `chunk_resume_token` for resumable uploads.

```
-- Migration V7_1__add_file_resume.sql
ALTER TABLE file_uploads
ADD COLUMN chunk_resume_token VARCHAR(64),
ADD COLUMN last_chunk_uploaded INT DEFAULT 0;

CREATE INDEX idx_resume_token ON file_uploads(chunk_resume_token);
```

#### Phase 8 – Authentication Enhancements

**New Table:** `two_factor_auth` for TOTP/WebAuthn.

```sql
-- Migration V8_1__add_2fa.sql
CREATE TABLE two_factor_auth (
    user_id VARCHAR(64) PRIMARY KEY,
    totp_secret VARCHAR(32),                               -- Base32-encoded secret
    backup_codes TEXT,                                     -- JSON array of hashed codes
    webauthn_credentials JSON,                             -- Array of {credentialId, publicKey}
    enabled BOOLEAN DEFAULT FALSE,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
```

#### Phase 9 – Monitoring Enhancements

**New Table:** `metrics_snapshots` for time-series data.

```sql
-- Migration V9_1__add_metrics.sql
CREATE TABLE metrics_snapshots (
    snapshot_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ingress_rate FLOAT,                                    -- Messages per second
    error_rate FLOAT,                                      -- Percentage
    avg_latency_ms FLOAT,
    active_sessions INT,
    storage_used_mb BIGINT,
    
    INDEX idx_timestamp (timestamp)
) ENGINE=InnoDB;
```

---

### Backward-Compatible Migration Patterns

#### Expand-Contract Patternmn Renaming)[10]

**Scenario:** Rename `username` → `login_identifier`

```
-- Phase 1: EXPAND (add new column)
ALTER TABLE users ADD COLUMN login_identifier VARCHAR(255);

-- Phase 2: Dual-write (application writes to both columns)
UPDATE users SET login_identifier = username WHERE login_identifier IS NULL;

-- Phase 3: Migrate application to read from login_identifier

-- Phase 4: CONTRACT (drop old column)
ALTER TABLE users DROP COLUMN username;
```

#### Blue-Green Database Deployment (Major Schema Overhaul)

**Scenario:** Change encryption algorithm from X25519 ECDH to Kyber1024 (post-quantum)

```sql
-- Step 1: Create parallel table
CREATE TABLE message_envelopes_v2 (
    envelope_id VARCHAR(64) PRIMARY KEY,
    sender_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    encrypted_payload BLOB NOT NULL,
    wrapped_key_kyber BLOB NOT NULL,                       -- Kyber1024 ciphertext
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
    
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (recipient_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    INDEX idx_recipient_delivered (recipient_id, delivered),
    INDEX idx_expires (expires_at)
);

-- Step 2: Dual-write to both tables (application logic)
-- Step 3: Migrate historical data in batches
INSERT INTO message_envelopes_v2 SELECT * FROM message_envelopes WHERE created_at < '2026-01-01';

-- Step 4: Switch application to read from message_envelopes_v2
-- Step 5: Drop old table after validation
DROP TABLE message_envelopes;
RENAME TABLE message_envelopes_v2 TO message_envelopes;
```

---

### Schema Versioning with Flyway

**Directory Structure:**
```
server/src/main/resources/db/migration/
├── V1_0__initial_schema.sql
├── V1_1__add_audit_severity.sql
├── V7_1__add_file_resume.sql
├── V8_1__add_2fa.sql
└── V9_1__add_metrics.sql
```

**Maven Configuration:**
```xml
<plugin>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-maven-plugin</artifactId>
    <version>9.22.0</version>
    <configuration>
        <url>${DB_URL}</url>
        <user>${DB_USER}</user>
        <password>${DB_PASS}</password>
        <locations>
            <location>filesystem:src/main/resources/db/migration</location>
        </locations>
        <baselineOnMigrate>true</baselineOnMigrate>
    </configuration>
</plugin>
```

**Execution:**
```bash
mvn flyway:migrate -DDB_URL=jdbc:mysql://localhost:3306/haf_messenger -DDB_USER=haf_admin -DDB_PASS=$DB_PASS
```

---

## Testing Strategy

### Unit Tests

```
// File: server/src/test/java/com.haf.server/db/DatabaseManagerTest.java

@Test
public void testStoreAndRetrieveEnvelope() {
EncryptedMessageDTO envelope = TestFixtures.createValidEnvelope();

    assertTrue(DatabaseManager.storeEnvelope(envelope));
    
    List<EncryptedMessageDTO> mailbox = DatabaseManager.getMailbox(envelope.recipientId);
    assertEquals(1, mailbox.size());
    assertEquals(envelope.messageId, mailbox.get(0).messageId);
}

@Test
public void testTTLCleanup() {
EncryptedMessageDTO envelope = TestFixtures.createExpiredEnvelope();
DatabaseManager.storeEnvelope(envelope);

    int deleted = DatabaseManager.cleanupExpiredMessages();
    assertEquals(1, deleted);
    
    List<EncryptedMessageDTO> mailbox = DatabaseManager.getMailbox(envelope.recipientId);
    assertTrue(mailbox.isEmpty());
}

@Test
public void testRateLimitEnforcement() {
String userId = UUID.randomUUID().toString();

    // Send 100 messages (should succeed)
    for (int i = 0; i < 100; i++) {
        assertTrue(DatabaseManager.checkRateLimit(userId));
    }
    
    // 101st message should fail
    assertFalse(DatabaseManager.checkRateLimit(userId));
}
```

### Integration Tests

```java
// File: server/src/test/java/com.haf.server/db/DatabaseE2EIT.java

@Test
public void testEndToEndMessageFlow() {
    // 1. Create users
    UserRepository.createUser(TestFixtures.createAliceRegistration());
    UserRepository.createUser(TestFixtures.createBobRegistration());
    
    // 2. Send message from Alice to Bob
    EncryptedMessageDTO msg = TestFixtures.createAliceToBobMessage();
    DatabaseManager.storeEnvelope(msg);
    
    // 3. Bob fetches mailbox
    List<EncryptedMessageDTO> bobMailbox = DatabaseManager.getMailbox("bob-user-id");
    assertEquals(1, bobMailbox.size());
    
    // 4. Mark as delivered
    DatabaseManager.markDelivered(msg.messageId);
    
    // 5. Verify no longer in mailbox
    bobMailbox = DatabaseManager.getMailbox("bob-user-id");
    assertTrue(bobMailbox.isEmpty());
}
```

---

## Acceptance Criteria

- **Functionality**
    - All 6 core tables created with correct constraints and indexes.
    - HikariCP connection pooling operational with TLS 1.3 enforcementvelope storage/retrieval, mailbox queries, TTL cleanup, rate limiting all functional.
- **Performance** (e.g., average message ingress < 50ms, mailbox retrieval < 100ms for 100 messages).
- **Security**
    - Zero-knowledge storage validated (server cannot decrypt payloads).
    - Password hashing via Argon2id with constant-time verification.
    - Audit logging for all critical operations (no exceptions).

- **Testing**
    - Unit tests: DatabaseManagerTest, UserRepositoryTest (>80% coverage).
    - Integration tests: DatabaseE2EIT, TTLCleanupIT, RateLimitIT (executed via Failsafe).

- **Documentation**
    - Schema definition documented in this DATABASE.md.
    - Migration scripts versioned in `db/migration/` with Flyway.
    - Environment variable configuration documented in server README.

---

## Best Practices

1. **Prepared Statements Only:** No string concatenation; prevents SQL injection.
2. **Connection Pooling:** HikariCP with leak detection enabled (60s threshold).
3. **Environment Variables:** Credentials via `System.getenv()`, never hardcoded.
4. **Indexes:** All foreign keys, timestamp columns, and frequently queried fields indexed.
5. **TTL Enforcement:** ScheduledExecutorService cleanup every 5 minutes.
6. **Audit Immutability:** Append-only logs with no UPDATE/DELETE operations.
7. **Transaction Boundaries:** Auto-commit disabled for multi-statement operations.
8. **Schema Versioning:** Flyway migrations with baseline-on-migrate enabled.

---

## Potential Issues & Mitigations

| Issue | Mitigation |
|---|---|
| Connection pool exhaustion | HikariCP leak detection + connection timeout 30s |
| TTL cleanup lag | Scheduled job every 5 minutes + `expires_at` index optimization |
| Rate limiting (exhaustion/bypass) | Sliding-window counters per user + lockout enforcement (e.g., 15 minutes) |
| Schema migration failure | Flyway baseline + tested rollback procedures and migration validation |
| Audit log storage growth | Partition by month + archive to cold storage (Phase 9) |
| SQL injection | Prepared statements exclusively + parameterized queries |
---

## Integration Points

- **Phase 2 (Crypto):** Public key PEM retrieval viablicKeyPem()`.
- **Phase 4 (Client):** Mailbox polling via `DatabaseManager.getMailbox()` on WebSocket connect.
- **Phase 5 (Server):** Ingress validation + envelope storage via `DatabaseManager.storeEnvelope()`.
- **Phase 8 (Auth):** Session creation/validation via `SessionRepository` (to be implemented).
- **Phase 9 (Monitoring):** Metrics snapshots via `MetricsRepository` (to be implemented).