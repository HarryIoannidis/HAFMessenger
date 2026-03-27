package com.haf.client.utils;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.controls.JFXToggleButton;
import com.jfoenix.controls.JFXTogglePane;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsRowBuilderTest {

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
    void build_switch_row_applies_ids_text_and_default_selection() throws Exception {
        JFXTogglePane row = onFxThread(() -> SettingsRowBuilder.buildSwitchRow(
                "switchRow",
                "switchControl",
                "Title",
                "Subtitle",
                true));

        assertEquals("switchRow", row.getId());
        assertInstanceOf(VBox.class, row.getChildren().get(0));
        assertInstanceOf(JFXToggleButton.class, row.getChildren().get(1));
        assertInstanceOf(JFXButton.class, row.getChildren().get(2));

        JFXToggleButton toggle = (JFXToggleButton) row.getChildren().get(1);
        assertEquals("switchControl", toggle.getId());
        assertTrue(toggle.isSelected());

        VBox textBox = (VBox) row.getChildren().get(0);
        Text title = (Text) textBox.getChildren().get(0);
        TextFlow subtitleFlow = (TextFlow) textBox.getChildren().get(1);
        Text subtitle = (Text) subtitleFlow.getChildren().get(0);

        assertEquals("Title", title.getText());
        assertTrue(title.getStyleClass().contains("settings-row-title"));

        assertEquals("Subtitle", subtitle.getText());
        assertTrue(subtitle.getStyleClass().contains("settings-row-subtitle"));
    }

    @Test
    void build_checkbox_row_applies_checkbox_id_and_selection() throws Exception {
        JFXTogglePane row = onFxThread(() -> SettingsRowBuilder.buildCheckboxRow(
                "checkboxRow",
                "checkboxControl",
                "C-Title",
                "C-Subtitle",
                true));

        assertEquals("checkboxRow", row.getId());
        assertInstanceOf(HBox.class, row.getChildren().get(1));
        assertInstanceOf(JFXButton.class, row.getChildren().get(2));

        HBox controlBox = (HBox) row.getChildren().get(1);
        assertInstanceOf(JFXCheckBox.class, controlBox.getChildren().get(0));

        JFXCheckBox checkBox = (JFXCheckBox) controlBox.getChildren().get(0);
        assertEquals("checkboxControl", checkBox.getId());
        assertTrue(checkBox.isSelected());
    }

    @Test
    void build_slider_row_applies_slider_id_bounds_and_ticks() throws Exception {
        JFXTogglePane row = onFxThread(() -> SettingsRowBuilder.buildSliderRow(
                "sliderRow",
                "sliderControl",
                "S-Title",
                "S-Subtitle",
                1.0,
                10.0,
                4.0,
                1.0,
                true,
                true));

        assertEquals("sliderRow", row.getId());
        assertEquals(2, row.getChildren().size());
        assertInstanceOf(HBox.class, row.getChildren().get(1));

        HBox sliderBox = (HBox) row.getChildren().get(1);
        assertInstanceOf(JFXSlider.class, sliderBox.getChildren().get(0));

        JFXSlider slider = (JFXSlider) sliderBox.getChildren().get(0);
        assertEquals("sliderControl", slider.getId());
        assertEquals(1.0, slider.getMin());
        assertEquals(10.0, slider.getMax());
        assertEquals(4.0, slider.getValue());
        assertEquals(1.0, slider.getMajorTickUnit());
        assertTrue(slider.isShowTickMarks());
        assertTrue(slider.isSnapToTicks());
        assertEquals(12.0, sliderBox.getPadding().getTop());
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
        T value = result.get();
        assertNotNull(value);
        return value;
    }

    private static boolean hasDisplay() {
        String display = System.getenv("DISPLAY");
        return display != null && !display.isBlank();
    }
}
