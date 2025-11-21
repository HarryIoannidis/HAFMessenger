package com.haf.shared.exceptions;

public class MessageExpiredException extends Exception {
    /**
     * Creates a new MessageExpiredException.
     */
    public MessageExpiredException() {
        super("Message has expired");
    }

    /**
     * Creates a new MessageExpiredException with a custom message.
     * @param message the detail message
     */
    public MessageExpiredException(String message) {
        super(message);
    }

    /**
     * Creates a new MessageExpiredException with a custom message and cause.
     * @param message the detail message
     * @param cause the cause
     */
    public MessageExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}

