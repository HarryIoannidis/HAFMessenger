package com.haf.server.router;

import com.haf.server.exceptions.RateLimitException;
import com.haf.server.metrics.AuditLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
        private PreparedStatement clearStatement;

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

                RateLimiterService.RateLimitDecision decision = service.checkAndConsume(requestId, userId);

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

                RateLimiterService.RateLimitDecision decision = service.checkAndConsume(requestId, userId);

                assertFalse(decision.allowed());
                assertTrue(decision.retryAfterSeconds() > 0);
                verify(auditLogger, times(1))
                                .logRateLimit(eq(requestId), eq(userId), anyLong());
        }

        @Test
        void check_and_consume_rate_limits_when_locked_out() throws SQLException {
                String requestId = "req-1";
                String userId = "user-123";
                Timestamp lockoutUntil = Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES));

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(lockoutUntil);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsume(requestId, userId);

                assertFalse(decision.allowed());
                assertTrue(decision.retryAfterSeconds() > 0);
                verify(auditLogger, times(1))
                                .logRateLimit(eq(requestId), eq(userId), anyLong());
        }

        @Test
        void check_and_consume_allows_when_lockout_expired() throws SQLException {
                String requestId = "req-1";
                String userId = "user-123";
                Timestamp lockoutUntil = Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)); // expired

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(lockoutUntil);
                when(resultSet.getInt("message_count")).thenReturn(50);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsume(requestId, userId);

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

                RateLimiterService.RateLimitDecision decision = service.checkAndConsume(requestId, userId);

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

                RateLimitException ex = assertThrows(
                                RateLimitException.class,
                                () -> service.checkAndConsume(requestId, userId));
                assertTrue(ex.getMessage().contains("Rate limit check failed"));

                verify(auditLogger, times(1))
                                .logError(eq("rate_limit_failed"), eq(requestId), eq(userId), any());
        }

        @Test
        void check_and_consume_login_attempt_allows_when_under_limit() throws SQLException {
                String requestId = "req-login-1";
                String email = "user@haf.gr";
                String sourceIp = "192.168.1.10";

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(null);
                when(resultSet.getInt("attempt_count")).thenReturn(3);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsumeLoginAttempt(requestId, email,
                                sourceIp);

                assertTrue(decision.allowed());
                assertEquals(0, decision.retryAfterSeconds());
                verify(auditLogger, never()).logRateLimit(anyString(), anyString(), anyLong());
        }

        @Test
        void check_and_consume_login_attempt_rate_limits_when_over_threshold() throws SQLException {
                String requestId = "req-login-2";
                String email = "user@haf.gr";
                String sourceIp = "192.168.1.11";

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(null);
                when(resultSet.getInt("attempt_count")).thenReturn(9);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsumeLoginAttempt(requestId, email,
                                sourceIp);

                assertFalse(decision.allowed());
                assertTrue(decision.retryAfterSeconds() > 0);
                verify(auditLogger, times(1)).logRateLimit(eq(requestId), eq(email), anyLong());
        }

        @Test
        void check_and_consume_login_attempt_rate_limits_when_locked_out() throws SQLException {
                String requestId = "req-login-3";
                String email = "user@haf.gr";
                String sourceIp = "192.168.1.12";
                Timestamp lockoutUntil = Timestamp.from(Instant.now().plus(10, ChronoUnit.MINUTES));

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(lockoutUntil);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsumeLoginAttempt(requestId, email,
                                sourceIp);

                assertFalse(decision.allowed());
                assertTrue(decision.retryAfterSeconds() > 0);
                verify(auditLogger, times(1)).logRateLimit(eq(requestId), eq(email), anyLong());
        }

        @Test
        void check_and_consume_login_attempt_throws_on_sql_exception() throws SQLException {
                String requestId = "req-login-4";
                String email = "user@haf.gr";
                String sourceIp = "192.168.1.13";

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement);
                when(upsertStatement.executeUpdate())
                                .thenThrow(new SQLException("DB error"));

                RateLimitException ex = assertThrows(
                                RateLimitException.class,
                                () -> service.checkAndConsumeLoginAttempt(requestId, email, sourceIp));
                assertTrue(ex.getMessage().contains("Login rate limit check failed"));
                verify(auditLogger, times(1))
                                .logError(eq("login_rate_limit_failed"), eq(requestId), eq(email), any());
        }

        @Test
        void clear_login_attempts_deletes_bucket() throws SQLException {
                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString())).thenReturn(clearStatement);
                when(clearStatement.executeUpdate()).thenReturn(1);

                service.clearLoginAttempts("user@haf.gr", "127.0.0.1");

                ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
                verify(clearStatement).setString(eq(1), keyCaptor.capture());
                String throttleKey = keyCaptor.getValue();
                assertNotNull(throttleKey);
                assertEquals(64, throttleKey.length());
                assertTrue(throttleKey.matches("[a-f0-9]{64}"));
                verify(clearStatement).executeUpdate();
        }

        @Test
        void clear_login_attempts_logs_error_when_delete_fails() throws SQLException {
                when(dataSource.getConnection()).thenThrow(new SQLException("DB unavailable"));

                assertDoesNotThrow(() -> service.clearLoginAttempts("user@haf.gr", "127.0.0.1"));
                verify(auditLogger, times(1))
                                .logError(eq("login_rate_limit_clear_failed"), isNull(), eq("user@haf.gr"), any());
        }

        @Test
        void rate_limit_decision_allowed_has_zero_retry() {
                RateLimiterService.RateLimitDecision decision = RateLimiterService.RateLimitDecision.allow();

                assertTrue(decision.allowed());
                assertEquals(0, decision.retryAfterSeconds());
        }

        @Test
        void rate_limit_decision_rateLimited_has_positive_retry() {
                RateLimiterService.RateLimitDecision decision = RateLimiterService.RateLimitDecision.block(60);

                assertFalse(decision.allowed());
                assertEquals(60, decision.retryAfterSeconds());
        }

        @Test
        void rate_limit_decision_rateLimited_enforces_minimum_retry() {
                RateLimiterService.RateLimitDecision decision = RateLimiterService.RateLimitDecision.block(0);

                assertFalse(decision.allowed());
                assertEquals(1, decision.retryAfterSeconds()); // minimum enforced
        }

        @Test
        void check_and_consume_api_allows_when_under_limit() throws SQLException {
                String requestId = "req-api-1";

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(null);
                when(resultSet.getInt("attempt_count")).thenReturn(1);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsumeApi(
                                requestId,
                                RateLimiterService.ApiRateLimitScope.SEARCH,
                                "caller-1");

                assertTrue(decision.allowed());
                assertEquals(0, decision.retryAfterSeconds());
        }

        @Test
        void check_and_consume_api_rate_limits_when_over_threshold() throws SQLException {
                String requestId = "req-api-2";

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(true);
                when(resultSet.getTimestamp("lockout_until")).thenReturn(null);
                when(resultSet.getInt("attempt_count")).thenReturn(1000);

                RateLimiterService.RateLimitDecision decision = service.checkAndConsumeApi(
                                requestId,
                                RateLimiterService.ApiRateLimitScope.REGISTER,
                                "198.51.100.7");

                assertFalse(decision.allowed());
                assertTrue(decision.retryAfterSeconds() > 0);
                verify(auditLogger, times(1)).logRateLimit(eq(requestId), eq("register"), anyLong());
        }

        @Test
        void check_and_consume_api_builds_hashed_scope_principal_key() throws SQLException {
                String requestId = "req-api-3";

                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.prepareStatement(anyString()))
                                .thenReturn(upsertStatement, selectStatement);
                when(upsertStatement.executeUpdate()).thenReturn(1);
                when(selectStatement.executeQuery()).thenReturn(resultSet);
                when(resultSet.next()).thenReturn(false);

                service.checkAndConsumeApi(requestId, RateLimiterService.ApiRateLimitScope.CONTACTS, "Caller-A");

                ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
                verify(upsertStatement).setString(eq(1), keyCaptor.capture());
                String throttleKey = keyCaptor.getValue();
                assertNotNull(throttleKey);
                assertEquals(64, throttleKey.length());
                assertTrue(throttleKey.matches("[a-f0-9]{64}"));
        }
}
