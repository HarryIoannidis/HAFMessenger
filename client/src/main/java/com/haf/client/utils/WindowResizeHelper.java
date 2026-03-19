package com.haf.client.utils;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.util.logging.Logger;

public final class WindowResizeHelper {

    private static final Logger LOGGER = Logger.getLogger(WindowResizeHelper.class.getName());

    // How many pixels from the edge count as "resize zone".
    private static final int BORDER = 8;

    // Fallback minimum dimensions if computed values are unusable.
    private static final double FALLBACK_MIN_WIDTH = 500;
    private static final double FALLBACK_MIN_HEIGHT = 400;

    // Computed minimum window dimensions, set after the first layout pass.
    private static double minWidth = FALLBACK_MIN_WIDTH;
    private static double minHeight = FALLBACK_MIN_HEIGHT;

    private WindowResizeHelper() {
        // Utility class
    }

    private static boolean resizing;
    private static boolean top;
    private static boolean bottom;
    private static boolean left;
    private static boolean right;

    private static double startScreenX;
    private static double startScreenY;
    private static double startW;
    private static double startH;
    private static double startStageX;
    private static double startStageY;

    /**
     * Enables edge-resizing on the given stage.
     * Must be called after the scene has been set on the stage.
     *
     * @param stage the undecorated stage
     */
    public static void enableResizing(Stage stage) {
        Scene scene = stage.getScene();
        if (scene == null) {
            LOGGER.warning("WindowResizeHelper: Scene is null, cannot enable resizing.");
            return;
        }

        // Compute minimum dimensions from the layout after the first render pass
        Platform.runLater(() -> {
            Parent root = scene.getRoot();

            double computedMinW = root.minWidth(-1);
            double computedMinH = root.minHeight(-1);
            minWidth = computedMinW > 0 ? computedMinW : FALLBACK_MIN_WIDTH;
            minHeight = computedMinH > 0 ? computedMinH : FALLBACK_MIN_HEIGHT;

            stage.setMinWidth(minWidth);
            stage.setMinHeight(minHeight);
        });

        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> handleMouseMoved(e, stage));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> handleMousePressed(e, stage));
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> handleMouseDragged(e, stage));
        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> handleMouseReleased(e, scene));
    }

    private static void handleMouseMoved(MouseEvent e, Stage stage) {
        if (resizing) {
            return;
        }
        Cursor cursor = detectEdgeCursor(e, stage);
        stage.getScene().setCursor(cursor);
    }

    private static void handleMousePressed(MouseEvent e, Stage stage) {
        detectEdges(e, stage);
        resizing = left || right || top || bottom;

        if (resizing) {
            if (stage.isMaximized()) {
                stage.setMaximized(false);
            }

            startScreenX = e.getScreenX();
            startScreenY = e.getScreenY();
            startW = stage.getWidth();
            startH = stage.getHeight();
            startStageX = stage.getX();
            startStageY = stage.getY();

            // Sanity check
            if (Double.isNaN(startW) || startW <= 0)
                startW = minWidth;
            if (Double.isNaN(startH) || startH <= 0)
                startH = minHeight;
            if (Double.isNaN(startStageX))
                startStageX = 0;
            if (Double.isNaN(startStageY))
                startStageY = 0;

            e.consume();
        }
    }

    private static void handleMouseDragged(MouseEvent e, Stage stage) {
        if (!resizing) {
            return;
        }

        e.consume();

        if (!stage.isResizable()) {
            stage.setResizable(true);
        }

        double dx = e.getScreenX() - startScreenX;
        double dy = e.getScreenY() - startScreenY;

        double newW = startW;
        double newH = startH;
        double newX = startStageX;
        double newY = startStageY;

        if (right) {
            newW = Math.max(minWidth, startW + dx);
        } else if (left) {
            double proposedW = startW - dx;
            newW = Math.max(minWidth, proposedW);
            newX = startStageX + (startW - newW);
        }

        if (bottom) {
            newH = Math.max(minHeight, startH + dy);
        } else if (top) {
            double proposedH = startH - dy;
            newH = Math.max(minHeight, proposedH);
            newY = startStageY + (startH - newH);
        }

        stage.setWidth(newW);
        stage.setHeight(newH);
        stage.setX(newX);
        stage.setY(newY);
    }

    private static void handleMouseReleased(MouseEvent e, Scene scene) {
        if (resizing) {
            resizing = false;
            top = false;
            bottom = false;
            left = false;
            right = false;
            scene.setCursor(Cursor.DEFAULT);
            e.consume();
        }
    }

    private static void detectEdges(MouseEvent e, Stage stage) {
        double x = e.getSceneX();
        double y = e.getSceneY();
        double w = stage.getScene().getWidth();
        double h = stage.getScene().getHeight();

        left = x < BORDER;
        right = x > w - BORDER;
        top = y < BORDER;
        bottom = y > h - BORDER;
    }

    private static Cursor detectEdgeCursor(MouseEvent e, Stage stage) {
        double x = e.getSceneX();
        double y = e.getSceneY();
        double w = stage.getScene().getWidth();
        double h = stage.getScene().getHeight();

        boolean atLeft = x < BORDER;
        boolean atRight = x > w - BORDER;
        boolean atTop = y < BORDER;
        boolean atBottom = y > h - BORDER;

        if (atTop && atLeft)
            return Cursor.NW_RESIZE;
        if (atTop && atRight)
            return Cursor.NE_RESIZE;
        if (atBottom && atLeft)
            return Cursor.SW_RESIZE;
        if (atBottom && atRight)
            return Cursor.SE_RESIZE;
        if (atTop)
            return Cursor.N_RESIZE;
        if (atBottom)
            return Cursor.S_RESIZE;
        if (atLeft)
            return Cursor.W_RESIZE;
        if (atRight)
            return Cursor.E_RESIZE;

        return Cursor.DEFAULT;
    }
}
