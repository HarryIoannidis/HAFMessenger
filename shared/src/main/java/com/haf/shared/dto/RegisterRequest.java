package com.haf.shared.dto;

import java.io.Serializable;

/**
 * Registration request DTO sent by the client.
 *
 * Contains all fields collected during the registration flow.
 * Password is sent in plaintext over TLS; the server hashes it before storage.
 */
public class RegisterRequest implements Serializable {
    public String fullName;
    public String regNumber;
    public String idNumber;
    public String rank;
    public String telephone;
    public String email;
    public String password;
    public String publicKeyPem;
    public String publicKeyFingerprint;

    /**
     * E2E-encrypted ID card photo (client-side encrypted, server cannot decrypt).
     */
    public EncryptedFileDTO idPhoto;

    /**
     * E2E-encrypted selfie photo (client-side encrypted, server cannot decrypt).
     */
    public EncryptedFileDTO selfiePhoto;
}
