# MAIN

### Purpose
- The central entry point of the Phase 5 server, responsible for bootstrapping all subsystems (database, TLS, ingress, routing, logging/metrics).
- Coordinates initialization, performs Flyway migrations, creates all services, and launches HTTP and WebSocket listeners.

### Startup sequence
- Reads configuration via `ServerConfig`.
    - If a mandatory env var is missing, it terminates with a configuration error before it reaches DB or TLS initialization.
- Creates HikariCP `DataSource` with DB credentials from `ServerConfig`.
    - Sets pool size, connection timeout, validation query, etc.
- Performs Flyway migrations.
    - Opens connection to the DB and runs all pending scripts from `src/main/resources/db/migration`.
    - If migration fails, it terminates immediately to prevent execution with an invalid schema.
- Loads TLS keystore and generates `SSLContext`.
    - Reads the PKCS12 keystore from the path given by `ServerConfig`.
    - Sets up TLS 1.3, ciphersuites, and (optional) client authentication policy.
- Builds core services with dependency injection pattern:
    - `AuditLogger` (Log4j2-based, for all audit events).
    - `MetricsRegistry` (in-memory counters: ingress, rejects, rate-limit rejects, queue depth).
    - `EnvelopeDAO` (JDBC-based access to `message_envelopes`).
    - `RateLimiterService` (sliding-window rate limiting with MySQL backend).
    - `MailboxRouter` (subscription and queue management for outbound delivery).
- Starts ingress servers:
    - `HttpIngressServer` on `HAF_HTTP_PORT` for `/api/v1/messages` (POST).
    - `WebSocketIngressServer` on `HAF_WS_PORT` for `/ws` (persistent bidirectional connections).
- Registers graceful shutdown hook:
    - On SIGTERM/SIGINT, it stops the servers with a timeout.
    - HikariCP pool closes.
    - Flush audit logs and metrics.

### Dependencies created
- `ServerConfig config = new ServerConfig();`
    - Takes all env vars once, fail-fast if something is missing.
- `DataSource dataSource = buildHikariDataSource(config);`
    - HikariCP pool with config from `HAF_DB_*`.
- `Flyway flyway = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();`
    - Perform migrations before using the DB for the first time.
- `SSLContext sslContext = buildSslContext(config);`
    - TLS context for HTTPS + secure WebSocket.
- `AuditLogger auditLogger = AuditLogger.create(...);`
    - Singleton or factory-based logger.
- `MetricsRegistry metricsRegistry = new MetricsRegistry();`
    - Thread-safe in-memory counters.
- `EnvelopeDAO envelopeDAO = new EnvelopeDAO(dataSource, auditLogger);`
    - Access to `message_envelopes` table.
- `RateLimiterService rateLimiterService = new RateLimiterService(dataSource, auditLogger);`
    - Sliding window + lockout logic.
- `MailboxRouter mailboxRouter = new MailboxRouter(envelopeDAO, metricsRegistry);`
    - Queue and subscriptions management.
- `HttpIngressServer httpServer = new HttpIngressServer(config, sslContext, mailboxRouter, rateLimiterService, auditLogger, metricsRegistry);`
    - REST ingress endpoint.
- `WebSocketIngressServer wsServer = new WebSocketIngressServer(config, sslContext, mailboxRouter, rateLimiterService, auditLogger, metricsRegistry);`
    - Bidirectional WebSocket ingress.

### Graceful shutdown
- `Runtime.getRuntime().addShutdownHook(new Thread(() -> { ... }));`
    - Stops servers with timeout (e.g. 10 seconds) to complete in-flight requests.
    - Closes `DataSource` to release connections.
    - Flush audit logs and metrics before termination.
- If the shutdown hook is not triggered (force kill), there are orphan connections, but the DB timeout will close them.

### Error handling
- Configuration errors:
    - If `ServerConfig` throws exception (missing env var, invalid format), `Main` logs fatal error and calls `System.exit(1)`.
- Migration errors:
    - If Flyway fails, log the stack trace and terminates with a non-zero exit code.
- TLS errors:
    - If the keystore path does not exist or the password is incorrect, it throws exception and aborts.
- Runtime errors:
    - If a server fails at start (port already in use, TLS handshake failure), log error and shuts down.
    - Partial startups are not allowed (e.g. only HTTP without WebSocket).