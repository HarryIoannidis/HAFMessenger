# HAF Secure Messenger

**End-to-end encrypted messaging for desktop — built with Java 25, JavaFX, and modern cryptography.**

[![CI](https://github.com/HarryIoannidis/HAFMessenger/actions/workflows/ci.yml/badge.svg)](https://github.com/HarryIoannidis/HAFMessenger/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)

---

HAFMessenger is an independent secure-messaging prototype built as a portfolio project. It demonstrates a complete encrypted messaging stack: a JavaFX desktop client, an HTTPS/WSS server, and a shared cryptography module — all in a single Maven multi-module repository. **This is not an official deployment — it is a self-contained demo of real-world secure messaging architecture.**

## Features

- 🖥️ **JavaFX 25 desktop client** with a modern UI, contact management, and live chat
- 🔒 **End-to-end encryption** — X25519 (ECDH) key agreement + AES-256-GCM payload encryption
- ✍️ **Ed25519 digital signatures** on every message envelope
- 🌐 **HTTPS REST + WSS realtime** — TLS 1.3 only, hardened cipher suites
- 🗄️ **MySQL + Flyway** — versioned schema migrations (V1–V18)
- 📎 **Encrypted attachment flow** — chunked upload/download with init/complete/bind lifecycle
- 🛡️ **Server-side hardening** — rate limiting, audit logging, JWT session management
- 🔑 **Zero-knowledge server** — the server never decrypts message payloads

## Quick Start

### Prerequisites

- Java 25+, Maven 3.9+ (wrapper included), MySQL 8.0+, OpenSSL

### 1. Bootstrap local environment

```bash
git clone https://github.com/HarryIoannidis/HAFMessenger.git
cd HAFMessenger
./scripts/bootstrap-local-dev.sh
```

This generates all secrets, certificates, and config files under `.local/hafmessenger/` (gitignored).

### 2. Set up the database

```sql
CREATE DATABASE IF NOT EXISTS haf_messenger;
CREATE USER 'haf_app'@'localhost' IDENTIFIED BY '<password-from-variables.env>';
GRANT ALL PRIVILEGES ON haf_messenger.* TO 'haf_app'@'localhost';
```

See [docs/setup/LOCAL_DEV.md](docs/setup/LOCAL_DEV.md) for MySQL SSL configuration.

### 3. Run tests

```bash
./mvnw test
```

### 4. Start the server

```bash
HAF_SERVER_ENV_FILE=.local/hafmessenger/server/variables.env ./mvnw -pl server exec:java
```

### 5. Start the client

```bash
cp .local/hafmessenger/client/client.properties client/src/main/resources/config/
cp .local/hafmessenger/client/truststore.p12 client/src/main/resources/config/
./mvnw -pl client javafx:run
```

## Architecture

```
HAFMessenger/
├── shared/     # Crypto primitives, DTOs, validators, keystore utilities
├── client/     # JavaFX 25 desktop application
├── server/     # HTTPS REST + WSS realtime server
└── scripts/    # Bootstrap, cert generation, packaging scripts
```

### Message Flow

1. Client authenticates via HTTPS → receives JWT access/refresh tokens
2. Client opens authenticated WSS connection for realtime messaging
3. Sender encrypts message with recipient's X25519 public key, signs with Ed25519
4. Server validates envelope metadata, rate-limits, stores, and routes via `MailboxRouter`
5. Receiver validates signature, decrypts with own X25519 private key
6. Read/delivery receipts flow back over WSS

### Key Types

| Interface | Purpose |
| --- | --- |
| `MessageSender` | Encrypt and send messages over WSS |
| `MessageReceiver` | Receive, validate, and decrypt WSS events |
| `KeyProvider` | Sender identity + recipient public-key resolution |
| `MessageValidator` | Wire/policy validation for `EncryptedMessage` |
| `HttpIngressServer` | HTTPS REST endpoint surface |
| `RealtimeWebSocketServer` | Authenticated WSS realtime gateway |
| `MailboxRouter` | Envelope routing and ACK handling |

## Regenerating Secrets and Certificates

All secrets are generated locally and never committed. The bootstrap script creates:

| File | Purpose |
| --- | --- |
| `variables.env` | Server config with random DB password, JWT secret, TLS passwords, etc. |
| `server.p12` | HTTPS keystore (self-signed, SANs: localhost/127.0.0.1/::1) |
| `mysql-truststore.p12` | Java truststore for MySQL SSL verification |
| `mysql-ssl/*` | MySQL CA and server certificates |
| `admin_private_key.txt` | Admin X25519 private key for registration photo decryption |
| `truststore.p12` | Client truststore containing the server certificate |

To regenerate everything: `FORCE=1 ./scripts/bootstrap-local-dev.sh`

Individual scripts: `generate-mysql-certs.sh`, `generate-server-tls.sh`, `generate-admin-keys.sh`

## Security Notes

- **The server cannot decrypt message payloads.** All message content is end-to-end encrypted between clients.
- **TLS 1.3 only** with `TLS_AES_256_GCM_SHA384` and `TLS_CHACHA20_POLY1305_SHA256`.
- **Ed25519 signature verification** happens before any message processing.
- **All secrets must stay local.** Never commit `.env` files, keystores, or private keys.
- **Rate limiting and audit logging** are server-side enforcement points.

See [SECURITY.md](SECURITY.md) for the full security policy and vulnerability reporting.

## Packaging

<details>
<summary><strong>Build macOS App (.app)</strong></summary>

```bash
./scripts/package-mac-app.sh
```

Produces: `client/target/native/HAFMessenger.app`

Environment overrides: `APP_NAME`, `MAIN_JAR`, `MAIN_CLASS`, `ICON_PATH`, `PACKAGE_TYPE`, `APP_VERSION`, `OUTPUT_DIR`, `SKIP_TESTS`
</details>

<details>
<summary><strong>Build Linux AppImage (.AppImage)</strong></summary>

```bash
./scripts/package-linux-appimage.sh
```

Produces: `client/target/native/HAFMessenger-x86_64.AppImage`

Environment overrides: `APP_NAME`, `MAIN_JAR`, `MAIN_CLASS`, `ICON_PATH`, `OUTPUT_DIR`, `SKIP_TESTS`, `APPIMAGE_TOOL`, `APPIMAGE_TOOL_URL`
</details>

<details>
<summary><strong>Build Linux Server AppImage (.AppImage)</strong></summary>

```bash
./scripts/package-linux-server-appimage.sh
```

Produces: `server/target/native/HAFMessengerServer-x86_64.AppImage`

Includes a first-run launcher that configures `devtunnel` for public access.

Environment overrides: `APP_NAME`, `MAIN_JAR`, `MAIN_CLASS`, `ICON_PATH`, `OUTPUT_DIR`, `SKIP_TESTS`, `APPIMAGE_TOOL`, `APPIMAGE_TOOL_URL`
</details>

<details>
<summary><strong>Build Windows Installer (.msi / .exe)</strong></summary>

```powershell
.\scripts\package-windows-app.ps1
```

Produces: `client/target/native/HAFMessenger-1.0.msi`

Environment overrides: `APP_NAME`, `MAIN_JAR`, `MAIN_CLASS`, `ICON_PATH`, `PACKAGE_TYPE`, `APP_VERSION`, `OUTPUT_DIR`, `PACKAGE_WORK_DIR`, `CLIENT_CONFIG_PATH`, `MVNW`, `SKIP_TESTS`
</details>

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

[MIT](LICENSE) © HAFMessenger Contributors
