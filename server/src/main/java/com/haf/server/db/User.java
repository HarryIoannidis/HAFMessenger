package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import com.haf.shared.requests.RegisterRequest;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Data-access object for the {@code users} table.
 *
 * Follows the same DataSource + AuditLogger pattern as {@link Envelope}.
 */
public final class User {

    private final DataSource dataSource;
    private final AuditLogger auditLogger;

    /**
     * SQL query for inserting a pending user record.
     */
    private static final String INSERT_SQL = """
            INSERT INTO users (
                user_id,
                username,
                email,
                password_hash,
                `rank`,
                reg_number,
                id_number,
                full_name,
                telephone,
                public_key_fingerprint,
                public_key_pem,
                signing_public_key_fingerprint,
                signing_public_key_pem,
                `status`,
                `role`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'USER')
            """;

    /**
     * SQL query for updating stored registration photo ids.
     */
    private static final String UPDATE_PHOTOS_SQL = """
            UPDATE users SET id_photo_id = ?, selfie_photo_id = ? WHERE user_id = ?
            """;

    /**
     * SQL query for checking whether an email already exists.
     */
    private static final String EXISTS_BY_EMAIL_SQL = """
            SELECT 1 FROM users WHERE email = ? LIMIT 1
            """;

    /**
     * SQL query for fetching login/profile fields by email.
     */
    private static final String FIND_BY_EMAIL_SQL = """
            SELECT user_id, password_hash, full_name, `rank`, reg_number, email, telephone, joined_date, `status`
            FROM users WHERE email = ? LIMIT 1
            """;

    /**
     * SQL query for selecting stored public-key material by user id.
     */
    private static final String SELECT_PUBLIC_KEY_SQL = """
            SELECT public_key_pem, public_key_fingerprint, signing_public_key_pem, signing_public_key_fingerprint
            FROM users WHERE user_id = ?
            """;

    /**
     * SQL query for rotating stored public key material by user id.
     */
    private static final String UPDATE_PUBLIC_KEY_SQL = """
            UPDATE users
            SET public_key_pem = ?, public_key_fingerprint = ?,
                signing_public_key_pem = ?, signing_public_key_fingerprint = ?
            WHERE user_id = ?
            """;

    /**
     * Represents a user record returned by {@link #findByEmail(String)}.
     */
    public record UserRecord(String userId, String passwordHash,
            String fullName, String rank, String regNumber, String email, String telephone, String joinedDate,
            String status) {
    }

    /**
     * Creates a User with a DataSource and AuditLogger.
     *
     * @param dataSource  the DataSource
     * @param auditLogger the AuditLogger
     */
    public User(DataSource dataSource, AuditLogger auditLogger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
    }

    /**
     * Inserts a new user into the database.
     *
     * @param request        the registration request
     * @param hashedPassword the BCrypt-hashed password
     * @return the generated user ID
     * @throws DatabaseOperationException if the insert fails
     */
    public String insert(RegisterRequest request, String hashedPassword) {
        String userId = UUID.randomUUID().toString();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {

            ps.setString(1, userId);
            ps.setString(2, request.getFullName()); // username = full name
            ps.setString(3, request.getEmail());
            ps.setString(4, hashedPassword);
            ps.setString(5, request.getRank());
            ps.setString(6, request.getRegNumber());
            ps.setString(7, request.getIdNumber());
            ps.setString(8, request.getFullName());
            ps.setString(9, request.getTelephone());
            ps.setString(10, request.getPublicKeyFingerprint());
            ps.setString(11, request.getPublicKeyPem());
            ps.setString(12, request.getSigningPublicKeyFingerprint());
            ps.setString(13, request.getSigningPublicKeyPem());

            ps.executeUpdate();

            return userId;
        } catch (SQLException ex) {
            auditLogger.logError("db_insert_user", null, request.getEmail(), ex);

            throw new DatabaseOperationException("Failed to register user", ex);
        }
    }

    /**
     * Updates the photo IDs on an existing user record.
     *
     * @param userId        the user's UUID
     * @param idPhotoId     file_id of the ID photo, or {@code null}
     * @param selfiePhotoId file_id of the selfie photo, or {@code null}
     */
    public void updatePhotoIds(String userId, String idPhotoId, String selfiePhotoId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(UPDATE_PHOTOS_SQL)) {
            ps.setString(1, idPhotoId);
            ps.setString(2, selfiePhotoId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            auditLogger.logError("db_update_photos", null, userId, ex);
            throw new DatabaseOperationException("Failed to update user photo IDs", ex);
        }
    }

