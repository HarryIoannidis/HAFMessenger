package com.haf.server.router;

import com.haf.server.exceptions.RateLimitException;
import javax.sql.DataSource;
import com.haf.server.metrics.AuditLogger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Enforces per-user ingress rate limits backed by database state.
 */
public final class RateLimiterService {

    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_MESSAGES_PER_WINDOW = 100;
    private static final int LOCKOUT_MINUTES = 15;
    private static final int LOGIN_WINDOW_SECONDS = 600;
    private static final int MAX_LOGIN_ATTEMPTS_PER_WINDOW = 8;
    private static final int LOGIN_LOCKOUT_MINUTES = 10;

    /**
     * SQL query for upserting rate limit data.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO rate_limits (user_id, message_count, window_start, lockout_until)
            VALUES (?, 1, NOW(), NULL)
            ON DUPLICATE KEY UPDATE
                message_count = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= ? THEN 1
                    ELSE message_count + 1
                END,
                window_start = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= ? THEN NOW()
                    ELSE window_start
                END,
                lockout_until = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= ? THEN NULL
                    WHEN message_count + 1 >= ? THEN DATE_ADD(NOW(), INTERVAL ? MINUTE)
                    ELSE lockout_until
                END
            """;

    /**
     * SQL query for selecting rate limit data.
     */
    private static final String SELECT_SQL = """
            SELECT message_count, window_start, lockout_until
              FROM rate_limits
             WHERE user_id = ?
            """;

    /**
     * SQL query for upserting login rate-limit data.
     */
    private static final String LOGIN_UPSERT_SQL = """
            INSERT INTO login_rate_limits (throttle_key, attempt_count, window_start, lockout_until)
            VALUES (?, 1, NOW(), NULL)
            ON DUPLICATE KEY UPDATE
                attempt_count = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= ? THEN 1
                    ELSE attempt_count + 1
                END,
                window_start = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= ? THEN NOW()
                    ELSE window_start
                END,
                lockout_until = CASE
                    WHEN TIMESTAMPDIFF(SECOND, window_start, NOW()) >= ? THEN NULL
                    WHEN attempt_count + 1 >= ? THEN DATE_ADD(NOW(), INTERVAL ? MINUTE)
                    ELSE lockout_until
                END
            """;

    /**
     * SQL query for selecting login rate-limit data.
     */
    private static final String LOGIN_SELECT_SQL = """
            SELECT attempt_count, lockout_until
              FROM login_rate_limits
             WHERE throttle_key = ?
            """;

    /**
     * SQL query for clearing login-rate limit data.
     */
    private static final String LOGIN_CLEAR_SQL = """
            DELETE FROM login_rate_limits
             WHERE throttle_key = ?
            """;

    private final DataSource dataSource;
    private final AuditLogger auditLogger;

