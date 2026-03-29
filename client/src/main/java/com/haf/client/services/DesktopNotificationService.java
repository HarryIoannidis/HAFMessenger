package com.haf.client.services;

import com.haf.client.utils.UiConstants;
import java.io.File;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OS-native desktop notification service backed by {@link SystemTray} and
 * {@link TrayIcon}.
 *
 * Initialization is lazy and safely no-ops on unsupported/headless
 * environments.
 */
public class DesktopNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DesktopNotificationService.class);
    private static final long NATIVE_COMMAND_TIMEOUT_MS = 1_500L;

    private final Object initLock = new Object();
    private final AtomicBoolean initializationAttempted = new AtomicBoolean();
    private final AtomicReference<TrayIcon> trayIconRef = new AtomicReference<>();
    private final AtomicReference<Runnable> pendingClickAction = new AtomicReference<>();
    private final AtomicReference<NativeNotifier> nativeNotifierRef = new AtomicReference<>();

    private enum NativeNotifier {
        LINUX_NOTIFY_SEND,
        MAC_OSASCRIPT,
        NONE
    }

    /**
     * Shows a native desktop notification.
     *
     * @param title   notification title text
     * @param message notification body text
     * @param onClick callback invoked when notification click is supported and
     *                the user activates the notification
     */
    public void showNotification(String title, String message, Runnable onClick) {
        String safeTitle = title == null || title.isBlank() ? UiConstants.APP_TITLE : title.trim();
        String safeMessage = message == null || message.isBlank() ? "New message received" : message.trim();

        if (tryShowNativeNotification(safeTitle, safeMessage)) {
            return;
        }

        TrayIcon icon = getOrCreateTrayIcon();
        if (icon == null) {
            return;
        }

        pendingClickAction.set(onClick);
        try {
            icon.displayMessage(safeTitle, safeMessage, TrayIcon.MessageType.NONE);
        } catch (Exception ex) {
            pendingClickAction.set(null);
            LOGGER.debug("Failed to show system tray notification: {}", ex.getMessage());
        }
    }

    private TrayIcon getOrCreateTrayIcon() {
        TrayIcon existing = trayIconRef.get();
        if (existing != null) {
            return existing;
        }
        if (initializationAttempted.get()) {
            return null;
        }

        synchronized (initLock) {
            existing = trayIconRef.get();
            if (existing != null) {
                return existing;
            }
            if (initializationAttempted.get()) {
                return null;
            }
            initializationAttempted.set(true);

            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
                return null;
            }

            try {
                SystemTray systemTray = SystemTray.getSystemTray();
                TrayIcon created = new TrayIcon(resolveTrayImage(), UiConstants.APP_TITLE);
                created.setImageAutoSize(true);
                created.addActionListener(event -> runClickAction());
                systemTray.add(created);
                trayIconRef.set(created);
                return created;
            } catch (Exception ex) {
                LOGGER.debug("System tray notification initialization unavailable: {}", ex.getMessage());
                return null;
            }
        }
    }

    private void runClickAction() {
        Runnable action = pendingClickAction.getAndSet(null);
        if (action == null) {
            return;
        }
        try {
            action.run();
        } catch (Exception ex) {
            LOGGER.debug("System tray notification click action failed: {}", ex.getMessage());
        }
    }

    private static Image resolveTrayImage() {
        URL iconUrl = DesktopNotificationService.class.getResource(UiConstants.IMAGE_APP_LOGO);
        if (iconUrl != null) {
            return java.awt.Toolkit.getDefaultToolkit().getImage(iconUrl);
        }
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    private boolean tryShowNativeNotification(String title, String message) {
        NativeNotifier notifier = resolveNativeNotifier();
        return switch (notifier) {
            case LINUX_NOTIFY_SEND -> runNativeCommand(List.of(
                    "notify-send",
                    "-a",
                    UiConstants.APP_TITLE,
                    title,
                    message));
            case MAC_OSASCRIPT -> runNativeCommand(List.of(
                    "osascript",
                    "-e",
                    "display notification " + toAppleScriptString(message)
                            + " with title " + toAppleScriptString(title)));
            case NONE -> false;
        };
    }

    private NativeNotifier resolveNativeNotifier() {
        NativeNotifier existing = nativeNotifierRef.get();
        if (existing != null) {
            return existing;
        }

        NativeNotifier detected = detectNativeNotifier();
        nativeNotifierRef.compareAndSet(null, detected);
        return nativeNotifierRef.get();
    }

    private static NativeNotifier detectNativeNotifier() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("linux") && isCommandOnPath("notify-send")) {
            return NativeNotifier.LINUX_NOTIFY_SEND;
        }
        if ((osName.contains("mac") || osName.contains("darwin")) && isCommandOnPath("osascript")) {
            return NativeNotifier.MAC_OSASCRIPT;
        }
        return NativeNotifier.NONE;
    }

    private static boolean isCommandOnPath(String command) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return false;
        }

        String[] entries = pathEnv.split(File.pathSeparator);
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path candidate = Path.of(entry, command);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean runNativeCommand(List<String> command) {
        List<String> safeCommand = new ArrayList<>(command);
        try {
            Process process = new ProcessBuilder(safeCommand)
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(NATIVE_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            LOGGER.debug("Native notification command failed: {}", ex.getMessage());
            return false;
        }
    }

    private static String toAppleScriptString(String text) {
        String value = text == null ? "" : text;
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                + "\"";
    }
}
