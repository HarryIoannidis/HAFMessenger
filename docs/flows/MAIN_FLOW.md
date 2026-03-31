# MAIN (SHELL) FLOW ANALYSIS

This document provides a deep-dive technical breakdown of the Core Shell orchestrations for the HAFMessenger client, primarily localized in `MainController.java` and `MainContentLoader.java`.

## 1. Asynchronous Application Preload Strategy

Because the Main application shell is heavy, its initialization is deeply optimized.

- `MainController.initialize()` actively hides the stage.
- It triggers an intensive `beginInitialMainLoadPipeline()` utilizing `CompletableFuture.allOf()`. This background parallel processing fetches the initial cached Contacts, preloads all expensive subordinate FXML views (Chat, Search, Settings), and decides which primary tab to activate based on user preferences.
- Only when this pipeline succeeds does `revealMainStageAfterInitialLoad()` finally expose the UI to the user avoiding flashing/locking the screen.

## 2. Dynamic View Swapping Orchestrator

To avoid destroying and recreating the thick application window on every click, the controller utilizes a specialized `contentPane` (`StackPane`).

- It passes control to a `MainContentLoader` that hot-swaps active nodes.
- When switching between `<Messages>` and `<Search>`, the system seamlessly mounts `FXML_CHAT`, `FXML_SEARCH`, or `FXML_PLACEHOLDER` directly on top of the stack while adjusting visual navigation CSS markers accordingly.

## 3. Throttled Search Debouncing

The top navigation bar acts as the gateway to the Directory flow.

- It enforces strict UI rules: preventing empty searches and capping minimum query sizes natively.
- To protect the backend from a query flood, as the user types, a `PauseTransition` tracks a sliding 300-millisecond window (`SEARCH_INSTANT_DEBOUNCE_MS`). If the user stops typing for 300ms, the instantaneous search query is finally dispatched.

## 4. Reactive Profile & Presence Synchronizer

The application observes user statuses natively.

- The `MainController` attaches a `ListChangeListener` natively reacting to mutated elements in the `ContactInfo` collection.
- If a contact goes online, the `refreshProfilePanelForSelectedContact()` updates the active profile panel, flipping their `profileActivenessCircle` to the correct hex color (e.g. Green) and updating the "Active last..." text.
- If the `"Hide Presence"` setting is toggled, it dynamically overwrites this loop rendering a `GaussianBlur` privacy filter and forcing a `"Hidden Activity"` label globally.