    /**
     * Creates a new rate limit service.
     *
     * @param dataSource  the database connection pool.
     * @param auditLogger the audit logger.
     */
    public RateLimiterService(DataSource dataSource, AuditLogger auditLogger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    /**
     * Checks and consumes a rate limit for a user.
     *
     * @param requestId the ID of the request.
     * @param userId    the ID of the user.
     * @return the rate limit decision.
     */
    public RateLimitDecision checkAndConsume(String requestId, String userId) {
        try (Connection connection = dataSource.getConnection()) {
            upsertWindow(connection, userId);
            try (PreparedStatement select = connection.prepareStatement(SELECT_SQL)) {
                select.setString(1, userId);
                ResultSet rs = select.executeQuery();
                if (rs.next()) {
                    Timestamp lockoutUntil = rs.getTimestamp("lockout_until");
                    if (lockoutUntil != null && lockoutUntil.after(Timestamp.from(Instant.now()))) {
                        long retryAfterSeconds = Duration.between(Instant.now(), lockoutUntil.toInstant()).toSeconds();
                        auditLogger.logRateLimit(requestId, userId, retryAfterSeconds);
                        return RateLimitDecision.block(retryAfterSeconds);
                    }

                    int count = rs.getInt("message_count");
                    if (count > MAX_MESSAGES_PER_WINDOW) {
                        auditLogger.logRateLimit(requestId, userId, WINDOW_SECONDS);
                        return RateLimitDecision.block(WINDOW_SECONDS);
                    }
                }
            }
            return RateLimitDecision.allow();
        } catch (SQLException ex) {
            auditLogger.logError("rate_limit_failed", requestId, userId, ex);
            throw new RateLimitException("Rate limit check failed", ex);
        }
    }

    /**
     * Checks and consumes a login rate limit by normalized email + source IP.
     *
     * @param requestId request id
     * @param email account email
     * @param sourceIp request source IP
     * @return login rate-limit decision
     */
    public RateLimitDecision checkAndConsumeLoginAttempt(String requestId, String email, String sourceIp) {
        String throttleKey = buildLoginThrottleKey(email, sourceIp);
        try (Connection connection = dataSource.getConnection()) {
            upsertLoginWindow(connection, throttleKey);
            try (PreparedStatement select = connection.prepareStatement(LOGIN_SELECT_SQL)) {
                select.setString(1, throttleKey);
                ResultSet rs = select.executeQuery();
                if (rs.next()) {
                    Timestamp lockoutUntil = rs.getTimestamp("lockout_until");
                    if (lockoutUntil != null && lockoutUntil.after(Timestamp.from(Instant.now()))) {
                        long retryAfterSeconds = Duration.between(Instant.now(), lockoutUntil.toInstant()).toSeconds();
                        auditLogger.logRateLimit(requestId, email, retryAfterSeconds);
                        return RateLimitDecision.block(retryAfterSeconds);
                    }

                    int count = rs.getInt("attempt_count");
                    if (count > MAX_LOGIN_ATTEMPTS_PER_WINDOW) {
                        auditLogger.logRateLimit(requestId, email, LOGIN_WINDOW_SECONDS);
                        return RateLimitDecision.block(LOGIN_WINDOW_SECONDS);
                    }
                }
            }
            return RateLimitDecision.allow();
        } catch (SQLException ex) {
            auditLogger.logError("login_rate_limit_failed", requestId, email, ex);
            throw new RateLimitException("Login rate limit check failed", ex);
        }
    }

    /**
     * Clears login rate-limit state after successful authentication.
     *
     * @param email account email
     * @param sourceIp request source IP
     */
    public void clearLoginAttempts(String email, String sourceIp) {
        String throttleKey = buildLoginThrottleKey(email, sourceIp);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement clear = connection.prepareStatement(LOGIN_CLEAR_SQL)) {
            clear.setString(1, throttleKey);
            clear.executeUpdate();
        } catch (SQLException ex) {
            auditLogger.logError("login_rate_limit_clear_failed", null, email, ex);
        }
    }

    /**
     * Upserts a new rate limit window for a user.
     *
     * @param connection the database connection.
     * @param userId     the ID of the user.
     * @throws SQLException if an error occurs while executing the query.
     */
    private void upsertWindow(Connection connection, String userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPSERT_SQL)) {
            ps.setString(1, userId);
            ps.setInt(2, WINDOW_SECONDS);
            ps.setInt(3, WINDOW_SECONDS);
            ps.setInt(4, WINDOW_SECONDS);
            ps.setInt(5, MAX_MESSAGES_PER_WINDOW);
            ps.setInt(6, LOCKOUT_MINUTES);
            ps.executeUpdate();
        }
    }

    /**
     * Upserts login rate-limit window for an email/ip throttle key.
     *
     * @param connection open connection
     * @param throttleKey normalized throttle key
     * @throws SQLException on SQL failure
     */
    private void upsertLoginWindow(Connection connection, String throttleKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(LOGIN_UPSERT_SQL)) {
            ps.setString(1, throttleKey);
            ps.setInt(2, LOGIN_WINDOW_SECONDS);
            ps.setInt(3, LOGIN_WINDOW_SECONDS);
            ps.setInt(4, LOGIN_WINDOW_SECONDS);
            ps.setInt(5, MAX_LOGIN_ATTEMPTS_PER_WINDOW);
            ps.setInt(6, LOGIN_LOCKOUT_MINUTES);
            ps.executeUpdate();
        }
    }

    /**
     * Builds stable throttle-key digest from email and source IP.
     *
     * @param email account email
     * @param sourceIp request source IP
     * @return SHA-256 hex key
     */
    private String buildLoginThrottleKey(String email, String sourceIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String normalizedIp = sourceIp == null ? "unknown" : sourceIp.trim();
        return sha256Hex(normalizedEmail + "|" + normalizedIp);
    }

    /**
     * Computes SHA-256 hex digest.
     *
     * @param value source value
     * @return lowercase hex digest
     */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    /**
     * Represents a rate limit decision.
     *
     * @param allowed           true if the request is allowed, false otherwise.
     * @param retryAfterSeconds the number of seconds to wait before retrying.
     */
    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        /**
         * Creates a new allow decision.
         *
         * @return a new allow decision.
         */
        public static RateLimitDecision allow() {
            return new RateLimitDecision(true, 0);
        }

        /**
         * Creates a new block decision.
         *
         * @param retryAfterSeconds the number of seconds to wait before retrying.
         * @return a new block decision.
         */
        public static RateLimitDecision block(long retryAfterSeconds) {
            return new RateLimitDecision(false, Math.max(retryAfterSeconds, 1));
        }
    }
}
