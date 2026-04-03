package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
            VALUES (?, ?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 24 HOUR))
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

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {

            ps.setString(1, sessionId);
            ps.setString(2, userId);
            ps.setString(3, jwtToken);

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
            WHERE session_id = ? AND expires_at > CURRENT_TIMESTAMP AND revoked = FALSE
            """;

    /**
     * SQL query for touching active-session last activity timestamp.
     */
    private static final String TOUCH_SESSION_SQL = """
            UPDATE sessions
            SET last_seen_at = CURRENT_TIMESTAMP
            WHERE session_id = ? AND expires_at > CURRENT_TIMESTAMP AND revoked = FALSE
            """;

    /**
     * SQL query for checking whether a user has a recently active session.
     */
    private static final String SELECT_RECENT_ACTIVE_SQL = """
            SELECT 1 FROM sessions
            WHERE user_id = ?
              AND expires_at > CURRENT_TIMESTAMP
              AND revoked = FALSE
              AND last_seen_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
            LIMIT 1
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
     * Retrieves the user id for a valid session and updates its last-seen
     * timestamp.
     *
     * @param sessionId caller session id
     * @return user id when session is valid, otherwise {@code null}
     */
    public String getUserIdForSessionAndTouch(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        try (Connection connection = dataSource.getConnection()) {
            String userId = loadUserIdForActiveSession(connection, sessionId);
            if (userId == null) {
                return null;
            }
            if (touchSession(connection, sessionId)) {
                return userId;
            }
            return null;
        } catch (SQLException ex) {
            auditLogger.logError("db_verify_touch_session", null, "unknown", ex);
            return null;
        }
    }

    /**
     * Returns whether the user has at least one recently active valid session.
     *
     * @param userId        user identifier
     * @param withinSeconds activity recency threshold in seconds
     * @return {@code true} when a recently active session exists
     */
    public boolean isUserRecentlyActive(String userId, long withinSeconds) {
        if (userId == null || userId.isBlank() || withinSeconds <= 0) {
            return false;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SELECT_RECENT_ACTIVE_SQL)) {
            ps.setString(1, userId);
            ps.setLong(2, withinSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            auditLogger.logError("db_recent_active_session", null, userId, ex);
            return false;
        }
    }

    /**
     * Loads user id for an active non-revoked session.
     *
     * @param connection open SQL connection
     * @param sessionId  session id to verify
     * @return user id when session is valid, otherwise {@code null}
     * @throws SQLException when SQL operations fail
     */
    private String loadUserIdForActiveSession(Connection connection, String sessionId)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_USER_SQL)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("user_id");
                }
            }
        }
        return null;
    }

    /**
     * Updates {@code last_seen_at} for a valid non-revoked session.
     *
     * @param connection open SQL connection
     * @param sessionId  session id to touch
     * @return {@code true} when exactly one session row was touched
     * @throws SQLException when SQL operations fail
     */
    private boolean touchSession(Connection connection, String sessionId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(TOUCH_SESSION_SQL)) {
            ps.setString(1, sessionId);
            return ps.executeUpdate() == 1;
        }
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
