package com.haf.client.utils;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXToggleButton;
import com.jfoenix.controls.JFXTogglePane;
import javafx.geometry.Insets;
import javafx.scene.control.ContentDisplay;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Builder helpers for standardized Settings rows using the existing toggle-pane
 * structure.
 */
public final class SettingsRowBuilder {

    private static final Paint PRIMARY_BLUE = Paint.valueOf("#007ABD");
    private static final Paint TOGGLE_OFF_KNOB = Paint.valueOf("#F4F4F4");
    private static final Paint TOGGLE_OFF_TRACK = Paint.valueOf("#BDBDBD");
    private static final Paint ROW_RIPPLE_FILL = Paint.valueOf("rgba(0, 0, 0, 0.14)");

    /**
     * Utility class.
     */
    private SettingsRowBuilder() {
    }

    /**
     * Builds a switch-based settings row.
     *
     * @param rowId     row node id
     * @param controlId switch control id
     * @param title     title text
     * @param subtitle  subtitle text
     * @param selected  initial switch selection
     * @return configured row node
     */
    public static JFXTogglePane buildSwitchRow(
            String rowId,
            String controlId,
            String title,
            String subtitle,
            boolean selected) {

        JFXTogglePane row = createBaseRow(rowId, title, subtitle, true);

        JFXToggleButton toggleButton = new JFXToggleButton();
        toggleButton.setId(controlId);
        toggleButton.setFocusTraversable(false);
        toggleButton.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        toggleButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        toggleButton.setSize(9.0);
        toggleButton.setViewOrder(4.0);
        toggleButton.setSelected(selected);
        toggleButton.setPadding(new Insets(0, 0, 1, 0));
        toggleButton.setToggleColor(PRIMARY_BLUE);
        toggleButton.setToggleLineColor(PRIMARY_BLUE);
        toggleButton.setUnToggleColor(TOGGLE_OFF_KNOB);
        toggleButton.setUnToggleLineColor(TOGGLE_OFF_TRACK);

        row.getChildren().add(1, toggleButton);
        return row;
    }

    /**
     * Builds a checkbox-based settings row.
     *
     * @param rowId     row node id
     * @param controlId checkbox control id
     * @param title     title text
     * @param subtitle  subtitle text
     * @param selected  initial checkbox selection
     * @return configured row node
     */
    public static JFXTogglePane buildCheckboxRow(
            String rowId,
            String controlId,
            String title,
            String subtitle,
            boolean selected) {

        JFXTogglePane row = createBaseRow(rowId, title, subtitle, true);

        JFXCheckBox checkBox = new JFXCheckBox();
        checkBox.setId(controlId);
        checkBox.setFocusTraversable(false);
        checkBox.setAlignment(javafx.geometry.Pos.CENTER);
        checkBox.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        checkBox.setSelected(selected);
        checkBox.setCheckedColor(PRIMARY_BLUE);

        HBox controlBox = new HBox(checkBox);
        controlBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        controlBox.setPadding(new Insets(10, 10, 10, 10));

        row.getChildren().add(1, controlBox);
        return row;
    }

    /**
     * Builds a slider-based settings row.
     *
     * @param rowId         row node id
     * @param controlId     slider control id
     * @param title         title text
     * @param subtitle      subtitle text
     * @param min           slider min value
     * @param max           slider max value
     * @param value         slider initial value
     * @param majorTickUnit major tick unit
     * @param showTickMarks whether ticks are visible
     * @param snapToTicks   whether slider snaps to ticks
     * @return configured row node
     */
    public static JFXTogglePane buildSliderRow(
            String rowId,
            String controlId,
            String title,
            String subtitle,
            double min,
            double max,
            double value,
            double majorTickUnit,
            boolean showTickMarks,
            boolean snapToTicks) {

        JFXTogglePane row = createBaseRow(rowId, title, subtitle, false);
        row.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        JFXSlider slider = new JFXSlider();
        slider.setId(controlId);
        slider.setMin(min);
        slider.setMax(max);
        slider.setValue(value);
        slider.setMajorTickUnit(majorTickUnit);
        slider.setShowTickMarks(showTickMarks);
        slider.setSnapToTicks(snapToTicks);
        slider.setPrefHeight(26.0);
        slider.setPrefWidth(290.0);
        slider.setStyle("-jfx-default-thumb: #007ABD; -jfx-default-track: #007ABD; -jfx-track-color: #C7C7C7;");

        HBox sliderBox = new HBox(slider);
        sliderBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        sliderBox.setPrefWidth(100.0);
        sliderBox.setPadding(new Insets(12, 0, 0, 0));

        row.getChildren().add(1, sliderBox);
        return row;
    }

