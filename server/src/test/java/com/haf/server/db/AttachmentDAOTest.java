package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentDAOTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    private AttachmentDAO dao;

    @BeforeEach
    void setUp() {
        dao = new AttachmentDAO(dataSource);
    }

    @Test
    void init_upload_returns_attachment_id_and_expiry() throws SQLException {
        PreparedStatement insert = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT INTO message_attachments"))).thenReturn(insert);

        AttachmentDAO.UploadInitResult result = dao.initUpload(
                "sender-1",
                "recipient-1",
                "application/vnd.haf.encrypted-message+json",
                2048,
                4,
                1800);

        assertNotNull(result);
        assertNotNull(result.attachmentId());
        assertFalse(result.attachmentId().isBlank());
        assertTrue(result.expiresAtEpochMs() > System.currentTimeMillis());
        verify(insert).executeUpdate();
    }

    @Test
    void store_chunk_duplicate_same_payload_is_idempotent() throws Exception {
        PreparedStatement selectUpload = mock(PreparedStatement.class);
        PreparedStatement selectChunk = mock(PreparedStatement.class);
        ResultSet uploadRs = mock(ResultSet.class);
        ResultSet chunkRs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("FROM message_attachments"))).thenReturn(selectUpload);
        when(connection.prepareStatement(contains("FROM message_attachment_chunks"))).thenReturn(selectChunk);

        when(selectUpload.executeQuery()).thenReturn(uploadRs);
        when(uploadRs.next()).thenReturn(true);
        when(uploadRs.getString("sender_id")).thenReturn("sender-1");
        when(uploadRs.getString("recipient_id")).thenReturn("recipient-1");
        when(uploadRs.getLong("expected_size")).thenReturn(2048L);
        when(uploadRs.getInt("expected_chunks")).thenReturn(4);
        when(uploadRs.getString("status")).thenReturn("UPLOADING");
        when(uploadRs.getTimestamp("expires_at")).thenReturn(new Timestamp(System.currentTimeMillis() + 60_000));

        byte[] chunk = "same".getBytes(StandardCharsets.UTF_8);
        when(selectChunk.executeQuery()).thenReturn(chunkRs);
        when(chunkRs.next()).thenReturn(true);
        when(chunkRs.getBytes("chunk_data")).thenReturn(chunk);

        AttachmentDAO.ChunkStoreResult result = dao.storeChunk("sender-1", "att-1", 0, chunk);

        assertEquals(0, result.chunkIndex());
        assertFalse(result.stored());
        verify(connection, never()).prepareStatement(contains("INSERT INTO message_attachment_chunks"));
    }

    @Test
    void complete_upload_rejects_when_chunks_missing() throws Exception {
        PreparedStatement selectUpload = mock(PreparedStatement.class);
        PreparedStatement selectStats = mock(PreparedStatement.class);
        ResultSet uploadRs = mock(ResultSet.class);
        ResultSet statsRs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("FROM message_attachments"))).thenReturn(selectUpload);
        when(connection.prepareStatement(contains("COUNT(*) AS c"))).thenReturn(selectStats);

        when(selectUpload.executeQuery()).thenReturn(uploadRs);
        when(uploadRs.next()).thenReturn(true);
        when(uploadRs.getString("sender_id")).thenReturn("sender-1");
        when(uploadRs.getString("recipient_id")).thenReturn("recipient-1");
        when(uploadRs.getLong("expected_size")).thenReturn(4096L);
        when(uploadRs.getInt("expected_chunks")).thenReturn(4);
        when(uploadRs.getString("status")).thenReturn("UPLOADING");
        when(uploadRs.getTimestamp("expires_at")).thenReturn(new Timestamp(System.currentTimeMillis() + 60_000));

        when(selectStats.executeQuery()).thenReturn(statsRs);
        when(statsRs.next()).thenReturn(true);
        when(statsRs.getInt("c")).thenReturn(3);
        when(statsRs.getLong("s")).thenReturn(3072L);

        assertThrows(IllegalStateException.class,
                () -> dao.completeUpload("sender-1", "att-1", 4, 4096L));
    }

    @Test
    void bind_upload_uses_envelope_expiry() throws Exception {
        PreparedStatement selectBind = mock(PreparedStatement.class);
        PreparedStatement updateBind = mock(PreparedStatement.class);
        ResultSet bindRs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("JOIN message_envelopes"))).thenReturn(selectBind);
        when(connection.prepareStatement(contains("SET status = 'BOUND'"))).thenReturn(updateBind);

        long expiry = System.currentTimeMillis() + 120_000;
        when(selectBind.executeQuery()).thenReturn(bindRs);
        when(bindRs.next()).thenReturn(true);
        when(bindRs.getString("sender_id")).thenReturn("sender-1");
        when(bindRs.getString("status")).thenReturn("COMPLETE");
        when(bindRs.getTimestamp("expires_at")).thenReturn(new Timestamp(expiry));

        AttachmentDAO.BindResult result = dao.bindUploadToEnvelope("sender-1", "att-1", "env-1");

        assertEquals("att-1", result.attachmentId());
        assertEquals("env-1", result.envelopeId());
        assertEquals(expiry, result.expiresAtEpochMs());
        verify(updateBind).executeUpdate();
    }

    @Test
    void download_throws_security_error_for_non_recipient() throws Exception {
        PreparedStatement selectDownload = mock(PreparedStatement.class);
        ResultSet downloadRs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE attachment_id = ?"))).thenReturn(selectDownload);
        when(selectDownload.executeQuery()).thenReturn(downloadRs);
        when(downloadRs.next()).thenReturn(false);

        assertThrows(SecurityException.class, () -> dao.loadForRecipient("recipient-x", "att-1"));
    }

    @Test
    void cleanup_wraps_sql_errors() throws Exception {
        PreparedStatement deleteStmt = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("DELETE FROM message_attachments"))).thenReturn(deleteStmt);
        when(deleteStmt.executeUpdate()).thenThrow(new SQLException("boom"));

        assertThrows(DatabaseOperationException.class, () -> dao.deleteExpiredUploads());
    }
}
