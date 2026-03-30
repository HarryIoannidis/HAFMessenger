# DEVELOPMENT

## Purpose

Provide a practical implementation workflow for adding features while preserving current module boundaries and MVVM design.

## Current Implementation

- UI assets live under:
  - `client/src/main/resources/fxml`
  - `client/src/main/resources/css`
  - `client/src/main/resources/images`
- Client logic split: `controllers` (UI events) -> `viewmodels` (state/commands) -> `services/network`.
- Shared contracts in `shared/src/main/java/com/haf/shared`.
- Server handling in `server/src/main/java/com/haf/server`.

## Key Types/Interfaces

- Client feature stack typically uses:
  - Controller class in `com.haf.client.controllers`
  - ViewModel class in `com.haf.client.viewmodels`
  - Service interface/impl in `com.haf.client.services`
- Shared DTO/request/response classes for wire compatibility.

## Flow

1. Add or update FXML/CSS resources under `client/src/main/resources`.
2. Implement controller event wiring only (no business-heavy logic in controller).
3. Implement ViewModel state and commands.
4. Integrate service/network calls using shared DTOs.
5. Add/update server ingress/router/DAO handling if API behavior changes.
6. Add tests in affected modules before finalizing.

## Error/Security Notes

- Keep secrets and prod cert material out of repo.
- Preserve E2E boundary: server should not decrypt client content.
- Validate payloads and policy centrally through shared/server validators.
- Prefer typed exceptions and user-safe error messages.

## Related Files

- `client/src/main/java/com/haf/client/controllers`
- `client/src/main/java/com/haf/client/viewmodels`
- `client/src/main/java/com/haf/client/services`
- `client/src/main/resources/fxml`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `shared/src/main/java/com/haf/shared/dto`
