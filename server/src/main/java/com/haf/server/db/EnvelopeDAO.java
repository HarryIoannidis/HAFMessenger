package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
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
     * SQL query template for marking multiple envelopes as delivered.
     * Placeholder %s will be replaced with IN clause placeholders.
     */
    private static final String MARK_DELIVERED_BY_IDS_SQL = """
            UPDATE message_envelopes
               SET delivered = TRUE
             WHERE envelope_id IN (%s)
            """;

    /**
     * SQL query for deleting expired envelopes.
     */
    private static final String DELETE_EXPIRED_SQL = """
            DELETE FROM message_envelopes
             WHERE expires_at < NOW()
            """;

    /**
     * Creates an EnvelopeDAO with a DataSource.
     *
     * @param dataSource  the DataSource
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
        long expiresAtMillis = message.getTimestampEpochMs() + (message.getTtlSeconds() * 1000L);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {

            ps.setString(1, envelopeId);
            ps.setString(2, message.getSenderId());
            ps.setString(3, message.getRecipientId());
            ps.setBytes(4, decodeB64("ciphertext", message.getCiphertextB64()));
            ps.setBytes(5, decodeB64("wrappedKey", message.getEphemeralPublicB64()));
            ps.setBytes(6, decodeB64("iv", message.getIvB64()));
            ps.setBytes(7, decodeB64("tag", message.getTagB64()));
            ps.setString(8, computeAadHash(message));
            ps.setString(9, message.getContentType());
            ps.setLong(10, message.getContentLength());
            ps.setLong(11, message.getTimestampEpochMs());
            ps.setLong(12, message.getTtlSeconds());
            ps.setTimestamp(13, new Timestamp(expiresAtMillis));

            ps.executeUpdate();

            return new QueuedEnvelope(envelopeId, message, createdAt, expiresAtMillis);
        } catch (SQLException ex) {
            auditLogger.logError("db_insert_envelope", null, message.getSenderId(), ex);

            throw new DatabaseOperationException("Failed to store envelope", ex);
        }
    }

    /**
     * Fetches envelopes for a specific recipient.
     *
     * @param recipientId The ID of the recipient.
     * @param limit       The maximum number of messages to fetch.
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
                        rs.getTimestamp("expires_at").getTime()));
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_mailbox", null, recipientId, ex);

            throw new DatabaseOperationException("Failed to fetch mailbox", ex);
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
                        rs.getTimestamp("expires_at").getTime());
                envelopes.put(envelope.envelopeId(), envelope);
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_by_ids", null, null, ex);
            throw new DatabaseOperationException("Failed to fetch envelopes by IDs", ex);
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
        String sql = String.format(MARK_DELIVERED_BY_IDS_SQL, placeholders);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {

            for (int i = 0; i < envelopeIds.size(); i++) {
                ps.setString(i + 1, envelopeIds.get(i));
            }

            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            auditLogger.logError("db_mark_delivered", null, null, ex);

            throw new DatabaseOperationException("Failed to mark delivered", ex);
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

            return statement.executeUpdate(DELETE_EXPIRED_SQL);
        } catch (SQLException ex) {
            auditLogger.logError("db_delete_expired", null, "system", ex);

            throw new DatabaseOperationException("Failed to delete expired envelopes", ex);
        }
    }

    /**
     * Computes the Authentication and Authorization (AAD) hash for an encrypted
     * message.
     *
     * @param message the encrypted message
     * @return the AAD hash
     */
    private String computeAadHash(EncryptedMessage message) {
        String aad = String.join("|",
                message.getVersion(),
                message.getAlgorithm(),
                message.getSenderId(),
                message.getRecipientId(),
                String.valueOf(message.getTimestampEpochMs()),
                String.valueOf(message.getTtlSeconds()),
                message.getContentType(),
                String.valueOf(message.getContentLength()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            return HexFormat.of().formatHex(digest.digest(aad.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new DatabaseOperationException("SHA-256 not available", e);
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
            throw new DatabaseOperationException("Invalid Base64 field: " + field, e);
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
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId(rs.getString("sender_id"));
        message.setRecipientId(rs.getString("recipient_id"));
        message.setCiphertextB64(encoder.encodeToString(rs.getBytes("encrypted_payload")));
        message.setEphemeralPublicB64(encoder.encodeToString(rs.getBytes("wrapped_key")));
        message.setIvB64(encoder.encodeToString(rs.getBytes("iv")));
        message.setTagB64(encoder.encodeToString(rs.getBytes("auth_tag")));
        message.setContentType(rs.getString("content_type"));
        message.setContentLength(rs.getLong("content_length"));
        message.setTimestampEpochMs(rs.getLong("timestamp"));
        message.setTtlSeconds(rs.getLong("ttl"));
        return message;
    }
}
