package com.haf.shared.exceptions;

import com.haf.shared.utils.MessageValidator;
import java.util.List;

public class MessageValidationException extends Exception {
    private final List<MessageValidator.ErrorCode> errorCodes;

    /**
     * Creates a new MessageValidationException.
     * @param message the detail message
     * @param errorCodes the list of validation error codes
     */
    public MessageValidationException(String message, List<MessageValidator.ErrorCode> errorCodes) {
        super(message);
        this.errorCodes = errorCodes != null ? List.copyOf(errorCodes) : List.of();
    }

    /**
     * Creates a new MessageValidationException with error codes.
     * @param errorCodes the list of validation error codes
     */
    public MessageValidationException(List<MessageValidator.ErrorCode> errorCodes) {
        this("Message validation failed: " + errorCodes, errorCodes);
    }

    /**
     * Returns the list of validation error codes.
     * @return the error codes
     */
    public List<MessageValidator.ErrorCode> getErrorCodes() {
        return errorCodes;
    }
}

