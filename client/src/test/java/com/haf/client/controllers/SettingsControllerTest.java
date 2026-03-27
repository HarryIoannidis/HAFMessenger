package com.haf.client.controllers;

import com.haf.client.models.SettingsMenuItem;
import com.haf.client.utils.UiConstants;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
