# WEBSOCKET

## Purpose

Document `WebSocketAdapter` behavior for websocket and authenticated HTTPS helper calls.

## Current Implementation

- Transport implementation uses Java `HttpClient` + `java.net.http.WebSocket`.
- One TLS-configured `HttpClient` is reused for websocket and authenticated REST helper requests.
- Adapter is used in both modes for authenticated HTTPS helper calls; websocket connect is used by receiver only in dev transport mode.
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

1. In dev mode, connect with bearer session header over WSS.
2. Receive fragmented text frames and reassemble safely.
3. Apply heartbeat ping/pong and stale-connection detection.
4. Retry websocket reconnect with bounded exponential backoff.
5. In all modes, authenticated REST helpers call HTTPS endpoints and perform one connection-level retry.

## Error/Security Notes

- TLS is constrained to `TLSv1.3` and hardened cipher suites.
- Inbound message size is guarded (`haf.ws.maxInboundBytes`, default 4MB).
- HTTP non-2xx responses raise `HttpCommunicationException`.
- In prod mode, receiver startup does not require a websocket connection and uses HTTPS polling transport.

## Related Files

- `client/src/main/java/com/haf/client/network/WebSocketAdapter.java`
- `client/src/main/java/com/haf/client/utils/SslContextUtils.java`
- `client/src/test/java/com/haf/client/network/WebSocketAdapterTest.java`
