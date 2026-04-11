package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.security.JwtTokenService;
import com.haf.server.security.JwtTokenService.VerifiedToken;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Data-access object for authentication sessions.
 */
public final class Session {

    private static final String DEFAULT_JWT_ISSUER = "haf-server";
    private static final long DEFAULT_ACCESS_TTL_SECONDS = 900L;
    private static final long DEFAULT_REFRESH_TTL_SECONDS = 2_592_000L;
    private static final long DEFAULT_ABSOLUTE_SESSION_TTL_SECONDS = 2_592_000L;
    private static final long DEFAULT_IDLE_SESSION_TTL_SECONDS = 600L;

    private static final String INSERT_SQL = """
            INSERT INTO sessions (
                session_id,
                access_jti,
                user_id,
                refresh_token_hash,
                issued_at,
                access_expires_at,
                refresh_expires_at,
                refresh_last_rotated_at,
                last_seen_at,
                revoked,
                absolute_expires_at
            ) VALUES (
                ?, ?, ?, ?,
                CURRENT_TIMESTAMP,
                DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND),
                DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND),
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP,
                FALSE,
                DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
            )
            """;

    private static final String SELECT_USER_BY_JTI_SQL = """
            SELECT user_id FROM sessions
            WHERE access_jti = ?
              AND last_seen_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
              AND access_expires_at > CURRENT_TIMESTAMP
              AND absolute_expires_at > CURRENT_TIMESTAMP
              AND revoked = FALSE
            LIMIT 1
            """;

    private static final String TOUCH_SESSION_SQL = """
            UPDATE sessions
            SET last_seen_at = CURRENT_TIMESTAMP
            WHERE access_jti = ?
              AND last_seen_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
              AND access_expires_at > CURRENT_TIMESTAMP
              AND absolute_expires_at > CURRENT_TIMESTAMP
              AND revoked = FALSE
            """;

    private static final String SELECT_RECENT_ACTIVE_SQL = """
            SELECT 1 FROM sessions
            WHERE user_id = ?
              AND access_expires_at > CURRENT_TIMESTAMP
              AND absolute_expires_at > CURRENT_TIMESTAMP
              AND revoked = FALSE
              AND last_seen_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
            LIMIT 1
            """;

    private static final String SELECT_REVOKED_BY_ACCESS_JTI_SQL = """
            SELECT revoked
            FROM sessions
            WHERE access_jti = ?
            LIMIT 1
            """;

    private static final String SELECT_REVOKED_BY_REFRESH_HASH_SQL = """
            SELECT revoked
            FROM sessions
            WHERE refresh_token_hash = ?
            LIMIT 1
            """;

    private static final String REVOKE_SESSION_SQL = """
            UPDATE sessions
            SET revoked = TRUE
            WHERE access_jti = ?
            """;

    private static final String REVOKE_ALL_SESSIONS_FOR_USER_SQL = """
            UPDATE sessions
            SET revoked = TRUE
            WHERE user_id = ?
            """;

    private static final String SELECT_REFRESH_ROW_SQL = """
            SELECT session_id, user_id, UNIX_TIMESTAMP(absolute_expires_at) AS absolute_expires_epoch
            FROM sessions
            WHERE refresh_token_hash = ?
              AND last_seen_at >= DATE_SUB(CURRENT_TIMESTAMP, INTERVAL ? SECOND)
              AND refresh_expires_at > CURRENT_TIMESTAMP
              AND absolute_expires_at > CURRENT_TIMESTAMP
              AND revoked = FALSE
            LIMIT 1
            """;

    private static final String ROTATE_REFRESH_SQL = """
            UPDATE sessions
            SET access_jti = ?,
                refresh_token_hash = ?,
                access_expires_at = LEAST(DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND), absolute_expires_at),
                refresh_expires_at = LEAST(DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? SECOND), absolute_expires_at),
                refresh_last_rotated_at = CURRENT_TIMESTAMP,
                last_seen_at = CURRENT_TIMESTAMP
            WHERE session_id = ?
              AND refresh_token_hash = ?
              AND refresh_expires_at > CURRENT_TIMESTAMP
              AND absolute_expires_at > CURRENT_TIMESTAMP
              AND revoked = FALSE
            """;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DataSource dataSource;
    private final AuditLogger auditLogger;
    private final JwtTokenService jwtTokenService;
    private final long refreshTokenTtlSeconds;
    private final long absoluteSessionTtlSeconds;
    private final long sessionIdleTtlSeconds;

    /**
     * Authentication token bundle.
     *
     * @param accessToken                  access JWT
     * @param refreshToken                 refresh token
     * @param accessExpiresAtEpochSeconds  access expiry epoch seconds
     * @param refreshExpiresAtEpochSeconds refresh expiry epoch seconds
     */
    public record SessionTokens(
            String accessToken,
            String refreshToken,
            long accessExpiresAtEpochSeconds,
            long refreshExpiresAtEpochSeconds) {
    }

