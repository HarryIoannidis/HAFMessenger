package com.haf.client.utils;

import com.haf.client.controllers.PopupMessageController;

/**
 * Fluent builder for reusable popup messages rendered via {@link ViewRouter}.
 */
public final class PopupMessageBuilder {

    private String popupKey = "popup-message-default";
    private String title = "Notice";
    private String message = "";
    private String actionText = "OK";
    private String cancelText = "Cancel";
    private boolean showCancel = true;
    private boolean dangerAction;
    private Runnable onAction;
    private Runnable onCancel;

    /**
     * Creates a builder with default popup values.
     */
    private PopupMessageBuilder() {
    }

    /**
     * Starts a new popup-message builder chain.
     *
     * @return a new mutable {@link PopupMessageBuilder} instance
     */
    public static PopupMessageBuilder create() {
        return new PopupMessageBuilder();
    }

    /**
     * Sets the popup cache key used by {@link ViewRouter} to reuse popup stages.
     *
     * @param popupKey stable popup identifier
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder popupKey(String popupKey) {
        this.popupKey = popupKey;
        return this;
    }

    /**
     * Sets the popup title text.
     *
     * @param title title shown in the popup header
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder title(String title) {
        this.title = title;
        return this;
    }

    /**
     * Sets the popup body message.
     *
     * @param message message shown inside the popup content area
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder message(String message) {
        this.message = message;
        return this;
    }

    /**
     * Sets the primary action button label.
     *
     * @param actionText caption for the primary action button
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder actionText(String actionText) {
        this.actionText = actionText;
        return this;
    }

    /**
     * Sets the secondary/cancel button label.
     *
     * @param cancelText caption for the cancel button
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder cancelText(String cancelText) {
        this.cancelText = cancelText;
        return this;
    }

    /**
     * Controls whether the cancel button is shown.
     *
     * @param showCancel {@code true} to render the cancel button
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder showCancel(boolean showCancel) {
        this.showCancel = showCancel;
        return this;
    }

    /**
     * Convenience toggle for single-action popups.
     *
     * @param singleAction {@code true} to hide cancel and keep only the main action
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder singleAction(boolean singleAction) {
        this.showCancel = !singleAction;
        return this;
    }

    /**
     * Marks the primary action as destructive to apply danger styling.
     *
     * @param dangerAction {@code true} to enable destructive action styling
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder dangerAction(boolean dangerAction) {
        this.dangerAction = dangerAction;
        return this;
    }

    /**
     * Sets the callback executed when the primary action is pressed.
     *
     * @param onAction callback for the primary action button
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder onAction(Runnable onAction) {
        this.onAction = onAction;
        return this;
    }

    /**
     * Sets the callback executed when cancel/close is pressed.
     *
     * @param onCancel callback for the cancel or close actions
     * @return this builder for fluent chaining
     */
    public PopupMessageBuilder onCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

    /**
     * Builds an immutable popup specification from the current builder values.
     *
     * @return immutable {@link PopupMessageSpec} configured with this builder's
     *         values
     */
    public PopupMessageSpec build() {
        return new PopupMessageSpec(
                popupKey,
                title,
                message,
                actionText,
                cancelText,
                showCancel,
                dangerAction,
                onAction,
                onCancel);
    }

    /**
     * Builds and immediately displays the popup through {@link ViewRouter}.
     */
    public void show() {
        PopupMessageSpec spec = build();
        ViewRouter.showPopup(
                spec.popupKey(),
                UiConstants.FXML_POPUP_MESSAGE,
                PopupMessageController.class,
                controller -> controller.showMessage(spec));
    }
}
