package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Data-access object for the {@code sessions} table.
 *
 * Follows the same DataSource + AuditLogger pattern as {@link UserDAO}.
 */
public final class SessionDAO {

    private final DataSource dataSource;
    private final AuditLogger auditLogger;

    /**
     * SQL query for inserting a new session row.
     */
    private static final String INSERT_SQL = """
            INSERT INTO sessions (session_id, user_id, jwt_token, expires_at)
            VALUES (?, ?, ?, ?)
            """;

    /**
     * Creates a SessionDAO with a DataSource and AuditLogger.
     *
     * @param dataSource  the DataSource
     * @param auditLogger the AuditLogger
     */
    public SessionDAO(DataSource dataSource, AuditLogger auditLogger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    /**
     * Creates a new session for the given user.
     *
     * @param userId the user ID
     * @return the generated session ID
     * @throws DatabaseOperationException if the insert fails
     */
    public String createSession(String userId) {
        String sessionId = UUID.randomUUID().toString();
        // Placeholder JWT token until real JWT signing is implemented
        String jwtToken = "jwt-placeholder-" + sessionId;
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {

            ps.setString(1, sessionId);
            ps.setString(2, userId);
            ps.setString(3, jwtToken);
            ps.setTimestamp(4, Timestamp.from(expiresAt));

            ps.executeUpdate();

            return sessionId;
        } catch (SQLException ex) {
            auditLogger.logError("db_create_session", null, userId, ex);

            throw new DatabaseOperationException("Failed to create session", ex);
        }
    }

    /**
     * SQL query for selecting user id from an active, non-revoked session.
     */
    private static final String SELECT_USER_SQL = """
            SELECT user_id FROM sessions
            WHERE session_id = ? AND expires_at > ? AND revoked = FALSE
            """;

    /**
     * SQL query for revoking a session.
     */
    private static final String REVOKE_SESSION_SQL = """
            UPDATE sessions
            SET revoked = TRUE
            WHERE session_id = ?
            """;

    /**
     * Retrieves the user ID for a given valid session ID.
     *
     * @param sessionId the session ID
     * @return the user ID, or null if invalid/expired
     */
    public String getUserIdForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SELECT_USER_SQL)) {

            ps.setString(1, sessionId);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));

            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("user_id");
                }
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_verify_session", null, "unknown", ex);
        }
        return null;
    }

    /**
     * Revokes the given session.
     * If the session does not exist or is already revoked, this is still treated as
     * a successful no-op.
     *
     * @param sessionId the session ID to revoke
     * @throws DatabaseOperationException if the revoke fails
     */
    public void revokeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(REVOKE_SESSION_SQL)) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            auditLogger.logError("db_revoke_session", null, sessionId, ex);
            throw new DatabaseOperationException("Failed to revoke session", ex);
        }
    }
}
