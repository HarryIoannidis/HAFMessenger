package com.haf.integration_test;

import com.haf.server.db.Envelope;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.QueuedEnvelope;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class EnvelopeIT {

    @Container
    @SuppressWarnings("resource")
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private HikariDataSource dataSource;
    private Envelope dao;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysql.getJdbcUrl());
        config.setUsername(mysql.getUsername());
        config.setPassword(mysql.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        MetricsRegistry metricsRegistry = new MetricsRegistry();
        auditLogger = AuditLogger.create(metricsRegistry);
        dao = new Envelope(dataSource, auditLogger);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
        // MySQLContainer is automatically managed by @Testcontainers annotation
    }

    @Test
    void insert_and_fetch_envelope() {
        EncryptedMessage message = createValidMessage();

        var inserted = dao.insert(message);
        assertNotNull(inserted);
        assertNotNull(inserted.envelopeId());

        List<QueuedEnvelope> fetched = dao.fetchForRecipient(message.getRecipientId(), 10);
        assertEquals(1, fetched.size());
        assertEquals(inserted.envelopeId(), fetched.get(0).envelopeId());
        assertEquals(message.getSenderId(), fetched.get(0).payload().getSenderId());
        assertEquals(message.getRecipientId(), fetched.get(0).payload().getRecipientId());
    }

    @Test
    void mark_delivered_removes_from_mailbox() {
        EncryptedMessage message = createValidMessage();
        var inserted = dao.insert(message);

        List<QueuedEnvelope> before = dao.fetchForRecipient(message.getRecipientId(), 10);
        assertEquals(1, before.size());

        boolean marked = dao.markDelivered(List.of(inserted.envelopeId()));
        assertTrue(marked);

        List<QueuedEnvelope> after = dao.fetchForRecipient(message.getRecipientId(), 10);
        assertEquals(0, after.size());
    }

    @Test
    void delete_expired_removes_expired_envelopes() {
        EncryptedMessage expired = createValidMessage();
        // Keep expiry far enough in the past to avoid timezone boundary flakiness.
        expired.setTimestampEpochMs(System.currentTimeMillis() - Duration.ofDays(2).toMillis());
        expired.setTtlSeconds((int) Duration.ofHours(1).toSeconds());

        dao.insert(expired);
        int deleted = dao.deleteExpired();
        assertTrue(deleted >= 1);

        List<QueuedEnvelope> remaining = dao.fetchForRecipient(expired.getRecipientId(), 10);
        assertEquals(0, remaining.size());
    }

    @Test
    void fetch_for_recipient_returns_only_undelivered() {
        EncryptedMessage message1 = createValidMessage();
        message1.setRecipientId("recipient-1");
        EncryptedMessage message2 = createValidMessage();
        message2.setRecipientId("recipient-1");
        seedUser("recipient-1");

        var env1 = dao.insert(message1);
        var env2 = dao.insert(message2);

        dao.markDelivered(List.of(env1.envelopeId()));

        List<QueuedEnvelope> fetched = dao.fetchForRecipient("recipient-1", 10);
        assertEquals(1, fetched.size());
        assertEquals(env2.envelopeId(), fetched.get(0).envelopeId());
    }

    private EncryptedMessage createValidMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        String senderId = "sender-" + System.nanoTime();
        String recipientId = "recipient-" + System.nanoTime();
        seedUser(senderId);
        seedUser(recipientId);
        message.setSenderId(senderId);
        message.setRecipientId(recipientId);
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds((int) Duration.ofDays(1).toSeconds());
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setContentType("text/plain");
        message.setContentLength(4);
        message.setAadB64(Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8)));
        message.setE2e(true);
        return message;
    }

    private void seedUser(String userId) {
        String username = "u_" + userId.replaceAll("[^A-Za-z0-9_\\-]", "_");
        String email = username + "@it.local";
        String fingerprint = sha256Hex("fp:" + userId);
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n" + userId + "\n-----END PUBLIC KEY-----";

        String sql = """
                INSERT INTO users (
                    user_id, username, email, password_hash, `rank`, reg_number, full_name,
                    joined_date, telephone, public_key_fingerprint, public_key_pem, `status`, `role`
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_DATE(), ?, ?, ?, 'APPROVED', 'USER')
                ON DUPLICATE KEY UPDATE user_id = user_id
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, "$argon2id$v=19$m=19456,t=2,p=1$dummy$dummy");
            ps.setString(5, "SMINIAS");
            ps.setString(6, userId);
            ps.setString(7, "IT " + userId);
            ps.setString(8, "+3000000000");
            ps.setString(9, fingerprint);
            ps.setString(10, publicKeyPem);
            ps.executeUpdate();
        } catch (SQLException ex) {
            fail("Failed to seed test user " + userId + ": " + ex.getMessage());
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
