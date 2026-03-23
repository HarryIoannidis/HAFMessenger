package com.haf.client.exceptions;

/**
 * Exception thrown when an HTTP communication error occurs,
 * such as unexpected non-2xx status codes.
 */
public class HttpCommunicationException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    /**
     * Creates an exception for a communication failure where no HTTP response
     * details are available.
     *
     * @param message human-readable explanation of the communication failure
     */
    public HttpCommunicationException(String message) {
        super(message);
        this.statusCode = 0;
        this.responseBody = null;
    }

    /**
     * Creates an exception for a communication failure caused by another
     * exception.
     *
     * @param message human-readable explanation of the communication failure
     * @param cause the root cause that triggered this failure
     */
    public HttpCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    /**
     * Creates an exception for an HTTP call that returned an error response.
     *
     * @param message human-readable explanation of the HTTP failure
     * @param statusCode HTTP status code returned by the server
     * @param responseBody raw response body returned by the server, if any
     */
    public HttpCommunicationException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    /**
     * Returns the HTTP status code associated with this failure.
     *
     * @return the HTTP status code, or {@code 0} when no response code was captured
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the server response body captured for this failure.
     *
     * @return the response body, or {@code null} when no body was captured
     */
    public String getResponseBody() {
        return responseBody;
    }
}
