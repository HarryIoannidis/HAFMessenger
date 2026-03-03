package com.haf.server.db;

import com.haf.server.metrics.AuditLogger;
import com.haf.shared.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDAOTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement insertStatement;

    @Mock
    private PreparedStatement existsStatement;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private ResultSet resultSet;

    private UserDAO dao;

    @BeforeEach
    void setUp() {
        dao = new UserDAO(dataSource, auditLogger);
    }

    @Test
    void insert_stores_user_successfully() throws SQLException {
        RegisterRequest request = createValidRequest();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        String userId = dao.insert(request, "$2a$12$hashedPasswordValue");

        assertNotNull(userId);
        assertFalse(userId.isBlank());
        verify(insertStatement, times(1)).executeUpdate();
        verify(insertStatement).setString(eq(3), eq(request.email)); // email
        verify(insertStatement).setString(eq(4), eq("$2a$12$hashedPasswordValue")); // password_hash
        verify(insertStatement).setString(eq(8), eq(request.fullName)); // full_name
    }

    @Test
    void insert_throws_on_sql_exception() throws SQLException {
        RegisterRequest request = createValidRequest();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenThrow(new SQLException("Duplicate entry"));

        assertThrows(IllegalStateException.class, () -> dao.insert(request, "hashedPass"));
        verify(auditLogger, times(1)).logError(eq("db_insert_user"), isNull(), eq(request.email), any());
    }

    @Test
    void existsByEmail_returns_true_when_user_exists() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);

        assertTrue(dao.existsByEmail("test@haf.gr"));
        verify(existsStatement).setString(1, "test@haf.gr");
    }

    @Test
    void existsByEmail_returns_false_when_user_not_found() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertFalse(dao.existsByEmail("new@haf.gr"));
    }

    @Test
    void existsByEmail_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(IllegalStateException.class, () -> dao.existsByEmail("test@haf.gr"));
        verify(auditLogger, times(1)).logError(eq("db_check_email"), isNull(), eq("test@haf.gr"), any());
    }

    @Test
    void constructor_rejects_null_datasource() {
        assertThrows(NullPointerException.class, () -> new UserDAO(null, auditLogger));
    }

    @Test
    void constructor_rejects_null_auditlogger() {
        assertThrows(NullPointerException.class, () -> new UserDAO(dataSource, null));
    }

    @Test
    void findByEmail_returns_record_when_found() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT user_id"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("uid-123");
        when(resultSet.getString("password_hash")).thenReturn("$2a$12$hash");
        when(resultSet.getString("full_name")).thenReturn("John Doe");
        when(resultSet.getString("rank")).thenReturn("Σμηναγός");
        when(resultSet.getString("status")).thenReturn("APPROVED");

        UserDAO.UserRecord record = dao.findByEmail("john@haf.gr");

        assertNotNull(record);
        assertEquals("uid-123", record.userId());
        assertEquals("$2a$12$hash", record.passwordHash());
        assertEquals("John Doe", record.fullName());
        assertEquals("Σμηναγός", record.rank());
        assertEquals("APPROVED", record.status());
    }

    @Test
    void findByEmail_returns_null_when_not_found() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT user_id"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertNull(dao.findByEmail("nobody@haf.gr"));
    }

    @Test
    void findByEmail_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT user_id"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(IllegalStateException.class, () -> dao.findByEmail("test@haf.gr"));
        verify(auditLogger, times(1)).logError(eq("db_find_user"), isNull(), eq("test@haf.gr"), any());
    }

    private RegisterRequest createValidRequest() {
        RegisterRequest request = new RegisterRequest();
        request.fullName = "John Doe";
        request.regNumber = "REG-001";
        request.idNumber = "ID-12345";
        request.rank = "Σμηναγός";
        request.telephone = "6912345678";
        request.email = "john.doe@haf.gr";
        request.password = "SecurePass123!";
        request.publicKeyPem = "-----BEGIN PUBLIC KEY-----\nMIIBIjAN...test\n-----END PUBLIC KEY-----";
        request.publicKeyFingerprint = "abc123fingerprint";
        return request;
    }
}
