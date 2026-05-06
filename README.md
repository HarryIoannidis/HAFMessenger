# HAF Secure Messenger

## Purpose

This repository contains a Java 25 secure messaging system for HAF workflows, split into `client`, `server`, and `shared` modules. The documentation in this repo reflects implemented behavior and clearly marks future work.

## Current Implementation

- Architecture: JavaFX 25 desktop client + plain Java server + shared contract/crypto module.
- Build: Maven multi-module (`shared`, `client`, `server`) targeting Java 25 (`maven.compiler.release=25`).
- Transport: TLS 1.3 with authenticated HTTPS APIs and mailbox polling for receive/ACK flows.
- Messaging crypto: X25519 (XDH) key agreement + AES-256-GCM payload encryption with detached tag.
- Persistence: MySQL via HikariCP and Flyway migrations (`V1`-`V15`).
- Server ingress: `/api/v1/messages`, auth, search, contacts, attachment lifecycle, config endpoints.

## Key Types/Interfaces

- `client.network.MessageSender`: send/encrypt message and attachment-related operations.
- `client.network.MessageReceiver`: receive/decrypt flows and envelope acknowledgement.
- `shared.keystore.KeyProvider`: sender identity + recipient public-key resolution.
- `shared.utils.MessageValidator`: wire/policy validation for `EncryptedMessage`.
- `server.ingress.HttpIngressServer`: HTTPS endpoint surface.
- `server.router.MailboxRouter`: envelope routing and ACK handling.

## Flow

1. Client authenticates via HTTPS and stores access/refresh session tokens.
2. Client encrypts payload with `MessageEncryptor` and sends envelope through `MessageSender`.
3. Server validates envelope metadata, rate-limits, stores via DAO, and routes via `MailboxRouter`.
4. Receiver consumes envelopes via HTTPS polling, validates, decrypts with `MessageDecryptor`, and acknowledges envelope IDs.
5. Attachments follow init/chunk/complete/bind/download endpoints, with binary chunk upload/download bodies and JSON lifecycle metadata.

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
- Stages runtime dependencies on the classpath
- Produces a native macOS package with `jpackage` (no separate Java install needed)

Output path:

- `client/target/native/HAFMessenger.app`

Optional environment overrides:

- `APP_NAME` (default: `HAFMessenger`)
- `MAIN_JAR` (default: `haf-client.jar`)
- `MAIN_CLASS` (default: `com.haf.client.core.Launcher`)
- `ICON_PATH` (default: `client/src/main/resources/images/logo/app_logo.icns`, if present)
- `PACKAGE_TYPE` (default: `app-image`)
- `APP_VERSION` (default: `1.0`)
- `OUTPUT_DIR` (default: `client/target/native`)
- `SKIP_TESTS` (default: `true`)

## Build Linux AppImage (`.AppImage`)

Run this on Linux with JDK 25+:

```bash
./scripts/package-linux-appimage.sh
```

This script:

- Builds `shared` and `client`
- Creates a Linux app image using `jpackage`
- Produces a portable `.AppImage` using `appimagetool` (auto-downloads on x86_64 if missing)

Output path:

- `client/target/native/HAFMessenger-x86_64.AppImage`

Optional environment overrides:

- `APP_NAME` (default: `HAFMessenger`)
- `MAIN_JAR` (default: `haf-client.jar`)
- `MAIN_CLASS` (default: `com.haf.client.core.Launcher`)
- `ICON_PATH` (default: `client/src/main/resources/images/logo/app_logo.png`)
- `OUTPUT_DIR` (default: `client/target/native`)
- `SKIP_TESTS` (default: `true`)
- `APPIMAGE_TOOL` (default: auto-detect `appimagetool` or download tool)
- `APPIMAGE_TOOL_URL` (default: official AppImageKit continuous build URL for x86_64)

## Build Linux Server AppImage (`.AppImage`)

Run this on Linux with JDK 25+:

```bash
./scripts/package-linux-server-appimage.sh
```

This script:

- Builds `shared` and `server`
- Creates a Linux server app image using `jpackage`
- Bundles a first-run server launcher that:
  - checks `devtunnel` availability and login status
  - creates/reuses a persistent tunnel ID
  - configures public access on port `8443`
  - starts tunnel host + server and prints the forwarding URL

Output path:

- `server/target/native/HAFMessengerServer-x86_64.AppImage`

Optional environment overrides:

- `APP_NAME` (default: `HAFMessengerServer`)
- `MAIN_JAR` (default: `server-1.0-SNAPSHOT.jar`)
- `MAIN_CLASS` (default: `com.haf.server.core.Main`)
- `ICON_PATH` (default: `client/src/main/resources/images/logo/app_logo.png`)
- `OUTPUT_DIR` (default: `server/target/native`)
- `SKIP_TESTS` (default: `true`)
- `APPIMAGE_TOOL` (default: auto-detect `appimagetool` or download tool)
- `APPIMAGE_TOOL_URL` (default: official AppImageKit continuous build URL for x86_64)

Runtime notes:

- The server launcher writes first-run config under `~/.config/hafmessenger-server/`.
- Tunnel metadata is persisted at `~/.config/hafmessenger-server/runtime/devtunnel.env`.
- The host must have `devtunnel` installed and authenticated once via `devtunnel user login`.

## Build Windows Installer (`.msi` / `.exe`)

Run this on Windows with JDK 25+ (PowerShell):

```powershell
.\scripts\package-windows-app.ps1
```

This script:

- Builds `shared` and `client`
- Stages runtime dependencies on the classpath
- Produces a native Windows installer with `jpackage`
- Creates Start Menu and Desktop shortcuts on install

Output path:

- `client/target/native/HAFMessenger-1.0.msi` (default)
- `client/target/native/HAFMessenger-1.0.exe` (when `PACKAGE_TYPE=exe`)

Optional environment overrides:

- `APP_NAME` (default: `HAFMessenger`)
- `MAIN_JAR` (default: `haf-client.jar`)
- `MAIN_CLASS` (default: `com.haf.client.core.Launcher`)
- `ICON_PATH` (default: `client/src/main/resources/images/logo/app_logo.ico`)
- `PACKAGE_TYPE` (default: `msi`, supported: `msi`, `exe`)
- `APP_VERSION` (default: `1.0`)
- `OUTPUT_DIR` (default: `client/target/native`)
- `PACKAGE_WORK_DIR` (default: `client/target/windows-package`)
- `MVNW` (default: `mvnw.cmd`)
- `SKIP_TESTS` (default: `true`)
