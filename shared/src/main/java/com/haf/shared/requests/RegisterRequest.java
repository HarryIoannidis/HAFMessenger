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

    /**
     * Creates an empty registration request DTO for JSON deserialization.
     */
    public RegisterRequest() {
        // Required for JSON deserialization
    }

    /**
     * Returns full name provided by the registrant.
     *
     * @return full name
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Sets full name provided by the registrant.
     *
     * @param fullName full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns service registration number.
     *
     * @return registration number
     */
    public String getRegNumber() {
        return regNumber;
    }

    /**
     * Sets service registration number.
     *
     * @param regNumber registration number
     */
    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    /**
     * Returns identity document number.
     *
     * @return identity document number
     */
    public String getIdNumber() {
        return idNumber;
    }

    /**
     * Sets identity document number.
     *
     * @param idNumber identity document number
     */
    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    /**
     * Returns rank value.
     *
     * @return rank value
     */
    public String getRank() {
        return rank;
    }

    /**
     * Sets rank value.
     *
     * @param rank rank value
     */
    public void setRank(String rank) {
        this.rank = rank;
    }

    /**
     * Returns telephone number.
     *
     * @return telephone number
     */
    public String getTelephone() {
        return telephone;
    }

    /**
     * Sets telephone number.
     *
     * @param telephone telephone number
     */
    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    /**
     * Returns email address.
     *
     * @return email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets email address.
     *
     * @param email email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns plaintext password provided during registration.
     *
     * @return plaintext password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets plaintext password provided during registration.
     *
     * @param password plaintext password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Returns caller public key in PEM format.
     *
     * @return public key PEM
     */
    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    /**
     * Sets caller public key in PEM format.
     *
     * @param publicKeyPem public key PEM
     */
    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    /**
     * Returns fingerprint for the caller public key.
     *
     * @return public key fingerprint
     */
    public String getPublicKeyFingerprint() {
        return publicKeyFingerprint;
    }

    /**
     * Sets fingerprint for the caller public key.
     *
     * @param publicKeyFingerprint public key fingerprint
     */
    public void setPublicKeyFingerprint(String publicKeyFingerprint) {
        this.publicKeyFingerprint = publicKeyFingerprint;
    }

    /**
     * Returns encrypted ID-photo payload.
     *
     * @return encrypted ID-photo payload
     */
    public EncryptedFileDTO getIdPhoto() {
        return idPhoto;
    }

    /**
     * Sets encrypted ID-photo payload.
     *
     * @param idPhoto encrypted ID-photo payload
     */
    public void setIdPhoto(EncryptedFileDTO idPhoto) {
        this.idPhoto = idPhoto;
    }

    /**
     * Returns encrypted selfie-photo payload.
     *
     * @return encrypted selfie-photo payload
     */
    public EncryptedFileDTO getSelfiePhoto() {
        return selfiePhoto;
    }

    /**
     * Sets encrypted selfie-photo payload.
     *
     * @param selfiePhoto encrypted selfie-photo payload
     */
    public void setSelfiePhoto(EncryptedFileDTO selfiePhoto) {
        this.selfiePhoto = selfiePhoto;
    }
}
