package com.haf.client.controllers;

import com.haf.client.models.ContactInfo;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXButton;
import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ListCell} that renders each contact using {@code contact_cell.fxml}.
 * The cell now works with {@link ContactInfo} objects.
 */
public class ContactCell extends ListCell<ContactInfo> {

    private static final Logger LOGGER = Logger.getLogger(ContactCell.class.getName());

    private static byte[] cachedFXMLBytes;
    private StackPane cellRoot;
    private Text nameText;
    private Text regNumberText;
    private Circle activenessCircle;
    private StackPane unreadBadge;
    private Text unreadBadgeText;
    private JFXButton overlayButton;
    private PauseTransition contextMenuDelay;

    @FunctionalInterface
    public interface ContextMenuRequestHandler {
        void onRequest(ContactInfo contact, double screenX, double screenY);
    }

    public void setOnClick(Runnable clickCallback) {
        ensureLoaded();
        installClickHandler(clickCallback);
    }

    public void setOnContextMenuRequest(ContextMenuRequestHandler handler) {
        ensureLoaded();
        installContextMenuHandler(handler);
    }

    public ContactCell() {
        // We move the loading logic out of the constructor to avoid
        // "burst" loading when the ListView pre-instantiates cells.
    }

    private void ensureLoaded() {
        if (cellRoot != null) {
            return;
        }

        try {
            var resource = getClass().getResource(UiConstants.FXML_CONTACT_CELL);
            LOGGER.log(Level.INFO, "Loading contact cell FXML: {0}", resource);
            FXMLLoader loader = new FXMLLoader(resource);

            loadFXML(loader);
            bindReferences(loader);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load contact_cell.fxml", e);
        }
    }

    private static synchronized void ensureFXMLBytesCached() throws IOException {
        if (cachedFXMLBytes == null) {
            try (var is = ContactCell.class.getResourceAsStream(UiConstants.FXML_CONTACT_CELL)) {
                if (is != null) {
                    cachedFXMLBytes = is.readAllBytes();
                }
            }
        }
    }

    private void loadFXML(FXMLLoader loader) throws IOException {
        // Optimization: If we already have the bytes in memory, use them
        // instead of hitting the disk again.
        ensureFXMLBytesCached();

        if (cachedFXMLBytes != null) {
            cellRoot = loader.load(new java.io.ByteArrayInputStream(cachedFXMLBytes));
        } else {
            cellRoot = loader.load();
        }
    }

    private void bindReferences(FXMLLoader loader) {
        if (cellRoot == null || loader == null) {
            return;
        }

        Map<String, Object> namespace = loader.getNamespace();
        nameText = asNamespaceNode(namespace, "nameText", Text.class);
        regNumberText = asNamespaceNode(namespace, "regNumberText", Text.class);
        activenessCircle = asNamespaceNode(namespace, "activenessCircle", Circle.class);
        unreadBadge = asNamespaceNode(namespace, "unreadBadge", StackPane.class);
        unreadBadgeText = asNamespaceNode(namespace, "unreadBadgeText", Text.class);
        overlayButton = asNamespaceNode(namespace, "overlayButton", JFXButton.class);
    }

    private void installClickHandler(Runnable clickCallback) {
        if (overlayButton == null) {
            return;
        }

        overlayButton.setOnAction(e -> {
            if (getListView() != null) {
                getListView().getSelectionModel().select(getItem());
            }
            if (clickCallback != null) {
                clickCallback.run();
            }
        });
    }

    private void installContextMenuHandler(ContextMenuRequestHandler handler) {
        if (overlayButton == null) {
            return;
        }

        overlayButton.setOnContextMenuRequested(event -> {
            ContactInfo item = getItem();
            if (isEmpty() || item == null) {
                event.consume();
                return;
            }
            final double screenX = event.getScreenX();
            final double screenY = event.getScreenY();

            // Treat right-click like a normal click first so the row's ripple/selection
            // finishes before we render the context menu.
            overlayButton.fire();
            if (contextMenuDelay != null) {
                contextMenuDelay.stop();
            }
            contextMenuDelay = new PauseTransition(Duration.millis(170));
            contextMenuDelay.setOnFinished(ignored -> dispatchContextMenu(handler, item, screenX, screenY));
            contextMenuDelay.playFromStart();
            event.consume();
        });
    }

