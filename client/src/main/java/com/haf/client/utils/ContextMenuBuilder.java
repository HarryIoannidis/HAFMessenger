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

    private static final String DEFAULT_MENU_STYLE = "dropdown-menu";
    private static final String DEFAULT_ITEM_STYLE = "dropdown-menu-item";
    private static final String DEFAULT_TEXT_STYLE = "dropdown-menu-option-text";
    private static final int DEFAULT_ICON_SIZE = 22;

    private final ContextMenu menu;

    private ContextMenuBuilder() {
        this.menu = new ContextMenu();
        this.menu.getStyleClass().add(DEFAULT_MENU_STYLE);
    }

    public static ContextMenuBuilder create() {
        return new ContextMenuBuilder();
    }

    public ContextMenuBuilder menuStyleClass(String styleClass) {
        if (styleClass != null && !styleClass.isBlank() && !menu.getStyleClass().contains(styleClass)) {
            menu.getStyleClass().add(styleClass);
        }
        return this;
    }

    public ContextMenuBuilder addOption(String iconLiteral, String text, Runnable action) {
        return addOption(iconLiteral, text, false, action);
    }

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

    public ContextMenuBuilder addSeparator() {
        menu.getItems().add(new SeparatorMenuItem());
        return this;
    }

    public ContextMenuBuilder onHidden(Runnable action) {
        menu.setOnHidden(event -> {
            if (action != null) {
                action.run();
            }
        });
        return this;
    }

    public ContextMenu build() {
        return menu;
    }

    private static MenuItem createIconMenuItem(String iconLiteral, String text) {
        FontIcon icon = new FontIcon(iconLiteral == null ? "" : iconLiteral);
        icon.setIconSize(DEFAULT_ICON_SIZE);

        Text optionText = new Text(text == null ? "" : text);
        optionText.getStyleClass().add(DEFAULT_TEXT_STYLE);

        HBox row = new HBox(12, icon, optionText);
        row.setAlignment(Pos.CENTER_LEFT);

        MenuItem item = new MenuItem();
        item.setGraphic(row);
        item.getStyleClass().add(DEFAULT_ITEM_STYLE);
        return item;
    }
}
