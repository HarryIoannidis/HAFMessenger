# WEBSOCKET

### Purpose
- Documents `WebSocketAdapter`, the client network bridge used for authenticated WebSocket and HTTPS API calls.

### Implementation
- Uses Java `HttpClient` + `java.net.http.WebSocket` (not Java-WebSocket).
- Shares one TLS-configured `HttpClient` for websocket and authenticated HTTP calls.

### Exposed operations
- `connect(Consumer<String> onMessage, Consumer<Throwable> onError)`
- `sendText(String message)`
- `close()`
- `isConnected()`
- `getAuthenticated(String path)`
- `postAuthenticated(String path, String body)`
- `deleteAuthenticated(String path)`

### Connection behavior
- Connects to `wss://...` URI with `Authorization: Bearer <sessionId>` header.
- Incoming text frames are reassembled across fragments.
- Max inbound message size: 4 MB by default (`haf.ws.maxInboundBytes` system-property override).
- Oversized frames emit `IOException("Inbound message too large")`, and remaining fragments of that same oversized message are discarded until the final fragment.

### Heartbeat and liveness
- Sends ping every 5 seconds.
- Tracks latest pong timestamp.
- If no pong for >15 seconds, aborts websocket and marks disconnected.

### Reconnect policy
- Automatic reconnect unless user explicitly closed connection.
- Max retry attempts: 3.
- Exponential backoff: 1s, 2s, 4s (capped at 5s).

### Authenticated HTTP helper behavior
- Rewrites websocket host URI to `https://<host>:8443/...` for REST paths.
- Adds bearer session header.
- Retries once on connection-level failures (e.g., stale pooled connection).

### TLS policy
- TLS 1.3 only.
- Cipher allowlist:
  - `TLS_AES_256_GCM_SHA384`
  - `TLS_CHACHA20_POLY1305_SHA256`
- Endpoint identification algorithm: `HTTPS`.
- SSL context is provided by `SslContextUtils`.