    static void dispatchContextMenu(
            ContextMenuRequestHandler handler,
            ContactInfo contact,
            double screenX,
            double screenY) {
        if (!canDispatchContextMenu(handler, contact)) {
            return;
        }
        handler.onRequest(contact, screenX, screenY);
    }

    static boolean canDispatchContextMenu(ContextMenuRequestHandler handler, ContactInfo contact) {
        return handler != null && contact != null;
    }

    @Override
    protected void updateItem(ContactInfo contact, boolean empty) {
        super.updateItem(contact, empty);

        if (isEmptyItem(empty, contact)) {
            clearCellVisuals();
            return;
        }

        if (!ensureCellAvailable()) {
            return;
        }

        bindContactData(contact);
        renderCell();
    }

    private static boolean isEmptyItem(boolean empty, ContactInfo contact) {
        return empty || contact == null;
    }

    private void clearCellVisuals() {
        stopContextMenuDelay();
        setGraphic(null);
        setText(null);
    }

    private boolean ensureCellAvailable() {
        ensureLoaded();
        if (cellRoot == null) {
            setGraphic(null);
            setText(null);
            return false;
        }
        return true;
    }

    private void bindContactData(ContactInfo contact) {
        applyPrimaryText(contact);
        applyActiveness(contact);
        applyUnreadBadge(contact);
    }

    private void applyPrimaryText(ContactInfo contact) {
        if (nameText != null) {
            nameText.setText(contact.name());
        }
        if (regNumberText != null) {
            regNumberText.setText(contact.regNumber());
        }
    }

    private void applyActiveness(ContactInfo contact) {
        if (activenessCircle == null) {
            return;
        }
        String activenessLabel = normalizeActivenessLabel(contact.activenessLabel());
        boolean hasActivenessLabel = !activenessLabel.isEmpty();
        activenessCircle.setVisible(hasActivenessLabel);
        activenessCircle.setManaged(hasActivenessLabel);
        if (hasActivenessLabel) {
            applyActivenessColor(contact.activenessColor());
        }
    }

    private static String normalizeActivenessLabel(String label) {
        return label == null ? "" : label.trim();
    }

    private void applyActivenessColor(String color) {
        try {
            activenessCircle.setFill(Color.web(color));
        } catch (IllegalArgumentException ex) {
            activenessCircle.setFill(Color.GRAY);
        }
    }

    private void applyUnreadBadge(ContactInfo contact) {
        if (unreadBadge == null || unreadBadgeText == null) {
            return;
        }
        int unreadCount = Math.max(0, contact.unreadCount());
        boolean showUnreadBadge = shouldShowUnreadBadge(unreadCount);
        unreadBadge.setVisible(showUnreadBadge);
        unreadBadge.setManaged(showUnreadBadge);
        if (showUnreadBadge) {
            unreadBadgeText.setText(formatUnreadBadgeText(unreadCount));
        }
    }

    private void renderCell() {
        setGraphic(cellRoot);
        setText(null);
    }

    private void stopContextMenuDelay() {
        if (contextMenuDelay != null) {
            contextMenuDelay.stop();
        }
    }

    private static <T> T asNamespaceNode(Map<String, Object> namespace, String key, Class<T> type) {
        if (namespace == null || key == null || key.isBlank() || type == null) {
            return null;
        }
        Object value = namespace.get(key);
        if (!type.isInstance(value)) {
            return null;
        }
        return type.cast(value);
    }

    private static boolean shouldShowUnreadBadge(int unreadCount) {
        return unreadCount > 0;
    }

    private static String formatUnreadBadgeText(int unreadCount) {
        int normalized = Math.max(0, unreadCount);
        if (normalized > 9) {
            return "9+";
        }
        return Integer.toString(normalized);
    }
}
