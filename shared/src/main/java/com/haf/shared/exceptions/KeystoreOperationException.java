package com.haf.shared.exceptions;

/**
 * Thrown when a keystore operation fails, such as loading keys,
 * validating the keystore root, or unsealing key material.
 */
public class KeystoreOperationException extends RuntimeException {

    /**
     * Creates a new KeystoreOperationException with a detail message.
     *
     * @param message the detail message
     */
    public KeystoreOperationException(String message) {
        super(message);
    }

    /**
     * Creates a new KeystoreOperationException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public KeystoreOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
