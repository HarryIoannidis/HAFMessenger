package com.haf.server.db;

import com.haf.server.exceptions.DatabaseOperationException;
import com.haf.shared.dto.EncryptedFileDTO;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Data-access object for the {@code file_uploads} table.
 *
 * Files are stored as opaque, client-side-encrypted blobs. This DAO
 * deliberately never inspects or decrypts the ciphertext – it is only
 * responsible for persisting and retrieving bytes.
 */
public final class FileUploadDAO {

    private final DataSource dataSource;

    /**
     * SQL query for inserting an encrypted file upload record.
     */
    private static final String INSERT_SQL = """
            INSERT INTO file_uploads (
                file_id,
                uploader_id,
                encrypted_chunks,
                content_type,
                file_size,
                sha256_hash,
                chunk_count,
                iv_b64,
                tag_b64,
                ephemeral_public_b64
            ) VALUES (?, ?, ?, ?, ?, '', 1, ?, ?, ?)
            """;

    /**
     * Creates a FileUploadDAO.
     *
     * @param dataSource the DataSource
     */
    public FileUploadDAO(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /**
     * Persists an {@link EncryptedFileDTO} and returns the generated
     * {@code file_id}.
     *
     * @param dto        the encrypted file metadata and ciphertext
     * @param uploaderId the user_id of the uploader (may not yet exist in
     *                   {@code users})
     * @return the new {@code file_id} UUID string
     * @throws DatabaseOperationException if the insert fails
     */
    public String insert(EncryptedFileDTO dto, String uploaderId) {
        Objects.requireNonNull(dto, "dto");
        Objects.requireNonNull(uploaderId, "uploaderId");

        String fileId = UUID.randomUUID().toString();
        byte[] ciphertext = Base64.getDecoder().decode(dto.getCiphertextB64());

        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {

            ps.setString(1, fileId);
            ps.setString(2, uploaderId);
            ps.setBytes(3, ciphertext);
            ps.setString(4, dto.getContentType() != null ? dto.getContentType() : "application/octet-stream");
            ps.setLong(5, dto.getOriginalSize());
            ps.setString(6, dto.getIvB64());
            ps.setString(7, dto.getTagB64());
            ps.setString(8, dto.getEphemeralPublicB64());

            ps.executeUpdate();
            return fileId;
        } catch (SQLException ex) {
            throw new DatabaseOperationException("Failed to store encrypted file upload", ex);
        }
    }
}
