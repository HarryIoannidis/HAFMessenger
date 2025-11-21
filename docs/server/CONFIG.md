### Purpose
- Central configuration object for the Phase 5 server, responsible for reading all the necessary environment variables and exposing them as typed values to the rest of the components.
- Isolates access to 'System.getenv(...)' so that HttpIngressServer, WebSocketIngressServer, EnvelopeDAO, MailboxRouter, RateLimiterService, AuditLogger etc. not know the details of the environment.

### Database configuration
- `HAF_DB_URL`
    - JDBC URL for MySQL 8.0+ (e.g., 'jdbc:mysql://db:3306/haf_messenger?useSSL=true').
    - Mandatory; If it is missing or empty, the server terminates with a configuration error.
- `HAF_DB_USER`
    - DB user with permissions for Flyway migrations and runtime queries.
    -Mandatory.
- `HAF_DB_PASS`
    - Code for 'HAF_DB_USER'.
    -Mandatory.
- `HAF_DB_POOL_SIZE`
    - Maximum HikariCP connection pool (integer) size.
    - Optional; If it is missing or invalid, a secure default (e.g. '20') is applied.

### TLS / HTTPS configuration
- `HAF_TLS_KEYSTORE_PATH`
    - Path to PKCS12 keystore with TLS certificate server and private key.
    - Compulsory for production; In the tests, bypass or use test keystore can be done.
- `HAF_TLS_KEYSTORE_PASS`
    - Code for the keystore.
    - Mandatory when TLS is active.
- `HAF_TLS_CLIENT_AUTH`
    - Optional mTLS FLAG ('NONE' / 'OPTIONAL' / 'REQUIRED').
    - In Phase 5 it can remain 'NONE', but it is reserved for future buff.

### Ingress configuration
- `HAF_HTTP_PORT`
    - HTTPS port for 'HttpIngressServer' ('/api/v1/messages').
    - If missing, default is used (e.g. '8443').
- `HAF_WS_PORT`
    - Secure WebSocket port for 'WebSocketIngressServer' ('/ws').
    - If it is missing, it gets the value of 'HAF_HTTP_PORT' for a common listener.
- `HAF_MAX_MESSAGE_BYTES`
    - Maximum allowable size of encrypted payload in bytes.
    - Used by 'EncryptedMessageValidator' to reject oversized messages before DB; Phase 5 baseline: '8*1024*1024' bytes if not set.

### Logging / metrics configuration
- `HAF_LOG_LEVEL`
    - Optional log level ('TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR') for Log4j2.
    - If missing, default 'INFO'.
- Can be used to enable detailed auditing in tests or events without code changes.

### Construction / validation
- 'ServerConfig' is generated once in 'Main' during startup.
- During construction:
    - Reads all relevant env vars.
    - Trims/checks for 'null' or blank values in required fields.
    - Parse numeric (ports, pool size, max bytes) and apply defaults to the wrong values.
    - If a mandatory field is missing/invalid, it throws unchecked exception and stops the server instead of running misconfigured.

### Usage
- `Main`
    - Gets DB params for HikariCP/Flyway and TLS params for 'SSLContext', as well as HTTP/WS ports.
- `HttpIngressServer` / `WebSocketIngressServer`
    - Read ports and 'maxMessageBytes' from 'ServerConfig' instead of hard-coded constants.
- `EncryptedMessageValidator`
    - Uses 'getMaxMessageBytes()' for payload size enforcement.
- `RateLimiterService`, `MailboxRouter`, `AuditLogger`
    - They can pull future tunables (thresholds, intervals, etc.) from 'ServerConfig', so that all operational settings are centralized.