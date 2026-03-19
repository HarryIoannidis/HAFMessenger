package com.haf.client.controllers;

import com.haf.client.models.ContactInfo;
import com.haf.client.utils.UiConstants;
import javafx.animation.PauseTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.util.Duration;
import java.io.IOException;
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
    private com.jfoenix.controls.JFXButton overlayButton;
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
            bindReferences();
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

    private void bindReferences() {
        if (cellRoot.getChildren().isEmpty()) {
            return;
        }

        if (cellRoot.getChildren().size() > 1) {
            overlayButton = (com.jfoenix.controls.JFXButton) cellRoot.getChildren().get(1);
        }

        var hbox = (HBox) cellRoot.getChildren().get(0);
        if (hbox.getChildren().size() > 1) {
            bindHBoxChildren(hbox);
        }
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

    private void bindHBoxChildren(HBox hbox) {
        var stackPane = (StackPane) hbox.getChildren().get(0);
        if (stackPane.getChildren().size() > 1) {
            activenessCircle = (Circle) stackPane.getChildren().get(1);
        }

        var vbox = (VBox) hbox.getChildren().get(1);
        if (vbox.getChildren().size() > 0) {
            nameText = (Text) vbox.getChildren().get(0);
        }
        if (vbox.getChildren().size() > 1) {
            regNumberText = (Text) vbox.getChildren().get(1);
        }
    }

    @Override
    protected void updateItem(ContactInfo contact, boolean empty) {
        super.updateItem(contact, empty);

        if (empty || contact == null) {
            if (contextMenuDelay != null) {
                contextMenuDelay.stop();
            }
            setGraphic(null);
            setText(null);
            return;
        }

        ensureLoaded();
        if (cellRoot == null) {
            setGraphic(null);
            return;
        }

        if (nameText != null) {
            nameText.setText(contact.name());
        }
        if (regNumberText != null) {
            regNumberText.setText(contact.regNumber());
        }
        if (activenessCircle != null) {
            String activenessLabel = contact.activenessLabel() == null ? "" : contact.activenessLabel().trim();
            boolean hasActivenessLabel = !activenessLabel.isEmpty();
            activenessCircle.setVisible(hasActivenessLabel);
            activenessCircle.setManaged(hasActivenessLabel);
            if (hasActivenessLabel) {
                try {
                    activenessCircle.setFill(Color.web(contact.activenessColor()));
                } catch (IllegalArgumentException ex) {
                    activenessCircle.setFill(Color.GRAY);
                }
            }
        }

        setGraphic(cellRoot);
        setText(null);
    }
}
