# SENDER_RECEIVER

## Purpose

Describe concrete messaging implementations used by the client runtime.

## Current Implementation

- `DefaultMessageSender`:
  - resolves keys via `KeyProvider`
  - encrypts payloads with `MessageEncryptor`
  - signs envelopes with Ed25519 (`MessageSignatureService`)
  - validates envelopes with `MessageValidator`
  - sends live envelopes through `RealtimeClientTransport`
- `DefaultMessageReceiver`:
  - consumes WSS realtime events (`NEW_MESSAGE`, receipts, typing, presence)
  - validates, verifies Ed25519 signatures, and decrypts envelopes
  - dispatches callbacks (`onMessage`, `onError`, `onPresenceUpdate`, typing and receipt callbacks)
  - performs WSS delivery/read receipt flow

## Key Types/Interfaces

- `client.network.DefaultMessageSender`
- `client.network.DefaultMessageReceiver`
- `shared.utils.MessageValidator`
- `shared.crypto.MessageEncryptor`
- `shared.crypto.MessageDecryptor`

## Flow

1. Sender builds encrypted envelope and sends WSS `SEND_MESSAGE`.
2. Server returns WSS `SEND_ACCEPTED` metadata (`envelopeId`, `expiresAt`).
3. Receiver gets inbound envelope events over WSS `NEW_MESSAGE`.
4. Receiver verifies sender signing key fingerprint/signature, decrypts, and notifies UI-facing listener.
5. Receiver emits WSS delivery/read receipts for delivered/viewed envelope ids.

## Error/Security Notes

- Send path propagates key/validation/network exceptions.
- Receive path rejects invalid/expired/tampered payloads before UI state update.
- Unsigned or wrongly signed envelopes are treated as tampered and rejected.
- Logs avoid plaintext/key material.

## Related Files

- `client/src/main/java/com/haf/client/network/DefaultMessageSender.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`