    /**
     * Creates DAO with test defaults for JWT settings.
     * Production bootstrap should use
     * {@link #Session(DataSource, AuditLogger, JwtTokenService, long, long, long)}.
     */
    public Session(DataSource dataSource, AuditLogger auditLogger) {
        this(dataSource,
                auditLogger,
                new JwtTokenService("test-only-jwt-secret", DEFAULT_JWT_ISSUER, DEFAULT_ACCESS_TTL_SECONDS),
                DEFAULT_REFRESH_TTL_SECONDS,
                DEFAULT_ABSOLUTE_SESSION_TTL_SECONDS);
    }

    /**
     * Creates DAO with explicit JWT and refresh settings.
     *
     * @param dataSource                data source
     * @param auditLogger               audit logger
     * @param jwtTokenService           JWT service
     * @param refreshTokenTtlSeconds    refresh-token TTL in seconds
     * @param absoluteSessionTtlSeconds absolute maximum session lifetime in seconds
     * @throws NullPointerException     when required collaborators are null
     * @throws IllegalArgumentException when TTL values are non-positive
     */
    public Session(DataSource dataSource,
            AuditLogger auditLogger,
            JwtTokenService jwtTokenService,
            long refreshTokenTtlSeconds,
            long absoluteSessionTtlSeconds) {
        this(dataSource,
                auditLogger,
                jwtTokenService,
                refreshTokenTtlSeconds,
                absoluteSessionTtlSeconds,
                resolveDefaultIdleTtlSeconds(jwtTokenService));
    }

