package com.haf.client.utils;

import java.util.Objects;

/**
 * Immutable payload describing a recoverable runtime issue that can be surfaced
 * to users with a retry action.
 */
public record RuntimeIssue(
        String dedupeKey,
        String title,
        String message,
        Runnable retryAction) {

    /**
     * Canonical constructor that normalizes text fields and guarantees a non-null
     * retry callback.
     *
     * @param dedupeKey stable key used for popup deduplication
     * @param title user-facing issue title
     * @param message user-facing issue details
     * @param retryAction callback executed when user clicks retry
     */
    public RuntimeIssue {
        dedupeKey = normalizeRequired(dedupeKey, "runtime-issue");
        title = normalizeRequired(title, "Runtime issue");
        message = normalizeRequired(message, "An unexpected runtime issue occurred.");
        retryAction = retryAction == null ? () -> {
        } : retryAction;
    }

    private static String normalizeRequired(String value, String fallback) {
        String normalized = normalize(value, fallback);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }
}
