# **HAF Secure Messenger — Project README**

## **Description**

The **HAF Secure Messenger** is a desktop application written in **JavaFX**, providing secure, encrypted client–server communication for personnel of the **Hellenic Air Force**.
The purpose of the project is to deliver **end-to-end encryption**, strict user authentication, and secure file transfer capabilities, focusing on **security**, **scalability**, and **documentation**.

---

## **Key Features**

* End-to-end encryption: AES-256 for message content + RSA for key exchange
* Authentication: username/password + TOTP (Google Authenticator) + WebAuthn/FIDO2 support
* Real-time user presence and push notifications
* Encrypted file transfer (images, PDFs) with temporary encrypted server storage and automatic deletion
* No local storage of sensitive data on the client side (when required)
* Self-destructing messages (configurable TTL)
* Logging & auditing with brute-force protection
* Admin panel for monitoring and user management

---

## **Technologies**

* Language: Java 21
* UI: JavaFX (FXML + CSS, MVVM pattern)
* Server: Java (Spring Boot recommended or custom secure socket server)
* Database: MySQL
* Cryptography: Java Cryptography (AES-256, RSA 2048, SHA-256)
* 2FA: otp-java (TOTP)
* WebAuthn: WebAuthn4J (when required)
* Build / Packaging: Maven, jlink / jpackage for self-contained installers
* Logging: Log4j2 or SLF4J + Logback

---

## **System Architecture**

The application follows a **client–server 3-tier** design and the **MVVM** pattern on the client side.

* **Client Layer (Presentation / JavaFX)**

    * UI components in `client/src/main/java/com/haf/client/ui/` for FXML loaders and UI helpers
    * Controllers in `client/src/main/java/com/haf/client/controllers/` connecting UI with ViewModels
    * ViewModels in `client/src/main/java/com/haf/client/viewmodel/` implementing MVVM pattern with business logic
    * Local crypto module in `client/src/main/java/com/haf/client/crypto/` for client-side crypto operations (uses shared crypto APIs)
    * Network client in `client/src/main/java/com/haf/client/network/` using secure TCP sockets or WebSockets
    * Models in `client/src/main/java/com/haf/client/models/` (Message, User, Session) for client-side data representation
    * Utilities in `client/src/main/java/com/haf/client/utils/` for configuration, logging, and helpers
    * FXML views in `client/src/main/resources/fxml/` for JavaFX scenes
    * CSS styles in `client/src/main/resources/css/` for dark military UI theme
    * Images in `client/src/main/resources/images/` for icons and assets

* **Server Layer (Application / Business)**

    * Core server logic in `server/src/main/java/com/haf/server/core/` (Main.java, server orchestration)
    * Handlers in `server/src/main/java/com/haf/server/handlers/` for message validation and processing
    * Ingress layer in `server/src/main/java/com/haf/server/ingress/` (HTTP and WebSocket servers for client connections)
    * Routing in `server/src/main/java/com/haf/server/router/` (MailboxRouter, rate limiting, message queuing)
    * Metrics in `server/src/main/java/com/haf/server/metrics/` (audit logging, metrics registry, observability)
    * Database management in `server/src/main/java/com/haf/server/db/` (EnvelopeDAO for message persistence)
    * Configuration in `server/src/main/java/com/haf/server/config/` (ServerConfig for server settings)
    * Resources in `server/src/main/resources/` (config/, db/ with migrations, log4j2.xml)

* **Shared Layer (Common / DTOs, Crypto & Utilities)**

    * DTOs in `shared/src/main/java/com/haf/shared/dto/` (EncryptedMessage, KeyMetadata) for client-server communication
    * Constants in `shared/src/main/java/com/haf/shared/constants/` (CryptoConstants, MessageHeader) for protocol and crypto constants
    * Crypto implementations in `shared/src/main/java/com/haf/shared/crypto/` (CryptoService, MessageEncryptor, MessageDecryptor, UserKeyStore, KeystoreBootstrap, KeystoreSealing, etc.) for E2E encryption, key management, and keystore operations
    * Utilities in `shared/src/main/java/com/haf/shared/utils/` (JsonCodec, MessageValidator, RsaKeyIO, FilePerms, FingerprintUtil, PemCodec) for serialization, validation, and helper functions

* **Documentation Layer (Docs / References)**

    * Main project documentation in `docs/misc/` — ARCHITECTURE.md, CRYPTO.md, DATABASE.md, DEVELOPMENT.md, PHASES.md, SCENES.md, STRUCTURE.md, TESTING.md, WORFKLOW.md
    * Shared module docs in `docs/shared/` — AAD.md, CODECS.md, CONSTANTS.md, CRYPTO_SERVICE.md, DECRYPTION.md, DTO.md, ENCRYPTION.md, EXCEPTIONS.md, FILEPERMS.md, KEYSTORE.md, UTILS.md, VALIDATION.md, WIRE_FORMAT.md
    * Client docs in `docs/client/` — KEYSTORE_PROVIDER.md, MSG_INTERFACES.md, SENDER_RECEIVER.md, WEBSOCKET.md
    * Server docs in `docs/server/` — CONFIG.md, INGRESS.md, MAIN.md, OBSERVABILITY.md, PERSISTANCE.md, RATE_LIMITER.md, ROUTING.md


