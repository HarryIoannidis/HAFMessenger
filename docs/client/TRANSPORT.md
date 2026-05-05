# CLIENT TRANSPORT

## Purpose

Document authenticated HTTPS transport behavior used by the client runtime.

## Current Implementation

- `AuthHttpClient` provides authenticated HTTP helpers:
  - `getAuthenticated(path)`
  - `postAuthenticated(path, body)`
  - `deleteAuthenticated(path)`
- `DefaultMessageReceiver` always uses HTTPS polling for inbound message envelopes and contact presence projection.
- Envelope acknowledgements are submitted through `POST /api/v1/messages/ack`.
- Envelope-size budgeting for inline media uses `haf.messaging.maxEnvelopeBytes` (default 4 MB).

## Key Types/Interfaces

- `client.network.AuthHttpClient`
- `client.network.DefaultMessageReceiver`
- `client.utils.SslContextUtils`
- `client.exceptions.HttpCommunicationException`

## Flow

1. Build authenticated HTTPS client with strict TLS parameters.
2. Send outbound API requests with bearer token.
3. Poll mailbox and contacts endpoints on a background schedule.
4. Decrypt validated envelopes and emit UI callbacks.
5. Send ACK payloads for viewed envelopes.

## Error/Security Notes

- TLS is constrained to `TLSv1.3` and hardened cipher suites.
- HTTP non-2xx responses map to `HttpCommunicationException`.
- Connection-level failures retry once in `AuthHttpClient`.

## Related Files

- `client/src/main/java/com/haf/client/network/AuthHttpClient.java`
- `client/src/main/java/com/haf/client/network/DefaultMessageReceiver.java`
- `client/src/main/java/com/haf/client/utils/SslContextUtils.java`
- `client/src/test/java/com/haf/client/network/AuthHttpClientTest.java`
