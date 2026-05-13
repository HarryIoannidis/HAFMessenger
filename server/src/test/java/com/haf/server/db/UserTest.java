package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.server.metrics.AuditLogger;
import com.haf.shared.requests.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserTest {

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

    private User dao;

    @BeforeEach
    void setUp() {
        dao = new User(dataSource, auditLogger);
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
        verify(insertStatement).setString(2, request.getFullName()); // username
        verify(insertStatement).setString(3, request.getEmail()); // email
        verify(insertStatement).setString(4, "$2a$12$hashedPasswordValue"); // password_hash
        verify(insertStatement).setString(8, request.getFullName()); // full_name
    }

    @Test
    void insert_throws_on_sql_exception() throws SQLException {
        RegisterRequest request = createValidRequest();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("INSERT"))).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenThrow(new SQLException("Duplicate entry"));

        assertThrows(DatabaseOperationException.class, () -> dao.insert(request, "hashedPass"));
        verify(auditLogger, times(1)).logError(eq("db_insert_user"), isNull(), eq(request.getEmail()), any());
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

        assertThrows(DatabaseOperationException.class, () -> dao.existsByEmail("test@haf.gr"));
        verify(auditLogger, times(1)).logError(eq("db_check_email"), isNull(), eq("test@haf.gr"), any());
    }

    @Test
    void constructor_rejects_null_datasource() {
        assertThrows(NullPointerException.class, () -> new User(null, auditLogger));
    }

    @Test
    void constructor_rejects_null_auditlogger() {
        assertThrows(NullPointerException.class, () -> new User(dataSource, null));
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
        when(resultSet.getString("reg_number")).thenReturn("REG-001");
        when(resultSet.getString("email")).thenReturn("john@haf.gr");
        when(resultSet.getString("telephone")).thenReturn("6900000000");
        when(resultSet.getDate("joined_date")).thenReturn(Date.valueOf("2026-01-01"));
        when(resultSet.getString("status")).thenReturn("APPROVED");

        User.UserRecord userRecord = dao.findByEmail("john@haf.gr");

        assertNotNull(userRecord);
        assertEquals("uid-123", userRecord.userId());
        assertEquals("$2a$12$hash", userRecord.passwordHash());
        assertEquals("John Doe", userRecord.fullName());
        assertEquals("Σμηναγός", userRecord.rank());
        assertEquals("REG-001", userRecord.regNumber());
        assertEquals("john@haf.gr", userRecord.email());
        assertEquals("6900000000", userRecord.telephone());
        assertEquals("2026-01-01", userRecord.joinedDate());
        assertEquals("APPROVED", userRecord.status());
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

        assertThrows(DatabaseOperationException.class, () -> dao.findByEmail("test@haf.gr"));
        verify(auditLogger, times(1)).logError(eq("db_find_user"), isNull(), eq("test@haf.gr"), any());
    }

    @Test
    void updatePhotoIds_updates_row_successfully() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE users SET id_photo_id"))).thenReturn(existsStatement);

        dao.updatePhotoIds("user-1", "id-photo-1", "selfie-1");

        verify(existsStatement).setString(1, "id-photo-1");
        verify(existsStatement).setString(2, "selfie-1");
        verify(existsStatement).setString(3, "user-1");
        verify(existsStatement).executeUpdate();
    }

    @Test
    void updatePhotoIds_wraps_sql_errors() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("UPDATE users SET id_photo_id"))).thenReturn(existsStatement);
        when(existsStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.updatePhotoIds("user-1", "id", "selfie"));
        verify(auditLogger, times(1)).logError(eq("db_update_photos"), isNull(), eq("user-1"), any());
    }

    @Test
    void getPublicKey_returns_record_when_found() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT public_key_pem"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("public_key_pem")).thenReturn("PEM");
        when(resultSet.getString("public_key_fingerprint")).thenReturn("fp-1");
        when(resultSet.getString("signing_public_key_pem")).thenReturn("SIGNING_PEM");
        when(resultSet.getString("signing_public_key_fingerprint")).thenReturn("sign-fp-1");

        User.PublicKeyRecord publicRecord = dao.getPublicKey("user-1");

        assertNotNull(publicRecord);
        assertEquals("PEM", publicRecord.publicKeyPem());
        assertEquals("fp-1", publicRecord.fingerprint());
        assertEquals("SIGNING_PEM", publicRecord.signingPublicKeyPem());
        assertEquals("sign-fp-1", publicRecord.signingFingerprint());
    }

    @Test
    void getPublicKey_returns_null_when_not_found() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT public_key_pem"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        assertNull(dao.getPublicKey("user-1"));
    }

    @Test
    void getPublicKey_wraps_sql_errors() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SELECT public_key_pem"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.getPublicKey("user-1"));
        verify(auditLogger, times(1)).logError(eq("db_find_public_key"), isNull(), eq("user-1"), any());
    }

    @Test
    void updatePublicKey_updates_row_successfully() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SET public_key_pem"))).thenReturn(existsStatement);

        dao.updatePublicKey("user-1", "PEM", "fp-1", "SIGNING_PEM", "sign-fp-1");

        verify(existsStatement).setString(1, "PEM");
        verify(existsStatement).setString(2, "fp-1");
        verify(existsStatement).setString(3, "SIGNING_PEM");
        verify(existsStatement).setString(4, "sign-fp-1");
        verify(existsStatement).setString(5, "user-1");
        verify(existsStatement).executeUpdate();
    }

    @Test
    void updatePublicKey_wraps_sql_errors() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("SET public_key_pem"))).thenReturn(existsStatement);
        when(existsStatement.executeUpdate()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class,
                () -> dao.updatePublicKey("user-1", "PEM", "fp-1", "SIGNING_PEM", "sign-fp-1"));
        verify(auditLogger, times(1)).logError(eq("db_update_public_key"), isNull(), eq("user-1"), any());
    }

    // ── searchUsers tests ────────────────────────────────────────────────

    @Test
    void searchUsers_returns_matching_records() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("LIKE"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, false); // two rows

        when(resultSet.getString("user_id")).thenReturn("uid-1", "uid-2");
        when(resultSet.getString("full_name")).thenReturn("Alice", "Bob");
        when(resultSet.getString("reg_number")).thenReturn("REG-001", "REG-002");
        when(resultSet.getString("email")).thenReturn("alice@haf.gr", "bob@haf.gr");
        when(resultSet.getString("rank")).thenReturn("Σμηναγός", "Σμηνίας");
        when(resultSet.getString("telephone")).thenReturn("6900000001", "6900000002");
        when(resultSet.getDate("joined_date")).thenReturn(Date.valueOf("2026-01-01"), Date.valueOf("2026-01-02"));

        var results = dao.searchUsers("a", "caller-id", 20);

        assertEquals(2, results.size());
        assertEquals("uid-1", results.get(0).userId());
        assertEquals("Alice", results.get(0).fullName());
        assertEquals("6900000001", results.get(0).telephone());
        assertEquals("2026-01-01", results.get(0).joinedDate());
        assertEquals("uid-2", results.get(1).userId());
        assertEquals("6900000002", results.get(1).telephone());
        assertEquals("2026-01-02", results.get(1).joinedDate());
        verify(existsStatement).setString(1, "caller-id");
        verify(existsStatement).setString(2, "%a%");
        verify(existsStatement).setString(3, "%a%");
        verify(existsStatement).setString(4, "%a%");
        verify(existsStatement).setString(5, "%a%");
        verify(existsStatement).setString(6, "%a%");
        verify(existsStatement).setInt(7, 0);
        verify(existsStatement).setInt(11, 21);
    }

    @Test
    void searchUsers_returns_empty_for_no_match() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("LIKE"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        var results = dao.searchUsers("zzz", "some-id", 10);

        assertTrue(results.isEmpty());
    }

    @Test
    void searchUsers_throws_on_sql_exception() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("LIKE"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenThrow(new SQLException("DB error"));

        assertThrows(DatabaseOperationException.class, () -> dao.searchUsers("test", "caller-id", 10));
        verify(auditLogger, times(1)).logError(eq("db_search_users"), isNull(), eq("caller-id"), any(),
                anyMap());
    }

    @Test
    void searchUsersPage_escapes_like_metacharacters() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("LIKE"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);

        dao.searchUsersPage("ab%_\\\\", "caller-id", 20, null, null);

        verify(existsStatement).setString(2, "%ab\\%\\_\\\\\\\\%");
        verify(existsStatement).setString(3, "%ab\\%\\_\\\\\\\\%");
        verify(existsStatement).setString(4, "%ab\\%\\_\\\\\\\\%");
        verify(existsStatement).setString(5, "%ab\\%\\_\\\\\\\\%");
        verify(existsStatement).setString(6, "%ab\\%\\_\\\\\\\\%");
    }

    @Test
    void searchUsersPage_returns_hasMore_and_cursor_keys_when_limit_exceeded() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(contains("ORDER BY full_name"))).thenReturn(existsStatement);
        when(existsStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true, false);

        when(resultSet.getString("user_id")).thenReturn("uid-1", "uid-2", "uid-3");
        when(resultSet.getString("full_name")).thenReturn("Alice", "Bob", "Charlie");
        when(resultSet.getString("reg_number")).thenReturn("REG-001", "REG-002", "REG-003");
        when(resultSet.getString("email")).thenReturn("alice@haf.gr", "bob@haf.gr", "charlie@haf.gr");
        when(resultSet.getString("rank")).thenReturn("Σμηναγός", "Σμηνίας", "Αντισμήναρχος");

        User.SearchPage page = dao.searchUsersPage("a", "caller-id", 2, null, null);

        assertEquals(2, page.results().size());
        assertTrue(page.hasMore());
        assertEquals("Bob", page.lastFullName());
        assertEquals("uid-2", page.lastUserId());
        verify(existsStatement).setInt(11, 3);
    }

    private RegisterRequest createValidRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setFullName("John Doe");
        request.setRegNumber("REG-001");
        request.setIdNumber("ID-12345");
        request.setRank("Σμηναγός");
        request.setTelephone("6912345678");
        request.setEmail("john.doe@haf.gr");
        request.setPassword("SecurePass123!");
        request.setPublicKeyPem("-----BEGIN PUBLIC KEY-----\nMIIBIjAN...test\n-----END PUBLIC KEY-----");
        request.setPublicKeyFingerprint("abc123fingerprint");
        return request;
    }
}
