package com.haf.server.db;

import com.haf.server.metrics.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
        verify(preparedStatement).setString(eq(1), eq(sessionId)); // session_id
        verify(preparedStatement).setString(eq(2), eq("user-123")); // user_id
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void createSession_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(IllegalStateException.class, () -> dao.createSession("user-123"));
        verify(auditLogger, times(1)).logError(eq("db_create_session"), isNull(), eq("user-123"), any());
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
