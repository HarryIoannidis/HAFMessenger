package com.haf.server.db;

import com.haf.server.metrics.AuditLogger;
import com.haf.shared.dto.RegisterRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
     * @throws IllegalStateException if the insert fails
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

            throw new IllegalStateException("Failed to register user", ex);
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
            throw new IllegalStateException("Failed to update user photo IDs", ex);
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

            throw new IllegalStateException("Failed to check email existence", ex);
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

            throw new IllegalStateException("Failed to find user by email", ex);
        }
    }
}
