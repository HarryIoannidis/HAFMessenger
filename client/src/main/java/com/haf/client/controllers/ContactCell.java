package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ListCell} that renders each contact using {@code contact_cell.fxml}.
 */
public class ContactCell extends ListCell<String> {

    private static final Logger LOGGER = Logger.getLogger(ContactCell.class.getName());

    private StackPane cellRoot;
    private Text nameText;

    public ContactCell() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(UiConstants.FXML_CONTACT_CELL));
            cellRoot = loader.load();

            // Tree: StackPane → HBox (index 0) → VBox (index 1) → Text (name)
            if (!cellRoot.getChildren().isEmpty()) {
                var hbox = (HBox) cellRoot.getChildren().get(0);
                if (hbox.getChildren().size() > 1) {
                    var vbox = (javafx.scene.layout.VBox) hbox.getChildren().get(1);
                    if (!vbox.getChildren().isEmpty()) {
                        nameText = (Text) vbox.getChildren().get(0);
                    }
                }

                // Get the JFXButton which is the second child of the StackPane (index 1)
                if (cellRoot.getChildren().size() > 1) {
                    var button = (com.jfoenix.controls.JFXButton) cellRoot.getChildren().get(1);
                    // When the ripple button is clicked, select this ListCell in the ListView
                    button.setOnAction(e -> {
                        getListView().getSelectionModel().select(getItem());
                    });
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load contact_cell.fxml", e);
        }
    }

    @Override
    protected void updateItem(String contactId, boolean empty) {
        super.updateItem(contactId, empty);

        if (empty || contactId == null || cellRoot == null) {
            setGraphic(null);
            setText(null);
            return;
        }

        if (nameText != null) {
            nameText.setText(contactId);
        }

        setGraphic(cellRoot);
        setText(null);
    }
}
