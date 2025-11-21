package com.haf.shared.exceptions;

import javax.crypto.AEADBadTagException;

public class MessageTamperedException extends Exception {
    /**
     * Creates a new MessageTamperedException.
     */
    public MessageTamperedException() {
        super("Message tampering detected (AEAD tag verification failed)");
    }

    /**
     * Creates a new MessageTamperedException with a custom message.
     * @param message the detail message
     */
    public MessageTamperedException(String message) {
        super(message);
    }

    /**
     * Creates a new MessageTamperedException from an AEADBadTagException.
     * @param cause the AEADBadTagException that caused this exception
     */
    public MessageTamperedException(AEADBadTagException cause) {
        super("Message tampering detected (AEAD tag verification failed)", cause);
    }

    /**
     * Creates a new MessageTamperedException with a custom message and cause.
     * @param message the detail message
     * @param cause the cause
     */
    public MessageTamperedException(String message, Throwable cause) {
        super(message, cause);
    }
}

