package com.haf.client.utils;

import com.haf.client.models.MessageVM;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
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

    /* utility class */
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
        Text text = new Text(message.content());
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
    private static ImageView buildImage(MessageVM message) {
        ImageView imageView = new ImageView();
        if (message.content() != null && !message.content().isBlank()) {
            try {
                imageView.setImage(new Image(message.content(), true)); // background loading
            } catch (Exception ignored) {
                /* broken URL – show empty view */ }
        }
        imageView.setFitWidth(220);
        imageView.setPreserveRatio(true);
        imageView.getStyleClass().add("bubble-image");
        return imageView;
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
