package com.haf.client.models;

import java.util.Objects;

/**
 * Immutable menu item descriptor for the Settings popup left navigation list.
 *
 * @param label visible menu label
 * @param iconLiteral Ikonli icon literal rendered in {@code settings_item_cell.fxml}
 * @param paneId target pane id in {@code settings.fxml}
 */
public record SettingsMenuItem(String label, String iconLiteral, String paneId) {

    /**
     * Canonical constructor enforcing non-null values.
     */
    public SettingsMenuItem {
        label = Objects.requireNonNullElse(label, "");
        iconLiteral = Objects.requireNonNullElse(iconLiteral, "mdi2c-cog-outline");
        paneId = Objects.requireNonNullElse(paneId, "");
    }
}
