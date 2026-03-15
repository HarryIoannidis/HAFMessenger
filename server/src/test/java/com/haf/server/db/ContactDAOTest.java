package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactDAOTest {

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    private ContactDAO dao;

    @BeforeEach
    void setUp() {
        dao = new ContactDAO(dataSource);
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
}
