# HAF Secure Messenger

## Purpose

This repository contains a Java 25 secure messaging system for HAF workflows, split into `client`, `server`, and `shared` modules. The documentation in this repo reflects implemented behavior and clearly marks future work.

## Current Implementation

- Architecture: JavaFX 25 desktop client + plain Java server + shared contract/crypto module.
- Build: Maven multi-module (`shared`, `client`, `server`) targeting Java 25 (`maven.compiler.release=25`).
- Transport: TLS 1.3 with mode-aware messaging receive transport (dev: WSS push, prod: HTTPS polling).
- Messaging crypto: X25519 (XDH) key agreement + AES-256-GCM payload encryption with detached tag.
- Persistence: MySQL via HikariCP and Flyway migrations (`V1`-`V15`).
- Server ingress: `/api/v1/messages`, auth, search, contacts, attachment lifecycle, config endpoints.
- Runtime mode control: server mode is controlled by `HAF_APP_IS_DEV`; client mode by `app.isDev`.

## Key Types/Interfaces

- `client.network.MessageSender`: send/encrypt message and attachment-related operations.
- `client.network.MessageReceiver`: receive/decrypt flows and envelope acknowledgement.
- `shared.keystore.KeyProvider`: sender identity + recipient public-key resolution.
- `shared.utils.MessageValidator`: wire/policy validation for `EncryptedMessage`.
- `server.ingress.HttpIngressServer`: HTTPS endpoint surface.
- `server.router.MailboxRouter`: envelope routing, ACK handling, push dispatch.

## Flow

1. Client authenticates via HTTPS and stores access/refresh session tokens.
2. Client encrypts payload with `MessageEncryptor` and sends envelope through `MessageSender`.
3. Server validates envelope metadata, rate-limits, stores via DAO, and routes via `MailboxRouter`.
4. Receiver consumes envelopes via mode-aware transport (dev websocket push, prod HTTPS polling), validates, decrypts with `MessageDecryptor`, and acknowledges envelope IDs.
5. Attachments follow init/chunk/complete/bind/download endpoints and inherit policy/TTL controls.

## Error/Security Notes

- Server never decrypts message payloads.
- TLS is restricted to `TLSv1.3` with hardened cipher suites.
- Validation and recipient checks happen before decrypt.
- Rate limiting and audit logging are server-side enforcement points.
- Docs distinguish implemented behavior from future/planned features.

## Related Files

- `pom.xml`
- `client/src/main/java/com/haf/client/core/ClientApp.java`
- `client/src/main/java/com/haf/client/network/MessageSender.java`
- `client/src/main/java/com/haf/client/network/MessageReceiver.java`
- `server/src/main/java/com/haf/server/core/Main.java`
- `server/src/main/java/com/haf/server/ingress/HttpIngressServer.java`
- `server/src/main/java/com/haf/server/router/MailboxRouter.java`
- `shared/src/main/java/com/haf/shared/dto/EncryptedMessage.java`
- `shared/src/main/java/com/haf/shared/utils/MessageValidator.java`

## Build Native macOS App (`.app`)

Run this on a Mac with JDK 25+:

```bash
./scripts/package-mac-app.sh
```

This script:

- Builds `shared` and `client`
- Creates a custom runtime with `jlink`
- Produces a native `.app` with `jpackage` (no separate Java install needed)

Output path:

- `client/target/native/HAFMessenger.app`

Optional environment overrides:

- `APP_NAME` (default: `HAFMessenger`)
- `MAIN_JAR` (default: `haf-client.jar`)
- `MAIN_CLASS` (default: `com.haf.client.core.Launcher`)
- `ICON_PATH` (default: `client/src/main/resources/images/logo/app_logo.icns`)
- `PACKAGE_TYPE` (default: `app-image`)
- `OUTPUT_DIR` (default: `client/target/native`)
