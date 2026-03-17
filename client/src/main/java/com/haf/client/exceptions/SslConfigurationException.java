package com.haf.client.exceptions;

/**
 * Thrown when SSL/TLS configuration fails,
 * such as a missing truststore, missing password, or invalid certificates.
 */
public class SslConfigurationException extends RuntimeException {

    public SslConfigurationException(String message) {
        super(message);
    }

    public SslConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
