package com.haf.shared.requests;

import com.haf.shared.dto.EncryptedFileDTO;
import java.io.Serializable;

/**
 * Registration request DTO sent by the client.
 *
 * Contains all fields collected during the registration flow.
 * Password is sent in plaintext over TLS; the server hashes it before storage.
 */
public class RegisterRequest implements Serializable {
    private String fullName;
    private String regNumber;
    private String idNumber;
    private String rank;
    private String telephone;
    private String email;
    private String password;
    private String publicKeyPem;
    private String publicKeyFingerprint;
    private EncryptedFileDTO idPhoto;
    private EncryptedFileDTO selfiePhoto;

    public RegisterRequest() {
        // Required for JSON deserialization
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    public String getIdNumber() {
        return idNumber;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    public EncryptedFileDTO getIdPhoto() {
        return idPhoto;
    }

    public void setIdPhoto(EncryptedFileDTO idPhoto) {
        this.idPhoto = idPhoto;
    }

    public EncryptedFileDTO getSelfiePhoto() {
        return selfiePhoto;
    }

    public void setSelfiePhoto(EncryptedFileDTO selfiePhoto) {
        this.selfiePhoto = selfiePhoto;
    }
}
