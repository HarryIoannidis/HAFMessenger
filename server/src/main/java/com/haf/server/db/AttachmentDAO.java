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
public final class AttachmentDAO {

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

    private static final String SELECT_UPLOAD_FOR_UPDATE_SQL = """
            SELECT sender_id, recipient_id, expected_size, expected_chunks, status, expires_at
            FROM message_attachments
            WHERE attachment_id = ?
            FOR UPDATE
            """;

    private static final String SELECT_CHUNK_SQL = """
            SELECT chunk_data
            FROM message_attachment_chunks
            WHERE attachment_id = ?
              AND chunk_index = ?
            """;

    private static final String INSERT_CHUNK_SQL = """
            INSERT INTO message_attachment_chunks (
                attachment_id,
                chunk_index,
                chunk_data,
                chunk_size
            ) VALUES (?, ?, ?, ?)
            """;

    private static final String MARK_UPLOADING_SQL = """
            UPDATE message_attachments
            SET status = 'UPLOADING'
            WHERE attachment_id = ?
              AND status = 'INIT'
            """;

    private static final String SELECT_CHUNK_STATS_SQL = """
            SELECT COUNT(*) AS c, COALESCE(SUM(chunk_size), 0) AS s
            FROM message_attachment_chunks
            WHERE attachment_id = ?
            """;

    private static final String MARK_COMPLETE_SQL = """
            UPDATE message_attachments
            SET status = 'COMPLETE'
            WHERE attachment_id = ?
              AND status IN ('INIT', 'UPLOADING')
            """;

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

    private static final String UPDATE_BIND_SQL = """
            UPDATE message_attachments
            SET status = 'BOUND',
                envelope_id = ?,
                bound_at = NOW(),
                expires_at = ?
            WHERE attachment_id = ?
            """;

    private static final String SELECT_DOWNLOAD_SQL = """
            SELECT attachment_id, sender_id, recipient_id, content_type, expected_size
            FROM message_attachments
            WHERE attachment_id = ?
              AND recipient_id = ?
              AND status = 'BOUND'
              AND expires_at > NOW()
            """;

    private static final String SELECT_ALL_CHUNKS_SQL = """
            SELECT chunk_data
            FROM message_attachment_chunks
            WHERE attachment_id = ?
            ORDER BY chunk_index ASC
            """;

    private final DataSource dataSource;

    public AttachmentDAO(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, PARAM_DATA_SOURCE);
    }

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

    public int deleteExpiredUploads() {
        final String sql = "DELETE FROM message_attachments WHERE expires_at < NOW()";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DatabaseOperationException("Failed to cleanup expired attachments", ex);
        }
    }

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

    private void markUploadAsUploading(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(MARK_UPLOADING_SQL)) {
            ps.setString(1, attachmentId);
            ps.executeUpdate();
        }
    }

    private void markUploadComplete(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(MARK_COMPLETE_SQL)) {
            ps.setString(1, attachmentId);
            ps.executeUpdate();
        }
    }

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

    private void updateBind(Connection conn, String envelopeId, String attachmentId, long envelopeExpiryEpochMs)
            throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(UPDATE_BIND_SQL)) {
            ps.setString(1, envelopeId);
            ps.setTimestamp(2, new Timestamp(envelopeExpiryEpochMs));
            ps.setString(3, attachmentId);
            ps.executeUpdate();
        }
    }

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

    private ChunkStats loadChunkStats(Connection conn, String attachmentId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_CHUNK_STATS_SQL);
                ResultSet rs = executeQueryWithAttachmentId(ps, attachmentId)) {
            if (!rs.next()) {
                return new ChunkStats(0, 0);
            }
            return new ChunkStats(rs.getInt("c"), rs.getLong("s"));
        }
    }

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

    private <T> T inTransaction(String failureMessage, SqlWork<T> work) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            return runTransactionalWork(conn, work);
        } catch (SQLException ex) {
            throw new DatabaseOperationException(failureMessage, ex);
        }
    }

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

    private static void rollbackQuietly(Connection conn, Exception originalException) {
        try {
            conn.rollback();
        } catch (SQLException rollbackEx) {
            originalException.addSuppressed(rollbackEx);
        }
    }

    private static ResultSet executeBindLookupQuery(PreparedStatement ps, String envelopeId, String attachmentId)
            throws SQLException {
        ps.setString(1, envelopeId);
        ps.setString(2, attachmentId);
        return ps.executeQuery();
    }

    private static ResultSet executeDownloadQuery(PreparedStatement ps, String attachmentId, String callerRecipientId)
            throws SQLException {
        ps.setString(1, attachmentId);
        ps.setString(2, callerRecipientId);
        return ps.executeQuery();
    }

    private static ResultSet executeQueryWithAttachmentId(PreparedStatement ps, String attachmentId)
            throws SQLException {
        ps.setString(1, attachmentId);
        return ps.executeQuery();
    }

    private static ResultSet executeChunkQuery(PreparedStatement ps, String attachmentId, int chunkIndex)
            throws SQLException {
        ps.setString(1, attachmentId);
        ps.setInt(2, chunkIndex);
        return ps.executeQuery();
    }

    private static void authorizeSender(String callerId, String senderId) {
        if (!Objects.equals(callerId, senderId)) {
            throw new SecurityException("Caller is not allowed to modify this attachment");
        }
    }

    private static void ensureNotExpired(long expiresAtEpochMs) {
        if (System.currentTimeMillis() >= expiresAtEpochMs) {
            throw new IllegalStateException("Attachment upload has expired");
        }
    }

    @FunctionalInterface
    private interface SqlWork<T> {
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

        @Override
        public int hashCode() {
            int result = Objects.hash(attachmentId, senderId, recipientId, contentType, encryptedSizeBytes, chunkCount);
            result = 31 * result + Arrays.hashCode(encryptedBlob);
            return result;
        }

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
        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkBlob(byte[] b, int count)))
                return false;
            return count == chunkCount && Arrays.equals(b, blob);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(chunkCount);
            result = 31 * result + Arrays.hashCode(blob);
            return result;
        }

        @Override
        public String toString() {
            return "ChunkBlob[" +
                    "blob=" + Arrays.toString(blob) +
                    ", chunkCount=" + chunkCount +
                    ']';
        }
    }
}
