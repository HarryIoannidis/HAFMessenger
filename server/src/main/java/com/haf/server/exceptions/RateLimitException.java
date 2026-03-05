package com.haf.server.exceptions;

/**
 * Thrown when a rate limit check fails due to an internal error.
 */
public class RateLimitException extends RuntimeException {

    /**
     * Creates a new RateLimitException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public RateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
