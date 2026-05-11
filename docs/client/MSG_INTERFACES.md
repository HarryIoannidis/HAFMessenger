# MSG_INTERFACES

## Purpose

Define current client messaging interfaces used by ViewModels/controllers.

## Current Implementation

- `MessageSender` supports plaintext send, encrypt-only, send-encrypted, and attachment-related REST operations.
- Attachment chunks are uploaded as raw encrypted bytes; attachment downloads return raw encrypted blob bytes plus metadata headers through `MessageSender.AttachmentDownload`.
- `MessageReceiver` supports WSS receive lifecycle, read receipts, typing events, and detached decrypt helper.
- Implementations are `DefaultMessageSender` and `DefaultMessageReceiver`.

## Key Types/Interfaces

- `client.network.MessageSender`
  - `sendMessage(...)`
  - `sendMessageWithResult(...)`
  - `encryptMessage(...)`
  - `sendEncryptedMessage(...)`
  - attachment operations (`init`, binary `chunk`, `complete`, `bind`, binary `download`)
- `client.network.MessageReceiver`
  - `setMessageListener(...)`
  - `start()` / `stop()`
  - `acknowledgeEnvelopes(senderId)`
  - `sendTypingStart(recipientId)` / `sendTypingStop(recipientId)`
  - `decryptDetachedMessage(...)`

## Flow

1. Outbound UI actions call `MessageSender` APIs.
2. Sender builds/validates encrypted envelopes and submits authenticated WSS events.
3. Receiver consumes WSS events, decrypts valid envelopes, and emits listener callbacks.
4. Receiver emits WSS read receipts when a chat is viewed.

## Error/Security Notes

- Structural validation uses `MessageValidator.validate(...)`.
- Key lookup failures propagate as `KeyNotFoundException`.
- Receiver errors are surfaced through `MessageListener.onError(...)`.

## Related Files

- `client/src/main/java/com/haf/client/network/MessageSender.java`
- `client/src/main/java/com/haf/client/network/MessageReceiver.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageSender.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
