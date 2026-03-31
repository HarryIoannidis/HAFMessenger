# SYSTEM SETTINGS FLOW ANALYSIS

This document provides a technical breakdown of how App settings are tracked, saved, and distributed across the active runtime in the HAFMessenger client via `SettingsController.java` and `ClientSettings.java`.

## 1. Centralized Observability (Pub/Sub)

The `ClientSettings` engine utilizes a robust `Listener` publisher/subscriber model.

- Subordinate controllers like `ChatController` or `MainController` register a callback action linking directly to a specific state key (e.g. `ClientSettings.Key.CHAT_SHOW_MESSAGE_TIMESTAMPS`).

## 2. Memory vs Storage Tracking

- Changes processed via sliders or toggle checkboxes within the `SettingsController` instantly update the in-memory variables to ensure zero UI-lag.
- Background jobs asynchronously push these variables to persistent disk storage using standard `java.util.prefs.Preferences`. By attaching to the OS native user registry/PLIST files, configurations easily survive application restarts.

## 3. Dynamic Re-Rendering

When critical UX boundaries are altered by the user (like disabling timestamps or disabling 24-hour clocks):

- The `SettingsController` throws an event broadcast using the Pub/Sub model.
- The listening `ChatController` traps the broadcast. It runs `Platform.runLater(this::refreshRenderedMessages)`.
- It aggressively clears the active `chatBox` container off the screen and loops over the observable active message list in memory, running them back through `MessageBubbleFactory` immediately injecting the new settings configuration. The bubble UI transforms live.

## 4. Process Locking Constraints

Certain destructive application settings (like changing network environments) can't be rendered cleanly mid-stream. In these cases, the `SettingsController` hooks to a `requestAppRestart()` callback mechanism natively prompting the JVM to initiate an exit sequence instead of risking corrupt states.
