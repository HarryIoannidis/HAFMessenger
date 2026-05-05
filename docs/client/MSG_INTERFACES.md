# MSG_INTERFACES

## Purpose

Define current client messaging interfaces used by ViewModels/controllers.

## Current Implementation

- `MessageSender` supports plaintext send, encrypt-only, send-encrypted, and attachment-related REST operations.
- `MessageReceiver` supports polling receive lifecycle, envelope acknowledgement, and detached decrypt helper.
- Implementations are `DefaultMessageSender` and `DefaultMessageReceiver`.

## Key Types/Interfaces

- `client.network.MessageSender`
  - `sendMessage(...)`
  - `sendMessageWithResult(...)`
  - `encryptMessage(...)`
  - `sendEncryptedMessage(...)`
  - attachment operations (`init`, `chunk`, `complete`, `bind`, `download`)
- `client.network.MessageReceiver`
  - `setMessageListener(...)`
  - `start()` / `stop()`
  - `acknowledgeEnvelopes(senderId)`
  - `decryptDetachedMessage(...)`

## Flow

1. Outbound UI actions call `MessageSender` APIs.
2. Sender builds/validates encrypted envelopes and submits authenticated HTTPS calls.
3. Receiver consumes HTTPS polling snapshots, decrypts valid envelopes, and emits listener callbacks.
4. Receiver acks envelope ids to mark delivery on server side through HTTPS ACK endpoints.

## Error/Security Notes

- Structural validation uses `MessageValidator.validate(...)`.
- Key lookup failures propagate as `KeyNotFoundException`.
- Receiver errors are surfaced through `MessageListener.onError(...)`.

## Related Files

- `client/src/main/java/com/haf/client/network/MessageSender.java`
- `client/src/main/java/com/haf/client/network/MessageReceiver.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageSender.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
