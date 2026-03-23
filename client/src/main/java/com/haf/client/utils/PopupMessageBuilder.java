package com.haf.client.utils;

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

    private PopupMessageBuilder() {
    }

    public static PopupMessageBuilder create() {
        return new PopupMessageBuilder();
    }

    public PopupMessageBuilder popupKey(String popupKey) {
        this.popupKey = popupKey;
        return this;
    }

    public PopupMessageBuilder title(String title) {
        this.title = title;
        return this;
    }

    public PopupMessageBuilder message(String message) {
        this.message = message;
        return this;
    }

    public PopupMessageBuilder actionText(String actionText) {
        this.actionText = actionText;
        return this;
    }

    public PopupMessageBuilder cancelText(String cancelText) {
        this.cancelText = cancelText;
        return this;
    }

    public PopupMessageBuilder showCancel(boolean showCancel) {
        this.showCancel = showCancel;
        return this;
    }

    public PopupMessageBuilder singleAction(boolean singleAction) {
        this.showCancel = !singleAction;
        return this;
    }

    public PopupMessageBuilder dangerAction(boolean dangerAction) {
        this.dangerAction = dangerAction;
        return this;
    }

    public PopupMessageBuilder onAction(Runnable onAction) {
        this.onAction = onAction;
        return this;
    }

    public PopupMessageBuilder onCancel(Runnable onCancel) {
        this.onCancel = onCancel;
        return this;
    }

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

    public void show() {
        PopupMessageSpec spec = build();
        ViewRouter.showPopup(
                spec.popupKey(),
                UiConstants.FXML_POPUP_MESSAGE,
                com.haf.client.controllers.PopupMessageController.class,
                controller -> controller.showMessage(spec));
    }
}
