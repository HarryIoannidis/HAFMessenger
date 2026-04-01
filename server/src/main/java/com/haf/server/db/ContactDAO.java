package com.haf.server.db;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.haf.server.exceptions.DatabaseOperationException;

/**
 * Provides database access for contact relationships and contact listings.
 */
public class ContactDAO {

    private final HikariDataSource dataSource;

    /**
     * SQL query for selecting accepted contacts for a user.
     */
    private static final String SELECT_CONTACTS_SQL = """
                SELECT u.user_id, u.full_name, u.reg_number, u.email, u.rank, u.telephone, u.joined_date
                FROM contacts c
                JOIN users u ON c.contact_id = u.user_id
                WHERE c.user_id = ?
                ORDER BY u.full_name ASC
            """;

    /**
     * SQL query for inserting or re-activating a contact relationship.
     */
    private static final String UPSERT_CONTACT_SQL = """
                INSERT INTO contacts (user_id, contact_id, status)
                VALUES (?, ?, 'ACCEPTED')
                ON DUPLICATE KEY UPDATE status = 'ACCEPTED'
            """;

    /**
     * SQL query for removing a contact relationship.
     */
    private static final String DELETE_CONTACT_SQL = "DELETE FROM contacts WHERE user_id = ? AND contact_id = ?";

    /**
     * SQL query for selecting watcher user ids for a contact.
     */
    private static final String SELECT_WATCHERS_SQL = """
                SELECT user_id
                FROM contacts
                WHERE contact_id = ?
                  AND status = 'ACCEPTED'
            """;

    /**
     * Creates a contact DAO backed by the provided pooled data source.
     *
     * @param dataSource JDBC connection pool
     */
    public ContactDAO(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record ContactRecord(String userId, String fullName, String regNumber, String email, String rank,
            String telephone, String joinedDate) {
    }

    /**
     * Loads accepted contacts for a user ordered by contact name.
     *
     * @param userId owner user id
     * @return contact records visible to the user
     * @throws DatabaseOperationException when database access fails
     */
    public List<ContactRecord> getContacts(String userId) {
        List<ContactRecord> contacts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(SELECT_CONTACTS_SQL)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    contacts.add(new ContactRecord(
                            rs.getString("user_id"),
                            rs.getString("full_name"),
                            rs.getString("reg_number"),
                            rs.getString("email"),
                            rs.getString("rank"),
                            rs.getString("telephone"),
                            toIsoDate(rs, "joined_date")));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseOperationException("Error fetching contacts for user " + userId, e);
        }
        return contacts;
    }

    /**
     * Adds or re-activates a contact relationship.
     *
     * @param userId    owner user id
     * @param contactId target contact user id
     * @throws DatabaseOperationException when database access fails
     */
    public void addContact(String userId, String contactId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(UPSERT_CONTACT_SQL)) {
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseOperationException("Error adding contact " + contactId + " for user " + userId, e);
        }
    }

    /**
     * Removes a contact relationship for the owner user.
     *
     * @param userId    owner user id
     * @param contactId contact user id to remove
     * @throws DatabaseOperationException when database access fails
     */
    public void removeContact(String userId, String contactId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(DELETE_CONTACT_SQL)) {
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseOperationException("Error removing contact " + contactId + " from user " + userId, e);
        }
    }

    /**
     * Returns users that currently watch a contact's presence (accepted relation).
     *
     * @param contactId contact user id being watched
     * @return watcher user ids
     * @throws DatabaseOperationException when database access fails
     */
    public List<String> getWatcherUserIds(String contactId) {
        List<String> watcherUserIds = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(SELECT_WATCHERS_SQL)) {
            stmt.setString(1, contactId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    watcherUserIds.add(rs.getString("user_id"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseOperationException("Error fetching watchers for contact " + contactId, e);
        }
        return watcherUserIds;
    }

    /**
     * Reads a SQL DATE column and converts it to ISO local-date text.
     *
     * @param rs          result set containing the date column
     * @param columnLabel target column label
     * @return ISO date string, or {@code null} when DB value is NULL
     * @throws SQLException when column access fails
     */
    private static String toIsoDate(ResultSet rs, String columnLabel) throws SQLException {
        java.sql.Date value = rs.getDate(columnLabel);
        return value == null ? null : value.toLocalDate().toString();
    }
}
