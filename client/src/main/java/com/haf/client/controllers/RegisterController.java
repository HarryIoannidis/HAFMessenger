package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class RegisterController {

    @FXML
    private ComboBox<String> rankComboBox;

    @FXML
    public void initialize() {
        // Populate ComboBox με τους βαθμούς
        rankComboBox.getItems().addAll(
                UiConstants.RANK_YPOSMINIAS,
                UiConstants.RANK_SMINIAS,
                UiConstants.RANK_EPISMINIAS,
                UiConstants.RANK_ARCHISMINIAS,
                UiConstants.RANK_ANTHYPASPISTIS,
                UiConstants.RANK_ANTHYPOSMINAGOS,
                UiConstants.RANK_YPOSMINAGOS,
                UiConstants.RANK_EPISMINAGOS,
                UiConstants.RANK_ANTISMINARCHOS,
                UiConstants.RANK_SMINARCHOS,
                UiConstants.RANK_TAKSIARCOS,
                UiConstants.RANK_YPOPTERARCHOS,
                UiConstants.RANK_ANTIPTERARCHOS
        );

        // Set cell factory για εικονίδια στη λίστα
        rankComboBox.setCellFactory(listView -> new ListCell<String>() {
            private final ImageView imageView = new ImageView();

            @Override
            protected void updateItem(String rank, boolean empty) {
                super.updateItem(rank, empty);

                if (empty || rank == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(rank);

                    // Φόρτωσε το εικονίδιο
                    try {
                        String iconPath = getRankIconPath(rank);
                        Image image = new Image(getClass().getResourceAsStream(iconPath));
                        imageView.setImage(image);
                        imageView.setFitWidth(UiConstants.RANK_ICON_SIZE);
                        imageView.setFitHeight(UiConstants.RANK_ICON_SIZE);
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                    } catch (Exception e) {
                        // Αν δεν βρεθεί το icon, μην βάζεις τίποτα
                        setGraphic(null);
                    }
                }
            }
        });

        // Set button cell για εικονίδιο στο selected item
        rankComboBox.setButtonCell(new ListCell<String>() {
            private final ImageView imageView = new ImageView();

            @Override
            protected void updateItem(String rank, boolean empty) {
                super.updateItem(rank, empty);

                if (empty || rank == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(rank);

                    try {
                        String iconPath = getRankIconPath(rank);
                        Image image = new Image(getClass().getResourceAsStream(iconPath));
                        imageView.setImage(image);
                        imageView.setFitWidth(UiConstants.RANK_ICON_SIZE);
                        imageView.setFitHeight(UiConstants.RANK_ICON_SIZE);
                        imageView.setPreserveRatio(true);
                        setGraphic(imageView);
                    } catch (Exception e) {
                        setGraphic(null);
                    }
                }
            }
        });
    }

    // Helper method - επιστρέφει το path του icon
    private String getRankIconPath(String rank) {
        if (UiConstants.RANK_YPOSMINIAS.equals(rank)) {
            return UiConstants.ICON_RANK_YPOSMINIAS;
        } else if (UiConstants.RANK_SMINIAS.equals(rank)) {
            return UiConstants.ICON_RANK_SMINIAS;
        } else if (UiConstants.RANK_EPISMINIAS.equals(rank)) {
            return UiConstants.ICON_RANK_EPISMINIAS;
        } else if (UiConstants.RANK_ARCHISMINIAS.equals(rank)) {
            return UiConstants.ICON_RANK_ARCHISMINIAS;
        } else if (UiConstants.RANK_ANTHYPASPISTIS.equals(rank)) {
            return UiConstants.ICON_RANK_ANTHYPASPISTIS;
        } else if (UiConstants.RANK_ANTHYPOSMINAGOS.equals(rank)) {
            return UiConstants.ICON_RANK_ANTHYPOSMINAGOS;
        } else if (UiConstants.RANK_YPOSMINAGOS.equals(rank)) {
            return UiConstants.ICON_RANK_YPOSMINAGOS;
        } else if (UiConstants.RANK_EPISMINAGOS.equals(rank)) {
            return UiConstants.ICON_RANK_EPISMINAGOS;
        } else if (UiConstants.RANK_ANTISMINARCHOS.equals(rank)) {
            return UiConstants.ICON_RANK_ANTISMINARCHOS;
        } else if (UiConstants.RANK_SMINARCHOS.equals(rank)) {
            return UiConstants.ICON_RANK_SMINARCHOS;
        } else if (UiConstants.RANK_TAKSIARCOS.equals(rank)) {
            return UiConstants.ICON_RANK_TAKSIARCOS;
        } else if (UiConstants.RANK_YPOPTERARCHOS.equals(rank)) {
            return UiConstants.ICON_RANK_YPOPTERARCHOS;
        } else if (UiConstants.RANK_ANTIPTERARCHOS.equals(rank)) {
            return UiConstants.ICON_RANK_ANTIPTERARCHOS;
        } else {
            return UiConstants.ICON_RANK_DEFAULT;
        }
    }

    // Get selected rank
    public String getSelectedRank() {
        return rankComboBox.getValue();
    }
}
