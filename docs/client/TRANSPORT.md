# CLIENT TRANSPORT

## Purpose

Document authenticated HTTPS REST and WSS transport behavior used by the client runtime.

## Current Implementation

- `AuthHttpClient` provides authenticated HTTP helpers:
  - `getAuthenticated(path)`
  - `getAuthenticatedBytes(path)`
  - `postAuthenticated(path, body)`
  - `postAuthenticatedBytes(path, body, contentType, headers)`
  - `deleteAuthenticated(path)`
- `RealtimeClientTransport` opens the configured WSS URI (e.g., `wss://.../ws/v1/realtime`) with an `Authorization: Bearer` header.
- `DefaultMessageReceiver` consumes inbound message, presence, typing, receipt, heartbeat, and error events over WSS.
- Envelope delivery/read receipts are submitted as WSS events.
- Envelope-size budgeting for inline media uses `haf.messaging.maxEnvelopeBytes` (default 4 MB).
- Attachment chunk upload uses `application/octet-stream` bodies with `X-Attachment-Chunk-Index`.
- Attachment download uses `application/octet-stream` response bytes with `X-Attachment-*` metadata headers.

## Key Types/Interfaces

- `client.network.AuthHttpClient`
- `client.network.RealtimeClientTransport`
- `client.network.DefaultMessageReceiver`
- `client.utils.SslContextUtils`
- `client.exceptions.HttpCommunicationException`

## Flow

1. Build authenticated HTTPS client with strict TLS parameters.
2. Send non-realtime REST API requests with bearer token.
3. Open authenticated WSS for live chat after login.
4. Decrypt validated WSS envelopes and emit UI callbacks.
5. Send WSS delivery/read receipts for delivered/viewed envelopes.

## Error/Security Notes

- TLS is constrained to `TLSv1.3` and hardened cipher suites.
- HTTP non-2xx responses map to `HttpCommunicationException`.
- Connection-level failures retry once in `AuthHttpClient`.
- WSS reconnects with bounded backoff and the current access token.

## Related Files

- `client/src/main/java/com/haf/client/network/AuthHttpClient.java`
- `client/src/main/java/com/haf/client/network/RealtimeClientTransport.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
- `client/src/main/java/com/haf/client/utils/SslContextUtils.java`
- `client/src/test/java/com/haf/client/network/AuthHttpClientTest.java`
