# PHASES 

### Phase 1 – Wire format & Validation (Complete)
- **Deliverables**
    - DTOs/Constants: `EncryptedMessage`, `MessageHeader`, `CryptoConstants` describe the JSON wire format (version, ids, algo, iv/tag/wrappedKey, metadata). 
    - Serialization: `JsonCodec` with Jackson in strict mode (`FAIL_ON_UNKNOWN_PROPERTIES`, UTF-8 only, canonical field order). 
    - Validation: `MessageValidator` checks VERSION/ALGO, sender/recipient lengths, TTL bounds, Base64 decoding (IV/tag/ciphertext/wrappedKey), timestamp, contentType allowlist, contentLength ≥0, AAD presence. 
    - Documentation: README + `docs/shared/WIRE_FORMAT.md` record schema, strict JSON rules, and validation policy. 
- **Tests / Validation**
    - Unit: `MessageHeaderTest`, `EncryptedMessageTest`, `JsonCodecTest`, `MessageValidatorTest` (happy path, null DTO, bad version/algo/ids/TTL/Base64). 
    - Validation policy: all tests green and docs aligned with implementation validators. 
---
### Phase 2 – Crypto core (Complete)
- **Deliverables**
    - Cryptography: `CryptoService` (AES-256-GCM, IV 12B, 128-bit tag), `CryptoECC` (RSA-OAEP SHA-256/MGF1 wrap/unwrap), `CryptoConstants`. 
    - AAD policy: `AadCodec` builds deterministic AAD from {version, algorithm, senderId, recipientId, timestamp, ttl, contentType, contentLength}.
    - Encrypt/Decrypt helpers: `MessageEncryptor`/`MessageDecryptor` round-trip APIs + `MessageFlowTest` scaffolding, with strict separation payload vs metadata. 
    - Hardening: use `SecureRandom`, zero-copy conversions where possible, avoid key/IV reuse. 
- **Tests**
    - Unit: AES-GCM round-trip, tamper detection (expected `AEADBadTagException`), wrong key/IV, RSA wrap/unwrap, AAD binding tests. 
    - Reports: surefire/failsafe artifacts in `shared/target/*` document green pipeline. 
---

### Phase 3 – Key management (Complete)
- **Deliverables**
    - Key lifecycle: X25519 generation via `EccKeyIO`, private key storage in PKCS#8 (`private.enc`) and public key in SubjectPublicKeyInfo (`public.pem`), import/export PEM/DER. 
    - User keystore: `UserKeyStore` binds CURRENT `<keyId>` directories (public.pem, private.enc, metadata.json) with sealing PBKDF2‑SHA256 → AES‑256‑GCM, file perms 700/600 (Unix) + ACL guidance for Windows. 
    - Trust metadata: `KeyMetadata` with fingerprint SHA‑256 (`FingerprintUtil`), status CURRENT/PREVIOUS/REVOKED, rotation & revocation policy documented (`docs/KEYSTORE.md`). 
    - Tooling: `KeystoreBootstrap` (provision), `KeystoreSealing`, `KeystoreRoot` selection, `FilePerms` helpers. 
- **Procedures**
    - Provisioning: `KeystoreBootstrap.run()` → `UserKeyStore.saveKeypair(...)` with sealing, permission hardening, fingerprint issuance & trust registration. 
    - Backup/restore: export public.pem + private.enc + metadata.json, restore via `loadCurrentPrivate(root, pass)` with fingerprint verification before activation. 
    - Rotation/audit: CURRENT demotion to PREVIOUS, new key creation, revocation list updates and audit log timestamps. 
- **Tests / Validation**
    - Unit: sealing round‑trip, wrong‑pass rejection, 1‑byte tamper detection, envelope parsing, metadata promotion/demotion. 
    - IT/Failsafe: `KeystoreE2EIT`, `KeystoreWrongPassIT`, `KeystoreTamperIT`, `KeystorePermsE2EIT` run during `verify` with artifacts in `shared/target/failsafe-reports`. 
---

### Phase 4 – Client integration (send/receive) (Complete)
- **Deliverables**
    - Send pipeline: `DefaultMessageSender` synthesizes payloads, validates with `MessageValidator`, encrypts via `MessageEncryptor` (AES-256-GCM + RSA-OAEP), serializes with `JsonCodec`, and sends with `WebSocketAdapter`. 
    - Receive pipeline: `DefaultMessageReceiver` consumes WebSocket frames, makes `JsonCodec.fromJson`, runs `MessageValidator.validate/validateRecipientOrThrow`, checks TTL with `ClockProvider`, decrypts via `MessageDecryptor` and routes to `MessageListener` based on `contentType`. 
    - Error handling: typed exceptions (`MessageValidationException`, `MessageExpiredException`, `MessageTamperedException`, `KeyNotFoundException`), bubbling in UI via `onError`, retry/backoff stubs (`shouldRetry`, `getRetryDelayMs`) in `WebSocketAdapter`, status binding in `MessageViewModel`. 
