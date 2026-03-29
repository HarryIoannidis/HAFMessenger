package com.haf.client.controllers;

import com.haf.client.models.SettingsMenuItem;
import com.haf.client.utils.ClientSettings;
import com.haf.client.utils.UiConstants;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXToggleButton;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsControllerTest {

    @BeforeAll
    static void initJavaFx() {
        Assumptions.assumeTrue(hasDisplay(), "JavaFX UI tests require DISPLAY.");
        System.setProperty("javafx.cachedir", System.getProperty("java.io.tmpdir") + "/openjfx-cache");
        System.setProperty("prism.order", "sw");
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Already initialized.
        } catch (RuntimeException ex) {
            Assumptions.assumeTrue(false, "JavaFX toolkit unavailable: " + ex.getMessage());
        }
    }

    @Test
    void settings_fxml_loads_and_initial_state_is_consistent() throws Exception {
        LoadedSettingsView loaded = onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(UiConstants.FXML_SETTINGS));
            Parent root = loader.load();
            SettingsController controller = loader.getController();
            return new LoadedSettingsView(root, controller);
        });

        assertNotNull(loaded.root());
        assertNotNull(loaded.controller());

        List<SettingsMenuItem> items = loaded.controller().menuItemsForTest();
        assertEquals(6, items.size());
        assertEquals(
                List.of("General", "Search", "Preview & Media", "Chat", "Notifications", "Privacy"),
                items.stream().map(SettingsMenuItem::label).toList());
        assertEquals("generalSettingsPane", loaded.controller().visiblePaneIdForTest());
    }

    @Test
    void selecting_menu_item_switches_visible_pane() throws Exception {
        SettingsController controller = onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(UiConstants.FXML_SETTINGS));
            loader.load();
            return loader.getController();
        });

        onFxThread(() -> {
            controller.selectMenuByPaneIdForTest("privacySettingsPane");
            return null;
        });

        assertEquals("privacySettingsPane", controller.visiblePaneIdForTest());
    }

    @Test
    void settings_item_cell_fxml_loads_with_expected_namespace_nodes() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(UiConstants.FXML_SETTINGS_ITEM_CELL));
        Parent root = onFxThread(loader::load);

        assertNotNull(root);
        assertTrue(loader.getNamespace().containsKey("menuIcon"));
        assertTrue(loader.getNamespace().containsKey("nameText"));
        assertTrue(loader.getNamespace().containsKey("overlayButton"));
    }

    @Test
    void search_rows_place_require_enter_directly_below_instant_search() throws Exception {
        LoadedSettingsView loaded = loadSettingsView();
        VBox searchRows = findById(loaded.root(), "searchRowsContainer", VBox.class);

        assertNotNull(searchRows);
        List<String> rowIds = searchRows.getChildren().stream()
                .map(Node::getId)
                .toList();
        int instantIndex = rowIds.indexOf("searchInstantOnTypeRow");
        int requireEnterIndex = rowIds.indexOf("searchRequireEnterToSearchRow");
        int autoOpenIndex = rowIds.indexOf("searchAutoOpenFilterOnFirstSearchRow");

        assertTrue(instantIndex >= 0);
        assertEquals(instantIndex + 1, requireEnterIndex);
        assertTrue(autoOpenIndex > requireEnterIndex);
    }

    @Test
    void dependent_slider_rows_follow_master_toggle_state_and_fade_class() throws Exception {
        ClientSettings settings = ClientSettings.defaults();
        settings.setPrivacyBlurOnFocusLoss(false);
        settings.setMediaHoverZoom(true);
        settings.setNotificationsShowUnreadBadges(true);

        LoadedSettingsView loaded = loadSettingsView();
        onFxThread(() -> {
            loaded.controller().setSettings(settings);
            return null;
        });

        Node blurStrengthRow = findById(loaded.root(), "privacyBlurStrengthRow", Node.class);
        JFXToggleButton blurToggle = findById(loaded.root(), "privacyBlurOnFocusLossToggle", JFXToggleButton.class);
        Node hoverZoomScaleRow = findById(loaded.root(), "mediaHoverZoomScaleRow", Node.class);
        JFXToggleButton hoverZoomToggle = findById(loaded.root(), "mediaHoverZoomToggle", JFXToggleButton.class);
        Node badgeCapRow = findById(loaded.root(), "notificationsBadgeCapRow", Node.class);
        JFXToggleButton badgeToggle = findById(loaded.root(), "notificationsShowUnreadBadgesToggle",
                JFXToggleButton.class);

        assertNotNull(blurStrengthRow);
        assertNotNull(blurToggle);
        assertNotNull(hoverZoomScaleRow);
        assertNotNull(hoverZoomToggle);
        assertNotNull(badgeCapRow);
        assertNotNull(badgeToggle);

        assertDependentRowState(blurStrengthRow, false);
        assertDependentRowState(hoverZoomScaleRow, true);
        assertDependentRowState(badgeCapRow, true);

        onFxThread(() -> {
            blurToggle.setSelected(true);
            hoverZoomToggle.setSelected(false);
            badgeToggle.setSelected(false);
            return null;
        });

        assertDependentRowState(blurStrengthRow, true);
        assertDependentRowState(hoverZoomScaleRow, false);
        assertDependentRowState(badgeCapRow, false);

        onFxThread(() -> {
            blurToggle.setSelected(false);
            hoverZoomToggle.setSelected(true);
            badgeToggle.setSelected(true);
            return null;
        });

        assertDependentRowState(blurStrengthRow, false);
        assertDependentRowState(hoverZoomScaleRow, true);
        assertDependentRowState(badgeCapRow, true);
    }

    @Test
    void search_toggle_modes_are_mutually_exclusive_on_user_enable() throws Exception {
        ClientSettings settings = ClientSettings.defaults();
        LoadedSettingsView loaded = loadSettingsView();
        onFxThread(() -> {
            loaded.controller().setSettings(settings);
            return null;
        });

        JFXToggleButton instantToggle = findById(loaded.root(), "searchInstantOnTypeToggle", JFXToggleButton.class);
        JFXToggleButton requireEnterToggle = findById(loaded.root(), "searchRequireEnterToSearchToggle",
                JFXToggleButton.class);

        assertNotNull(instantToggle);
        assertNotNull(requireEnterToggle);
        assertFalse(instantToggle.isSelected());
        assertFalse(requireEnterToggle.isSelected());

        onFxThread(() -> {
            requireEnterToggle.setSelected(true);
            return null;
        });
        assertTrue(requireEnterToggle.isSelected());
        assertFalse(instantToggle.isSelected());
        assertTrue(settings.isSearchRequireEnterToSearch());
        assertFalse(settings.isSearchInstantOnType());

        onFxThread(() -> {
            instantToggle.setSelected(true);
            return null;
        });
        assertTrue(instantToggle.isSelected());
        assertFalse(requireEnterToggle.isSelected());
        assertTrue(settings.isSearchInstantOnType());
        assertFalse(settings.isSearchRequireEnterToSearch());
    }

    @Test
    void legacy_both_on_search_settings_stay_as_loaded_until_user_toggles() throws Exception {
        ClientSettings settings = ClientSettings.defaults();
        settings.setSearchInstantOnType(true);
        settings.setSearchRequireEnterToSearch(true);

        LoadedSettingsView loaded = loadSettingsView();
        onFxThread(() -> {
            loaded.controller().setSettings(settings);
            return null;
        });

        JFXToggleButton instantToggle = findById(loaded.root(), "searchInstantOnTypeToggle", JFXToggleButton.class);
        JFXToggleButton requireEnterToggle = findById(loaded.root(), "searchRequireEnterToSearchToggle",
                JFXToggleButton.class);

        assertNotNull(instantToggle);
        assertNotNull(requireEnterToggle);
        assertTrue(instantToggle.isSelected());
        assertTrue(requireEnterToggle.isSelected());
        assertTrue(settings.isSearchInstantOnType());
        assertTrue(settings.isSearchRequireEnterToSearch());

        onFxThread(() -> {
            requireEnterToggle.setSelected(false);
            requireEnterToggle.setSelected(true);
            return null;
        });

        assertFalse(instantToggle.isSelected());
        assertTrue(requireEnterToggle.isSelected());
        assertFalse(settings.isSearchInstantOnType());
        assertTrue(settings.isSearchRequireEnterToSearch());
    }

    @Test
    void auto_clear_search_toggle_maps_inverse_to_preserve_last_query_setting() throws Exception {
        ClientSettings settings = ClientSettings.defaults();
        LoadedSettingsView loaded = loadSettingsView();
        onFxThread(() -> {
            loaded.controller().setSettings(settings);
            return null;
        });

        JFXCheckBox autoClearCheck = findById(loaded.root(), "searchAutoClearOnTabExitCheck", JFXCheckBox.class);
        assertNotNull(autoClearCheck);
        assertTrue(autoClearCheck.isSelected());
        assertFalse(settings.isSearchPreserveLastQuery());

        onFxThread(() -> {
            autoClearCheck.setSelected(false);
            return null;
        });

        assertFalse(autoClearCheck.isSelected());
        assertTrue(settings.isSearchPreserveLastQuery());
    }

    private static LoadedSettingsView loadSettingsView() throws Exception {
        return onFxThread(() -> {
            FXMLLoader loader = new FXMLLoader(SettingsControllerTest.class.getResource(UiConstants.FXML_SETTINGS));
            Parent root = loader.load();
            SettingsController controller = loader.getController();
            return new LoadedSettingsView(root, controller);
        });
    }

    private static void assertDependentRowState(Node row, boolean enabled) {
        assertEquals(!enabled, row.isDisable());
        assertEquals(!enabled, row.getStyleClass().contains("settings-row-disabled"));
    }

    private static <T extends Node> T findById(Node root, String id, Class<T> type) {
        if (root == null || id == null || type == null) {
            return null;
        }
        if (id.equals(root.getId()) && type.isInstance(root)) {
            return type.cast(root);
        }
        for (Node child : childNodesOf(root)) {
            T found = findById(child, id, type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Node> childNodesOf(Node node) {
        if (node == null) {
            return List.of();
        }
        java.util.ArrayList<Node> children = new java.util.ArrayList<>();
        if (node instanceof Parent parent) {
            children.addAll(parent.getChildrenUnmodifiable());
        }
        if (node instanceof SplitPane splitPane) {
            children.addAll(splitPane.getItems());
        }
        if (node instanceof ScrollPane scrollPane && scrollPane.getContent() != null) {
            children.add(scrollPane.getContent());
        }
        return children;
    }

    private static <T> T onFxThread(Callable<T> callable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                result.set(callable.call());
            } catch (Throwable ex) {
                error.set(ex);
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed);
        if (error.get() != null) {
            throw new AssertionError("FX-thread execution failed", error.get());
        }
        return result.get();
    }

    private static boolean hasDisplay() {
        String display = System.getenv("DISPLAY");
        return display != null && !display.isBlank();
    }

    private record LoadedSettingsView(Parent root, SettingsController controller) {
    }

    private static final Path CONTROLLER_SOURCE = Path
            .of("src/main/java/com/haf/client/controllers/SettingsController.java");

    @Test
    void close_flow_uses_restart_dirty_flag_and_shows_restart_popup() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("if (!settings.isRestartRequiredDirty()) {"));
        assertTrue(source.contains(".title(\"Restart required\")"));
        assertTrue(source.contains(".actionText(\"Restart now\")"));
        assertTrue(source.contains(".cancelText(\"Later\")"));
    }

    @Test
    void row_overlay_clicks_toggle_switch_and_checkbox_controls() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        assertTrue(source.contains("wireOverlayRowToggle(rowId, () -> toggle.setSelected(!toggle.isSelected()));"));
        assertTrue(source.contains("wireOverlayRowToggle(rowId, () -> checkBox.setSelected(!checkBox.isSelected()));"));
    }

    @Test
    void search_toggle_order_mutual_exclusion_and_dependent_slider_row_states_are_wired() throws IOException {
        String source = Files.readString(CONTROLLER_SOURCE);

        int instantRowIndex = source.indexOf("\"searchInstantOnTypeRow\"");
        int requireEnterRowIndex = source.indexOf("\"searchRequireEnterToSearchRow\"");
        int autoOpenRowIndex = source.indexOf("\"searchAutoOpenFilterOnFirstSearchRow\"");

        assertTrue(instantRowIndex >= 0);
        assertTrue(requireEnterRowIndex > instantRowIndex);
        assertTrue(autoOpenRowIndex > requireEnterRowIndex);

        assertTrue(source.contains("wireSearchModeMutualExclusivity();"));
        assertTrue(source.contains(
                "wireDependentSliderRowState(\"privacyBlurOnFocusLossToggle\", \"privacyBlurStrengthRow\");"));
        assertTrue(
                source.contains("wireDependentSliderRowState(\"mediaHoverZoomToggle\", \"mediaHoverZoomScaleRow\");"));
        assertTrue(source.contains(
                "wireDependentSliderRowState(\"notificationsShowUnreadBadgesToggle\", \"notificationsBadgeCapRow\");"));
        assertTrue(source.contains(
                "wireSwitch(\"notificationsShowOsNotificationsRow\", \"notificationsShowOsNotificationsToggle\","));
        assertTrue(source.contains("if (syncing.get() || !Boolean.TRUE.equals(newValue)) {"));
        assertTrue(source.contains("applyDependentRowState(dependentRow, toggle.isSelected());"));
        assertTrue(source.contains("row.setDisable(!enabled);"));
        assertTrue(source.contains("DISABLED_ROW_STYLE_CLASS"));
        assertTrue(source.contains("\"notificationsShowOsNotificationsRow\""));
        assertTrue(source
                .contains("wireSlider(\"searchMinimumQueryLengthSlider\", settings::setSearchMinimumQueryLength);"));
        assertTrue(source
                .contains("wireInvertedCheckbox(\"searchAutoClearOnTabExitRow\", \"searchAutoClearOnTabExitCheck\","));
        assertTrue(source.contains(
                "wireSwitch(\"privacyBlurOnStartupUntilUnlockRow\", \"privacyBlurOnStartupUntilUnlockToggle\","));
        assertTrue(source.contains(
                "wireCheckbox(\"privacyConfirmExternalLinkOpenRow\", \"privacyConfirmExternalLinkOpenCheck\","));
        assertTrue(source.contains(
                "wireCheckbox(\"privacyShowNotificationMessagePreviewRow\", \"privacyShowNotificationMessagePreviewCheck\","));
        assertTrue(source.contains("\"privacyShowNotificationMessagePreviewRow\""));
        assertTrue(source.contains("SettingsRowBuilder.buildSectionSpacer(\"searchFlowSectionSpacer\")"));
        assertTrue(source.contains("SettingsRowBuilder.buildSectionSpacer(\"privacySafetySectionSpacer\")"));
        assertTrue(source.contains("SettingsRowBuilder.buildSectionSpacer(\"privacyPresenceSectionSpacer\")"));
        assertTrue(source.contains("SettingsRowBuilder.buildSectionHeader("));
    }
}
