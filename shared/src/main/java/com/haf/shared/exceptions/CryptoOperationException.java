package com.haf.shared.exceptions;

/**
 * Thrown when a cryptographic operation fails, such as key agreement,
 * key derivation, encryption, or decryption.
 */
public class CryptoOperationException extends RuntimeException {

    /**
     * Creates a new CryptoOperationException with a detail message.
     *
     * @param message the detail message
     */
    public CryptoOperationException(String message) {
        super(message);
    }

    /**
     * Creates a new CryptoOperationException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CryptoOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
