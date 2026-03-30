package com.haf.shared.exceptions;

/**
 * Thrown when decryption of a message fails, which may include structural validation,
 * tampering detection, expiration, or generic cryptographic errors.
 */
public class MessageDecryptionException extends Exception {

    /**
     * Creates a new MessageDecryptionException with a detail message.
     *
     * @param message the detail message
     */
    public MessageDecryptionException(String message) {
        super(message);
    }

    /**
     * Creates a new MessageDecryptionException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public MessageDecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
