# WEBSOCKET

## Purpose

Document `WebSocketAdapter` behavior for websocket and authenticated HTTPS helper calls.

## Current Implementation

- Transport implementation uses Java `HttpClient` + `java.net.http.WebSocket`.
- One TLS-configured `HttpClient` is reused for websocket and authenticated REST helper requests.
- REST helper methods:
  - `getAuthenticated(path)`
  - `postAuthenticated(path, body)`
  - `deleteAuthenticated(path)`
- Websocket lifecycle methods:
  - `connect(onMessage, onError)`
  - `sendText(...)`
  - `close()`
  - `isConnected()`

## Key Types/Interfaces

- `client.network.WebSocketAdapter`
- `client.utils.SslContextUtils`
- `client.exceptions.HttpCommunicationException`

## Flow

1. Connect with bearer session header over WSS.
2. Receive fragmented text frames and reassemble safely.
3. Apply heartbeat ping/pong and stale-connection detection.
4. Retry websocket reconnect with bounded exponential backoff.
5. Authenticated REST helpers rewrite host/scheme to HTTPS and perform one connection-level retry.

## Error/Security Notes

- TLS is constrained to `TLSv1.3` and hardened cipher suites.
- Inbound message size is guarded (`haf.ws.maxInboundBytes`, default 4MB).
- HTTP non-2xx responses raise `HttpCommunicationException`.

## Related Files

- `client/src/main/java/com/haf/client/network/WebSocketAdapter.java`
- `client/src/main/java/com/haf/client/utils/SslContextUtils.java`
- `client/src/test/java/com/haf/client/network/WebSocketAdapterTest.java`
