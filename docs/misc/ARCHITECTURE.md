# ARCHITECTURE

## Purpose

Describe the implemented system architecture, boundaries, and responsibilities across client/server/shared modules.

## Current Implementation

- Client: JavaFX 25 MVVM app (`controllers`, `viewmodels`, `services`, `network`, `crypto`, `utils`).
- Server: layered plain Java service (`ingress`, `router`, `db`, `metrics`, `config`, `handlers`).
- Shared: DTOs, requests/responses, constants, crypto, keystore, validation, and utility contracts.
- Dependency direction: `client -> shared`, `server -> shared`; no direct `client -> server` compile dependency.
- Runtime transport: HTTPS REST for non-realtime API operations and WSS for live messaging, typing, receipts, presence, and server push.

## Key Types/Interfaces

- Client: `MainController`, `ChatController`, `SearchController`, `MessagesViewModel`, `SearchViewModel`.
- Server: `HttpIngressServer`, `MailboxRouter`, `RateLimiterService`, DAO classes.
- Shared: `EncryptedMessage`, `MessageHeader`, `MessageValidator`, `MessageEncryptor`, `MessageDecryptor`, `KeyProvider`.

## Flow

1. UI interaction enters controller layer and delegates to ViewModels/services.
2. Client network layer sends authenticated HTTPS REST calls for normal API work and authenticated WSS events for live chat.
3. Server REST ingress validates/authenticates non-realtime requests; WSS gateway validates/revalidates live events and passes encrypted envelopes to router/services.
4. Router supports realtime mailbox subscription, backlog push, and ownership-scoped receipt workflows.
5. DAOs persist and retrieve encrypted metadata/payload blobs.
6. Shared contracts/crypto guarantee wire compatibility and deterministic validation in both modules.

## Error/Security Notes

- E2E principle is enforced: encryption/decryption keys are client-side; server stores opaque encrypted content.
- TLS 1.3 is mandatory for network ingress.
- Envelope validation and rate-limiting occur before routing/persistence.
- Audit + metrics modules are part of normal runtime, not optional add-ons.

## Related Files

- `client/src/main/java/com/haf/client/controllers/MainController.java`
- `client/src/main/java/com/haf/client/viewmodels/MessagesViewModel.java`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/router/MailboxRouter.java`
- `server/src/main/java/com/haf/server/db/EnvelopeDAO.java`
- `shared/src/main/java/com/haf/shared/dto/EncryptedMessage.java`
