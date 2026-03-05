package com.haf.server.exceptions;

/**
 * Thrown when server configuration is invalid or incomplete,
 * such as missing environment variables or malformed values.
 */
public class ConfigurationException extends RuntimeException {

    /**
     * Creates a new ConfigurationException with a detail message.
     *
     * @param message the detail message
     */
    public ConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new ConfigurationException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
