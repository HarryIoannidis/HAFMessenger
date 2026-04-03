# SENDER_RECEIVER

## Purpose

Describe concrete messaging implementations used by the client runtime.

## Current Implementation

- `DefaultMessageSender`:
  - resolves keys via `KeyProvider`
  - encrypts payloads with `MessageEncryptor`
  - validates envelopes with `MessageValidator`
  - sends via authenticated HTTPS helper in `WebSocketAdapter`
- `DefaultMessageReceiver`:
  - consumes websocket payloads in dev mode
  - consumes HTTPS polling snapshots (`/api/v1/messages`, `/api/v1/contacts`) in prod mode
  - validates and decrypts envelopes
  - dispatches callbacks (`onMessage`, `onError`, `onPresenceUpdate`)
  - performs envelope acknowledgement flow

## Key Types/Interfaces

- `client.network.DefaultMessageSender`
- `client.network.DefaultMessageReceiver`
- `shared.utils.MessageValidator`
- `shared.crypto.MessageEncryptor`
- `shared.crypto.MessageDecryptor`

## Flow

1. Sender builds encrypted envelope and posts to `/api/v1/messages`.
2. Server returns envelope metadata (`envelopeId`, `expiresAt`).
3. Receiver gets inbound envelope events over websocket (dev) or HTTPS polling (prod).
4. Receiver decrypts and notifies UI-facing listener.
5. Receiver acknowledges delivered envelope ids via authenticated ACK path.

## Error/Security Notes

- Send path propagates key/validation/network exceptions.
- Receive path rejects invalid/expired/tampered payloads before UI state update.
- Logs avoid plaintext/key material.

## Related Files

- `client/src/main/java/com/haf/client/network/DefaultMessageSender.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
