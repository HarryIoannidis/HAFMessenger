# FILES

## Purpose
Index the main source layout and notable classes without duplicating generated/build output.

## Current Implementation
- Top-level runtime modules:
  - `client`
  - `server`
  - `shared`
- Each module uses `src/main/java` for production code and `src/test/java` for tests.

## Key Types/Interfaces
- Client notable classes:
  - Controllers: `MainController`, `ChatController`, `SearchController`, `SettingsController`
  - ViewModels: `MessagesViewModel`, `ChatViewModel`, `SearchViewModel`, `MainViewModel`
  - Network: `WebSocketAdapter`, `MessageSender`, `MessageReceiver`
- Server notable classes:
  - `Main`, `ServerConfig`
  - `HttpIngressServer`, `WebSocketIngressServer`
  - `MailboxRouter`, `RateLimiterService`
  - DAOs in `server.db`
- Shared notable classes:
  - `EncryptedMessage`, `EncryptedFileDTO`, request/response DTOs
  - `MessageValidator`, `JsonCodec`, `MessageEncryptor`, `MessageDecryptor`
  - `KeyProvider`, `UserKeystore`

## Flow
1. Client UI and ViewModel layers produce service/network calls.
2. Server ingress and router layers enforce policy and persistence.
3. Shared module supplies type-safe contracts and common crypto/validation.

## Error/Security Notes
- This file documents source layout only; build artifacts under `target/` are excluded.
- Keep class names synchronized with source tree on refactors (for example `MessagesViewModel`, not removed legacy names).

## Related Files
- `client/src/main/java`
- `server/src/main/java`
- `shared/src/main/java`
- `docs/misc/STRUCTURE.md`
