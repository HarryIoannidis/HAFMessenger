package com.haf.server.router;

import javax.sql.DataSource;
import com.haf.server.metrics.AuditLogger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RateLimiterService {

    private static final int WINDOW_SECONDS = 60;
    private static final int MAX_MESSAGES_PER_WINDOW = 100;
    private static final int LOCKOUT_MINUTES = 15;

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

    private final DataSource dataSource;
    private final AuditLogger auditLogger;

    /**
     * Creates a new rate limit service.
     * @param dataSource the database connection pool.
     * @param auditLogger the audit logger.
     */
    public RateLimiterService(DataSource dataSource, AuditLogger auditLogger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    /**
     * Checks and consumes a rate limit for a user.
     * @param requestId the ID of the request.
     * @param userId the ID of the user.
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
            throw new IllegalStateException("Rate limit check failed", ex);
        }
    }

    /**
     * Upserts a new rate limit window for a user.
     * @param connection the database connection.
     * @param userId the ID of the user.
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
     * Represents a rate limit decision.
     * @param allowed true if the request is allowed, false otherwise.
     * @param retryAfterSeconds the number of seconds to wait before retrying.
     */
    public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {

        /**
         * Creates a new allow decision.
         * @return a new allow decision.
         */
        public static RateLimitDecision allow() {
            return new RateLimitDecision(true, 0);
        }

        /**
         * Creates a new block decision.
         * @param retryAfterSeconds the number of seconds to wait before retrying.
         * @return a new block decision.
         */
        public static RateLimitDecision block(long retryAfterSeconds) {
            return new RateLimitDecision(false, Math.max(retryAfterSeconds, 1));
        }
    }
}

