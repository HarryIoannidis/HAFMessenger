package com.haf.client.controllers;

import com.haf.client.model.ContactInfo;
import com.haf.client.utils.UiConstants;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ListCell} that renders each contact using {@code contact_cell.fxml}.
 * The cell now works with {@link ContactInfo} objects instead of raw Strings.
 */
public class ContactCell extends ListCell<ContactInfo> {

    private static final Logger LOGGER = Logger.getLogger(ContactCell.class.getName());

    private StackPane cellRoot;
    private Text nameText;
    private Text activenessText;
    private Circle activenessCircle;

    public ContactCell() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(UiConstants.FXML_CONTACT_CELL));
            cellRoot = loader.load();

            // Tree: StackPane → HBox (index 0) → VBox (index 1)
            // VBox: Text (name, index 0) , HBox (activeness row, index 1)
            // activeness HBox: Text (label, index 0), Circle (dot, index 1)
            if (!cellRoot.getChildren().isEmpty()) {
                var hbox = (HBox) cellRoot.getChildren().get(0);
                if (hbox.getChildren().size() > 1) {
                    var vbox = (VBox) hbox.getChildren().get(1);
                    if (!vbox.getChildren().isEmpty()) {
                        nameText = (Text) vbox.getChildren().get(0);
                    }
                    if (vbox.getChildren().size() > 1) {
                        var activenessRow = (HBox) vbox.getChildren().get(1);
                        if (!activenessRow.getChildren().isEmpty()) {
                            activenessText = (Text) activenessRow.getChildren().get(0);
                        }
                        if (activenessRow.getChildren().size() > 1) {
                            activenessCircle = (Circle) activenessRow.getChildren().get(1);
                        }
                    }
                }

                // The transparent JFXButton overlay (index 1) triggers selection
                if (cellRoot.getChildren().size() > 1) {
                    var button = (com.jfoenix.controls.JFXButton) cellRoot.getChildren().get(1);
                    button.setOnAction(e -> getListView().getSelectionModel().select(getItem()));
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load contact_cell.fxml", e);
        }
    }

    @Override
    protected void updateItem(ContactInfo contact, boolean empty) {
        super.updateItem(contact, empty);

        if (empty || contact == null || cellRoot == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        if (nameText != null) {
            nameText.setText(contact.name());
        }
        if (activenessText != null) {
            activenessText.setText(contact.activenessLabel());
        }
        if (activenessCircle != null) {
            try {
                activenessCircle.setFill(Color.web(contact.activenessColor()));
            } catch (IllegalArgumentException ex) {
                activenessCircle.setFill(Color.GRAY);
            }
        }

        setGraphic(cellRoot);
        setText(null);
    }
}
