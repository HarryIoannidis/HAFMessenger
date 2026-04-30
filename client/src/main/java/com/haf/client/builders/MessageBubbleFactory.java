package com.haf.client.builders;

import com.haf.client.models.MessageVM;
import com.haf.client.models.MessageType;
import com.jfoenix.controls.JFXButton;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
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
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignZ;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Factory that builds a chat-bubble {@link Node} ready to be inserted into
 * the chat scroll-pane.
 */
public final class MessageBubbleFactory {

    private static final DateTimeFormatter TIME_FMT_24H = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_FMT_12H = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final double BUBBLE_MAX_WIDTH = 360.0;
    private static final double BUBBLE_MAX_WIDTH_RATIO = 0.72;
    private static final double BUBBLE_HORIZONTAL_PADDING = 28.0;
    private static final double IMAGE_BUBBLE_MAX_WIDTH = 280.0;
    private static final PseudoClass HOVER_PSEUDO_CLASS = PseudoClass.getPseudoClass("hover");
    private static final PseudoClass PRESSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("pressed");
    private static final Ikon DEFAULT_FILE_ICON = MaterialDesignF.FILE;
    private static final Map<String, Ikon> FILE_ICON_BY_EXTENSION = Map.ofEntries(
            Map.entry("pdf", MaterialDesignF.FILE_PDF_BOX),
            Map.entry("doc", MaterialDesignF.FILE_WORD_BOX),
            Map.entry("docx", MaterialDesignF.FILE_WORD_BOX),
            Map.entry("odt", MaterialDesignF.FILE_WORD_BOX),
            Map.entry("rtf", MaterialDesignF.FILE_WORD_BOX),
            Map.entry("xls", MaterialDesignF.FILE_EXCEL_BOX),
            Map.entry("xlsx", MaterialDesignF.FILE_EXCEL_BOX),
            Map.entry("ods", MaterialDesignF.FILE_EXCEL_BOX),
            Map.entry("csv", MaterialDesignF.FILE_DELIMITED),
            Map.entry("ppt", MaterialDesignF.FILE_POWERPOINT_BOX),
            Map.entry("pptx", MaterialDesignF.FILE_POWERPOINT_BOX),
            Map.entry("odp", MaterialDesignF.FILE_POWERPOINT_BOX),
            Map.entry("png", MaterialDesignF.FILE_PNG_BOX),
            Map.entry("jpg", MaterialDesignF.FILE_JPG_BOX),
            Map.entry("jpeg", MaterialDesignF.FILE_JPG_BOX),
            Map.entry("gif", MaterialDesignF.FILE_GIF_BOX),
            Map.entry("bmp", MaterialDesignF.FILE_IMAGE),
            Map.entry("svg", MaterialDesignF.FILE_IMAGE),
            Map.entry("webp", MaterialDesignF.FILE_IMAGE),
            Map.entry("tif", MaterialDesignF.FILE_IMAGE),
            Map.entry("tiff", MaterialDesignF.FILE_IMAGE),
            Map.entry("mp3", MaterialDesignF.FILE_MUSIC),
            Map.entry("wav", MaterialDesignF.FILE_MUSIC),
            Map.entry("ogg", MaterialDesignF.FILE_MUSIC),
            Map.entry("flac", MaterialDesignF.FILE_MUSIC),
            Map.entry("aac", MaterialDesignF.FILE_MUSIC),
            Map.entry("m4a", MaterialDesignF.FILE_MUSIC),
            Map.entry("mp4", MaterialDesignF.FILE_VIDEO),
            Map.entry("mov", MaterialDesignF.FILE_VIDEO),
            Map.entry("avi", MaterialDesignF.FILE_VIDEO),
            Map.entry("mkv", MaterialDesignF.FILE_VIDEO),
            Map.entry("webm", MaterialDesignF.FILE_VIDEO),
            Map.entry("mpeg", MaterialDesignF.FILE_VIDEO),
            Map.entry("mpg", MaterialDesignF.FILE_VIDEO),
            Map.entry("zip", MaterialDesignZ.ZIP_BOX),
            Map.entry("rar", MaterialDesignZ.ZIP_BOX),
            Map.entry("7z", MaterialDesignZ.ZIP_BOX),
            Map.entry("tar", MaterialDesignZ.ZIP_BOX),
            Map.entry("gz", MaterialDesignZ.ZIP_BOX),
            Map.entry("tgz", MaterialDesignZ.ZIP_BOX),
            Map.entry("bz2", MaterialDesignZ.ZIP_BOX),
            Map.entry("xz", MaterialDesignZ.ZIP_BOX),
            Map.entry("xml", MaterialDesignF.FILE_XML_BOX),
            Map.entry("html", MaterialDesignF.FILE_CODE),
            Map.entry("htm", MaterialDesignF.FILE_CODE),
            Map.entry("json", MaterialDesignF.FILE_CODE),
            Map.entry("java", MaterialDesignF.FILE_CODE),
            Map.entry("js", MaterialDesignF.FILE_CODE),
            Map.entry("jsx", MaterialDesignF.FILE_CODE),
            Map.entry("ts", MaterialDesignF.FILE_CODE),
            Map.entry("tsx", MaterialDesignF.FILE_CODE),
            Map.entry("css", MaterialDesignF.FILE_CODE),
            Map.entry("scss", MaterialDesignF.FILE_CODE),
            Map.entry("sql", MaterialDesignF.FILE_CODE),
            Map.entry("py", MaterialDesignF.FILE_CODE),
            Map.entry("sh", MaterialDesignF.FILE_CODE),
            Map.entry("txt", MaterialDesignF.FILE_DOCUMENT_OUTLINE),
            Map.entry("md", MaterialDesignF.FILE_DOCUMENT_OUTLINE),
            Map.entry("log", MaterialDesignF.FILE_DOCUMENT_OUTLINE),
            Map.entry("exe", MaterialDesignF.FILE_COG),
            Map.entry("msi", MaterialDesignF.FILE_COG),
            Map.entry("dmg", MaterialDesignF.FILE_COG),
            Map.entry("app", MaterialDesignF.FILE_COG),
            Map.entry("apk", MaterialDesignF.FILE_COG),
            Map.entry("deb", MaterialDesignF.FILE_COG),
            Map.entry("rpm", MaterialDesignF.FILE_COG),
            Map.entry("bat", MaterialDesignF.FILE_COG),
            Map.entry("cmd", MaterialDesignF.FILE_COG));

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
        return create(message, true);
    }

    /**
     * Creates a full-width row bubble with optional timestamp row.
     *
     * @param message       message to render
     * @param showTimestamp whether to render timestamp metadata
     * @return rendered row node
     */
    public static Node create(MessageVM message, boolean showTimestamp) {
        return create(message, showTimestamp, true);
    }

    /**
     * Creates a full-width row bubble with optional timestamp row.
     *
     * @param message       message to render
     * @param showTimestamp whether to render timestamp metadata
     * @param use24HourTime whether to format timestamp as 24-hour clock
     * @return rendered row node
     */
    public static Node create(MessageVM message, boolean showTimestamp, boolean use24HourTime) {
        // Outer row
        HBox row = new HBox();
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(message.isOutgoing() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        HBox.setHgrow(row, javafx.scene.layout.Priority.ALWAYS);

        // Bubble VBox
        VBox bubble = new VBox(4);
        bubble.maxWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            double rowWidth = row.getWidth();
            if (rowWidth <= 0) {
                return BUBBLE_MAX_WIDTH;
            }
            return Math.min(BUBBLE_MAX_WIDTH, rowWidth * BUBBLE_MAX_WIDTH_RATIO);
        }, row.widthProperty()));
        bubble.setPadding(new Insets(14));
        bubble.getStyleClass().add(message.isOutgoing() ? "bubble-out" : "bubble-in");
        if (isPendingOutgoingText(message)) {
            bubble.getStyleClass().add("bubble-out-pending");
        }
        bubble.setCursor(Cursor.HAND);

        // Message content
        Node content = buildContent(message, bubble);
        bubble.getChildren().add(content);

        // Timestamp
        if (showTimestamp) {
            HBox timestampRow = buildTimestampRow(message, use24HourTime);
            bubble.getChildren().add(timestampRow);
        }

        JFXButton rippleOverlay = buildRippleOverlayButton(message, bubble);
        StackPane bubbleStack = new StackPane(bubble, rippleOverlay);
        row.getChildren().add(bubbleStack);
        return row;
    }

    /**
     * Creates an overlay button that provides ripple feedback while leaving
     * the bubble visuals unchanged.
     *
     * @param message bubble message model
     * @param bubble  visual bubble node rendered underneath
     * @return transparent ripple button covering the bubble bounds
     */
    private static JFXButton buildRippleOverlayButton(MessageVM message, VBox bubble) {
        JFXButton rippleOverlay = new JFXButton();
        rippleOverlay.setFocusTraversable(false);
        rippleOverlay.setText("");
        rippleOverlay.setCursor(Cursor.HAND);
        rippleOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        rippleOverlay.prefWidthProperty().bind(bubble.widthProperty());
        rippleOverlay.prefHeightProperty().bind(bubble.heightProperty());
        rippleOverlay.getStyleClass().add("bubble-ripple-overlay");
        rippleOverlay.getStyleClass().add(message.isOutgoing() ? "bubble-ripple-out" : "bubble-ripple-in");
        if (isPendingOutgoingText(message)) {
            rippleOverlay.getStyleClass().add("bubble-ripple-out-pending");
        }
        bridgeOverlayPseudoClasses(rippleOverlay, bubble);
        return rippleOverlay;
    }

    /**
     * Mirrors overlay hover/pressed states to the bubble node so existing
     * bubble pseudo-class styling continues to apply.
     *
     * @param rippleOverlay transparent overlay button
     * @param bubble        visual bubble under the overlay
     */
    private static void bridgeOverlayPseudoClasses(JFXButton rippleOverlay, VBox bubble) {
        rippleOverlay.hoverProperty()
                .addListener((obs, oldV, hovering) -> bubble.pseudoClassStateChanged(HOVER_PSEUDO_CLASS, hovering));
        rippleOverlay.pressedProperty()
                .addListener((obs, oldV, pressed) -> bubble.pseudoClassStateChanged(PRESSED_PSEUDO_CLASS, pressed));
    }

    /**
     * Creates the message content node based on the message type.
     *
     * @param message the message to render
     * @return the message content node
     */
    private static Node buildContent(MessageVM message, VBox bubble) {
        return switch (message.type()) {
            case TEXT -> buildText(message, bubble);
            case IMAGE -> buildImage(message, bubble);
            case FILE -> buildFile(message);
        };
    }

    /**
     * Creates the text content node for a message.
     *
     * @param message the message to render
     * @return the text content node
     */
    private static Text buildText(MessageVM message, VBox bubble) {
        String body = message.content() == null ? "" : message.content();
        Text text = new Text(body);
        double estimatedTextWidth = estimateLongestLineWidth(body);
        text.wrappingWidthProperty().bind(Bindings.createDoubleBinding(
                () -> {
                    double availableWidth = Math.max(80.0, bubble.getMaxWidth() - BUBBLE_HORIZONTAL_PADDING);
                    double desiredWidth = Math.max(56.0, estimatedTextWidth + 18.0);
                    return Math.min(availableWidth, desiredWidth);
                },
                bubble.maxWidthProperty()));
        text.getStyleClass().add(
                message.isOutgoing() ? "bubble-text-out" : "bubble-text-in");
        if (isPendingOutgoingText(message)) {
            text.getStyleClass().add("bubble-text-out-pending");
        }
        return text;
    }

    /**
     * Estimates the width of the longest logical line so short messages keep
     * compact bubbles without collapsing into per-character wrapping.
     *
     * @param body message body
     * @return estimated pixel width for the longest line
     */
    private static double estimateLongestLineWidth(String body) {
        if (body == null || body.isBlank()) {
            return 0.0;
        }
        String[] lines = body.split("\\R", -1);
        int longestLineChars = 0;
        for (String line : lines) {
            longestLineChars = Math.max(longestLineChars, line.length());
        }
        // 8px/ch keeps sizing stable for Manrope-like text at 16px.
        return longestLineChars * 8.0;
    }

    /**
     * Creates the image content node for a message.
     *
     * @param message the message to render
     * @return the image content node
     */
    private static Node buildImage(MessageVM message, VBox bubble) {
        var imageWidthBinding = Bindings.createDoubleBinding(
                () -> Math.max(140.0,
                        Math.min(IMAGE_BUBBLE_MAX_WIDTH, bubble.getMaxWidth() - BUBBLE_HORIZONTAL_PADDING)),
                bubble.maxWidthProperty());

        StackPane imageContainer = new StackPane();
        imageContainer.prefWidthProperty().bind(imageWidthBinding);
        imageContainer.maxWidthProperty().bind(imageWidthBinding);
        imageContainer.getStyleClass().add("bubble-image-clickable");
        imageContainer.setCursor(Cursor.HAND);

        ImageView imageView = new ImageView();
        imageView.fitWidthProperty().bind(imageWidthBinding);
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
            imageContainer.setMinHeight(160);
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
        } catch (Exception _) {
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
        FontIcon icon = new FontIcon(resolveFileIcon(message));
        icon.setIconSize(24);

        boolean loadingPlaceholder = message.isLoading()
                && (message.localPath() == null || message.localPath().isBlank());

        String label = (message.fileName() != null ? message.fileName() : "File");
        if (message.fileSize() != null && !message.fileSize().isBlank()) {
            label += "  •  " + message.fileSize();
        }
        if (loadingPlaceholder) {
            label += "  •  Loading...";
        }

        Text fileText = new Text(label);
        fileText.getStyleClass().add(
                message.isOutgoing() ? "bubble-text-out" : "bubble-text-in");

        HBox fileRow = new HBox(8, icon, fileText);
        if (loadingPlaceholder) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(18, 18);
            spinner.setMaxSize(18, 18);
            spinner.getStyleClass().add("bubble-file-spinner");
            spinner.getStyleClass().add(message.isOutgoing() ? "bubble-file-spinner-out" : "bubble-file-spinner-in");
            spinner.setMouseTransparent(true);
            fileRow.getChildren().add(spinner);
        }
        fileRow.setAlignment(Pos.CENTER_LEFT);
        if (!loadingPlaceholder) {
            fileRow.getStyleClass().add("bubble-file-clickable");
            fileRow.setCursor(Cursor.HAND);
        }
        return fileRow;
    }

    /**
     * Resolves a Material Design 2 file icon that matches the attachment
     * extension.
     *
     * @param message file message to inspect
     * @return existing MDI2 icon for the file kind, or generic file fallback
     */
    private static Ikon resolveFileIcon(MessageVM message) {
        String extension = extractFileExtension(message);
        return FILE_ICON_BY_EXTENSION.getOrDefault(extension, DEFAULT_FILE_ICON);
    }

    /**
     * Extracts a lowercase file extension from the display name, falling back to
     * local path when needed.
     *
     * @param message file message to inspect
     * @return lowercase extension without dot, or empty string
     */
    private static String extractFileExtension(MessageVM message) {
        if (message == null) {
            return "";
        }
        String extension = extractFileExtension(message.fileName());
        if (!extension.isBlank()) {
            return extension;
        }
        return extractFileExtension(message.localPath());
    }

    /**
     * Extracts a lowercase file extension from a name or path-like string.
     *
     * @param candidate file name, path, or URL
     * @return lowercase extension without dot, or empty string
     */
    private static String extractFileExtension(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "";
        }
        String value = candidate.trim();
        int queryIndex = value.indexOf('?');
        int hashIndex = value.indexOf('#');
        int endIndex = value.length();
        if (queryIndex >= 0) {
            endIndex = Math.min(endIndex, queryIndex);
        }
        if (hashIndex >= 0) {
            endIndex = Math.min(endIndex, hashIndex);
        }
        value = value.substring(0, endIndex);

        int separatorIndex = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        String name = separatorIndex >= 0 ? value.substring(separatorIndex + 1) : value;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == name.length() - 1) {
            return "";
        }
        return name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Creates the timestamp row pinned to the bottom-right of every bubble.
     *
     * @param message the message to render
     * @return the timestamp row
     */
    private static HBox buildTimestampRow(MessageVM message, boolean use24HourTime) {
        DateTimeFormatter formatter = use24HourTime ? TIME_FMT_24H : TIME_FMT_12H;
        String time = message.timestamp() != null
                ? message.timestamp().format(formatter)
                : "";
        Text ts = new Text(time);
        ts.getStyleClass().add(
                message.isOutgoing() ? "bubble-time-out" : "bubble-time-in");
        if (isPendingOutgoingText(message)) {
            ts.getStyleClass().add("bubble-time-out-pending");
        }

        HBox row = new HBox(ts);
        row.setAlignment(Pos.CENTER_RIGHT);
        VBox.setMargin(row, new Insets(2, 0, 0, 0));
        return row;
    }

    /**
     * Checks whether a message is an outgoing text bubble currently waiting for
     * send completion.
     *
     * @param message message candidate
     * @return {@code true} when outgoing text is pending send
     */
    private static boolean isPendingOutgoingText(MessageVM message) {
        return message != null
                && message.isOutgoing()
                && message.type() == MessageType.TEXT
                && message.isLoading();
    }
}
