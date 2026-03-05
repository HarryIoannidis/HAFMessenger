package com.haf.server.exceptions;

/**
 * Thrown when the server fails to start up properly.
 */
public class StartupException extends RuntimeException {

    /**
     * Creates a new StartupException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public StartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
