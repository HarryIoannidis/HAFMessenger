package com.haf.client.exceptions;

/**
 * Thrown when client runtime configuration cannot be loaded or validated.
 */
public class ClientConfigurationException extends RuntimeException {

    /**
     * Creates an exception for invalid or missing runtime configuration.
     *
     * @param message human-readable configuration error
     */
    public ClientConfigurationException(String message) {
        super(message);
    }

    /**
     * Creates an exception for invalid or missing runtime configuration with a wrapped cause.
     *
     * @param message human-readable configuration error
     * @param cause root cause of the configuration failure
     */
    public ClientConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
