# MAIN

### Purpose
- Documents the real server bootstrap flow in `com.haf.server.core.Main`.

### Startup sequence
1. Load config via `ServerConfig.load()`.
2. Run Flyway migrations from `filesystem:server/src/main/resources/db/migration`.
3. Create `HikariDataSource` using `HAF_DB_*` settings.
4. Build TLS 1.3 `SSLContext` from PKCS12 keystore.
5. Create core services:
  - `MetricsRegistry`
  - `AuditLogger`
  - `EncryptedMessageValidator`
  - DAOs: `EnvelopeDAO`, `UserDAO`, `SessionDAO`, `FileUploadDAO`, `AttachmentDAO`, `ContactDAO`
  - `MailboxRouter`
  - `RateLimiterService`
  - `PresenceRegistry`
6. Build ingress servers:
  - `HttpIngressServer`
  - `WebSocketIngressServer`
7. Start scheduled jobs:
  - metrics snapshot every 60s
  - attachment cleanup every 300s
8. Start router and servers:
  - `mailboxRouter.start()`
  - `webSocketServer.start()` + `awaitStartup(Duration.ofSeconds(10))`
  - `httpServer.start()`

### TLS behavior
- Protocol: `TLSv1.3` only.
- Cipher suites:
  - `TLS_AES_256_GCM_SHA384`
  - `TLS_CHACHA20_POLY1305_SHA256`

### Shutdown behavior
- Registers JVM shutdown hook that:
  - stops websocket + HTTP servers
  - closes router
  - cancels scheduled tasks
  - shuts down scheduler
  - closes datasource
- Main thread waits on shutdown latch until termination.

### Error handling
- Any startup failure logs `Server startup failed` and throws `StartupException`.
- WebSocket startup has explicit timeout/failure signaling via `awaitStartup(...)`.
