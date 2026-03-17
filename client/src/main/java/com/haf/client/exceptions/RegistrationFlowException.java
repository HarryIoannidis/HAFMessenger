package com.haf.client.exceptions;

/**
 * Checked exception for client registration workflow operations.
 */
public class RegistrationFlowException extends Exception {

    public RegistrationFlowException(String message) {
        super(message);
    }

    public RegistrationFlowException(String message, Throwable cause) {
        super(message, cause);
    }
}
