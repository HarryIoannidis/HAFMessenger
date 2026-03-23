package com.haf.client.exceptions;

/**
 * Checked exception for client registration workflow operations.
 */
public class RegistrationFlowException extends Exception {

    /**
     * Creates an exception for a validation or processing error during the
     * registration flow.
     *
     * @param message human-readable description of the registration failure
     */
    public RegistrationFlowException(String message) {
        super(message);
    }

    /**
     * Creates an exception for a registration error that wraps a lower-level
     * cause.
     *
     * @param message human-readable description of the registration failure
     * @param cause root cause that triggered this exception
     */
    public RegistrationFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
