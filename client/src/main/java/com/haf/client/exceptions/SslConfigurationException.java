package com.haf.client.exceptions;

/**
 * Thrown when SSL/TLS configuration fails,
 * such as a missing truststore, missing password, or invalid certificates.
 */
public class SslConfigurationException extends RuntimeException {

    /**
     * Creates an exception for SSL/TLS initialization failures without a wrapped
     * cause.
     *
     * @param message human-readable description of the SSL configuration problem
     */
    public SslConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates an exception for SSL/TLS initialization failures with a wrapped
     * root cause.
     *
     * @param message human-readable description of the SSL configuration problem
     * @param cause   root cause that triggered the SSL configuration failure
     */
    public SslConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
