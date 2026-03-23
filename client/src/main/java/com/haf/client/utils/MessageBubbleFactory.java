package com.haf.client.utils;

import com.haf.client.models.MessageVM;
import javafx.scene.Cursor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.kordamp.ikonli.javafx.FontIcon;
import java.time.format.DateTimeFormatter;

/**
 * Factory that builds a chat-bubble {@link Node} ready to be inserted into
 * the chat scroll-pane.
 */
public final class MessageBubbleFactory {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Prevents instantiation of this utility factory.
     */
    private MessageBubbleFactory() {
    }

    /**
     * Creates a full-width {@link HBox} row containing a styled bubble for the
     * given {@link MessageVM}.
     *
     * @param message the message to render
     * @return a JavaFX {@link Node} ready to be appended to the chat list
     */
    public static Node create(MessageVM message) {
        // Outer row
        HBox row = new HBox();
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(message.isOutgoing() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox.setHgrow(row, javafx.scene.layout.Priority.ALWAYS);

        // Bubble VBox
        VBox bubble = new VBox(4);
        bubble.setMaxWidth(320);
        bubble.setPadding(new Insets(14));
        bubble.getStyleClass().add(message.isOutgoing() ? "bubble-out" : "bubble-in");
        bubble.setCursor(Cursor.HAND);

        // Message content
        Node content = buildContent(message);
        bubble.getChildren().add(content);

        // Timestamp
        HBox timestampRow = buildTimestampRow(message);
        bubble.getChildren().add(timestampRow);

        row.getChildren().add(bubble);
        return row;
    }

    /**
     * Creates the message content node based on the message type.
     *
     * @param message the message to render
     * @return the message content node
     */
    private static Node buildContent(MessageVM message) {
        return switch (message.type()) {
            case TEXT -> buildText(message);
            case IMAGE -> buildImage(message);
            case FILE -> buildFile(message);
        };
    }

    /**
     * Creates the text content node for a message.
     *
     * @param message the message to render
     * @return the text content node
     */
    private static Text buildText(MessageVM message) {
        String body = message.content() == null ? "" : message.content();
        Text text = new Text(body);
        text.setWrappingWidth(280);
        text.getStyleClass().add(
                message.isOutgoing() ? "bubble-text-out" : "bubble-text-in");
        return text;
    }

    /**
     * Creates the image content node for a message.
     *
     * @param message the message to render
     * @return the image content node
     */
    private static Node buildImage(MessageVM message) {
        StackPane imageContainer = new StackPane();
        imageContainer.setPrefWidth(220);
        imageContainer.setMaxWidth(220);
        imageContainer.getStyleClass().add("bubble-image-clickable");
        imageContainer.setCursor(Cursor.HAND);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(220);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("bubble-image");
        imageView.setCursor(Cursor.HAND);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        spinner.setMaxSize(28, 28);
        spinner.getStyleClass().add("bubble-image-spinner");
        spinner.setMouseTransparent(true);

        imageContainer.getChildren().addAll(imageView, spinner);

        boolean loadingPlaceholder = message.isLoading()
                && (message.content() == null || message.content().isBlank());
        if (loadingPlaceholder) {
            imageContainer.setMinHeight(140);
            setSpinnerVisible(spinner, true);
        } else {
            setSpinnerVisible(spinner, false);
        }

        loadImageContent(message, imageView, spinner);
        return imageContainer;
    }

    /**
     * Loads the actual image from the message content URL and manages
     * the spinner visibility during loading, completion, and error states.
     *
     * @param message   the message containing the image URL
     * @param imageView the view to display the image in
     * @param spinner   the loading spinner overlay
     */
    private static void loadImageContent(MessageVM message,
                                         ImageView imageView,
                                         ProgressIndicator spinner) {
        if (message.content() == null || message.content().isBlank()) {
            return;
        }
        try {
            Image image = new Image(message.content(), true);
            imageView.setImage(image);
            if (message.isLoading() || image.getProgress() < 1.0) {
                setSpinnerVisible(spinner, true);
            }

            image.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && newV.doubleValue() >= 1.0) {
                    setSpinnerVisible(spinner, false);
                }
            });
            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (Boolean.TRUE.equals(isError)) {
                    setSpinnerVisible(spinner, false);
                }
            });
        } catch (Exception ignored) {
            /* broken URL – show empty view */
            setSpinnerVisible(spinner, false);
        }
    }

    /**
     * Toggles visibility/management for the image loading spinner.
     *
     * @param spinner spinner node rendered over preview images
     * @param visible {@code true} when loading is in progress
     */
    private static void setSpinnerVisible(ProgressIndicator spinner, boolean visible) {
        spinner.setVisible(visible);
        spinner.setManaged(visible);
    }

    /**
     * Creates the file attachment content node for a message.
     *
     * @param message the message to render
     * @return the file attachment content node
     */
    private static HBox buildFile(MessageVM message) {
        FontIcon icon = new FontIcon("mdi2f-file-pdf-box");
        icon.setIconSize(24);

        String label = (message.fileName() != null ? message.fileName() : "File")
                + (message.fileSize() != null ? "  •  " + message.fileSize() : "");

        Text fileText = new Text(label);
        fileText.getStyleClass().add(
                message.isOutgoing() ? "bubble-text-out" : "bubble-text-in");

        HBox fileRow = new HBox(8, icon, fileText);
        fileRow.setAlignment(Pos.CENTER_LEFT);
        fileRow.getStyleClass().add("bubble-file-clickable");
        fileRow.setCursor(Cursor.HAND);
        return fileRow;
    }

    /**
     * Creates the timestamp row pinned to the bottom-right of every bubble.
     *
     * @param message the message to render
     * @return the timestamp row
     */
    private static HBox buildTimestampRow(MessageVM message) {
        String time = message.timestamp() != null
                ? message.timestamp().format(TIME_FMT)
                : "";
        Text ts = new Text(time);
        ts.getStyleClass().add(
                message.isOutgoing() ? "bubble-time-out" : "bubble-time-in");

        HBox row = new HBox(ts);
        row.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(row, new Insets(2, 0, 0, 0));
        return row;
    }
}
