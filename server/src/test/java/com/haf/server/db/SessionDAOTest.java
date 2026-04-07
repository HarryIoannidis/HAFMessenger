package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.security.JwtTokenService;
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
import java.time.Instant;
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
    private PreparedStatement selectStatement;

    @Mock
    private PreparedStatement touchStatement;

    @Mock
    private PreparedStatement rotateStatement;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ResultSet resultSet;

    private SessionDAO dao;
    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService("unit-test-secret", "haf-server", 900L);
        dao = new SessionDAO(dataSource, auditLogger, jwtTokenService, 2_592_000L, 2_592_000L);
    }

    @Test
    void createSession_returns_access_token() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT INTO sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        String accessToken = dao.createSession("user-123");

        assertNotNull(accessToken);
        assertFalse(accessToken.isBlank());
        assertTrue(accessToken.contains("."));
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void createSessionTokens_returns_access_and_refresh_tokens() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT INTO sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        SessionDAO.SessionTokens tokens = dao.createSessionTokens("user-123");

        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
        assertTrue(tokens.accessExpiresAtEpochSeconds() > 0L);
        assertTrue(tokens.refreshExpiresAtEpochSeconds() > tokens.accessExpiresAtEpochSeconds());
        verify(preparedStatement).setLong(5, 900L);
        verify(preparedStatement).setLong(6, 2_592_000L);
        verify(preparedStatement).setLong(7, 2_592_000L);
    }

    @Test
    void createSession_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT INTO sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.createSession("user-123"));
        verify(auditLogger, times(1)).logError(eq("db_create_session"), isNull(), eq("user-123"), any());
    }

    @Test
    void getUserIdForSession_returns_user_for_active_non_revoked_session() throws SQLException {
        String accessToken = issueToken("user-123", "jti-1");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT user_id FROM sessions"))).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("user-123");

        String userId = dao.getUserIdForSession(accessToken);

        assertEquals("user-123", userId);
    }

    @Test
    void getUserIdForSession_returns_null_for_blank_or_invalid_token() {
        assertNull(dao.getUserIdForSession(" "));
        assertNull(dao.getUserIdForSession("not-a-jwt"));
    }

    @Test
    void getUserIdForSessionAndTouch_returns_user_for_active_session() throws SQLException {
        String accessToken = issueToken("user-123", "jti-1");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT user_id FROM sessions"))).thenReturn(selectStatement);
        when(connection.prepareStatement(contains("SET last_seen_at"))).thenReturn(touchStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("user-123");
        when(touchStatement.executeUpdate()).thenReturn(1);

        String userId = dao.getUserIdForSessionAndTouch(accessToken);

        assertEquals("user-123", userId);
        verify(selectStatement, times(1)).executeQuery();
        verify(touchStatement, times(1)).executeUpdate();
    }

    @Test
    void refreshSession_returns_rotated_tokens_for_valid_refresh_token() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE refresh_token_hash"))).thenReturn(selectStatement);
        when(connection.prepareStatement(contains("SET access_jti"))).thenReturn(rotateStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("session_id")).thenReturn("session-row-1");
        when(resultSet.getString("user_id")).thenReturn("user-123");
        when(resultSet.getLong("absolute_expires_epoch")).thenReturn(Instant.now().plusSeconds(3600L).getEpochSecond());
        when(rotateStatement.executeUpdate()).thenReturn(1);

        SessionDAO.SessionTokens tokens = dao.refreshSession("refresh-plain-token");

        assertNotNull(tokens);
        assertNotNull(tokens.accessToken());
        assertNotNull(tokens.refreshToken());
        assertNotEquals("refresh-plain-token", tokens.refreshToken());
        verify(rotateStatement).setLong(3, 900L);
        verify(rotateStatement).setLong(4, 2_592_000L);
    }

    @Test
    void isAccessSessionRevoked_returns_true_when_matching_session_is_revoked() throws SQLException {
        String accessToken = issueToken("user-123", "jti-revoked");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT revoked"))).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean("revoked")).thenReturn(true);

        assertTrue(dao.isAccessSessionRevoked(accessToken));
    }

    @Test
    void isRefreshSessionRevoked_returns_true_when_matching_session_is_revoked() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE refresh_token_hash"))).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getBoolean("revoked")).thenReturn(true);

        assertTrue(dao.isRefreshSessionRevoked("refresh-token"));
    }

    @Test
    void constructor_without_explicit_idle_ttl_uses_access_ttl_when_access_ttl_is_higher_than_default_idle()
            throws SQLException {
        JwtTokenService longAccessTtlService = new JwtTokenService("unit-test-secret", "haf-server", 1_800L);
        SessionDAO adjustedDao = new SessionDAO(dataSource, auditLogger, longAccessTtlService, 2_592_000L, 2_592_000L);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE refresh_token_hash"))).thenReturn(selectStatement);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        SessionDAO.SessionTokens tokens = adjustedDao.refreshSession("refresh-plain-token");

        assertNull(tokens);
        verify(selectStatement).setLong(2, 1_800L);
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
        String accessToken = issueToken("user-123", "jti-1");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);

        assertDoesNotThrow(() -> dao.revokeSession(accessToken));
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void revokeSession_throws_on_sql_exception() throws SQLException {
        String accessToken = issueToken("user-123", "jti-1");
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE sessions"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.revokeSession(accessToken));
        verify(auditLogger, times(1)).logError(eq("db_revoke_session"), isNull(), eq("jti-1"), any());
    }

    @Test
    void revokeAllSessionsByUserId_executes_update() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE user_id"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(2);

        assertDoesNotThrow(() -> dao.revokeAllSessionsByUserId("user-123"));
        verify(preparedStatement).setString(1, "user-123");
        verify(preparedStatement, times(1)).executeUpdate();
    }

    @Test
    void revokeAllSessionsByUserId_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE user_id"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.revokeAllSessionsByUserId("user-123"));
        verify(auditLogger, times(1)).logError(eq("db_revoke_all_sessions"), isNull(), eq("user-123"), any());
    }

    @Test
    void constructor_rejects_null_datasource() {
        assertThrows(NullPointerException.class, () -> new SessionDAO(null, auditLogger));
    }

    @Test
    void constructor_rejects_null_auditlogger() {
        assertThrows(NullPointerException.class, () -> new SessionDAO(dataSource, null));
    }

    private String issueToken(String userId, String jti) {
        Instant now = Instant.now();
        return jwtTokenService.issueAccessToken(userId, jti, now, now.plusSeconds(900L));
    }
}
