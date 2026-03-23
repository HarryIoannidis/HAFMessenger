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

    /**
     * Canonical constructor that normalizes empty inputs and guarantees non-null
     * callbacks.
     *
     * @param popupKey     stable key used to identify/reuse popup windows
     * @param title        popup title text
     * @param message      popup body message
     * @param actionText   primary button caption
     * @param cancelText   cancel button caption
     * @param showCancel   whether the cancel button should be visible
     * @param dangerAction whether the primary action should use destructive styling
     * @param onAction     callback invoked when primary action is clicked
     * @param onCancel     callback invoked when cancel/close is clicked
     */
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

    /**
     * Normalizes required text values and enforces a non-blank fallback.
     *
     * @param value    source value to normalize
     * @param fallback fallback used when the source value is missing
     * @return trimmed source value, or a non-blank fallback
     */
    private static String normalizeRequired(String value, String fallback) {
        String normalized = normalize(value, fallback);
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized;
    }

    /**
     * Trims text input and replaces empty values with a fallback.
     *
     * @param value    source value to normalize
     * @param fallback fallback used when the source value is empty
     * @return normalized text value
     */
    private static String normalize(String value, String fallback) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }
}
