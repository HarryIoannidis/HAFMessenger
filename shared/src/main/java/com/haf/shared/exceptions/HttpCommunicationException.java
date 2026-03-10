package com.haf.shared.exceptions;

/**
 * Exception thrown when an HTTP communication error occurs,
 * such as unexpected non-2xx status codes.
 */
public class HttpCommunicationException extends RuntimeException {
    private final int statusCode;
    private final String responseBody;

    public HttpCommunicationException(String message) {
        super(message);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public HttpCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public HttpCommunicationException(String message, int statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
