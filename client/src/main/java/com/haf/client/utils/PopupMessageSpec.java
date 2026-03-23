package com.haf.client.utils;

import java.util.Objects;

/**
 * Immutable configuration payload for popup-message rendering.
 */
public record PopupMessageSpec(
        String popupKey,
        String title,
        String message,
        String actionText,
        String cancelText,
        boolean showCancel,
        boolean dangerAction,
        Runnable onAction,
        Runnable onCancel) {

    public PopupMessageSpec {
        popupKey = normalizeRequired(popupKey, "popup-message");
        title = normalizeRequired(title, "Notice");
        message = normalize(message, "");
        actionText = normalize(actionText, "OK");
        cancelText = normalize(cancelText, "Cancel");
        onAction = onAction == null ? () -> {
        } : onAction;
        onCancel = onCancel == null ? () -> {
        } : onCancel;
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
