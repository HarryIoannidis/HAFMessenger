package com.haf.client.utils;

import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Fluent builder for styled context menus used across client controllers.
 */
public final class ContextMenuBuilder {

    private static final int DEFAULT_ICON_SIZE = 22;

    private final ContextMenu menu;

    /**
     * Creates a builder initialized with the default menu style class.
     */
    private ContextMenuBuilder() {
        this.menu = new ContextMenu();
        this.menu.getStyleClass().add("dropdown-menu");
    }

    /**
     * Creates a new context-menu builder.
     *
     * @return a new {@link ContextMenuBuilder} instance
     */
    public static ContextMenuBuilder create() {
        return new ContextMenuBuilder();
    }

    /**
     * Adds an additional style class to the context menu container.
     *
     * @param styleClass style class to append when non-blank
     * @return this builder for fluent chaining
     */
    public ContextMenuBuilder menuStyleClass(String styleClass) {
        if (styleClass != null && !styleClass.isBlank() && !menu.getStyleClass().contains(styleClass)) {
            menu.getStyleClass().add(styleClass);
        }
        return this;
    }

    /**
     * Adds an enabled menu option with icon and label.
     *
     * @param iconLiteral Ikonli icon literal for the option
     * @param text        option label text
     * @param action      callback executed when the option is selected
     * @return this builder for fluent chaining
     */
    public ContextMenuBuilder addOption(String iconLiteral, String text, Runnable action) {
        return addOption(iconLiteral, text, false, action);
    }

    /**
     * Adds a menu option with explicit disabled state.
     *
     * @param iconLiteral Ikonli icon literal for the option
     * @param text        option label text
     * @param disabled    {@code true} to disable selection
     * @param action      callback executed when the option is selected
     * @return this builder for fluent chaining
     */
    public ContextMenuBuilder addOption(String iconLiteral, String text, boolean disabled, Runnable action) {
        MenuItem item = createIconMenuItem(iconLiteral, text);
        item.setDisable(disabled);
        item.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        menu.getItems().add(item);
        return this;
    }

    /**
     * Appends a visual separator to the menu.
     *
     * @return this builder for fluent chaining
     */
    public ContextMenuBuilder addSeparator() {
        menu.getItems().add(new SeparatorMenuItem());
        return this;
    }

    /**
     * Registers a callback invoked when the context menu is hidden.
     *
     * @param action callback to invoke when the menu closes
     * @return this builder for fluent chaining
     */
    public ContextMenuBuilder onHidden(Runnable action) {
        menu.setOnHidden(event -> {
            if (action != null) {
                action.run();
            }
        });
        return this;
    }

    /**
     * Builds and returns the configured JavaFX {@link ContextMenu}.
     *
     * @return the configured context menu instance
     */
    public ContextMenu build() {
        return menu;
    }

    /**
     * Creates a menu item row composed of an icon and text label.
     *
     * @param iconLiteral Ikonli icon literal
     * @param text        visible option label
     * @return configured {@link MenuItem} with custom graphic and styles
     */
    private static MenuItem createIconMenuItem(String iconLiteral, String text) {
        FontIcon icon = new FontIcon(iconLiteral == null ? "" : iconLiteral);
        icon.setIconSize(DEFAULT_ICON_SIZE);

        Text optionText = new Text(text == null ? "" : text);
        optionText.getStyleClass().add("dropdown-menu-option-text");

        HBox row = new HBox(12, icon, optionText);
        row.setAlignment(Pos.CENTER_LEFT);

        MenuItem item = new MenuItem();
        item.setGraphic(row);
        item.getStyleClass().add("dropdown-menu-item");
        return item;
    }
}
