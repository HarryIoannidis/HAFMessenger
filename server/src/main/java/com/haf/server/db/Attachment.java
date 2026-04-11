package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * DAO for encrypted chat attachments and their chunks.
 */
public final class Attachment {

    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_BOUND = "BOUND";

    private static final String COL_SENDER_ID = "sender_id";
    private static final String COL_RECIPIENT_ID = "recipient_id";
    private static final String COL_STATUS = "status";
    private static final String COL_EXPIRES_AT = "expires_at";
    private static final String COL_EXPECTED_SIZE = "expected_size";
    private static final String COL_EXPECTED_CHUNKS = "expected_chunks";
    private static final String COL_CHUNK_DATA = "chunk_data";
    private static final String COL_CONTENT_TYPE = "content_type";

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private static final String PARAM_DATA_SOURCE = "dataSource";
    private static final String PARAM_SENDER_ID = "senderId";
    private static final String PARAM_RECIPIENT_ID = "recipientId";
    private static final String PARAM_CONTENT_TYPE = "contentType";
    private static final String PARAM_CALLER_ID = "callerId";
    private static final String PARAM_ATTACHMENT_ID = "attachmentId";
    private static final String PARAM_CHUNK_DATA = "chunkData";
    private static final String PARAM_ENVELOPE_ID = "envelopeId";
    private static final String PARAM_CALLER_RECIPIENT_ID = "callerRecipientId";

    /**
     * SQL query for inserting attachment upload metadata at init time.
     */
    private static final String INSERT_UPLOAD_SQL = """
            INSERT INTO message_attachments (
                attachment_id,
                sender_id,
                recipient_id,
                content_type,
                expected_size,
                expected_chunks,
                status,
                expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'INIT', ?)
            """;

    /**
     * SQL query for loading and locking attachment upload metadata.
     */
    private static final String SELECT_UPLOAD_FOR_UPDATE_SQL = """
            SELECT sender_id, recipient_id, expected_size, expected_chunks, status, expires_at
            FROM message_attachments
            WHERE attachment_id = ?
            FOR UPDATE
            """;

    /**
     * SQL query for selecting one chunk by attachment id and chunk index.
     */
    private static final String SELECT_CHUNK_SQL = """
            SELECT chunk_data
            FROM message_attachment_chunks
            WHERE attachment_id = ?
              AND chunk_index = ?
            """;

    /**
     * SQL query for inserting one attachment chunk.
     */
    private static final String INSERT_CHUNK_SQL = """
            INSERT INTO message_attachment_chunks (
                attachment_id,
                chunk_index,
                chunk_data,
                chunk_size
            ) VALUES (?, ?, ?, ?)
            """;

    /**
     * SQL query for moving attachment status from INIT to UPLOADING.
     */
    private static final String MARK_UPLOADING_SQL = """
            UPDATE message_attachments
            SET status = 'UPLOADING'
            WHERE attachment_id = ?
              AND status = 'INIT'
            """;

    /**
     * SQL query for selecting chunk count and total size aggregates.
     */
    private static final String SELECT_CHUNK_STATS_SQL = """
            SELECT COUNT(*) AS c, COALESCE(SUM(chunk_size), 0) AS s
            FROM message_attachment_chunks
            WHERE attachment_id = ?
            """;

    /**
     * SQL query for marking an attachment upload as COMPLETE.
     */
    private static final String MARK_COMPLETE_SQL = """
            UPDATE message_attachments
            SET status = 'COMPLETE'
            WHERE attachment_id = ?
              AND status IN ('INIT', 'UPLOADING')
            """;

    /**
     * SQL query for loading bind prerequisites by joining attachment and envelope
     * rows.
     */
    private static final String SELECT_BIND_JOIN_SQL = """
            SELECT a.sender_id, a.recipient_id, a.status, e.expires_at
            FROM message_attachments a
            JOIN message_envelopes e
              ON e.envelope_id = ?
             AND e.sender_id = a.sender_id
             AND e.recipient_id = a.recipient_id
            WHERE a.attachment_id = ?
            FOR UPDATE
            """;

    /**
     * SQL query for binding an attachment upload to an envelope id.
     */
    private static final String UPDATE_BIND_SQL = """
            UPDATE message_attachments
            SET status = 'BOUND',
                envelope_id = ?,
                bound_at = NOW(),
                expires_at = ?
            WHERE attachment_id = ?
            """;

