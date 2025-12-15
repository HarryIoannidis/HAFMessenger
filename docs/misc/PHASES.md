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
    - Cryptography: `CryptoService` (AES-256-GCM, IV 12B, 128-bit tag), `CryptoRSA` (RSA-OAEP SHA-256/MGF1 wrap/unwrap), `CryptoConstants`. 
    - AAD policy: `AadCodec` builds deterministic AAD from {version, algorithm, senderId, recipientId, timestamp, ttl, contentType, contentLength}.
    - Encrypt/Decrypt helpers: `MessageEncryptor`/`MessageDecryptor` round-trip APIs + `MessageFlowTest` scaffolding, with strict separation payload vs metadata. 
    - Hardening: use `SecureRandom`, zero-copy conversions where possible, avoid key/IV reuse. 
- **Tests**
    - Unit: AES-GCM round-trip, tamper detection (expected `AEADBadTagException`), wrong key/IV, RSA wrap/unwrap, AAD binding tests. 
    - Reports: surefire/failsafe artifacts in `shared/target/*` document green pipeline. 
---

### Phase 3 – Key management (Complete)
- **Deliverables**
    - Key lifecycle: RSA 2048/3072 generation via `RsaKeyIO`, αποθήκευση ιδιωτικού σε PKCS#8 (`private.enc`) και δημοσίου σε SubjectPublicKeyInfo (`public.pem`), import/export PEM/DER. 
    - User keystore: `UserKeyStore` δένει CURRENT `<keyId>` directories (public.pem, private.enc, metadata.json) με sealing PBKDF2‑SHA256 → AES‑256‑GCM, file perms 700/600 (Unix) + ACL guidance για Windows. 
    - Trust metadata: `KeyMetadata` με fingerprint SHA‑256 (`FingerprintUtil`), status CURRENT/PREVIOUS/REVOKED, rotation & revocation policy documented (`docs/KEYSTORE.md`). 
    - Tooling: `KeystoreBootstrap` (provision), `KeystoreSealing`, `KeystoreRoot` selection, `FilePerms` helpers. 
- **Procedures**
    - Provisioning: `KeystoreBootstrap.run()` → `UserKeyStore.saveKeypair(...)` με sealing, permission hardening, fingerprint issuance & trust registration. 
    - Backup/restore: export public.pem + private.enc + metadata.json, restore μέσω `loadCurrentPrivate(root, pass)` με fingerprint verification before activation. 
    - Rotation/audit: CURRENT demotion to PREVIOUS, new key creation, revocation list updates και audit log timestamps. 
- **Tests / Validation**
    - Unit: sealing round‑trip, wrong‑pass rejection, 1‑byte tamper detection, envelope parsing, metadata promotion/demotion. 
    - IT/Failsafe: `KeystoreE2EIT`, `KeystoreWrongPassIT`, `KeystoreTamperIT`, `KeystorePermsE2EIT` τρέχουν στο `verify` με artifacts σε `shared/target/failsafe-reports`. 
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
  - REST/WebSocket ingress, schema validation (χωρίς decrypt περιεχομένου), routing σε mailboxes/queues.
  - Quotas/rate limits, anti‑abuse, envelope‑only persistence (χωρίς payload), audit logs.
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

### Φάση 6 – Client UI integration
- Παραδοτέα
  - Views: ChatList, Conversation, Composer, Attachments drawer, Notifications. 
  - MVVM binding με use‑cases (SendMessageUseCase, ReceiveMessageUseCase). 
  - Rendering policy ανά contentType: text, image preview, video player sandbox, “Save as…” για office/PDF με safe handlers. 
- Ασφάλεια UI
  - No auto‑opening external apps από UI, explicit user action, εμφανής SHA‑256 πριν άνοιγμα αρχείων.

---

### Φάση 7 – Secure file transfer
- Παραδοτέα
  - Chunked uploads/downloads, resume, integrity (SHA‑256), contentType‑based caps, antivirus hook. 
  - UI: progress bars, cancel, retry, checksum display, thumbnails. 
- **Tests**
  - Large file flows, network interruptions, integrity mismatch handling. 

---

### Φάση 8 – Authentication & Authorization
- **Deliverables**
  - Login, session tokens (short‑lived JWT), refresh strategy, optional 2FA/WebAuthn.
  - RBAC per endpoint, per‑user quotas, audit events για login/keys.
  - **User Directory Service**: REST API for public key lookup by userId, key fingerprint verification, key status (CURRENT/PREVIOUS/REVOKED).
  - **Client Integration**: Replace placeholder `UserKeystoreKeyProvider` with directory service queries for recipient public keys.
- **Hardening**
  - Password policy, lockouts, CSRF (όπου σχετικό), strict CORS (αν web).
- **Implementation Notes**
  - Update `UserKeystoreKeyProvider.java` to query directory service instead of local-only keystore lookup
  - Add caching layer for frequently accessed public keys
  - Implement key revocation checking before encryption 

---

### Φάση 9 – Telemetry & monitoring
- Παραδοτέα
  - Metrics dashboards (ingress, failures, latencies), alerts για error spikes, storage thresholds. 
  - Distributed tracing για αποσφαλμάτωση E2E. 

---

### Φάση 10 – Hardening & QA
- Παραδοτέα
  - Fuzzing στο wire (invalid Base64, boundary TTL, oversize), property‑based tests. 
  - Performance: encryption throughput, server RPS, latency budgets. 
  - SAST/DAST, dependency review (Jackson, BouncyCastle), reproducible builds. 

---

### Φάση 11 – Deployment & runbooks
- Παραδοτέα
  - Packaging (native image/JRE bundle), config management (secrets), staging/prod parity. 
  - Runbooks: incident response, key compromise procedures, rotation drills. 

---

### Φάση 12 – Compliance & documentation
- Παραδοτέα
  - Security whitepaper: threat model, crypto choices, key lifecycle, logging policy. 
  - User/Operator manuals, API docs (OpenAPI), versioning policy. 

### Προτεινόμενη σειρά εκτέλεσης
- 2 → 3 → 4 → 5 πριν το UI, ώστε το interface να στηριχτεί σε σταθερές ροές και σκληρυμένα endpoints. 
- 6 → 7 για πλήρη εμπειρία χρήστη, κατόπιν 8–12 για παραγωγική ετοιμότητα.
