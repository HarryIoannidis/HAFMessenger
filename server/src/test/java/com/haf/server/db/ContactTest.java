package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactTest {

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private Contact dao;

    @BeforeEach
    void setUp() {
        dao = new Contact(dataSource);
    }

    @Test
    void getWatcherUserIds_returns_accepted_watchers() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE contact_id = ?"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("user_id")).thenReturn("watcher-1", "watcher-2");

        List<String> watcherIds = dao.getWatcherUserIds("contact-1");

        assertEquals(List.of("watcher-1", "watcher-2"), watcherIds);
    }

    @Test
    void getWatcherUserIds_throws_on_sql_error() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("WHERE contact_id = ?"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.getWatcherUserIds("contact-1"));
    }

    @Test
    void getContacts_returns_sorted_contact_records() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("FROM contacts c"))).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("user_id")).thenReturn("u-1", "u-2");
        when(resultSet.getString("full_name")).thenReturn("Alice", "Bob");
        when(resultSet.getString("reg_number")).thenReturn("REG-1", "REG-2");
        when(resultSet.getString("email")).thenReturn("a@haf.gr", "b@haf.gr");
        when(resultSet.getString("rank")).thenReturn("SMINIAS", "SMINIAS");
        when(resultSet.getString("telephone")).thenReturn("6900000001", "6900000002");
        when(resultSet.getDate("joined_date")).thenReturn(Date.valueOf("2026-01-01"), Date.valueOf("2026-01-02"));

        List<Contact.ContactRecord> contacts = dao.getContacts("caller-1");

        assertEquals(2, contacts.size());
        assertEquals("u-1", contacts.get(0).userId());
        assertEquals("Alice", contacts.get(0).fullName());
        assertEquals("2026-01-01", contacts.get(0).joinedDate());
        assertEquals("u-2", contacts.get(1).userId());
    }

    @Test
    void addContact_executes_upsert() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT INTO contacts"))).thenReturn(preparedStatement);

        dao.addContact("caller-1", "contact-1");

        verify(preparedStatement).setString(1, "caller-1");
        verify(preparedStatement).setString(2, "contact-1");
        verify(preparedStatement).executeUpdate();
    }

    @Test
    void removeContact_executes_delete() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("DELETE FROM contacts"))).thenReturn(preparedStatement);

        dao.removeContact("caller-1", "contact-1");

        verify(preparedStatement).setString(1, "caller-1");
        verify(preparedStatement).setString(2, "contact-1");
        verify(preparedStatement).executeUpdate();
    }

    @Test
    void addContact_wraps_sql_errors() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT INTO contacts"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("broken"));

        DatabaseOperationException ex = assertThrows(DatabaseOperationException.class,
                () -> dao.addContact("caller-1", "contact-1"));
        assertTrue(ex.getMessage().contains("Error adding contact"));
    }

    @Test
    void removeContact_wraps_sql_errors() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("DELETE FROM contacts"))).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenThrow(new SQLException("broken"));

        DatabaseOperationException ex = assertThrows(DatabaseOperationException.class,
                () -> dao.removeContact("caller-1", "contact-1"));
        assertTrue(ex.getMessage().contains("Error removing contact"));
    }
}