    /**
     * SQL query for selecting downloadable attachment metadata for a recipient.
     */
    private static final String SELECT_DOWNLOAD_SQL = """
            SELECT attachment_id, sender_id, recipient_id, content_type, expected_size
            FROM message_attachments
            WHERE attachment_id = ?
              AND recipient_id = ?
              AND status = 'BOUND'
              AND expires_at > NOW()
            """;

    /**
     * SQL query for selecting all chunks for an attachment in chunk order.
     */
    private static final String SELECT_ALL_CHUNKS_SQL = """
            SELECT chunk_data
            FROM message_attachment_chunks
            WHERE attachment_id = ?
            ORDER BY chunk_index ASC
            """;

    /**
     * SQL query for deleting expired attachment uploads.
     */
    private static final String DELETE_EXPIRED_UPLOADS_SQL = "DELETE FROM message_attachments WHERE expires_at < NOW()";

    private final DataSource dataSource;

    /**
     * Creates an attachment DAO.
     *
     * @param dataSource JDBC data source used for attachment persistence
     * @throws NullPointerException when {@code dataSource} is {@code null}
     */
    public Attachment(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, PARAM_DATA_SOURCE);
    }

    /**
     * Initializes metadata for a new chunked attachment upload.
     *
     * @param senderId          sender user id
     * @param recipientId       recipient user id
     * @param contentType       payload content type
     * @param expectedSize      expected total encrypted size in bytes
     * @param expectedChunks    expected chunk count
     * @param unboundTtlSeconds TTL before upload expires when not yet bound to an
     *                          envelope
     * @return upload init result containing attachment id and expiry timestamp
     * @throws DatabaseOperationException when database write fails
     */
    public UploadInitResult initUpload(String senderId,
            String recipientId,
            String contentType,
            long expectedSize,
            int expectedChunks,
            long unboundTtlSeconds) {
        Objects.requireNonNull(senderId, PARAM_SENDER_ID);
        Objects.requireNonNull(recipientId, PARAM_RECIPIENT_ID);
        Objects.requireNonNull(contentType, PARAM_CONTENT_TYPE);

        String attachmentId = UUID.randomUUID().toString();
        long expiresAtEpochMs = System.currentTimeMillis() + (unboundTtlSeconds * 1000L);

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_UPLOAD_SQL)) {
            ps.setString(1, attachmentId);
            ps.setString(2, senderId);
            ps.setString(3, recipientId);
            ps.setString(4, contentType);
            ps.setLong(5, expectedSize);
            ps.setInt(6, expectedChunks);
            ps.setTimestamp(7, new Timestamp(expiresAtEpochMs));
            ps.executeUpdate();
            return new UploadInitResult(attachmentId, expiresAtEpochMs);
        } catch (SQLException ex) {
            throw new DatabaseOperationException("Failed to initialize attachment upload", ex);
        }
    }

    /**
     * Stores one attachment chunk inside a transaction with ownership and state
     * checks.
     *
     * @param callerId     authenticated caller id
     * @param attachmentId upload attachment id
     * @param chunkIndex   zero-based chunk index
     * @param chunkData    raw encrypted chunk bytes
     * @return chunk store result describing whether a new chunk was stored
     * @throws DatabaseOperationException when database operation fails
     */
    public ChunkStoreResult storeChunk(String callerId,
            String attachmentId,
            int chunkIndex,
            byte[] chunkData) {
        Objects.requireNonNull(callerId, PARAM_CALLER_ID);
        Objects.requireNonNull(attachmentId, PARAM_ATTACHMENT_ID);
        Objects.requireNonNull(chunkData, PARAM_CHUNK_DATA);

        return inTransaction(
                "Failed to store attachment chunk",
                conn -> storeChunkInTransaction(conn, callerId, attachmentId, chunkIndex, chunkData));
    }

    /**
     * Finalizes an attachment upload after validating chunk count and accumulated
     * size.
     *
     * @param callerId       authenticated caller id
     * @param attachmentId   upload attachment id
     * @param expectedChunks expected chunk count from client
     * @param expectedSize   expected encrypted size from client
     * @return completion result with received counters and resulting upload status
     * @throws DatabaseOperationException when database operation fails
     */
    public CompletionResult completeUpload(String callerId,
            String attachmentId,
            int expectedChunks,
            long expectedSize) {
        Objects.requireNonNull(callerId, PARAM_CALLER_ID);
        Objects.requireNonNull(attachmentId, PARAM_ATTACHMENT_ID);

        return inTransaction(
                "Failed to complete attachment upload",
                conn -> completeUploadInTransaction(conn, callerId, attachmentId, expectedChunks, expectedSize));
    }

    /**
     * Binds a completed attachment upload to a message envelope.
     *
     * @param callerId     authenticated caller id
     * @param attachmentId upload attachment id
     * @param envelopeId   envelope id that references the attachment
     * @return bind result with attachment/envelope ids and final expiry
     * @throws DatabaseOperationException when database operation fails
     */
    public BindResult bindUploadToEnvelope(String callerId,
            String attachmentId,
            String envelopeId) {
        Objects.requireNonNull(callerId, PARAM_CALLER_ID);
        Objects.requireNonNull(attachmentId, PARAM_ATTACHMENT_ID);
        Objects.requireNonNull(envelopeId, PARAM_ENVELOPE_ID);

        return inTransaction(
                "Failed to bind attachment upload",
                conn -> bindUploadToEnvelopeInTransaction(conn, callerId, attachmentId, envelopeId));
    }

    /**
     * Loads a bound attachment for the intended recipient and rebuilds its
     * encrypted blob.
     *
     * @param callerRecipientId recipient id of authenticated caller
     * @param attachmentId      attachment id to fetch
     * @return attachment download blob with metadata and encrypted payload bytes
     * @throws DatabaseOperationException when database access fails
     */
    public DownloadBlob loadForRecipient(String callerRecipientId,
            String attachmentId) {
        Objects.requireNonNull(callerRecipientId, PARAM_CALLER_RECIPIENT_ID);
        Objects.requireNonNull(attachmentId, PARAM_ATTACHMENT_ID);

        try (Connection conn = dataSource.getConnection()) {
            DownloadMeta downloadMeta = loadDownloadMeta(conn, attachmentId, callerRecipientId);
            ChunkBlob chunkBlob = loadAttachmentBlob(conn, attachmentId);

            if (chunkBlob.blob().length != downloadMeta.expectedSize()) {
                throw new IllegalStateException("Attachment upload is incomplete");
            }

            return new DownloadBlob(
                    attachmentId,
                    downloadMeta.senderId(),
                    downloadMeta.recipientId(),
                    downloadMeta.contentType(),
                    downloadMeta.expectedSize(),
                    chunkBlob.chunkCount(),
                    chunkBlob.blob());
        } catch (SQLException ex) {
            throw new DatabaseOperationException("Failed to load attachment", ex);
        }
    }

    /**
     * Deletes expired uploads and their dependent rows via DB constraints.
     *
     * @return number of deleted upload rows
     * @throws DatabaseOperationException when cleanup query fails
     */
    public int deleteExpiredUploads() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(DELETE_EXPIRED_UPLOADS_SQL)) {
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseOperationException("Failed to cleanup expired attachments", ex);
        }
    }

    /**
     * Transactional chunk-store implementation with authorization and lifecycle
     * checks.
     *
     * @param conn         active SQL connection in transaction mode
     * @param callerId     authenticated caller id
     * @param attachmentId upload attachment id
     * @param chunkIndex   zero-based chunk index
     * @param chunkData    encrypted chunk bytes
     * @return chunk-store outcome
     * @throws SQLException when SQL access fails
     */
    private ChunkStoreResult storeChunkInTransaction(Connection conn,
            String callerId,
            String attachmentId,
            int chunkIndex,
            byte[] chunkData) throws SQLException {
        UploadMeta meta = loadUploadForUpdate(conn, attachmentId);
        authorizeSender(callerId, meta.senderId());
        ensureNotExpired(meta.expiresAtEpochMs());
        if (STATUS_COMPLETE.equals(meta.status()) || STATUS_BOUND.equals(meta.status())) {
            throw new IllegalStateException("Attachment upload is already finalized");
        }
        if (chunkIndex < 0 || chunkIndex >= meta.expectedChunks()) {
            throw new IllegalArgumentException("Invalid chunk index");
        }

        byte[] existingChunk = loadChunk(conn, attachmentId, chunkIndex);
        if (existingChunk.length > 0) {
            if (!Arrays.equals(existingChunk, chunkData)) {
                throw new IllegalStateException("Chunk already uploaded with different data");
            }
            return new ChunkStoreResult(chunkIndex, false);
        }

        insertChunk(conn, attachmentId, chunkIndex, chunkData);
        markUploadAsUploading(conn, attachmentId);
        return new ChunkStoreResult(chunkIndex, true);
    }

    /**
     * Transactional upload-completion implementation validating metadata and
     * accumulated chunks.
     *
     * @param conn           active SQL connection in transaction mode
     * @param callerId       authenticated caller id
     * @param attachmentId   upload attachment id
     * @param expectedChunks expected chunk count provided by caller
     * @param expectedSize   expected encrypted byte count provided by caller
     * @return completion result with current chunk/byte counters and status
     * @throws SQLException when SQL access fails
     */
    private CompletionResult completeUploadInTransaction(Connection conn,
            String callerId,
            String attachmentId,
            int expectedChunks,
            long expectedSize) throws SQLException {
        UploadMeta meta = loadUploadForUpdate(conn, attachmentId);
        authorizeSender(callerId, meta.senderId());
        ensureNotExpired(meta.expiresAtEpochMs());

        if (meta.expectedChunks() != expectedChunks || meta.expectedSize() != expectedSize) {
            throw new IllegalArgumentException("Upload metadata does not match init request");
        }

        ChunkStats stats = loadChunkStats(conn, attachmentId);
        if (stats.chunkCount() < expectedChunks) {
            throw new IllegalStateException("Missing attachment chunks");
        }
        if (stats.totalBytes() != expectedSize) {
            throw new IllegalStateException("Uploaded attachment size mismatch");
        }

        if (!STATUS_BOUND.equals(meta.status())) {
            markUploadComplete(conn, attachmentId);
        }

        String status = STATUS_BOUND.equals(meta.status()) ? STATUS_BOUND : STATUS_COMPLETE;
        return new CompletionResult(stats.chunkCount(), stats.totalBytes(), status);
    }

    /**
     * Transactional bind implementation linking completed attachment to envelope
     * metadata.
     *
     * @param conn         active SQL connection in transaction mode
     * @param callerId     authenticated caller id
     * @param attachmentId attachment id being bound
     * @param envelopeId   envelope id to bind to
     * @return bind result containing effective expiry inherited from envelope
     * @throws SQLException when SQL access fails
     */
    private BindResult bindUploadToEnvelopeInTransaction(Connection conn,
            String callerId,
            String attachmentId,
            String envelopeId) throws SQLException {
        BindLookup bindLookup = loadBindLookup(conn, envelopeId, attachmentId);

        authorizeSender(callerId, bindLookup.senderId());
        if (!STATUS_COMPLETE.equals(bindLookup.status()) && !STATUS_BOUND.equals(bindLookup.status())) {
            throw new IllegalStateException("Attachment must be completed before binding");
        }

        updateBind(conn, envelopeId, attachmentId, bindLookup.envelopeExpiryEpochMs());
        return new BindResult(attachmentId, envelopeId, bindLookup.envelopeExpiryEpochMs());
    }

    /**
     * Inserts one chunk row into the chunk table.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @param chunkIndex   chunk index
     * @param chunkData    chunk bytes
     * @throws SQLException when insert fails
     */
    private void insertChunk(Connection conn, String attachmentId, int chunkIndex, byte[] chunkData)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(INSERT_CHUNK_SQL)) {
            ps.setString(1, attachmentId);
            ps.setInt(2, chunkIndex);
            ps.setBytes(3, chunkData);
            ps.setInt(4, chunkData.length);
            ps.executeUpdate();
        }
    }

    /**
     * Moves upload status from INIT to UPLOADING when first chunks arrive.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @throws SQLException when update fails
     */
    private void markUploadAsUploading(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(MARK_UPLOADING_SQL)) {
            ps.setString(1, attachmentId);
            ps.executeUpdate();
        }
    }

    /**
     * Marks an upload as COMPLETE.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @throws SQLException when update fails
     */
    private void markUploadComplete(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(MARK_COMPLETE_SQL)) {
            ps.setString(1, attachmentId);
            ps.executeUpdate();
        }
    }

    /**
     * Loads sender/status/expiry data needed to bind an attachment to an envelope.
     *
     * @param conn         active SQL connection
     * @param envelopeId   envelope id
     * @param attachmentId attachment id
     * @return bind lookup row
     * @throws SQLException when query fails
     */
    private BindLookup loadBindLookup(Connection conn, String envelopeId, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_BIND_JOIN_SQL);
                ResultSet rs = executeBindLookupQuery(ps, envelopeId, attachmentId)) {
            if (!rs.next()) {
                throw new IllegalArgumentException("Attachment or envelope not found for binding");
            }
            return new BindLookup(
                    rs.getString(COL_SENDER_ID),
                    rs.getString(COL_STATUS),
                    rs.getTimestamp(COL_EXPIRES_AT).getTime());
        }
    }

    /**
     * Persists envelope binding metadata and final expiry for an attachment.
     *
     * @param conn                  active SQL connection
     * @param envelopeId            envelope id
     * @param attachmentId          attachment id
     * @param envelopeExpiryEpochMs expiry timestamp inherited from envelope
     * @throws SQLException when update fails
     */
    private void updateBind(Connection conn, String envelopeId, String attachmentId, long envelopeExpiryEpochMs)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_BIND_SQL)) {
            ps.setString(1, envelopeId);
            ps.setTimestamp(2, new Timestamp(envelopeExpiryEpochMs));
            ps.setString(3, attachmentId);
            ps.executeUpdate();
        }
    }

    /**
     * Loads downloadable attachment metadata for an authorized recipient.
     *
     * @param conn              active SQL connection
     * @param attachmentId      attachment id
     * @param callerRecipientId authenticated recipient id
     * @return download metadata
     * @throws SQLException when query fails
     */
    private DownloadMeta loadDownloadMeta(Connection conn, String attachmentId, String callerRecipientId)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_DOWNLOAD_SQL);
                ResultSet rs = executeDownloadQuery(ps, attachmentId, callerRecipientId)) {
            if (!rs.next()) {
                throw new SecurityException("Attachment not found or not accessible");
            }
            return new DownloadMeta(
                    rs.getString(COL_SENDER_ID),
                    rs.getString(COL_RECIPIENT_ID),
                    rs.getString(COL_CONTENT_TYPE),
                    rs.getLong(COL_EXPECTED_SIZE));
        }
    }

    /**
     * Concatenates all attachment chunks in ascending chunk index order.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @return rebuilt blob and chunk count
     * @throws SQLException when query fails
     */
    private ChunkBlob loadAttachmentBlob(Connection conn, String attachmentId) throws SQLException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int chunkCount = 0;

        try (PreparedStatement ps = conn.prepareStatement(SELECT_ALL_CHUNKS_SQL);
                ResultSet rs = executeQueryWithAttachmentId(ps, attachmentId)) {
            while (rs.next()) {
                out.writeBytes(rs.getBytes(COL_CHUNK_DATA));
                chunkCount++;
            }
        }
        return new ChunkBlob(out.toByteArray(), chunkCount);
    }

    /**
     * Loads and locks upload metadata row for mutation paths.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @return upload metadata
     * @throws SQLException when query fails
     */
    private UploadMeta loadUploadForUpdate(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_UPLOAD_FOR_UPDATE_SQL);
                ResultSet rs = executeQueryWithAttachmentId(ps, attachmentId)) {
            if (!rs.next()) {
                throw new IllegalArgumentException("Attachment upload not found");
            }
            return new UploadMeta(
                    rs.getString(COL_SENDER_ID),
                    rs.getString(COL_RECIPIENT_ID),
                    rs.getLong(COL_EXPECTED_SIZE),
                    rs.getInt(COL_EXPECTED_CHUNKS),
                    rs.getString(COL_STATUS),
                    rs.getTimestamp(COL_EXPIRES_AT).getTime());
        }
    }

    /**
     * Loads aggregate chunk counters (count and total bytes) for an upload.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @return chunk stats
     * @throws SQLException when query fails
     */
    private ChunkStats loadChunkStats(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_CHUNK_STATS_SQL);
                ResultSet rs = executeQueryWithAttachmentId(ps, attachmentId)) {
            if (!rs.next()) {
                return new ChunkStats(0, 0);
            }
            return new ChunkStats(rs.getInt("c"), rs.getLong("s"));
        }
    }

    /**
     * Loads one specific chunk payload.
     *
     * @param conn         active SQL connection
     * @param attachmentId attachment id
     * @param chunkIndex   chunk index
     * @return chunk bytes, or empty array when chunk does not exist
     * @throws SQLException when query fails
     */
    private byte[] loadChunk(Connection conn, String attachmentId, int chunkIndex) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_CHUNK_SQL);
                ResultSet rs = executeChunkQuery(ps, attachmentId, chunkIndex)) {
            if (!rs.next()) {
                return EMPTY_BYTE_ARRAY;
            }
            byte[] data = rs.getBytes(COL_CHUNK_DATA);
            return data != null ? data : EMPTY_BYTE_ARRAY;
        }
    }

    /**
     * Executes a SQL work unit inside a transaction with unified error mapping.
     *
     * @param failureMessage message used for wrapped database exception
     * @param work           transactional unit of work
     * @param <T>            result type
     * @return work result
     * @throws DatabaseOperationException when connection acquisition or SQL
     *                                    operations fail
     */
    private <T> T inTransaction(String failureMessage, SqlWork<T> work) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            return runTransactionalWork(conn, work);
        } catch (SQLException ex) {
            throw new DatabaseOperationException(failureMessage, ex);
        }
    }

    /**
     * Runs transactional work and commits on success, rolling back on failure.
     *
     * @param conn active SQL connection
     * @param work transactional unit of work
     * @param <T>  result type
     * @return work result
     * @throws SQLException when SQL execution or commit fails
     */
    private static <T> T runTransactionalWork(Connection conn, SqlWork<T> work) throws SQLException {
        try {
            T result = work.execute(conn);
            conn.commit();
            return result;
        } catch (RuntimeException | SQLException ex) {
            rollbackQuietly(conn, ex);
            throw ex;
        }
    }

    /**
     * Attempts rollback while preserving the original failure as primary exception.
     *
     * @param conn              active SQL connection
     * @param originalException original exception that triggered rollback
     */
    private static void rollbackQuietly(Connection conn, Exception originalException) {
        try {
            conn.rollback();
        } catch (SQLException rollbackEx) {
            originalException.addSuppressed(rollbackEx);
        }
    }

    /**
     * Executes bind lookup query after binding parameters.
     *
     * @param ps           prepared statement for bind lookup
     * @param envelopeId   envelope id parameter
     * @param attachmentId attachment id parameter
     * @return result set from executed query
     * @throws SQLException when execution fails
     */
    private static ResultSet executeBindLookupQuery(PreparedStatement ps, String envelopeId, String attachmentId)
            throws SQLException {
        ps.setString(1, envelopeId);
        ps.setString(2, attachmentId);
        return ps.executeQuery();
    }

    /**
     * Executes attachment download query after binding parameters.
     *
     * @param ps                prepared statement for download lookup
     * @param attachmentId      attachment id parameter
     * @param callerRecipientId recipient id parameter
     * @return result set from executed query
     * @throws SQLException when execution fails
     */
    private static ResultSet executeDownloadQuery(PreparedStatement ps, String attachmentId, String callerRecipientId)
            throws SQLException {
        ps.setString(1, attachmentId);
        ps.setString(2, callerRecipientId);
        return ps.executeQuery();
    }

    /**
     * Executes a single-parameter query that expects attachment id in position 1.
     *
     * @param ps           prepared statement
     * @param attachmentId attachment id parameter
     * @return result set from executed query
     * @throws SQLException when execution fails
     */
    private static ResultSet executeQueryWithAttachmentId(PreparedStatement ps, String attachmentId)
            throws SQLException {
        ps.setString(1, attachmentId);
        return ps.executeQuery();
    }

    /**
     * Executes chunk lookup query after binding attachment id and chunk index.
     *
     * @param ps           prepared statement for chunk lookup
     * @param attachmentId attachment id parameter
     * @param chunkIndex   chunk index parameter
     * @return result set from executed query
     * @throws SQLException when execution fails
     */
    private static ResultSet executeChunkQuery(PreparedStatement ps, String attachmentId, int chunkIndex)
            throws SQLException {
        ps.setString(1, attachmentId);
        ps.setInt(2, chunkIndex);
        return ps.executeQuery();
    }

    /**
     * Verifies that the caller is the sender authorized to mutate this upload.
     *
     * @param callerId authenticated caller id
     * @param senderId upload owner sender id
     * @throws SecurityException when caller is not owner
     */
    private static void authorizeSender(String callerId, String senderId) {
        if (!Objects.equals(callerId, senderId)) {
            throw new SecurityException("Caller is not allowed to modify this attachment");
        }
    }

    /**
     * Ensures upload has not expired.
     *
     * @param expiresAtEpochMs expiry instant in epoch milliseconds
     * @throws IllegalStateException when upload is expired
     */
    private static void ensureNotExpired(long expiresAtEpochMs) {
        if (System.currentTimeMillis() >= expiresAtEpochMs) {
            throw new IllegalStateException("Attachment upload has expired");
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        /**
         * Executes SQL work within the caller-managed transaction.
         *
         * @param conn active SQL connection
         * @return work result
         * @throws SQLException when SQL operations fail
         */
        T execute(Connection conn) throws SQLException;
    }

    public record UploadInitResult(String attachmentId, long expiresAtEpochMs) {
    }

    public record ChunkStoreResult(int chunkIndex, boolean stored) {
    }

    public record CompletionResult(int receivedChunks, long receivedBytes, String status) {
    }

    public record BindResult(String attachmentId, String envelopeId, long expiresAtEpochMs) {
    }

    public record DownloadBlob(
            String attachmentId,
            String senderId,
            String recipientId,
            String contentType,
            long encryptedSizeBytes,
            int chunkCount,
            byte[] encryptedBlob) {

        /**
         * Compares blob metadata and byte contents.
         *
         * @param o candidate object
         * @return {@code true} when all fields including blob bytes match
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof DownloadBlob(String id, String sId, String rId, String type, long size, int count, byte[] blob)))
                return false;
            return size == encryptedSizeBytes &&
                    count == chunkCount &&
                    Objects.equals(id, attachmentId) &&
                    Objects.equals(sId, senderId) &&
                    Objects.equals(rId, recipientId) &&
                    Objects.equals(type, contentType) &&
                    Arrays.equals(blob, encryptedBlob);
        }

        /**
         * Computes hash code including blob byte contents.
         *
         * @return hash code value
         */
        @Override
        public int hashCode() {
            int result = Objects.hash(attachmentId, senderId, recipientId, contentType, encryptedSizeBytes, chunkCount);
            result = 31 * result + Arrays.hashCode(encryptedBlob);
            return result;
        }

        /**
         * Returns string representation including metadata and blob bytes.
         *
         * @return debug-friendly string form
         */
        @Override
        public String toString() {
            return "DownloadBlob[" +
                    "attachmentId=" + attachmentId +
                    ", senderId=" + senderId +
                    ", recipientId=" + recipientId +
                    ", contentType=" + contentType +
                    ", encryptedSizeBytes=" + encryptedSizeBytes +
                    ", chunkCount=" + chunkCount +
                    ", encryptedBlob=" + Arrays.toString(encryptedBlob) +
                    ']';
        }
    }

    private record UploadMeta(String senderId,
            String recipientId,
            long expectedSize,
            int expectedChunks,
            String status,
            long expiresAtEpochMs) {
    }

    private record ChunkStats(int chunkCount, long totalBytes) {
    }

    private record BindLookup(String senderId, String status, long envelopeExpiryEpochMs) {
    }

    private record DownloadMeta(String senderId, String recipientId, String contentType, long expectedSize) {
    }

    private record ChunkBlob(byte[] blob, int chunkCount) {
        /**
         * Compares chunk count and blob bytes.
         *
         * @param o candidate object
         * @return {@code true} when both records are byte-for-byte equal
         */
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkBlob(byte[] b, int count)))
                return false;
            return count == chunkCount && Arrays.equals(b, blob);
        }

        /**
         * Computes hash code including blob bytes.
         *
         * @return hash code value
         */
        @Override
        public int hashCode() {
            int result = Objects.hash(chunkCount);
            result = 31 * result + Arrays.hashCode(blob);
            return result;
        }

        /**
         * Returns string representation including chunk count and raw bytes.
         *
         * @return debug-friendly string form
         */
        @Override
        public String toString() {
            return "ChunkBlob[" +
                    "blob=" + Arrays.toString(blob) +
                    ", chunkCount=" + chunkCount +
                    ']';
        }
    }
}