    /**
     * Creates DAO with explicit JWT, refresh, and idle-session settings.
     *
     * @param dataSource                data source
     * @param auditLogger               audit logger
     * @param jwtTokenService           JWT service
     * @param refreshTokenTtlSeconds    refresh-token TTL in seconds
     * @param absoluteSessionTtlSeconds absolute maximum session lifetime in seconds
     * @param sessionIdleTtlSeconds     maximum idle time in seconds before session
     *                                  is invalid
     * @throws NullPointerException     when required collaborators are null
     * @throws IllegalArgumentException when TTL values are non-positive
     */
    public Session(DataSource dataSource,
            AuditLogger auditLogger,
            JwtTokenService jwtTokenService,
            long refreshTokenTtlSeconds,
            long absoluteSessionTtlSeconds,
            long sessionIdleTtlSeconds) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
        this.jwtTokenService = Objects.requireNonNull(jwtTokenService, "jwtTokenService");
        if (refreshTokenTtlSeconds <= 0L) {
            throw new IllegalArgumentException("refreshTokenTtlSeconds");
        }
        if (absoluteSessionTtlSeconds <= 0L) {
            throw new IllegalArgumentException("absoluteSessionTtlSeconds");
        }
        if (sessionIdleTtlSeconds <= 0L) {
            throw new IllegalArgumentException("sessionIdleTtlSeconds");
        }
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.absoluteSessionTtlSeconds = absoluteSessionTtlSeconds;
        this.sessionIdleTtlSeconds = sessionIdleTtlSeconds;
    }

    /**
     * Creates a new access/refresh token pair for user.
     *
     * @param userId user id
     * @return access JWT (compat helper)
     */
    public String createSession(String userId) {
        return createSessionTokens(userId).accessToken();
    }

    /**
     * Creates a new access/refresh token pair for user.
     *
     * @param userId user id
     * @return issued token bundle
     */
    public SessionTokens createSessionTokens(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId");
        }

        String sessionRowId = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String refreshToken = generateRefreshToken();
        String refreshTokenHash = sha256Hex(refreshToken);

        Instant issuedAt = Instant.now();
        Instant absoluteExpiresAt = issuedAt.plusSeconds(absoluteSessionTtlSeconds);
        Instant accessExpiresAt = minInstant(issuedAt.plusSeconds(jwtTokenService.getAccessTtlSeconds()),
                absoluteExpiresAt);
        Instant refreshExpiresAt = minInstant(issuedAt.plusSeconds(refreshTokenTtlSeconds), absoluteExpiresAt);

        String accessToken = jwtTokenService.issueAccessToken(userId, accessJti, issuedAt, accessExpiresAt);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {

            ps.setString(1, sessionRowId);
            ps.setString(2, accessJti);
            ps.setString(3, userId);
            ps.setString(4, refreshTokenHash);
            ps.setLong(5, jwtTokenService.getAccessTtlSeconds());
            ps.setLong(6, refreshTokenTtlSeconds);
            ps.setLong(7, absoluteSessionTtlSeconds);
            ps.executeUpdate();

            return new SessionTokens(
                    accessToken,
                    refreshToken,
                    accessExpiresAt.getEpochSecond(),
                    refreshExpiresAt.getEpochSecond());
        } catch (SQLException ex) {
            auditLogger.logError("db_create_session", null, userId, ex);
            throw new DatabaseOperationException("Failed to create session", ex);
        }
    }

    /**
     * Rotates token pair using valid refresh token.
     *
     * @param refreshToken refresh token
     * @return new token bundle, or null when refresh token is invalid/expired
     */
    public SessionTokens refreshSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }

        String oldRefreshHash = sha256Hex(refreshToken);

        try (Connection connection = dataSource.getConnection()) {
            RefreshSessionRow refreshRow = loadRefreshSessionRow(connection, oldRefreshHash);
            if (refreshRow == null) {
                return null;
            }

            String newAccessJti = UUID.randomUUID().toString();
            String newRefreshToken = generateRefreshToken();
            String newRefreshHash = sha256Hex(newRefreshToken);

            Instant issuedAt = Instant.now();
            Instant absoluteExpiresAt = Instant.ofEpochSecond(refreshRow.absoluteExpiresAtEpochSeconds());
            Instant accessExpiresAt = minInstant(
                    issuedAt.plusSeconds(jwtTokenService.getAccessTtlSeconds()),
                    absoluteExpiresAt);
            Instant refreshExpiresAt = minInstant(
                    issuedAt.plusSeconds(refreshTokenTtlSeconds),
                    absoluteExpiresAt);
            if (!accessExpiresAt.isAfter(issuedAt) || !refreshExpiresAt.isAfter(issuedAt)) {
                return null;
            }
            String accessToken = jwtTokenService.issueAccessToken(
                    refreshRow.userId(),
                    newAccessJti,
                    issuedAt,
                    accessExpiresAt);

            if (!rotateRefreshSession(
                    connection,
                    refreshRow.sessionId(),
                    oldRefreshHash,
                    newAccessJti,
                    newRefreshHash,
                    jwtTokenService.getAccessTtlSeconds(),
                    refreshTokenTtlSeconds)) {
                return null;
            }

            return new SessionTokens(
                    accessToken,
                    newRefreshToken,
                    accessExpiresAt.getEpochSecond(),
                    refreshExpiresAt.getEpochSecond());
        } catch (SQLException ex) {
            auditLogger.logError("db_refresh_session", null, null, ex);
            throw new DatabaseOperationException("Failed to refresh session", ex);
        }
    }

    /**
     * Resolves user id from valid access JWT.
     *
     * @param accessToken access JWT
     * @return user id or null when invalid
     */
    public String getUserIdForSession(String accessToken) {
        VerifiedToken verifiedToken = parseVerifiedAccessToken(accessToken);
        if (verifiedToken == null) {
            return null;
        }

        try (Connection connection = dataSource.getConnection()) {
            String userId = loadUserIdForActiveSession(connection, verifiedToken.jti());
            if (userId == null) {
                return null;
            }
            if (!userId.equals(verifiedToken.userId())) {
                return null;
            }
            return userId;
        } catch (SQLException ex) {
            auditLogger.logError("db_verify_session", null, "unknown", ex);
            return null;
        }
    }

    /**
     * Resolves user id from valid access JWT and updates last seen timestamp.
     *
     * @param accessToken access JWT
     * @return user id or null when invalid
     */
    public String getUserIdForSessionAndTouch(String accessToken) {
        VerifiedToken verifiedToken = parseVerifiedAccessToken(accessToken);
        if (verifiedToken == null) {
            return null;
        }

        try (Connection connection = dataSource.getConnection()) {
            String userId = loadUserIdForActiveSession(connection, verifiedToken.jti());
            if (userId == null) {
                return null;
            }
            if (!userId.equals(verifiedToken.userId())) {
                return null;
            }
            if (touchSession(connection, verifiedToken.jti())) {
                return userId;
            }
            return null;
        } catch (SQLException ex) {
            auditLogger.logError("db_verify_touch_session", null, "unknown", ex);
            return null;
        }
    }

    /**
     * Returns whether user has at least one recently active valid access session.
     *
     * @param userId        user identifier
     * @param withinSeconds recency threshold in seconds
     * @return true when recently active session exists
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
     * Checks whether access token maps to a session row explicitly marked revoked.
     *
     * @param accessToken access JWT
     * @return {@code true} when matching session row exists and is revoked
     */
    public boolean isAccessSessionRevoked(String accessToken) {
        VerifiedToken verifiedToken = parseVerifiedAccessToken(accessToken);
        if (verifiedToken == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return loadRevokedFlagByAccessJti(connection, verifiedToken.jti());
        } catch (SQLException ex) {
            auditLogger.logError("db_check_revoked_access_session", null, verifiedToken.jti(), ex);
            return false;
        }
    }

    /**
     * Checks whether refresh token maps to a session row explicitly marked revoked.
     *
     * @param refreshToken refresh token
     * @return {@code true} when matching session row exists and is revoked
     */
    public boolean isRefreshSessionRevoked(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        String refreshTokenHash = sha256Hex(refreshToken);
        try (Connection connection = dataSource.getConnection()) {
            return loadRevokedFlagByRefreshHash(connection, refreshTokenHash);
        } catch (SQLException ex) {
            auditLogger.logError("db_check_revoked_refresh_session", null, null, ex);
            return false;
        }
    }

    /**
     * Revokes session represented by access JWT.
     *
     * @param accessToken access JWT
     */
    public void revokeSession(String accessToken) {
        VerifiedToken verifiedToken = parseVerifiedAccessToken(accessToken);
        if (verifiedToken == null) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(REVOKE_SESSION_SQL)) {
            ps.setString(1, verifiedToken.jti());
            ps.executeUpdate();
        } catch (SQLException ex) {
            auditLogger.logError("db_revoke_session", null, verifiedToken.jti(), ex);
            throw new DatabaseOperationException("Failed to revoke session", ex);
        }
    }

    /**
     * Revokes all sessions for user.
     *
     * @param userId user id
     */
    public void revokeAllSessionsByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(REVOKE_ALL_SESSIONS_FOR_USER_SQL)) {
            ps.setString(1, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            auditLogger.logError("db_revoke_all_sessions", null, userId, ex);
            throw new DatabaseOperationException("Failed to revoke all sessions for user", ex);
        }
    }

    private VerifiedToken parseVerifiedAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        try {
            return jwtTokenService.verifyAccessToken(accessToken);
        } catch (JwtTokenService.JwtValidationException _) {
            return null;
        }
    }

    private String loadUserIdForActiveSession(Connection connection, String accessJti) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_USER_BY_JTI_SQL)) {
            ps.setString(1, accessJti);
            ps.setLong(2, sessionIdleTtlSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("user_id");
                }
            }
        }
        return null;
    }

    private boolean touchSession(Connection connection, String accessJti) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(TOUCH_SESSION_SQL)) {
            ps.setString(1, accessJti);
            ps.setLong(2, sessionIdleTtlSeconds);
            return ps.executeUpdate() == 1;
        }
    }

    private boolean loadRevokedFlagByAccessJti(Connection connection, String accessJti) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_REVOKED_BY_ACCESS_JTI_SQL)) {
            ps.setString(1, accessJti);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("revoked");
                }
            }
        }
        return false;
    }

    private boolean loadRevokedFlagByRefreshHash(Connection connection, String refreshTokenHash) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_REVOKED_BY_REFRESH_HASH_SQL)) {
            ps.setString(1, refreshTokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("revoked");
                }
            }
        }
        return false;
    }

    private RefreshSessionRow loadRefreshSessionRow(Connection connection, String refreshTokenHash)
            throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT_REFRESH_ROW_SQL)) {
            ps.setString(1, refreshTokenHash);
            ps.setLong(2, sessionIdleTtlSeconds);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RefreshSessionRow(
                            rs.getString("session_id"),
                            rs.getString("user_id"),
                            rs.getLong("absolute_expires_epoch"));
                }
            }
        }
        return null;
    }

    private static Instant minInstant(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    /**
     * Resolves default idle-session TTL used by constructors that do not receive an
     * explicit idle TTL.
     *
     * Keeps the historical minimum (10 minutes) and automatically extends it when
     * access-token TTL is configured above that baseline so refresh windows remain
     * reachable.
     *
     * @param jwtTokenService JWT service containing configured access-token TTL
     * @return derived idle-session TTL in seconds
     */
    private static long resolveDefaultIdleTtlSeconds(JwtTokenService jwtTokenService) {
        Objects.requireNonNull(jwtTokenService, "jwtTokenService");
        return Math.max(DEFAULT_IDLE_SESSION_TTL_SECONDS, jwtTokenService.getAccessTtlSeconds());
    }

    private boolean rotateRefreshSession(Connection connection,
            String sessionId,
            String oldRefreshTokenHash,
            String newAccessJti,
            String newRefreshTokenHash,
            long accessTtlSeconds,
            long refreshTtlSeconds) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(ROTATE_REFRESH_SQL)) {
            ps.setString(1, newAccessJti);
            ps.setString(2, newRefreshTokenHash);
            ps.setLong(3, accessTtlSeconds);
            ps.setLong(4, refreshTtlSeconds);
            ps.setString(5, sessionId);
            ps.setString(6, oldRefreshTokenHash);
            return ps.executeUpdate() == 1;
        }
    }

    private static String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record RefreshSessionRow(String sessionId, String userId, long absoluteExpiresAtEpochSeconds) {
    }
}