    /**
     * Builds an in-pane section header using the existing title + divider visual
     * style.
     *
     * @param sectionId section node id
     * @param title section title
     * @return section header node
     */
    public static VBox buildSectionHeader(String sectionId, String title) {
        VBox sectionBox = new VBox(0.0);
        sectionBox.setId(sectionId);

        Text sectionTitle = new Text(title == null ? "" : title);
        sectionTitle.getStyleClass().add("settings-pane-title");

        Pane divider = new Pane();
        divider.setMinHeight(Double.NEGATIVE_INFINITY);
        divider.setMinWidth(Double.NEGATIVE_INFINITY);
        divider.setPrefHeight(1.0);
        divider.getStyleClass().add("divider-profile");
        VBox.setMargin(divider, new Insets(10.0, 0.0, 10.0, 0.0));

        sectionBox.getChildren().addAll(sectionTitle, divider);
        VBox.setMargin(sectionBox, new Insets(0.0, 0.0, 2.0, 0.0));
        return sectionBox;
    }

    /**
     * Builds an in-pane spacer used to separate section groups.
     *
     * @param spacerId spacer node id
     * @return spacer pane with a fixed 20px height
     */
    public static Pane buildSectionSpacer(String spacerId) {
        Pane spacer = new Pane();
        spacer.setId(spacerId);
        spacer.setMinHeight(20.0);
        spacer.setPrefHeight(20.0);
        spacer.setMaxHeight(20.0);
        return spacer;
    }

    /**
     * Creates the shared row scaffold with text content and optional overlay
     * button.
     *
     * @param rowId                row node id
     * @param title                title text
     * @param subtitle             subtitle text
     * @param includeOverlayButton whether to include a full-size overlay/ripple
     *                             node
     * @return preconfigured base row
     */
    private static JFXTogglePane createBaseRow(
            String rowId,
            String title,
            String subtitle,
            boolean includeOverlayButton) {
        JFXTogglePane row = new JFXTogglePane();
        row.setId(rowId);
        row.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        row.setPrefHeight(60.0);

        VBox textBox = new VBox(5.0);
        textBox.setPrefHeight(200.0);
        textBox.getChildren().addAll(createTitle(title), createSubtitle(subtitle));
        textBox.setPadding(new Insets(10.0, 10.0, 10.0, 10.0));
        HBox.setHgrow(textBox, Priority.ALWAYS);

        row.getChildren().add(textBox);
        if (includeOverlayButton) {
            JFXButton overlayButton = new JFXButton();
            overlayButton.setAlignment(javafx.geometry.Pos.CENTER);
            overlayButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            overlayButton.setFocusTraversable(false);
            overlayButton.setMaxHeight(Double.MAX_VALUE);
            overlayButton.setMaxWidth(Double.MAX_VALUE);
            overlayButton.setRipplerFill(ROW_RIPPLE_FILL);
            overlayButton.getStyleClass().add("settings-row-overlay-button");
            overlayButton.setText(" ");
            row.getChildren().add(overlayButton);
        }

        VBox.setMargin(row, new Insets(5.0, 0.0, 5.0, 0.0));
        return row;
    }

    /**
     * Creates the title node used by every row.
     *
     * @param title title text
     * @return title text node
     */
    private static Text createTitle(String title) {
        Text titleText = new Text(title == null ? "" : title);
        titleText.getStyleClass().add("settings-row-title");
        return titleText;
    }

    /**
     * Creates the subtitle flow used by every row.
     *
     * @param subtitle subtitle text
     * @return subtitle flow
     */
    private static TextFlow createSubtitle(String subtitle) {
        Text subtitleText = new Text(subtitle == null ? "" : subtitle);
        subtitleText.getStyleClass().add("settings-row-subtitle");
        return new TextFlow(subtitleText);
    }
}
