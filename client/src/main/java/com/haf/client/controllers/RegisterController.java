package com.haf.client.controllers;

import com.haf.client.utils.UiConstants;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.text.Text;
import com.jfoenix.controls.JFXButton;

import java.util.Objects;

public class RegisterController {

    @FXML
    private BorderPane rootContainer;

    @FXML
    private HBox titleBar;

    @FXML
    private HBox ttileHBox;

    @FXML
    private HBox buttonsHBox;

    @FXML
    private StackPane leftPanel;

    @FXML
    private StackPane rightPanel;

    @FXML
    private TextField nameField;

    @FXML
    private TextField regNumField;

    @FXML
    private TextField idNumField;

    @FXML
    private TextField phoneNumField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField passwordConfField;

    @FXML
    private JFXButton registerButton;

    @FXML
    private Text gotoSignInButton;

    @FXML
    private ComboBox<String> rankComboBox;

    @FXML
    public void initialize() {
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
                UiConstants.RANK_ANTIPTERARCHOS);

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

                    try {
                        String iconPath = getRankIconPath(rank);
                        Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath)));
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
                        Image image = new Image(Objects.requireNonNull(getClass().getResourceAsStream(iconPath)));
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

    /**
     * Returns the rank's image
     * 
     * @param rank the rank
     * @return the rank's corresponding image path
     */
    private String getRankIconPath(String rank) {
        return switch (rank) {
            case UiConstants.RANK_YPOSMINIAS -> UiConstants.ICON_RANK_YPOSMINIAS;
            case UiConstants.RANK_SMINIAS -> UiConstants.ICON_RANK_SMINIAS;
            case UiConstants.RANK_EPISMINIAS -> UiConstants.ICON_RANK_EPISMINIAS;
            case UiConstants.RANK_ARCHISMINIAS -> UiConstants.ICON_RANK_ARCHISMINIAS;
            case UiConstants.RANK_ANTHYPASPISTIS -> UiConstants.ICON_RANK_ANTHYPASPISTIS;
            case UiConstants.RANK_ANTHYPOSMINAGOS -> UiConstants.ICON_RANK_ANTHYPOSMINAGOS;
            case UiConstants.RANK_YPOSMINAGOS -> UiConstants.ICON_RANK_YPOSMINAGOS;
            case UiConstants.RANK_EPISMINAGOS -> UiConstants.ICON_RANK_EPISMINAGOS;
            case UiConstants.RANK_ANTISMINARCHOS -> UiConstants.ICON_RANK_ANTISMINARCHOS;
            case UiConstants.RANK_SMINARCHOS -> UiConstants.ICON_RANK_SMINARCHOS;
            case UiConstants.RANK_TAKSIARCOS -> UiConstants.ICON_RANK_TAKSIARCOS;
            case UiConstants.RANK_YPOPTERARCHOS -> UiConstants.ICON_RANK_YPOPTERARCHOS;
            case UiConstants.RANK_ANTIPTERARCHOS -> UiConstants.ICON_RANK_ANTIPTERARCHOS;
            case null, default -> UiConstants.ICON_RANK_DEFAULT;
        };
    }

    // Get selected rank
    public String getSelectedRank() {
        return rankComboBox.getValue();
    }
}
