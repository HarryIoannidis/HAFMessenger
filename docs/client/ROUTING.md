# ROUTING

## Purpose
Document scene routing and popup lifecycle managed by `ViewRouter`.

## Current Implementation
- Main stage is registered once via `setMainStage(...)`.
- Scene transitions:
  - `switchTo(...)` for decorated stage
  - `switchToTransparent(...)` for custom transparent chrome
- Popup management:
  - `showPopup(...)`
  - `preloadPopup(...)`
  - keyed popup caching and center-on-active-window behavior
- Router preloads Manrope fonts and applies Linux-friendly transparent-stage handling for splash/login transitions.

## Key Types/Interfaces
- `client.utils.ViewRouter`
- `client.utils.UiConstants` (FXML/CSS paths and popup keys)

## Flow
1. Controller/ViewModel requests a route change.
2. Router loads target FXML with `FXMLLoader`.
3. Router updates/recreates stage style when needed.
4. Router closes stale popups before route swaps.
5. Optional popup views are preloaded and reused by popup key.

## Error/Security Notes
- FXML loading failures throw unchecked I/O wrappers.
- Router closes popups during route transitions to avoid stale overlay state.

## Related Files
- `client/src/main/java/com/haf/client/utils/ViewRouter.java`
- `client/src/main/java/com/haf/client/utils/UiConstants.java`
- `client/src/main/resources/fxml`
