package com.haf.client.utils;

import com.haf.client.exceptions.HttpCommunicationException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.ClosedChannelException;
import java.util.Objects;

/**
 * Immutable payload describing a recoverable runtime issue that can be surfaced
 * to users with a retry action.
 */
public record RuntimeIssue(
        String dedupeKey,
        String title,
        String message,
        Runnable retryAction,
        boolean connectionIssue) {

    /**
     * Backward-compatible constructor that defaults
     * {@code connectionIssue=false}.
     *
     * @param dedupeKey   stable key used for popup deduplication
     * @param title       user-facing issue title
     * @param message     user-facing issue details
     * @param retryAction callback executed when user clicks retry
     */
    public RuntimeIssue(String dedupeKey, String title, String message, Runnable retryAction) {
        this(dedupeKey, title, message, retryAction, false);
    }

    /**
     * Canonical constructor that normalizes text fields and guarantees a non-null
     * retry callback.
     *
     * @param dedupeKey   stable key used for popup deduplication
     * @param title       user-facing issue title
     * @param message     user-facing issue details
     * @param retryAction callback executed when user clicks retry
     * @param connectionIssue whether this runtime issue represents transport
     *                        connectivity loss
     */
    public RuntimeIssue {
        dedupeKey = normalizeRequired(dedupeKey, "runtime-issue");
        title = normalizeRequired(title, "Runtime issue");
        message = normalizeRequired(message, "An unexpected runtime issue occurred.");
        retryAction = retryAction == null ? () -> {
        } : retryAction;
    }

    /**
     * Normalizes a mandatory text field and falls back to a default label when
     * blank.
     *
     * @param value    input field value
     * @param fallback fallback value for null/blank inputs
     * @return normalized non-blank value
     */
    private static String normalizeRequired(String value, String fallback) {
        String normalized = normalize(value, fallback);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }

    /**
     * Trims nullable text and applies a fallback for empty values.
     *
     * @param value    input field value
     * @param fallback fallback value for null/empty inputs
     * @return trimmed value or fallback
     */
    private static String normalize(String value, String fallback) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }

    /**
     * Returns whether a throwable chain represents transport connectivity failure
     * (including HTTP 5xx backend unavailability).
     *
     * @param error throwable to classify
     * @return {@code true} when error should be treated as connection loss
     */
    public static boolean isConnectionFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof HttpCommunicationException communicationException) {
                int statusCode = communicationException.getStatusCode();
                if (statusCode >= 500 && statusCode <= 599) {
                    return true;
                }
            }

            if (current instanceof ConnectException
                    || current instanceof ClosedChannelException
                    || current instanceof HttpTimeoutException
                    || current instanceof SocketException
                    || current instanceof UnknownHostException) {
                return true;
            }

            current = current.getCause();
        }
        return false;
    }
}
