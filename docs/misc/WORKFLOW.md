# WORKFLOW

## Purpose

Describe implemented end-to-end workflows for message send/receive, routing, and key usage.

## Current Implementation

- Send path uses `MessageSender` + `MessageEncryptor` and submits encrypted envelopes over WSS.
- Server path validates/rate-limits/routes via `RealtimeWebSocketServer`, `MessageIngressService`, and `MailboxRouter`, with persistence through DAOs.
- Receive path uses `MessageReceiver` + `MessageDecryptor`, updates UI state, and emits WSS delivery/read receipts.
- Key provider path resolves sender id and recipient public keys through `KeyProvider` (`UserKeystoreKeyProvider` implementation).
- Attachment flow extends message workflow with init/chunk/complete/bind/download endpoints while preserving encrypted payload handling.

## Key Types/Interfaces

- Client: `DefaultMessageSender`, `DefaultMessageReceiver`, `AuthHttpClient`, `MessagesViewModel`.
- Server: `RealtimeWebSocketServer`, `MessageIngressService`, `MailboxRouter`, `RateLimiterService`, `EnvelopeDAO`.
- Shared: `EncryptedMessage`, `MessageValidator`, `MessageEncryptor`, `MessageDecryptor`, `KeyProvider`.

## Flow

1. Compose message in UI -> ViewModel -> `MessageSender`.
2. Encrypt payload and send envelope as a WSS `SEND_MESSAGE` event.
3. Server validates and stores envelope, then pushes `NEW_MESSAGE` to the recipient socket or reconnect backlog.
4. Receiver validates/decrypts envelope and updates chat state from WSS delivery.
5. Client emits WSS delivery/read receipts to avoid duplicate processing and update read state.

## Error/Security Notes

- Validation occurs on both client and server boundaries.
- Decrypt failures are surfaced as errors without exposing sensitive internals.
- Envelope metadata and TTL are enforced server-side and receiver-side.
- Receipt operations are ownership-scoped to prevent cross-user acknowledgement of foreign envelope IDs.

## Related Files

- `client/src/main/java/com/haf/client/network/DefaultMessageSender.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/router/MailboxRouter.java`
- `shared/src/main/java/com/haf/shared/crypto/MessageEncryptor.java`
- `shared/src/main/java/com/haf/shared/crypto/MessageDecryptor.java`
