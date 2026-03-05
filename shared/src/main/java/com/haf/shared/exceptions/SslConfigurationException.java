package com.haf.shared.exceptions;

/**
 * Thrown when SSL/TLS configuration fails,
 * such as a missing truststore, missing password, or invalid certificates.
 */
public class SslConfigurationException extends RuntimeException {

    /**
     * Creates a new SslConfigurationException with a detail message.
     *
     * @param message the detail message
     */
    public SslConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates a new SslConfigurationException with a detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public SslConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
