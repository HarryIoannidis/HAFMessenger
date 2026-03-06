package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import com.haf.shared.dto.RegisterRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Data-access object for the {@code users} table.
 *
 * <p>
 * Follows the same DataSource + AuditLogger pattern as {@link EnvelopeDAO}.
 * </p>
 */
public final class UserDAO {

    private final DataSource dataSource;
    private final AuditLogger auditLogger;

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
                `status`,
                `role`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 'USER')
            """;

    private static final String UPDATE_PHOTOS_SQL = """
            UPDATE users SET id_photo_id = ?, selfie_photo_id = ? WHERE user_id = ?
            """;

    private static final String EXISTS_BY_EMAIL_SQL = """
            SELECT 1 FROM users WHERE email = ? LIMIT 1
            """;

    private static final String FIND_BY_EMAIL_SQL = """
            SELECT user_id, password_hash, full_name, `rank`, `status`
            FROM users WHERE email = ? LIMIT 1
            """;

    /**
     * Represents a user record returned by {@link #findByEmail(String)}.
     */
    public record UserRecord(String userId, String passwordHash,
            String fullName, String rank, String status) {
    }

    /**
     * Creates a UserDAO with a DataSource and AuditLogger.
     *
     * @param dataSource  the DataSource
     * @param auditLogger the AuditLogger
     */
    public UserDAO(DataSource dataSource, AuditLogger auditLogger) {
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
            ps.setString(2, request.email); // username = email
            ps.setString(3, request.email);
            ps.setString(4, hashedPassword);
            ps.setString(5, request.rank);
            ps.setString(6, request.regNumber);
            ps.setString(7, request.idNumber);
            ps.setString(8, request.fullName);
            ps.setString(9, request.telephone);
            ps.setString(10, request.publicKeyFingerprint);
            ps.setString(11, request.publicKeyPem);

            ps.executeUpdate();

            return userId;
        } catch (SQLException ex) {
            auditLogger.logError("db_insert_user", null, request.email, ex);

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
                    rs.getString("status"));
        } catch (SQLException ex) {
            auditLogger.logError("db_find_user", null, email, ex);

            throw new DatabaseOperationException("Failed to find user by email", ex);
        }
    }

    /**
     * DTO for returning public key information.
     */
    public record PublicKeyRecord(String publicKeyPem, String fingerprint) {
    }

    /**
     * Fetches public key details by user ID.
     *
     * @param userId the user ID to search for
     * @return the record, or null if not found
     * @throws DatabaseOperationException if a database error occurs
     */
    public PublicKeyRecord getPublicKey(String userId) {
        String sql = "SELECT public_key_pem, public_key_fingerprint FROM users WHERE user_id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PublicKeyRecord(
                            rs.getString("public_key_pem"),
                            rs.getString("public_key_fingerprint"));
                }
                return null;
            }
        } catch (SQLException e) {
            auditLogger.logError("db_find_public_key", null, userId, e);
            throw new DatabaseOperationException("Failed to fetch public key for user: " + userId, e);
        }
    }

    // ── Search ───────────────────────────────────────────────────────────

    /**
     * Represents a user record returned by {@link #searchUsers(String, int)}.
     */
    public record SearchRecord(String userId, String fullName,
            String regNumber, String email, String rank) {
    }

    private static final String SEARCH_USERS_SQL = """
            SELECT user_id, full_name, reg_number, email, `rank`
            FROM users
            WHERE `status` = 'APPROVED'
              AND (full_name LIKE ? OR reg_number LIKE ?)
            LIMIT ?
            """;

    /**
     * Searches for approved users whose name or registration number matches.
     *
     * @param query the search term (matched with {@code %query%})
     * @param limit maximum number of results
     * @return list of matching records, never null
     * @throws DatabaseOperationException if the query fails
     */
    public List<SearchRecord> searchUsers(String query, int limit) {
        String pattern = "%" + query + "%";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement ps = connection.prepareStatement(SEARCH_USERS_SQL)) {

            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setInt(3, limit);

            List<SearchRecord> results = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new SearchRecord(
                            rs.getString("user_id"),
                            rs.getString("full_name"),
                            rs.getString("reg_number"),
                            rs.getString("email"),
                            rs.getString("rank")));
                }
            }
            return results;
        } catch (SQLException ex) {
            auditLogger.logError("db_search_users", null, query, ex);
            throw new DatabaseOperationException("Failed to search users", ex);
        }
    }
}
