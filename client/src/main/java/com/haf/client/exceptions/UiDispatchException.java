package com.haf.client.exceptions;

/**
 * Thrown when dispatching an action to the JavaFX UI thread fails,
 * typically due to thread interruption while awaiting completion.
 */
public class UiDispatchException extends RuntimeException {

    /**
     * Creates an exception for UI-dispatch failures without a wrapped cause.
     *
     * @param message human-readable description of the dispatch failure
     */
    public UiDispatchException(String message) {
        super(message);
    }

    /**
     * Creates an exception for UI-dispatch failures with a wrapped root cause.
     *
     * @param message human-readable description of the dispatch failure
     * @param cause root cause that triggered the dispatch failure
     */
    public UiDispatchException(String message, Throwable cause) {
        super(message, cause);
    }
}
