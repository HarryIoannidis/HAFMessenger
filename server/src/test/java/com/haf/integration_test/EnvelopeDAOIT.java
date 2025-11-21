package com.haf.integration_test;

import com.haf.server.db.EnvelopeDAO;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires Docker/Testcontainers; disabled on local dev without Docker")
@Testcontainers
class EnvelopeDAOIT {

    @Container
    @SuppressWarnings("resource")
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private HikariDataSource dataSource;
    private EnvelopeDAO dao;
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
        dao = new EnvelopeDAO(dataSource, auditLogger);
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

        List<com.haf.server.router.QueuedEnvelope> fetched = dao.fetchForRecipient(message.recipientId, 10);
        assertEquals(1, fetched.size());
        assertEquals(inserted.envelopeId(), fetched.get(0).envelopeId());
        assertEquals(message.senderId, fetched.get(0).payload().senderId);
        assertEquals(message.recipientId, fetched.get(0).payload().recipientId);
    }

    @Test
    void mark_delivered_removes_from_mailbox() {
        EncryptedMessage message = createValidMessage();
        var inserted = dao.insert(message);

        List<com.haf.server.router.QueuedEnvelope> before = dao.fetchForRecipient(message.recipientId, 10);
        assertEquals(1, before.size());

        boolean marked = dao.markDelivered(List.of(inserted.envelopeId()));
        assertTrue(marked);

        List<com.haf.server.router.QueuedEnvelope> after = dao.fetchForRecipient(message.recipientId, 10);
        assertEquals(0, after.size());
    }

    @Test
    void delete_expired_removes_expired_envelopes() {
        EncryptedMessage expired = createValidMessage();
        expired.timestampEpochMs = System.currentTimeMillis() - Duration.ofHours(2).toMillis();
        expired.ttlSeconds = 3600; // Expired 1 hour ago

        int deleted = dao.deleteExpired();
        assertTrue(deleted >= 1);

        List<com.haf.server.router.QueuedEnvelope> remaining = dao.fetchForRecipient(expired.recipientId, 10);
        assertEquals(0, remaining.size());
    }

    @Test
    void fetch_for_recipient_returns_only_undelivered() {
        EncryptedMessage message1 = createValidMessage();
        message1.recipientId = "recipient-1";
        EncryptedMessage message2 = createValidMessage();
        message2.recipientId = "recipient-1";

        var env1 = dao.insert(message1);
        var env2 = dao.insert(message2);

        dao.markDelivered(List.of(env1.envelopeId()));

        List<com.haf.server.router.QueuedEnvelope> fetched = dao.fetchForRecipient("recipient-1", 10);
        assertEquals(1, fetched.size());
        assertEquals(env2.envelopeId(), fetched.get(0).envelopeId());
    }

    private EncryptedMessage createValidMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.version = MessageHeader.VERSION;
        message.algorithm = MessageHeader.ALGO_AEAD;
        message.senderId = "sender-" + System.currentTimeMillis();
        message.recipientId = "recipient-" + System.currentTimeMillis();
        message.timestampEpochMs = System.currentTimeMillis();
        message.ttlSeconds = (int) Duration.ofDays(1).toSeconds();
        message.ivB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]);
        message.wrappedKeyB64 = Base64.getEncoder().encodeToString(new byte[256]);
        message.ciphertextB64 = Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8));
        message.tagB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]);
        message.contentType = "text/plain";
        message.contentLength = 4;
        message.aadB64 = Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8));
        message.e2e = true;
        return message;
    }
}