- **Interfaces / Infrastructure**
    - `MessageSender`, `MessageReceiver`, `KeyProvider`, `ClockProvider` (with `SystemClockProvider` and `FixedClockProvider` for deterministic tests) are used in production and test diodes. 
- **Tests**
    - `ClockProviderTest`, `KeyProviderTest`, `MessageSenderTest`, `MessageReceiverTest`, and integration workflow cover happy paths, recipient mismatch, expiry/tamper, network failure.
---

### Phase 5 – Server ingress/routing (Completed)
- **Deliverables**
  - REST/WebSocket ingress, schema validation (without content decryption), routing to mailboxes/queues.
  - Quotas/rate limits, anti‑abuse, envelope‑only persistence (without payload), audit logs.
  - TLS 1.3, optional mTLS, hardened headers.
- **Observability**
  - Structured logs (JSON), metrics (ingress rate, reject codes, latency), request IDs.
- **Implementation Details**
  - HikariCP connection pooling with MySQL Connector/J 8.0.33
  - Flyway migrations (V1-V6) for all database tables
  - `HttpIngressServer` using `com.sun.net.httpserver.HttpServer` with TLS 1.3
  - `WebSocketIngressServer` using Java-WebSocket library for real-time notifications
  - `EnvelopeDAO` with JDBC PreparedStatements (insert, fetchForRecipient, fetchByIds, markDelivered, deleteExpired)
  - `MailboxRouter` service for envelope routing and WebSocket notifications
  - `RateLimiterService` with database-backed quota enforcement (MAX_DAILY_MESSAGES, MAX_MESSAGE_BYTES)
  - `AuditLogger` with Log4j2 JSON structured logging
  - `MetricsRegistry` for in-memory metrics tracking
  - TTL cleanup job scheduled every 5 minutes
  - Server configuration via environment variables (HAF_DB_URL, HAF_TLS_KEYSTORE_PATH, etc.)

---

### Phase 6 – Client UI integration
- **Deliverables**
  - Views: ChatList, Conversation, Composer, Attachments drawer, Notifications. 
  - MVVM binding with use‑cases (SendMessageUseCase, ReceiveMessageUseCase). 
  - Rendering policy per contentType: text, image preview, video player sandbox, “Save as…” για office/PDF με safe handlers. 
- **UI Security**
  - No auto‑opening external apps from UI, explicit user action, visible SHA‑256 before opening files.

---

### Phase 7 – Secure file transfer
- **Deliverables**
  - Chunked uploads/downloads, resume, integrity (SHA‑256), contentType‑based caps, antivirus hook. 
  - UI: progress bars, cancel, retry, checksum display, thumbnails. 
- **Tests**
  - Large file flows, network interruptions, integrity mismatch handling. 

---

### Phase 8 – Authentication & Authorization
- **Deliverables**
  - Login, session tokens (short‑lived JWT), refresh strategy, optional 2FA/WebAuthn.
  - RBAC per endpoint, per‑user quotas, audit events for login/keys.
  - **User Directory Service**: REST API for public key lookup by userId, key fingerprint verification, key status (CURRENT/PREVIOUS/REVOKED).
  - **Client Integration**: Replace placeholder `UserKeystoreKeyProvider` with directory service queries for recipient public keys.
- **Hardening**
  - Password policy, lockouts, CSRF (where applicable), strict CORS (if web).
- **Implementation Notes**
  - Update `UserKeystoreKeyProvider.java` to query directory service instead of local-only keystore lookup
  - Add caching layer for frequently accessed public keys
  - Implement key revocation checking before encryption 

---

### Phase 9 – Telemetry & monitoring
- **Deliverables**
  - Metrics dashboards (ingress, failures, latencies), alerts for error spikes, storage thresholds. 
  - Distributed tracing for E2E debugging. 

---

### Phase 10 – Hardening & QA
- **Deliverables**
  - Wire fuzzing (invalid Base64, boundary TTL, oversize), property‑based tests. 
  - Performance: encryption throughput, server RPS, latency budgets. 
  - SAST/DAST, dependency review (Jackson, BouncyCastle), reproducible builds. 

---

### Phase 11 – Deployment & runbooks
- **Deliverables**
  - Packaging (native image/JRE bundle), config management (secrets), staging/prod parity. 
  - Runbooks: incident response, key compromise procedures, rotation drills. 

---

### Phase 12 – Compliance & documentation
- **Deliverables**
  - Security whitepaper: threat model, crypto choices, key lifecycle, logging policy. 
  - User/Operator manuals, API docs (OpenAPI), versioning policy. 

### Recommended execution order
- 2 → 3 → 4 → 5 before UI, so that the interface is built on stable flows and hardened endpoints. 
- 6 → 7 for full user experience, then 8–12 for production readiness.
