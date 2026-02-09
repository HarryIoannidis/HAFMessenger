package com.haf.server.db;

import com.haf.server.metrics.AuditLogger;
import com.haf.server.router.QueuedEnvelope;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;

public final class EnvelopeDAO {

    private final DataSource dataSource;
    private final AuditLogger auditLogger;
    private final Base64.Decoder decoder = Base64.getDecoder();
    private final Base64.Encoder encoder = Base64.getEncoder();

    /**
     * SQL query for inserting a new envelope into the database.
     */
    private static final String INSERT_SQL = """
        INSERT INTO message_envelopes (
            envelope_id,
            sender_id,
            recipient_id,
            encrypted_payload,
            wrapped_key,
            iv,
            auth_tag,
            aad_hash,
            content_type,
            content_length,
            timestamp,
            ttl,
            expires_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    /**
     * SQL query for fetching envelopes from the database.
     */
    private static final String FETCH_SQL = """
        SELECT envelope_id,
               sender_id,
               recipient_id,
               encrypted_payload,
               wrapped_key,
               iv,
               auth_tag,
               content_type,
               content_length,
               timestamp,
               ttl,
               created_at,
               expires_at
          FROM message_envelopes
         WHERE recipient_id = ?
           AND delivered = FALSE
           AND expires_at > NOW()
         ORDER BY timestamp ASC
         LIMIT ?
        """;

    /**
     * SQL query for fetching multiple envelopes by IDs.
     * Placeholder %s will be replaced with IN clause placeholders.
     */
    private static final String FETCH_BY_IDS_SQL = """
        SELECT envelope_id,
               sender_id,
               recipient_id,
               encrypted_payload,
               wrapped_key,
               iv,
               auth_tag,
               content_type,
               content_length,
               timestamp,
               ttl,
               created_at,
               expires_at
          FROM message_envelopes
         WHERE envelope_id IN (%s)
           AND delivered = FALSE
           AND expires_at > NOW()
        """;

    /**
     * Creates an EnvelopeDAO with a DataSource.
     *
     * @param dataSource the DataSource
     * @param auditLogger the AuditLogger
     */
    public EnvelopeDAO(DataSource dataSource, AuditLogger auditLogger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    /**
     * Inserts a new envelope into the database.
     *
     * @param message the message to insert
     * @return the inserted envelope
     */
    public QueuedEnvelope insert(EncryptedMessage message) {
        String envelopeId = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        long expiresAtMillis = message.timestampEpochMs + (message.ttlSeconds * 1000L);

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {

            ps.setString(1, envelopeId);
            ps.setString(2, message.senderId);
            ps.setString(3, message.recipientId);
            ps.setBytes(4, decodeB64("ciphertext", message.ciphertextB64));
            ps.setBytes(5, decodeB64("wrappedKey", message.wrappedKeyB64));
            ps.setBytes(6, decodeB64("iv", message.ivB64));
            ps.setBytes(7, decodeB64("tag", message.tagB64));
            ps.setString(8, computeAadHash(message));
            ps.setString(9, message.contentType);
            ps.setLong(10, message.contentLength);
            ps.setLong(11, message.timestampEpochMs);
            ps.setLong(12, message.ttlSeconds);
            ps.setTimestamp(13, new Timestamp(expiresAtMillis));

            ps.executeUpdate();

            return new QueuedEnvelope(envelopeId, message, createdAt, expiresAtMillis);
        } catch (SQLException ex) {
            auditLogger.logError("db_insert_envelope", null, message.senderId, ex);

            throw new IllegalStateException("Failed to store envelope", ex);
        }
    }

    /**
     * Fetches envelopes for a specific recipient.
     *
     * @param recipientId The ID of the recipient.
     * @param limit The maximum number of messages to fetch.
     * @return list of envelopes for the recipient.
     */
    public List<QueuedEnvelope> fetchForRecipient(String recipientId, int limit) {
        List<QueuedEnvelope> envelopes = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(FETCH_SQL)) {

            ps.setString(1, recipientId);
            ps.setInt(2, limit);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                EncryptedMessage message = hydrateMessage(rs);

                envelopes.add(new QueuedEnvelope(
                    rs.getString("envelope_id"),
                    message,
                    rs.getTimestamp("created_at").getTime(),
                    rs.getTimestamp("expires_at").getTime()
                ));
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_mailbox", null, recipientId, ex);

            throw new IllegalStateException("Failed to fetch mailbox", ex);
        }

        return envelopes;
    }

    /**
     * Fetches multiple envelopes by their IDs.
     *
     * @param envelopeIds collection of envelope IDs to fetch.
     * @return map of envelopeId -> QueuedEnvelope for found envelopes.
     */
    public Map<String, QueuedEnvelope> fetchByIds(Collection<String> envelopeIds) {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = envelopeIds.stream()
                .map(id -> "?")
                .reduce((a, b) -> a + "," + b)
                .orElse("?");
        String sql = String.format(FETCH_BY_IDS_SQL, placeholders);

        Map<String, QueuedEnvelope> envelopes = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            int index = 1;
            for (String envelopeId : envelopeIds) {
                ps.setString(index++, envelopeId);
            }

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                EncryptedMessage message = hydrateMessage(rs);
                QueuedEnvelope envelope = new QueuedEnvelope(
                        rs.getString("envelope_id"),
                        message,
                        rs.getTimestamp("created_at").getTime(),
                        rs.getTimestamp("expires_at").getTime()
                );
                envelopes.put(envelope.envelopeId(), envelope);
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_by_ids", null, null, ex);
            throw new IllegalStateException("Failed to fetch envelopes by IDs", ex);
        }

        return envelopes;
    }

    /**
     * Marks a list of envelopes as delivered.
     *
     * @param envelopeIds the list of envelope IDs to mark as delivered.
     * @return true if the operation was successful, false otherwise.
     */
    public boolean markDelivered(List<String> envelopeIds) {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return false;
        }

        String placeholders = envelopeIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("?");
        String sql = "UPDATE message_envelopes SET delivered = TRUE WHERE envelope_id IN (" + placeholders + ")";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {

            for (int i = 0; i < envelopeIds.size(); i++) {
                ps.setString(i + 1, envelopeIds.get(i));
            }

            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            auditLogger.logError("db_mark_delivered", null, null, ex);

            throw new IllegalStateException("Failed to mark delivered", ex);
        }
    }

    /**
     * Deletes expired envelopes.
     *
     * @return the number of deleted envelopes
     */
    public int deleteExpired() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            return statement.executeUpdate("DELETE FROM message_envelopes WHERE expires_at < NOW()");
        } catch (SQLException ex) {
            auditLogger.logError("db_delete_expired", null, "system", ex);

            throw new IllegalStateException("Failed to delete expired envelopes", ex);
        }
    }

    /**
     * Computes the Authentication and Authorization (AAD) hash for an encrypted message.
     *
     * @param message the encrypted message
     * @return the AAD hash
     */
    private String computeAadHash(EncryptedMessage message) {
        String aad = String.join("|",
            message.version,
            message.algorithm,
            message.senderId,
            message.recipientId,
            String.valueOf(message.timestampEpochMs),
            String.valueOf(message.ttlSeconds),
            message.contentType,
            String.valueOf(message.contentLength)
        );
        try {
            MessageDigest digest = MessageDigest.getInstance(com.haf.shared.constants.CryptoConstants.OAEP_HASH);

            return HexFormat.of().formatHex(digest.digest(aad.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(com.haf.shared.constants.CryptoConstants.OAEP_HASH + " not available", e);
        }
    }

    /**
     * Decodes a Base64-encoded string.
     *
     * @param field the field name
     * @param value the Base64-encoded value
     * @return the decoded bytes
     */
    private byte[] decodeB64(String field, String value) {
        try {
            return decoder.decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid Base64 field: " + field, e);
        }
    }

    /**
     * Hydrate an EncryptedMessage from a ResultSet.
     *
     * @param rs the ResultSet
     * @return the EncryptedMessage
     * @throws SQLException if the ResultSet cannot be read
     */
    private EncryptedMessage hydrateMessage(ResultSet rs) throws SQLException {
        EncryptedMessage message = new EncryptedMessage();
        message.version = MessageHeader.VERSION;
        message.algorithm = MessageHeader.ALGO_AEAD;
        message.senderId = rs.getString("sender_id");
        message.recipientId = rs.getString("recipient_id");
        message.ciphertextB64 = encoder.encodeToString(rs.getBytes("encrypted_payload"));
        message.wrappedKeyB64 = encoder.encodeToString(rs.getBytes("wrapped_key"));
        message.ivB64 = encoder.encodeToString(rs.getBytes("iv"));
        message.tagB64 = encoder.encodeToString(rs.getBytes("auth_tag"));
        message.contentType = rs.getString("content_type");
        message.contentLength = rs.getLong("content_length");
        message.timestampEpochMs = rs.getLong("timestamp");
        message.ttlSeconds = rs.getLong("ttl");
        return message;
    }
}

