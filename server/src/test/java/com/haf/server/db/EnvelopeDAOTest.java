package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.router.QueuedEnvelope;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
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
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvelopeDAOTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement insertStatement;

    @Mock
    private PreparedStatement fetchStatement;

    @Mock
    private PreparedStatement markDeliveredStatement;

    @Mock
    private Statement deleteStatement;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private PreparedStatement fetchByIdsStatement;

    private EnvelopeDAO dao;

    @BeforeEach
    void setUp() {
        dao = new EnvelopeDAO(dataSource, auditLogger);
    }

    @Test
    void insert_stores_envelope_successfully() throws SQLException {
        EncryptedMessage message = createValidMessage();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        var result = dao.insert(message);

        assertNotNull(result);
        assertNotNull(result.envelopeId());
        assertEquals(message, result.payload());
        verify(insertStatement, times(1)).executeUpdate();
        verify(insertStatement, times(1)).setString(eq(1), anyString()); // envelope_id
        verify(insertStatement, times(1)).setString(2, message.senderId);
        verify(insertStatement, times(1)).setString(3, message.recipientId);
    }

    @Test
    void insert_throws_on_sql_exception() throws SQLException {
        EncryptedMessage message = createValidMessage();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.insert(message));
        verify(auditLogger, times(1)).logError(eq("db_insert_envelope"), isNull(), eq(message.senderId), any());
    }

    @Test
    void fetch_for_recipient_returns_envelopes() throws SQLException {
        String recipientId = "recipient-123";
        ResultSet rs = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(fetchStatement);
        when(fetchStatement.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("envelope_id")).thenReturn("envelope-1");
        when(rs.getString("sender_id")).thenReturn("sender-1");
        when(rs.getString("recipient_id")).thenReturn(recipientId);
        when(rs.getBytes("encrypted_payload")).thenReturn("payload".getBytes());
        when(rs.getBytes("wrapped_key")).thenReturn("key".getBytes());
        when(rs.getBytes("iv")).thenReturn(new byte[12]);
        when(rs.getBytes("auth_tag")).thenReturn(new byte[16]);
        when(rs.getString("content_type")).thenReturn("text/plain");
        when(rs.getLong("content_length")).thenReturn(10L);
        when(rs.getLong("timestamp")).thenReturn(System.currentTimeMillis());
        when(rs.getLong("ttl")).thenReturn(3600L);
        when(rs.getTimestamp("created_at")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(rs.getTimestamp("expires_at")).thenReturn(new Timestamp(System.currentTimeMillis() + 3600000));

        List<com.haf.server.router.QueuedEnvelope> result = dao.fetchForRecipient(recipientId, 10);

        assertNotNull(result);
        verify(fetchStatement, times(1)).setString(1, recipientId);
        verify(fetchStatement, times(1)).setInt(2, 10);
    }

    @Test
    void fetch_for_recipient_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(fetchStatement);
        when(fetchStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.fetchForRecipient("recipient-123", 10));
        verify(auditLogger, times(1)).logError(eq("db_fetch_mailbox"), isNull(), eq("recipient-123"), any());
    }

    @Test
    void mark_delivered_updates_envelopes() throws SQLException {
        List<String> envelopeIds = List.of("id1", "id2", "id3");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE"))).thenReturn(markDeliveredStatement);
        when(markDeliveredStatement.executeUpdate()).thenReturn(3);

        boolean result = dao.markDelivered(envelopeIds);

        assertTrue(result);
        verify(markDeliveredStatement, times(3)).setString(anyInt(), anyString());
        verify(markDeliveredStatement, times(1)).executeUpdate();
    }

    @Test
    void mark_delivered_returns_false_for_empty_list() {
        boolean result = dao.markDelivered(List.of());

        assertFalse(result);
    }

    @Test
    void mark_delivered_returns_false_for_null() {
        boolean result = dao.markDelivered(null);

        assertFalse(result);
    }

    @Test
    void delete_expired_deletes_expired_envelopes() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(deleteStatement);
        when(deleteStatement.executeUpdate(contains("DELETE"))).thenReturn(5);

        int result = dao.deleteExpired();

        assertEquals(5, result);
        verify(deleteStatement, times(1)).executeUpdate(contains("DELETE"));
    }

    @Test
    void delete_expired_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(deleteStatement);
        when(deleteStatement.executeUpdate(anyString())).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.deleteExpired());
        verify(auditLogger, times(1)).logError(eq("db_delete_expired"), isNull(), eq("system"), any());
    }

    @Test
    void fetch_by_ids_returns_envelopes_for_given_ids() throws SQLException {
        List<String> envelopeIds = List.of("id1", "id2");
        ResultSet rs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("IN"))).thenReturn(fetchByIdsStatement);
        when(fetchByIdsStatement.executeQuery()).thenReturn(rs);

        // Mock two rows returned
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("envelope_id")).thenReturn("id1", "id2");
        when(rs.getString("sender_id")).thenReturn("sender-1", "sender-2");
        when(rs.getString("recipient_id")).thenReturn("recipient-1", "recipient-2");
        when(rs.getBytes("encrypted_payload")).thenReturn("payload1".getBytes(), "payload2".getBytes());
        when(rs.getBytes("wrapped_key")).thenReturn("key1".getBytes(), "key2".getBytes());
        when(rs.getBytes("iv")).thenReturn(new byte[12], new byte[12]);
        when(rs.getBytes("auth_tag")).thenReturn(new byte[16], new byte[16]);
        when(rs.getString("content_type")).thenReturn("text/plain", "text/plain");
        when(rs.getLong("content_length")).thenReturn(8L, 8L);
        when(rs.getLong("timestamp")).thenReturn(System.currentTimeMillis(), System.currentTimeMillis());
        when(rs.getLong("ttl")).thenReturn(3600L, 3600L);

        long now = System.currentTimeMillis();
        when(rs.getTimestamp("created_at")).thenReturn(new Timestamp(now - 1000), new Timestamp(now - 2000));
        when(rs.getTimestamp("expires_at")).thenReturn(new Timestamp(now + 3600000), new Timestamp(now + 3600000));

        Map<String, QueuedEnvelope> result = dao.fetchByIds(envelopeIds);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("id1"));
        assertTrue(result.containsKey("id2"));

        verify(fetchByIdsStatement, times(1)).setString(1, "id1");
        verify(fetchByIdsStatement, times(1)).setString(2, "id2");
        verify(fetchByIdsStatement, times(1)).executeQuery();
    }

    @Test
    void fetch_by_ids_returns_empty_map_for_empty_list() {
        Map<String, com.haf.server.router.QueuedEnvelope> result = dao.fetchByIds(List.of());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetch_by_ids_returns_empty_map_for_null() {
        Map<String, com.haf.server.router.QueuedEnvelope> result = dao.fetchByIds(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetch_by_ids_handles_partial_results() throws SQLException {
        List<String> envelopeIds = List.of("id1", "id2", "id3");
        ResultSet rs = mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("IN"))).thenReturn(fetchByIdsStatement);
        when(fetchByIdsStatement.executeQuery()).thenReturn(rs);

        // Only one row returned (id1 found, id2 and id3 not found)
        when(rs.next()).thenReturn(true, false);
        when(rs.getString("envelope_id")).thenReturn("id1");
        when(rs.getString("sender_id")).thenReturn("sender-1");
        when(rs.getString("recipient_id")).thenReturn("recipient-1");
        when(rs.getBytes("encrypted_payload")).thenReturn("payload".getBytes());
        when(rs.getBytes("wrapped_key")).thenReturn("key".getBytes());
        when(rs.getBytes("iv")).thenReturn(new byte[12]);
        when(rs.getBytes("auth_tag")).thenReturn(new byte[16]);
        when(rs.getString("content_type")).thenReturn("text/plain");
        when(rs.getLong("content_length")).thenReturn(7L);
        when(rs.getLong("timestamp")).thenReturn(System.currentTimeMillis());
        when(rs.getLong("ttl")).thenReturn(3600L);
        when(rs.getTimestamp("created_at")).thenReturn(new Timestamp(System.currentTimeMillis()));
        when(rs.getTimestamp("expires_at")).thenReturn(new Timestamp(System.currentTimeMillis() + 3600000));

        Map<String, com.haf.server.router.QueuedEnvelope> result = dao.fetchByIds(envelopeIds);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("id1"));
        assertFalse(result.containsKey("id2"));
        assertFalse(result.containsKey("id3"));
    }

    @Test
    void fetch_by_ids_throws_on_sql_exception() throws SQLException {
        List<String> envelopeIds = List.of("id1", "id2");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("IN"))).thenReturn(fetchByIdsStatement);
        when(fetchByIdsStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.fetchByIds(envelopeIds));
        verify(auditLogger, times(1)).logError(eq("db_fetch_by_ids"), isNull(), isNull(), any());
    }

    private EncryptedMessage createValidMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.version = MessageHeader.VERSION;
        message.algorithm = MessageHeader.ALGO_AEAD;
        message.senderId = "sender-123";
        message.recipientId = "recipient-456";
        message.timestampEpochMs = System.currentTimeMillis();
        message.ttlSeconds = 3600;
        message.ivB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]);
        message.ephemeralPublicB64 = Base64.getEncoder().encodeToString(new byte[256]);
        message.ciphertextB64 = Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8));
        message.tagB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]);
        message.contentType = "text/plain";
        message.contentLength = 4;
        message.aadB64 = Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8));
        message.e2e = true;
        return message;
    }
}
