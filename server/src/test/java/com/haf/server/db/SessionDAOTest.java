package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionDAOTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ResultSet resultSet;

    private SessionDAO dao;

    @BeforeEach
    void setUp() {
        dao = new SessionDAO(dataSource, auditLogger);
    }

    @Test
    void createSession_returns_session_id() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        String sessionId = dao.createSession("user-123");

        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
        verify(preparedStatement).setString(1, sessionId); // session_id
        verify(preparedStatement).setString(2, "user-123"); // user_id
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void createSession_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.createSession("user-123"));
        verify(auditLogger, times(1)).logError(eq("db_create_session"), isNull(), eq("user-123"), any());
    }

    @Test
    void getUserIdForSession_returns_user_for_active_non_revoked_session() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("revoked = FALSE"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("user-123");

        String userId = dao.getUserIdForSession("session-123");

        assertEquals("user-123", userId);
    }

    @Test
    void getUserIdForSession_returns_null_for_blank_session() {
        assertNull(dao.getUserIdForSession(" "));
    }

    @Test
    void getUserIdForSessionAndTouch_returns_user_for_active_session() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT user_id FROM sessions"))).thenReturn(preparedStatement);
        when(connection.prepareStatement(contains("SET last_seen_at"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("user-123");
        when(preparedStatement.executeUpdate()).thenReturn(1);

        String userId = dao.getUserIdForSessionAndTouch("session-123");

        assertEquals("user-123", userId);
        verify(preparedStatement, times(1)).executeQuery();
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void isUserRecentlyActive_returns_true_when_recent_session_exists() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("DATE_SUB(CURRENT_TIMESTAMP"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        boolean active = dao.isUserRecentlyActive("user-123", 8);

        assertTrue(active);
    }

    @Test
    void revokeSession_executes_update() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        assertDoesNotThrow(() -> dao.revokeSession("session-123"));
        verify(preparedStatement).setString(1, "session-123");
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void revokeSession_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.revokeSession("session-123"));
        verify(auditLogger, times(1)).logError(eq("db_revoke_session"), isNull(), eq("session-123"), any());
    }

    @Test
    void constructor_rejects_null_datasource() {
        assertThrows(NullPointerException.class, () -> new SessionDAO(null, auditLogger));
    }

    @Test
    void constructor_rejects_null_auditlogger() {
        assertThrows(NullPointerException.class, () -> new SessionDAO(dataSource, null));
    }
}
