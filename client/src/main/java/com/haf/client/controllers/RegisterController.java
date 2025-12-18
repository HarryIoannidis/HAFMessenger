package com.haf.client.controllers;

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
                "Σμηνίας",
                "Υποσμηναγός",
                "Σμηναγός",
                "Επισμηναγός",
                "Ανθυποσμηνάρχης",
                "Σμηνάρχης",
                "Αντισμήναρχος",
                "Σμήναρχος"
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
                        imageView.setFitWidth(24);
                        imageView.setFitHeight(24);
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
                        imageView.setFitWidth(24);
                        imageView.setFitHeight(24);
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
        switch (rank) {
            case "Σμηνίας":
                return "/images/ranks/sminias.png";
            case "Υποσμηναγός":
                return "/images/ranks/yposminagos.png";
            case "Σμηναγός":
                return "/images/ranks/sminagos.png";
            case "Επισμηναγός":
                return "/images/ranks/episminagos.png";
            case "Ανθυποσμηνάρχης":
                return "/images/ranks/anthyposminarchis.png";
            case "Σμηνάρχης":
                return "/images/ranks/sminarchis.png";
            case "Αντισμήναρχος":
                return "/images/ranks/antisminarchos.png";
            case "Σμήναρχος":
                return "/images/ranks/sminarchos.png";
            default:
                return "/images/ranks/default.png";
        }
    }

    // Get selected rank
    public String getSelectedRank() {
        return rankComboBox.getValue();
    }
}
