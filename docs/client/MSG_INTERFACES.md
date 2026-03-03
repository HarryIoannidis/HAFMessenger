# MSG_INTERFACES

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
- Does not log payloads (ciphertextB64, ephemeralPublicB64, ivB64, tagB64).
- Logs only identifiers: senderId, recipientId, timestamp, error types.
- Validation failure → exception, not silent drop.

---

## MessageReceiver

### Purpose
- Defines the contract for receiving encrypted messages from the network.

### Dependencies
- `WebSocketAdapter`: transport layer.
- `MessageValidator`: structural validation.
- `JsonCodec`: JSON deserialization with strict mode.
- `Logger`: logging without payloads.

### Receive flow
1. Constructor injection: `WebSocketAdapter adapter`.
2. `onMessage(String jsonText)`:
    - `JsonCodec.fromJson(jsonText, EncryptedMessage.class)` → strict JSON parsing.
    - `MessageValidator.validateEncryptedMessage(m)` → throws on failure.
    - Dispatch to registered `MessageListener`.
3. Exception handling:
    - Validation → `IllegalArgumentException`.
    - JSON deserialization → `JsonProcessingException`.
    - Network → propagated from adapter.

### Security rules
- Log format: `"Received message: sender={}, recipient={}, timestamp={}"` (no ciphertext/keys).
- Does not perform retry at this layer (retry in WebSocketAdapter).
- Validation before processing for fast-fail.