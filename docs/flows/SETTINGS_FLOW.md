# SYSTEM SETTINGS FLOW ANALYSIS

This document provides a technical breakdown of how App settings are tracked, saved, and distributed across the active runtime in the HAFMessenger client via `SettingsController.java` and `ClientSettings.java`.

## 1. Centralized Observability (Pub/Sub)

The `ClientSettings` engine utilizes a robust `Listener` publisher/subscriber model.

- Subordinate controllers like `ChatController` or `MainController` register a callback action linking directly to a specific state key (e.g. `ClientSettings.Key.CHAT_SHOW_MESSAGE_TIMESTAMPS`).
- Media settings include `Image Send Quality`, an immediate per-user slider from `60` to `100` in steps of `5`; the default `100` preserves original image bytes.

## 2. Memory vs Storage Tracking

- Changes processed via sliders or toggle checkboxes within the `SettingsController` instantly update the in-memory variables to ensure zero UI-lag.
- Writes are persisted synchronously in the same call chain: `setValue()` (ClientSettings.java, line 961) calls `persistValue()` (line 1019) which delegates to `java.util.prefs.Preferences` directly on the calling thread. By attaching to the OS native user registry/PLIST files, configurations easily survive application restarts.
- `MessagesViewModel` reads the active `ClientSettings` instance for outbound image preparation, so changing the quality slider affects later sends without a restart.

## 3. Dynamic Re-Rendering

When critical UX boundaries are altered by the user (like disabling timestamps or disabling 24-hour clocks):

- The `SettingsController` throws an event broadcast using the Pub/Sub model.
- The listening `ChatController` traps the broadcast. It runs `Platform.runLater(this::refreshRenderedMessages)`.
- It aggressively clears the active `chatBox` container off the screen and loops over the observable active message list in memory, running them back through `MessageBubbleFactory` immediately injecting the new settings configuration. The bubble UI transforms live.

## 4. Process Locking Constraints

Certain destructive application settings (like changing network environments) can't be rendered cleanly mid-stream. In these cases, the `SettingsController` invokes the `requestAppRestart()` callback (wired from `MainController`), which performs a full logout via `mainSessionService.logout()`, spawns a new client JVM process through `relaunchClientProcess()`, and then terminates the current process with `Platform.exit()` / `System.exit(0)`.
