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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ListCell} that renders each contact using {@code contact_cell.fxml}.
 * The cell now works with {@link ContactInfo} objects.
 */
public class ContactCell extends ListCell<ContactInfo> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContactCell.class);
    private static volatile boolean showUnreadBadges = true;
    private static volatile int unreadBadgeCap = 10;
    private static volatile boolean hidePresenceIndicators;

    private static byte[] cachedFXMLBytes;
    private StackPane cellRoot;
    private Text nameText;
    private Text regNumberText;
    private Circle activenessCircle;
    private StackPane unreadBadge;
    private Text unreadBadgeText;
    private JFXButton overlayButton;
    private PauseTransition contextMenuDelay;

    public static void setShowUnreadBadges(boolean showUnreadBadges) {
        ContactCell.showUnreadBadges = showUnreadBadges;
    }

    public static void setUnreadBadgeCap(int unreadBadgeCap) {
        ContactCell.unreadBadgeCap = Math.max(1, unreadBadgeCap);
    }

    public static void setHidePresenceIndicators(boolean hidePresenceIndicators) {
        ContactCell.hidePresenceIndicators = hidePresenceIndicators;
    }

    @FunctionalInterface
    public interface ContextMenuRequestHandler {
        /**
         * Handles a context-menu request for a specific contact cell.
         *
         * @param contact contact represented by the row
         * @param screenX request X coordinate in screen space
         * @param screenY request Y coordinate in screen space
         */
        void onRequest(ContactInfo contact, double screenX, double screenY);
    }

    /**
     * Registers a click callback for the overlay button that covers the cell
     * content.
     *
     * @param clickCallback callback invoked after selection is updated; may be
     *                      {@code null}
     */
    public void setOnClick(Runnable clickCallback) {
        ensureLoaded();
        installClickHandler(clickCallback);
    }

    /**
     * Registers a context-menu callback for right-click requests on the cell
     * overlay.
     *
     * @param handler handler invoked with contact and screen coordinates
     */
    public void setOnContextMenuRequest(ContextMenuRequestHandler handler) {
        ensureLoaded();
        installContextMenuHandler(handler);
    }

    /**
     * Creates a contact cell. FXML is loaded lazily on first render to reduce eager
     * startup cost.
     */
    public ContactCell() {
        // We move the loading logic out of the constructor to avoid
        // "burst" loading when the ListView pre-instantiates cells.
    }

    /**
     * Lazily loads the cell FXML and binds node references once per cell instance.
     */
    private void ensureLoaded() {
        if (cellRoot != null) {
            return;
        }

        try {
            var resource = getClass().getResource(UiConstants.FXML_CONTACT_CELL);
            LOGGER.info("Loading contact cell FXML: {}", resource);
            FXMLLoader loader = new FXMLLoader(resource);

            loadFXML(loader);
            bindReferences(loader);
        } catch (IOException e) {
            LOGGER.error("Could not load contact_cell.fxml", e);
        }
    }

    /**
     * Ensures the contact-cell FXML bytes are cached in memory for subsequent fast
     * loads.
     *
     * @throws IOException when stream read fails
     */
    private static synchronized void ensureFXMLBytesCached() throws IOException {
        if (cachedFXMLBytes == null) {
            try (var is = ContactCell.class.getResourceAsStream(UiConstants.FXML_CONTACT_CELL)) {
                if (is != null) {
                    cachedFXMLBytes = is.readAllBytes();
                }
            }
        }
    }

    /**
     * Loads the cell FXML either from cached bytes or from the classpath resource.
     *
     * @param loader configured FXMLLoader for the contact-cell resource
     * @throws IOException when FXML loading fails
     */
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

    /**
     * Binds commonly used nodes from the FXMLLoader namespace into cached fields.
     *
     * @param loader loader containing namespace entries after FXML load
     */
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

    /**
     * Installs left-click behavior that selects the row and then invokes the
     * optional callback.
     *
     * @param clickCallback callback invoked after row selection; may be
     *                      {@code null}
     */
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

    /**
     * Installs right-click behavior and dispatches delayed context-menu requests
     * for smoother UX.
     *
     * @param handler context-menu request handler
     */
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

    /**
     * Dispatches a context-menu request only when dispatch preconditions are met.
     *
     * @param handler callback handler
     * @param contact contact target
     * @param screenX popup X in screen coordinates
     * @param screenY popup Y in screen coordinates
     */
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

    /**
     * Determines whether a context-menu request can be dispatched.
     *
     * @param handler callback handler
     * @param contact contact target
     * @return {@code true} when both handler and contact are present
     */
    static boolean canDispatchContextMenu(ContextMenuRequestHandler handler, ContactInfo contact) {
        return handler != null && contact != null;
    }

    /**
     * Updates row visuals for the current contact item or clears visuals when row
     * becomes empty.
     *
     * @param contact contact item assigned to this cell
     * @param empty   JavaFX empty-row flag
     */
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

    /**
     * Determines whether the current row should be treated as empty.
     *
     * @param empty   JavaFX empty flag
     * @param contact assigned contact item
     * @return {@code true} when row is empty or contact is absent
     */
    private static boolean isEmptyItem(boolean empty, ContactInfo contact) {
        return empty || contact == null;
    }

    /**
     * Clears cell graphics/text and stops any pending context-menu delay timers.
     */
    private void clearCellVisuals() {
        stopContextMenuDelay();
        setGraphic(null);
        setText(null);
    }

    /**
     * Ensures FXML root is available before rendering contact data.
     *
     * @return {@code true} when cell root is ready for rendering
     */
    private boolean ensureCellAvailable() {
        ensureLoaded();
        if (cellRoot == null) {
            setGraphic(null);
            setText(null);
            return false;
        }
        return true;
    }

    /**
     * Applies all contact data fragments to the visual nodes.
     *
     * @param contact contact model to render
     */
    private void bindContactData(ContactInfo contact) {
        applyPrimaryText(contact);
        applyActiveness(contact);
        applyUnreadBadge(contact);
    }

    /**
     * Applies contact primary text fields (name and registration number).
     *
     * @param contact contact model
     */
    private void applyPrimaryText(ContactInfo contact) {
        if (nameText != null) {
            nameText.setText(contact.name());
        }
        if (regNumberText != null) {
            regNumberText.setText(contact.regNumber());
        }
    }

    /**
     * Applies activeness indicator visibility and color based on contact presence
     * fields.
     *
     * @param contact contact model
     */
    private void applyActiveness(ContactInfo contact) {
        if (activenessCircle == null) {
            return;
        }
        if (hidePresenceIndicators) {
            activenessCircle.setVisible(false);
            activenessCircle.setManaged(false);
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

    /**
     * Normalizes activeness labels for rendering logic.
     *
     * @param label raw activeness label
     * @return trimmed label or empty string when absent
     */
    private static String normalizeActivenessLabel(String label) {
        return label == null ? "" : label.trim();
    }

    /**
     * Applies activeness circle color with a gray fallback on invalid color input.
     *
     * @param color CSS/web color value
     */
    private void applyActivenessColor(String color) {
        try {
            activenessCircle.setFill(Color.web(color));
        } catch (IllegalArgumentException ex) {
            activenessCircle.setFill(Color.GRAY);
        }
    }

    /**
     * Applies unread badge visibility and text formatting for the contact row.
     *
     * @param contact contact model containing unread count
     */
    private void applyUnreadBadge(ContactInfo contact) {
        if (unreadBadge == null || unreadBadgeText == null) {
            return;
        }
        int unreadCount = Math.max(0, contact.unreadCount());
        boolean showUnreadBadge = showUnreadBadges && shouldShowUnreadBadge(unreadCount);
        unreadBadge.setVisible(showUnreadBadge);
        unreadBadge.setManaged(showUnreadBadge);
        if (showUnreadBadge) {
            unreadBadgeText.setText(formatUnreadBadgeText(unreadCount, unreadBadgeCap));
        }
    }

    /**
     * Renders the loaded cell root as the active row graphic.
     */
    private void renderCell() {
        setGraphic(cellRoot);
        setText(null);
    }

    /**
     * Stops any pending delayed context-menu dispatch timer.
     */
    private void stopContextMenuDelay() {
        if (contextMenuDelay != null) {
            contextMenuDelay.stop();
        }
    }

    /**
     * Retrieves and casts a node/object from an FXMLLoader namespace map.
     *
     * @param namespace loader namespace map
     * @param key       namespace key
     * @param type      expected runtime type
     * @param <T>       expected type parameter
     * @return cast value when key exists and matches type, otherwise {@code null}
     */
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

    /**
     * Determines whether unread badge should be shown for a count.
     *
     * @param unreadCount normalized unread count
     * @return {@code true} when unread count is greater than zero
     */
    private static boolean shouldShowUnreadBadge(int unreadCount) {
        return unreadCount > 0;
    }

    /**
     * Formats unread badge text with capped display style (for example
     * {@code 10+}).
     *
     * @param unreadCount unread count value
     * @return badge label text
     */
    private static String formatUnreadBadgeText(int unreadCount, int badgeCap) {
        int normalized = Math.max(0, unreadCount);
        int cap = Math.max(1, badgeCap);
        if (normalized > cap) {
            return cap + "+";
        }
        return Integer.toString(normalized);
    }
}