    /**
     * Checks whether a user with the given email already exists.
     *
     * @param email the email address to check
     * @return true if a user with this email exists
     */
    public boolean existsByEmail(String email) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(EXISTS_BY_EMAIL_SQL)) {

            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            return rs.next();
        } catch (SQLException ex) {
            auditLogger.logError("db_check_email", null, email, ex);

            throw new DatabaseOperationException("Failed to check email existence", ex);
        }
    }

    /**
     * Looks up a user by email address.
     *
     * @param email the email address to look up
     * @return the matching {@link UserRecord}, or {@code null} if not found
     */
    public UserRecord findByEmail(String email) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(FIND_BY_EMAIL_SQL)) {

            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) {
                return null;
            }

            return new UserRecord(
                    rs.getString("user_id"),
                    rs.getString("password_hash"),
                    rs.getString("full_name"),
                    rs.getString("rank"),
                    rs.getString("reg_number"),
                    rs.getString("email"),
                    rs.getString("telephone"),
                    toIsoDate(rs, "joined_date"),
                    rs.getString("status"));
        } catch (SQLException ex) {
            auditLogger.logError("db_find_user", null, email, ex);

            throw new DatabaseOperationException("Failed to find user by email", ex);
        }
    }

    /**
     * DTO for returning public key information.
     */
    public record PublicKeyRecord(String publicKeyPem, String fingerprint, String signingPublicKeyPem,
            String signingFingerprint) {
    }

    /**
     * Fetches public key details by user ID.
     *
     * @param userId the user ID to search for
     * @return the record, or null if not found
     * @throws DatabaseOperationException if a database error occurs
     */
    public PublicKeyRecord getPublicKey(String userId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(SELECT_PUBLIC_KEY_SQL)) {

            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PublicKeyRecord(
                            rs.getString("public_key_pem"),
                            rs.getString("public_key_fingerprint"),
                            rs.getString("signing_public_key_pem"),
                            rs.getString("signing_public_key_fingerprint"));
                }
                return null;
            }
        } catch (SQLException e) {
            auditLogger.logError("db_find_public_key", null, userId, e);
            throw new DatabaseOperationException("Failed to fetch public key for user: " + userId, e);
        }
    }

    /**
     * Updates the public key material for a user account.
     *
     * @param userId              user id whose key should be rotated
     * @param publicKeyPem        PEM-encoded X25519 public key
     * @param fingerprint         SHA-256 fingerprint of the public key
     * @param signingPublicKeyPem PEM-encoded Ed25519 signing public key
     * @param signingFingerprint  SHA-256 fingerprint of signing public key
     * @throws DatabaseOperationException when update fails
     */
    public void updatePublicKey(
            String userId,
            String publicKeyPem,
            String fingerprint,
            String signingPublicKeyPem,
            String signingFingerprint) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(UPDATE_PUBLIC_KEY_SQL)) {
            ps.setString(1, publicKeyPem);
            ps.setString(2, fingerprint);
            ps.setString(3, signingPublicKeyPem);
            ps.setString(4, signingFingerprint);
            ps.setString(5, userId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            auditLogger.logError("db_update_public_key", null, userId, ex);
            throw new DatabaseOperationException("Failed to update public key for user: " + userId, ex);
        }
    }

    // ── Search ───────────────────────────────────────────────────────────

    /**
     * Represents a user record returned by user search.
     */
    public record SearchRecord(String userId, String fullName,
            String regNumber, String email, String rank, String telephone, String joinedDate) {
    }

    /**
     * Represents one page of user-search results.
     *
     * @param results      matching rows for the requested page
     * @param hasMore      true when more rows exist
     * @param lastFullName full_name from the last row in this page (cursor key)
     * @param lastUserId   user_id from the last row in this page (cursor key)
     */
    public record SearchPage(List<SearchRecord> results, boolean hasMore, String lastFullName, String lastUserId) {
    }

    /**
     * SQL query for paged search over approved users with keyset cursor conditions.
     */
    private static final String SEARCH_USERS_PAGED_SQL = """
            SELECT user_id, full_name, reg_number, email, `rank`, telephone, joined_date
            FROM users
            WHERE `status` = 'APPROVED'
              AND user_id != ?
              AND (full_name LIKE ? ESCAPE '\\\\'
                   OR reg_number LIKE ? ESCAPE '\\\\'
                   OR `rank` LIKE ? ESCAPE '\\\\'
                   OR username LIKE ? ESCAPE '\\\\'
                   OR email LIKE ? ESCAPE '\\\\')
              AND (? = 0 OR full_name > ? OR (full_name = ? AND user_id > ?))
            ORDER BY full_name ASC, user_id ASC
            LIMIT ?
            """;

    /**
     * Searches for approved users whose profile fields match using SQL contains
     * matching and keyset pagination.
     *
     * @param query          the search term
     * @param excludeUserId  user ID to exclude from results
     * @param limit          page size
     * @param cursorFullName cursor full_name from the previous page (nullable)
     * @param cursorUserId   cursor user_id from the previous page (nullable)
     * @return paged results, never null
     */
    public SearchPage searchUsersPage(String query, String excludeUserId, int limit, String cursorFullName,
            String cursorUserId) {
        String normalizedQuery = query == null ? "" : query;
        String pattern = toContainsPattern(normalizedQuery);
        boolean hasCursor = cursorFullName != null && !cursorFullName.isBlank()
                && cursorUserId != null && !cursorUserId.isBlank();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SEARCH_USERS_PAGED_SQL)) {

            ps.setString(1, excludeUserId);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            ps.setString(5, pattern);
            ps.setString(6, pattern);
            ps.setInt(7, hasCursor ? 1 : 0);
            ps.setString(8, hasCursor ? cursorFullName : null);
            ps.setString(9, hasCursor ? cursorFullName : null);
            ps.setString(10, hasCursor ? cursorUserId : null);
            ps.setInt(11, limit + 1);

            List<SearchRecord> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SearchRecord(
                            rs.getString("user_id"),
                            rs.getString("full_name"),
                            rs.getString("reg_number"),
                            rs.getString("email"),
                            rs.getString("rank"),
                            rs.getString("telephone"),
                            toIsoDate(rs, "joined_date")));
                }
            }

            boolean hasMore = rows.size() > limit;
            if (hasMore) {
                rows.removeLast();
            }

            String lastFullName = null;
            String lastUserId = null;
            if (hasMore && !rows.isEmpty()) {
                SearchRecord last = rows.getLast();
                lastFullName = last.fullName();
                lastUserId = last.userId();
            }

            return new SearchPage(rows, hasMore, lastFullName, lastUserId);
        } catch (SQLException ex) {
            auditLogger.logError("db_search_users", null, excludeUserId, ex, Map.of(
                    "queryLength", normalizedQuery.length(),
                    "queryHash", sha256Hex(normalizedQuery)));
            throw new DatabaseOperationException("Failed to search users", ex);
        }
    }

    /**
     * Legacy non-paginated search helper retained for backwards compatibility in
     * tests and callers that only need the first page.
     * Searches for approved users whose profile fields match,
     * excluding the user performing the search.
     *
     * @param query         the search term
     * @param excludeUserId the user ID to exclude from results
     * @param limit         maximum number of results
     * @return list of matching records, never null
     * @throws DatabaseOperationException if the query fails
     */
    public List<SearchRecord> searchUsers(String query, String excludeUserId, int limit) {
        return searchUsersPage(query, excludeUserId, limit, null, null).results();
    }

    /**
     * Converts raw query text into a SQL LIKE contains pattern with escaping.
     *
     * @param input raw user query text
     * @return escaped pattern wrapped with {@code %...%}
     */
    private static String toContainsPattern(String input) {
        return "%" + escapeLikeLiteral(input) + "%";
    }

    /**
     * Reads a SQL DATE column and converts it to ISO-8601 local-date text.
     *
     * @param rs source result set
     * @param columnLabel column name to read
     * @return ISO date string, or {@code null} when DB value is NULL
     * @throws SQLException when column access fails
     */
    private static String toIsoDate(ResultSet rs, String columnLabel) throws SQLException {
        java.sql.Date value = rs.getDate(columnLabel);
        return value == null ? null : value.toLocalDate().toString();
    }

    /**
     * Escapes SQL LIKE wildcard and escape characters in a literal fragment.
     *
     * @param input literal text fragment
     * @return escaped text safe for LIKE patterns with ESCAPE clause
     */
    private static String escapeLikeLiteral(String input) {
        StringBuilder escaped = new StringBuilder(input.length() + 8);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\\' || ch == '%' || ch == '_') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    /**
     * Computes SHA-256 hex digest for audit-safe query fingerprinting.
     *
     * @param value source value to hash
     * @return lowercase hex SHA-256 digest
     * @throws IllegalStateException when SHA-256 is unavailable in runtime
     */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
