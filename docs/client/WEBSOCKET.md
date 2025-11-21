### Purpose
- Manages WebSocket lifecycle (connect, send, receive, close) with TLS policy, retry logic, and backpressure for client-side messaging.

### Architecture
- Wraps Java-WebSocket or similar library.
- Exposes: `connect(URI)`, `sendText(String)`, `close()`, `setMessageHandler(DefaultMessageReceiver)`.

### Connection flow
1. `connect(URI wssUri)`:
    - TLS 1.3+ configuration (cipher suite allowlist).
    - Certificate pinning hooks (TODO: implement pinning validation).
    - Establish WebSocket handshake.
    - Start reconnect watchdog.

### Send flow
1. `sendText(String json)`:
    - Check connection state → throw if disconnected.
    - Write frame to WebSocket.
    - Backpressure: if send queue > limit, drop oldest or block (configurable).

### Receive flow
1. `onMessage(String text)`:
    - Dispatch to `DefaultMessageReceiver.handleIncomingMessage(text)`.

### Retry policy
- Transient IO failures: bounded exponential backoff with jitter (max 3 retries, cap 5s).
- Validation/tamper/expiry failures: **no retry** (dispatch to onError).
- Reconnect: automatic reconnection with backoff on disconnect.

### Security rules
- TLS 1.3+ only, cipher suite allowlist (TLS_AES_256_GCM_SHA384, TLS_CHACHA20_POLY1305_SHA256).
- Certificate pinning: validate server cert fingerprint (SHA-256) during handshake (TODO).
- Does not log frame payloads—only frame size and event types.
- Backpressure: drop oversize frames before allocation (max frame size: 2MB).