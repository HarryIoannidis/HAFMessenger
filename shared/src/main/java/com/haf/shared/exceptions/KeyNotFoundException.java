package com.haf.shared.exceptions;

/**
 * Indicates that a requested key could not be found.
 */
public class KeyNotFoundException extends Exception {
    /**
     * Creates a new KeyNotFoundException.
     */
    public KeyNotFoundException() {
        super("Key not found");
    }

    /**
     * Creates a new KeyNotFoundException with a custom message.
     *
     * @param message the detail message
     */
    public KeyNotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a new KeyNotFoundException with a custom message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public KeyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
