package com.haf.server.db;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import com.haf.server.exceptions.DatabaseOperationException;

public class ContactDAO {

    private final HikariDataSource dataSource;

    public ContactDAO(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public record ContactRecord(String userId, String fullName, String regNumber, String email, String rank,
            String telephone, String joinedDate) {
    }

    public List<ContactRecord> getContacts(String userId) {
        String sql = """
                    SELECT u.user_id, u.full_name, u.reg_number, u.email, u.rank, u.telephone, u.joined_date
                    FROM contacts c
                    JOIN users u ON c.contact_id = u.user_id
                    WHERE c.user_id = ?
                    ORDER BY u.full_name ASC
                """;
        List<ContactRecord> contacts = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    public void addContact(String userId, String contactId) {
        String sql = """
                    INSERT INTO contacts (user_id, contact_id, status)
                    VALUES (?, ?, 'ACCEPTED')
                    ON DUPLICATE KEY UPDATE status = 'ACCEPTED'
                """;
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseOperationException("Error adding contact " + contactId + " for user " + userId, e);
        }
    }

    public void removeContact(String userId, String contactId) {
        String sql = "DELETE FROM contacts WHERE user_id = ? AND contact_id = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, contactId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseOperationException("Error removing contact " + contactId + " from user " + userId, e);
        }
    }

    public List<String> getWatcherUserIds(String contactId) {
        String sql = """
                    SELECT user_id
                    FROM contacts
                    WHERE contact_id = ?
                      AND status = 'ACCEPTED'
                """;
        List<String> watcherUserIds = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
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

    private static String toIsoDate(ResultSet rs, String columnLabel) throws SQLException {
        java.sql.Date value = rs.getDate(columnLabel);
        return value == null ? null : value.toLocalDate().toString();
    }
}
