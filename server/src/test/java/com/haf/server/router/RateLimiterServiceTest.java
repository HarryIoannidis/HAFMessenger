package com.haf.server.router;

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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement upsertStatement;

    @Mock
    private PreparedStatement selectStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private AuditLogger auditLogger;

    private RateLimiterService service;

    @BeforeEach
    void setUp() {
        // No stubbing here to keep Mockito strict – all stubs are in the tests
        service = new RateLimiterService(dataSource, auditLogger);
    }

    @Test
    void check_and_consume_allows_when_under_limit() throws SQLException {
        String requestId = "req-1";
        String userId = "user-123";

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStatement, selectStatement);
        when(upsertStatement.executeUpdate()).thenReturn(1);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getTimestamp("lockout_until")).thenReturn(null);
        when(resultSet.getInt("message_count")).thenReturn(50); // below threshold

        RateLimiterService.RateLimitDecision decision =
                service.checkAndConsume(requestId, userId);

        assertTrue(decision.allowed());
        assertEquals(0, decision.retryAfterSeconds());
        verify(upsertStatement, times(1)).executeUpdate();
        verify(selectStatement, times(1)).executeQuery();
        verify(auditLogger, never()).logRateLimit(anyString(), anyString(), anyLong());
    }

    @Test
    void check_and_consume_rate_limits_when_over_threshold() throws SQLException {
        String requestId = "req-1";
        String userId = "user-123";

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStatement, selectStatement);
        when(upsertStatement.executeUpdate()).thenReturn(1);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getTimestamp("lockout_until")).thenReturn(null);
        when(resultSet.getInt("message_count")).thenReturn(101); // above threshold

        RateLimiterService.RateLimitDecision decision =
                service.checkAndConsume(requestId, userId);

        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
        verify(auditLogger, times(1))
                .logRateLimit(eq(requestId), eq(userId), anyLong());
    }

    @Test
    void check_and_consume_rate_limits_when_locked_out() throws SQLException {
        String requestId = "req-1";
        String userId = "user-123";
        Timestamp lockoutUntil =
                Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES));

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStatement, selectStatement);
        when(upsertStatement.executeUpdate()).thenReturn(1);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getTimestamp("lockout_until")).thenReturn(lockoutUntil);

        RateLimiterService.RateLimitDecision decision =
                service.checkAndConsume(requestId, userId);

        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
        verify(auditLogger, times(1))
                .logRateLimit(eq(requestId), eq(userId), anyLong());
    }

    @Test
    void check_and_consume_allows_when_lockout_expired() throws SQLException {
        String requestId = "req-1";
        String userId = "user-123";
        Timestamp lockoutUntil =
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)); // expired

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStatement, selectStatement);
        when(upsertStatement.executeUpdate()).thenReturn(1);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getTimestamp("lockout_until")).thenReturn(lockoutUntil);
        when(resultSet.getInt("message_count")).thenReturn(50);

        RateLimiterService.RateLimitDecision decision =
                service.checkAndConsume(requestId, userId);

        assertTrue(decision.allowed());
        assertEquals(0, decision.retryAfterSeconds());
    }

    @Test
    void check_and_consume_allows_when_no_existing_record() throws SQLException {
        String requestId = "req-1";
        String userId = "user-123";

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStatement, selectStatement);
        when(upsertStatement.executeUpdate()).thenReturn(1);
        when(selectStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false); // no row

        RateLimiterService.RateLimitDecision decision =
                service.checkAndConsume(requestId, userId);

        assertTrue(decision.allowed());
        assertEquals(0, decision.retryAfterSeconds());
        verify(auditLogger, never()).logRateLimit(anyString(), anyString(), anyLong());
    }

    @Test
    void check_and_consume_throws_on_sql_exception() throws SQLException {
        String requestId = "req-1";
        String userId = "user-123";

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString()))
                .thenReturn(upsertStatement);
        when(upsertStatement.executeUpdate())
                .thenThrow(new SQLException("DB error"));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.checkAndConsume(requestId, userId)
        );
        assertTrue(ex.getMessage().contains("Rate limit check failed"));

        verify(auditLogger, times(1))
                .logError(eq("rate_limit_failed"), eq(requestId), eq(userId), any());
    }

    @Test
    void rate_limit_decision_allowed_has_zero_retry() {
        RateLimiterService.RateLimitDecision decision =
                RateLimiterService.RateLimitDecision.allow();

        assertTrue(decision.allowed());
        assertEquals(0, decision.retryAfterSeconds());
    }

    @Test
    void rate_limit_decision_rateLimited_has_positive_retry() {
        RateLimiterService.RateLimitDecision decision =
                RateLimiterService.RateLimitDecision.block(60);

        assertFalse(decision.allowed());
        assertEquals(60, decision.retryAfterSeconds());
    }

    @Test
    void rate_limit_decision_rateLimited_enforces_minimum_retry() {
        RateLimiterService.RateLimitDecision decision =
                RateLimiterService.RateLimitDecision.block(0);

        assertFalse(decision.allowed());
        assertEquals(1, decision.retryAfterSeconds()); // minimum enforced
    }
}
