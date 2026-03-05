package com.haf.server.exceptions;

/**
 * Thrown when a database operation fails, such as inserting,
 * fetching, updating, or deleting records.
 */
public class DatabaseOperationException extends RuntimeException {

    /**
     * Creates a new DatabaseOperationException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public DatabaseOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
