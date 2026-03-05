package com.haf.shared.exceptions;

/**
 * Thrown when JSON serialization or deserialization fails.
 */
public class JsonCodecException extends RuntimeException {

    /**
     * Creates a new JsonCodecException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public JsonCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
