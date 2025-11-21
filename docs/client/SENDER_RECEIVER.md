## DefaultMessageSender

### Purpose
- Default implementation of `MessageSender` that sends encrypted messages via WebSocket with strict validation and JSON serialization.

### Dependencies
- `WebSocketAdapter`: transport layer.
- `MessageValidator`: structural validation.
- `JsonCodec`: JSON serialization with strict mode.
- `Logger`: logging without payloads.

### Send flow
1. Constructor injection: `WebSocketAdapter adapter`.
2. `send(EncryptedMessage m)`:
    - `MessageValidator.validateEncryptedMessage(m)` → throws on failure.
    - `JsonCodec.toJson(m)` → strict JSON string.
    - `adapter.sendText(json)` → transmission.
    - Log success with senderId/recipientId/timestamp (not payload).
3. Exception handling:
    - Validation → `IllegalArgumentException`.
    - JSON serialization → `JsonProcessingException`.
    - Network → propagated from adapter.

### Security rules
- Log format: `"Sent message: sender={}, recipient={}, timestamp={}"` (no ciphertext/keys).
- Does not perform retry at this layer (retry in WebSocketAdapter).
- Validation before serialization for fast-fail.

***

## DefaultMessageReceiver

### Purpose
- Default implementation that receives WebSocket frames, parses them into `EncryptedMessage`, validates, performs recipient check, and expiry check before dispatching to the listener.

### Dependencies
- `MessageReceiver` listener: callback for valid messages.
- `JsonCodec`: strict JSON deserialization.
- `MessageValidator`: structural and recipient validation.
- `String localRecipientId`: the local user's ID.

### Receive flow
1. Constructor injection: `MessageReceiver listener, String localRecipientId`.
2. `handleIncomingMessage(String jsonText)`:
    - Parse with `JsonCodec.fromJson(jsonText, EncryptedMessage.class)` (strict mode).
    - `MessageValidator.validateEncryptedMessage(m)`.
    - `MessageValidator.validateRecipientOrThrow(localRecipientId, m)` → fail if recipient != local.
    - Expiry check: `now > timestamp + ttl*1000` → `onError(new MessageExpiredException())`.
    - Success → `listener.onMessage(m)`.
3. Error handling:
    - JSON parse failure → `listener.onError(e)`.
    - Validation failure → `listener.onError(e)`.
    - Recipient mismatch → `listener.onError(new RecipientMismatchException())`.
    - Expiry → `listener.onError(new MessageExpiredException())`.

### Security rules
- Strict JSON: failure on unknown fields.
- Recipient check **before** any payload processing.
- Expiry check **before** decrypt.
- Log only event types and IDs, not payloads.
- All validation failures → `onError`, not silent drop.