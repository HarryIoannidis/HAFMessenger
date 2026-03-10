package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.shared.dto.EncryptedFileDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FileUploadDAO}.
 * Verifies that encrypted file blobs are persisted with all required metadata.
 */
@ExtendWith(MockitoExtension.class)
class FileUploadDAOTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement insertStatement;

    private FileUploadDAO dao;

    @BeforeEach
    void setUp() {
        dao = new FileUploadDAO(dataSource);
    }

    @Test
    void insert_returns_non_blank_file_id() throws SQLException {
        // Arrange
        EncryptedFileDTO dto = buildValidDto();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        // Act
        String fileId = dao.insert(dto, "user-uuid-123");

        // Assert
        assertNotNull(fileId);
        assertFalse(fileId.isBlank());
    }

    @Test
    void insert_executes_update_exactly_once() throws SQLException {
        EncryptedFileDTO dto = buildValidDto();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        dao.insert(dto, "user-uuid-123");

        verify(insertStatement, times(1)).executeUpdate();
    }

    @Test
    void insert_sets_uploader_id_as_second_param() throws SQLException {
        EncryptedFileDTO dto = buildValidDto();
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        String uploaderId = "user-uuid-abc";
        dao.insert(dto, uploaderId);

        // param 2 = uploader_id
        verify(insertStatement).setString(2, uploaderId);
    }

    @Test
    void insert_sets_content_type() throws SQLException {
        EncryptedFileDTO dto = buildValidDto();
        dto.setContentType("image/png");

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        dao.insert(dto, "user-id");

        // param 4 = content_type
        verify(insertStatement).setString(4, "image/png");
    }

    @Test
    void insert_uses_fallback_content_type_when_null() throws SQLException {
        EncryptedFileDTO dto = buildValidDto();
        dto.setContentType(null);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(insertStatement);
        when(insertStatement.executeUpdate()).thenReturn(1);

        dao.insert(dto, "user-id");

        // Should fall back to "application/octet-stream"
        verify(insertStatement).setString(4, "application/octet-stream");
    }

    @Test
    void insert_wraps_sql_exception() throws SQLException {
        EncryptedFileDTO dto = buildValidDto();
        when(dataSource.getConnection()).thenThrow(new SQLException("connection failed"));

        assertThrows(DatabaseOperationException.class, () -> dao.insert(dto, "user-id"));
    }

    @Test
    void insert_requires_non_null_dto() {
        assertThrows(NullPointerException.class, () -> dao.insert(null, "user-id"));
    }

    @Test
    void insert_requires_non_null_uploader_id() {
        assertThrows(NullPointerException.class, () -> dao.insert(buildValidDto(), null));
    }

    private static EncryptedFileDTO buildValidDto() {
        EncryptedFileDTO dto = new EncryptedFileDTO();
        // Base64-encode some fake ciphertext so the DAO doesn't choke on decode
        dto.setCiphertextB64(Base64.getEncoder().encodeToString("fake-ciphertext-bytes".getBytes()));
        dto.setIvB64(Base64.getEncoder().encodeToString(new byte[12]));
        dto.setTagB64(Base64.getEncoder().encodeToString(new byte[16]));
        dto.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
        dto.setContentType("image/jpeg");
        dto.setOriginalSize(204800L); // 200 KB
        return dto;
    }
}
