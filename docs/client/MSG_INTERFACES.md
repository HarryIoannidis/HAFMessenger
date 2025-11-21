## MessageSender

### Purpose
- Defines the contract for sending encrypted messages over the network to the client.

### Architecture
- Interface: `MessageSender` with method `void send(EncryptedMessage message) throws Exception`.
- Implemented by `DefaultMessageSender`.

### Flow
- Receives `EncryptedMessage` (already encrypted by MessageEncryptor).
- Validates with `MessageValidator.validateEncryptedMessage()`.
- Serializes to JSON with `JsonCodec.toJson()` (strict mode: FAIL_ON_UNKNOWN_PROPERTIES).
- Transmits via `WebSocketAdapter.sendText(json)`.

### Security rules
- Does not log payloads (ciphertextB64, wrappedKeyB64, ivB64, tagB64).
- Logs only identifiers: senderId, recipientId, timestamp, error types.
- Validation failure → exception, not silent drop.


***

## MessageReceiver

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