---

## **Project Structure**

```
haf-messenger/
│
├── client/
│   ├── src/main/java/com/haf/client/
│   │   ├── ui/                  # UI components and FXML loaders
│   │   ├── controllers/         # Controllers (Login, Chat, Settings)
│   │   ├── viewmodel/           # ViewModels for MVVM pattern
│   │   ├── crypto/              # Client-side crypto operations (uses shared crypto)
│   │   ├── network/             # Client-side socket/WebSocket handlers
│   │   ├── models/              # Message, User, Session classes
│   │   └── utils/               # Logging, configuration, helpers
│   ├── src/main/resources/
│   │   ├── fxml/                # JavaFX scenes (login.fxml, mainChat.fxml)
│   │   ├── css/                 # Dark military UI theme
│   │   └── images/              # Icons, SVGs, etc.
│   └── src/test/                # Test classes mirroring main structure
│
├── server/
│   ├── src/main/java/com/haf/server/
│   │   ├── core/                # Server main, orchestration
│   │   ├── handlers/            # Message validation and processing
│   │   ├── ingress/             # HTTP and WebSocket ingress servers
│   │   ├── router/              # MailboxRouter, rate limiting, message queuing
│   │   ├── metrics/             # Audit logging, metrics registry
│   │   ├── db/                  # EnvelopeDAO, database access
│   │   └── config/              # ServerConfig, server settings
│   ├── src/main/resources/
│   │   ├── config/              # Server configuration files
│   │   ├── db/                  # Database initialization 
│   │   │   └── migration/       # Database migration scripts
│   │   └── log4j2.xml           # Logging configuration
│   └── src/test/                # Test classes mirroring main structure
│
├── shared/
│   ├── src/main/java/com/haf/shared/
│   │   ├── dto/                 # Data Transfer Objects (EncryptedMessage, KeyMetadata)
│   │   ├── constants/           # Shared constants (CryptoConstants, MessageHeader)
│   │   ├── crypto/              # Crypto implementations (CryptoService, MessageEncryptor, MessageDecryptor)
│   │   ├── keystore/            # Key store formats, root selection, bootstrap, key loading logic
│   │   ├── exceptions/          # Typed exceptions (MessageValidationException, KeyNotFoundException, etc.)
│   │   └── utils/               # Common utilities (JsonCodec, MessageValidator, RsaKeyIO, FilePerms, FingerprintUtil, PemCodec)
│   └── src/test/                # Unit and integration tests (KeystoreE2EIT, etc.)
│
├── scripts/                     # Build and deployment scripts
│
└── docs/
    ├── client/                  # Client-specific documentation
    ├── server/                  # Server-specific documentation 
    ├── shared/                  # Shared module docs
    └── misc/                    # General docs 
```

---

## **Protocol & Message Model**

* Uses a JSON-based protocol over TLS or secure WebSocket with end-to-end encryption.

* Message format: `EncryptedMessage` DTO (defined in `shared/src/main/java/com/haf/shared/dto/EncryptedMessage.java`)

* Encryption flow:

  1. Client generates a session AES-256 key.
  2. AES key is wrapped (encrypted) with the recipient’s public RSA key using RSA-OAEP (SHA-256/MGF1).
  3. Payload is encrypted using AES-256-GCM with a 12-byte IV and 128-bit authentication tag.
  4. AAD (Additional Authenticated Data) is constructed from DTO metadata fields (version, algo, senderId, recipientId, timestamp, ttl, contentType, contentLength) — not transmitted, reconstructed during decryption.
  5. Packet structure: `{version, senderId, recipientId, timestampEpochMs, ttlSeconds, algorithm, ivB64, wrappedKeyB64, ciphertextB64, tagB64, contentType, contentLength, e2e}`

* The server must **not decrypt messages** — true **E2E encryption** ensures the server only handles encrypted blobs and metadata.

* Validation: All messages are validated using `MessageValidator` before sending (client) and upon receipt (server). See `docs/shared/WIRE_FORMAT.md` and `docs/shared/VALIDATION.md` for details.

---

## **Database Design (summary)**

* users(id, username, password_hash, pubkey, salt, role, rank, created_at)
* messages(id, sender_id, receiver_id, content_blob, content_meta, timestamp, ttl)
* sessions(id, user_id, token_hash, expires_at, last_active)
* logs(id, user_id, event_type, details_encrypted, created_at)

**Note:** message contents (`content_blob`) are always stored encrypted.

---

## **Security Best Practices**

* Passwords: salted hashing (PBKDF2 / Argon2 / bcrypt)
* Key management: private keys stored in client OS keystore (when available)
* TLS enforced with certificate pinning where possible
* Least privilege: separate admin scopes
* Rate limiting & account lockout for brute-force protection
* Audit logging: encrypted event logs
* Regular key rotation policy and forced rekey capability

---

## **UI / UX (JavaFX)**

* **Pattern:** MVVM — each FXML view has a corresponding ViewModel
* **SceneManager:** central manager for FXML loading and scene transitions

---

## **Build, Packaging & Deployment**

* Build: Maven 
* Native packaging: `jlink` + `jpackage` for self-contained installers (Linux/Windows/macOS)
* Configuration: externalized `application.properties` / `.env`, secrets kept out of repository (use vault for production)
