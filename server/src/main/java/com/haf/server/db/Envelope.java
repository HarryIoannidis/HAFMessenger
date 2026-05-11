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

/**
 * Persists and retrieves encrypted message envelopes for mailbox delivery.
 */
public final class Envelope {

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
                signature,
                aad_hash,
                signature_algorithm,
                sender_signing_key_fingerprint,
                content_type,
                content_length,
                timestamp,
                ttl,
                expires_at,
                client_message_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                   signature,
                   signature_algorithm,
                   sender_signing_key_fingerprint,
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

    private static final String FETCH_BY_CLIENT_MESSAGE_ID_SQL = """
            SELECT envelope_id,
                   sender_id,
                   recipient_id,
                   encrypted_payload,
                   wrapped_key,
                   iv,
                   auth_tag,
                   signature,
                   signature_algorithm,
                   sender_signing_key_fingerprint,
                   content_type,
                   content_length,
                   timestamp,
                   ttl,
                   created_at,
                   expires_at
              FROM message_envelopes
             WHERE sender_id = ?
               AND client_message_id = ?
               AND expires_at > NOW()
             LIMIT 1
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
                   signature,
                   signature_algorithm,
                   sender_signing_key_fingerprint,
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

    private static final String FETCH_BY_IDS_ANY_STATUS_SQL = """
            SELECT envelope_id,
                   sender_id,
                   recipient_id,
                   encrypted_payload,
                   wrapped_key,
                   iv,
                   auth_tag,
                   signature,
                   signature_algorithm,
                   sender_signing_key_fingerprint,
                   content_type,
                   content_length,
                   timestamp,
                   ttl,
                   created_at,
                   expires_at
              FROM message_envelopes
            WHERE envelope_id IN (%s)
              AND expires_at > NOW()
            """;

    /**
     * SQL query template for marking multiple envelopes as delivered.
     * Placeholder %s will be replaced with IN clause placeholders.
     */
    private static final String MARK_DELIVERED_BY_IDS_SQL = """
            UPDATE message_envelopes
               SET delivered = TRUE,
                   delivered_at = COALESCE(delivered_at, CURRENT_TIMESTAMP)
             WHERE envelope_id IN (%s)
            """;

    private static final String MARK_READ_BY_IDS_SQL = """
            UPDATE message_envelopes
               SET delivered = TRUE,
                   delivered_at = COALESCE(delivered_at, CURRENT_TIMESTAMP),
                   read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
             WHERE envelope_id IN (%s)
            """;

    private static final String FETCH_RECEIPTS_FOR_SENDER_SQL = """
            SELECT envelope_id,
                   recipient_id,
                   CASE
                       WHEN read_at IS NOT NULL THEN 'READ'
                       ELSE 'DELIVERED'
                   END AS receipt_state
              FROM message_envelopes
             WHERE sender_id = ?
               AND expires_at > NOW()
               AND (read_at IS NOT NULL OR delivered_at IS NOT NULL OR delivered = TRUE)
             ORDER BY timestamp ASC
             LIMIT ?
            """;

    /**
     * SQL query for deleting expired envelopes.
     */
    private static final String DELETE_EXPIRED_SQL = """
            DELETE FROM message_envelopes
             WHERE expires_at < NOW()
            """;

    /**
     * Creates an Envelope with a DataSource.
     *
     * @param dataSource  the DataSource
     * @param auditLogger the AuditLogger
     */
    public Envelope(DataSource dataSource, AuditLogger auditLogger) {
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
        return insertInternal(message, null);
    }

    /**
     * Inserts a message with client-supplied idempotency metadata.
     *
     * @param message         message to insert
     * @param clientMessageId per-sender idempotency key
     * @return inserted or previously inserted envelope and duplicate flag
     */
    public InsertResult insertIdempotent(EncryptedMessage message, String clientMessageId) {
        String normalizedClientMessageId = normalizeClientMessageId(clientMessageId);
        if (normalizedClientMessageId == null) {
            return new InsertResult(insert(message), false);
        }
        try {
            return new InsertResult(insertInternal(message, normalizedClientMessageId), false);
        } catch (DatabaseOperationException ex) {
            if (!isDuplicateKey(ex.getCause())) {
                throw ex;
            }
            Optional<QueuedEnvelope> existing = fetchByClientMessageId(message.getSenderId(), normalizedClientMessageId);
            if (existing.isPresent()) {
                return new InsertResult(existing.get(), true);
            }
            throw ex;
        }
    }

    /**
     * Internal method to insert an envelope.
     *
     * @param message         the message to insert
     * @param clientMessageId the client message ID
     * @return the queued envelope
     */
    private QueuedEnvelope insertInternal(EncryptedMessage message, String clientMessageId) {
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
            ps.setBytes(8, decodeB64("signature", message.getSignatureB64()));
            ps.setString(9, computeAadHash(message));
            ps.setString(10, message.getSignatureAlgorithm());
            ps.setString(11, message.getSenderSigningKeyFingerprint());
            ps.setString(12, message.getContentType());
            ps.setLong(13, message.getContentLength());
            ps.setLong(14, message.getTimestampEpochMs());
            ps.setLong(15, message.getTtlSeconds());
            ps.setTimestamp(16, new Timestamp(expiresAtMillis));
            ps.setString(17, clientMessageId);

            ps.executeUpdate();

            return new QueuedEnvelope(envelopeId, message, createdAt, expiresAtMillis);
        } catch (SQLException ex) {
            if (isDuplicateKey(ex)) {
                throw new DatabaseOperationException("Duplicate envelope idempotency key", ex);
            }
            auditLogger.logError("db_insert_envelope", null, message.getSenderId(), ex);

            throw new DatabaseOperationException("Failed to store envelope", ex);
        }
    }

    /**
     * Fetches an envelope by client message ID.
     *
     * @param senderId        the sender ID
     * @param clientMessageId the client message ID
     * @return the optional queued envelope
     */
    private Optional<QueuedEnvelope> fetchByClientMessageId(String senderId, String clientMessageId) {
        if (senderId == null || senderId.isBlank() || clientMessageId == null || clientMessageId.isBlank()) {
            return Optional.empty();
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(FETCH_BY_CLIENT_MESSAGE_ID_SQL)) {

            ps.setString(1, senderId);
            ps.setString(2, clientMessageId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                EncryptedMessage message = hydrateMessage(rs);
                return Optional.of(new QueuedEnvelope(
                        rs.getString("envelope_id"),
                        message,
                        rs.getTimestamp("created_at").getTime(),
                        rs.getTimestamp("expires_at").getTime()));
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_by_client_message_id", null, senderId, ex);
            throw new DatabaseOperationException("Failed to fetch envelope by client message id", ex);
        }
        return Optional.empty();
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
        return fetchByIdsWithSql(envelopeIds, FETCH_BY_IDS_SQL);
    }

    /**
     * Fetches envelopes by ID regardless of delivered state.
     *
     * @param envelopeIds collection of envelope IDs
     * @return map of envelopeId -> QueuedEnvelope for found envelopes
     */
    public Map<String, QueuedEnvelope> fetchByIdsIncludingDelivered(Collection<String> envelopeIds) {
        return fetchByIdsWithSql(envelopeIds, FETCH_BY_IDS_ANY_STATUS_SQL);
    }

    /**
     * Internal method to fetch envelopes by IDs using a specific SQL template.
     *
     * @param envelopeIds the envelope IDs
     * @param sqlTemplate the SQL template to use
     * @return map of envelope ID to queued envelope
     */
    private Map<String, QueuedEnvelope> fetchByIdsWithSql(Collection<String> envelopeIds, String sqlTemplate) {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = envelopeIds.stream()
                .map(id -> "?")
                .reduce((a, b) -> a + "," + b)
                .orElse("?");
        String sql = String.format(sqlTemplate, placeholders);

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
        return updateReceiptState(envelopeIds, MARK_DELIVERED_BY_IDS_SQL, "db_mark_delivered",
                "Failed to mark delivered");
    }

    /**
     * Marks a list of envelopes as read. Read receipts imply delivery, so this
     * also sets the delivered flag and delivery timestamp if needed.
     *
     * @param envelopeIds the list of envelope IDs to mark as read.
     * @return true if at least one row was updated, false otherwise.
     */
    public boolean markRead(List<String> envelopeIds) {
        return updateReceiptState(envelopeIds, MARK_READ_BY_IDS_SQL, "db_mark_read", "Failed to mark read");
    }

    /**
     * Fetches persisted receipt state for messages originally sent by a user.
     * Only minimal metadata needed to replay receipt events is returned.
     *
     * @param senderId sender whose outbound receipt state should be replayed
     * @param limit    maximum receipt rows to return
     * @return ordered receipt metadata
     */
    public List<ReceiptRecord> fetchReceiptsForSender(String senderId, int limit) {
        if (senderId == null || senderId.isBlank() || limit <= 0) {
            return List.of();
        }
        List<ReceiptRecord> receipts = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(FETCH_RECEIPTS_FOR_SENDER_SQL)) {

            ps.setString(1, senderId);
            ps.setInt(2, limit);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                receipts.add(new ReceiptRecord(
                        rs.getString("envelope_id"),
                        rs.getString("recipient_id"),
                        ReceiptState.valueOf(rs.getString("receipt_state"))));
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_fetch_receipts_for_sender", null, senderId, ex);
            throw new DatabaseOperationException("Failed to fetch receipts for sender", ex);
        }
        return receipts;
    }

    private boolean updateReceiptState(
            List<String> envelopeIds,
            String sqlTemplate,
            String auditAction,
            String failureMessage) {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return false;
        }

        String placeholders = envelopeIds.stream().map(id -> "?").reduce((a, b) -> a + "," + b).orElse("?");
        String sql = String.format(sqlTemplate, placeholders);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {

            for (int i = 0; i < envelopeIds.size(); i++) {
                ps.setString(i + 1, envelopeIds.get(i));
            }

            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            auditLogger.logError(auditAction, null, null, ex);
            throw new DatabaseOperationException(failureMessage, ex);
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
     * Normalizes a client message ID.
     *
     * @param clientMessageId the raw client message ID
     * @return the normalized client message ID
     */
    private static String normalizeClientMessageId(String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return null;
        }
        String trimmed = clientMessageId.trim();
        if (trimmed.length() > 128) {
            throw new DatabaseOperationException(
                    "Invalid clientMessageId",
                    new IllegalArgumentException("clientMessageId too long"));
        }
        return trimmed;
    }

    /**
     * Checks if the given error represents a duplicate key exception.
     *
     * @param error the throwable error
     * @return true if it is a duplicate key exception
     */
    private static boolean isDuplicateKey(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLException sqlException &&
                    ("23000".equals(sqlException.getSQLState()) || sqlException.getErrorCode() == 1062)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
        message.setSignatureB64(encoder.encodeToString(rs.getBytes("signature")));
        message.setSignatureAlgorithm(rs.getString("signature_algorithm"));
        message.setSenderSigningKeyFingerprint(rs.getString("sender_signing_key_fingerprint"));
        message.setContentType(rs.getString("content_type"));
        message.setContentLength(rs.getLong("content_length"));
        message.setTimestampEpochMs(rs.getLong("timestamp"));
        message.setTtlSeconds(rs.getLong("ttl"));
        return message;
    }

    /**
     * Idempotent insert outcome.
     *
     * @param envelope  stored envelope
     * @param duplicate true when an existing envelope was returned
     */
    public record InsertResult(QueuedEnvelope envelope, boolean duplicate) {
    }

    /**
     * Strongest persisted receipt state for an outbound envelope.
     */
    public enum ReceiptState {
        DELIVERED,
        READ
    }

    /**
     * Minimal persisted receipt metadata for replaying sender notifications.
     *
     * @param envelopeId  envelope whose receipt state changed
     * @param recipientId original message recipient who emitted the receipt
     * @param state       strongest known receipt state
     */
    public record ReceiptRecord(String envelopeId, String recipientId, ReceiptState state) {
    }
}
