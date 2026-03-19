# STRUCTURE

## **Project Structure**

```
haf-messenger/
├── client/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/haf/client/
│           │       ├── core/
│           │       ├── controllers/
│           │       ├── viewmodels/
│           │       ├── services/
│           │       ├── crypto/
│           │       ├── network/
│           │       ├── models/
│           │       ├── exceptions/
│           │       └── utils/
│           └── resources/
│               ├── fxml/
│               ├── css/
│               └── images/
├── server/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/haf/server/
│           │       ├── config/
│           │       ├── core/
│           │       ├── db/
│           │       ├── handlers/
│           │       ├── router/
│           │       ├── ingress/
│           │       └── metrics/
│           └── resources/
│               └── config/
├── shared/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/haf/shared/
│           │       ├── constants/
│           │       ├── crypto/
│           │       ├── dto/
│           │       ├── requests/
│           │       ├── responses/
│           │       ├── exceptions/
│           │       ├── keystore/
│           │       └── utils/
│           └── resources/
├── scripts/  
└── docs/
    ├── client/
    ├── server/
    ├── shared/
    └── misc/

```

---

## **Client**

The client represents the presentation layer, implemented in **JavaFX**, following the **MVVM** pattern to separate the presentation from the logic and data models.
Encryption is applied on the client side (AES-256 for content, X25519 for key exchange), and no sensitive data are permanently stored on the workstation.
The networking stack uses **TLS/WebSocket** for secure communication and integrates **2FA** (TOTP, WebAuthn/FIDO2).

**Folder structure:**

```
client/
   └── src/
       └── main/
           ├── java/
           │   └── com/haf/client/
           │       ├── core/
           │       ├── controllers/
           │       ├── viewmodels/
           │       ├── services/
           │       ├── crypto/
           │       ├── network/
           │       ├── models/
           │       ├── exceptions/
           │       └── utils/
           └── resources/
               ├── fxml/
               ├── css/
               └── images/
```               

* **core/**: Application entry point (`Launcher`, `ClientApp`), active session containers (`ChatSession`, `NetworkSession`, `CurrentUserSession`).
* **controllers/**: FXML controllers connecting the UI with ViewModels — handles events and validation.
* **viewmodels/**: MVVM bindings, business state, and commands for the views (`LoginViewModel`, `ChatViewModel`, `MainViewModel`, etc.).
* **services/**: Service interfaces and implementations for login, registration, and attachment operations (`LoginService`, `RegistrationService`, `ChatAttachmentService`, etc.).
* **crypto/**: Client-side key provider (`UserKeystoreKeyProvider`) bridging the shared keystore with the network layer.
* **network/**: TLS/WebSocket client — sending/receiving encrypted packets (`WebSocketAdapter`, `DefaultMessageSender`, `DefaultMessageReceiver`).
* **models/**: Client-side view data classes (`ContactInfo`, `MessageVM`, `MessageType`, `UserProfileInfo`).
* **exceptions/**: Typed client-side exceptions (`SslConfigurationException`, `RegistrationFlowException`, `HttpCommunicationException`).
* **utils/**: Logging, configuration, and shared helpers (`ViewRouter`, `ContextMenuBuilder`, `ImageSaveSupport`, `WindowResizeHelper`, `UiConstants`, `SslContextUtils`, `MessageBubbleFactory`).
* **resources/fxml/**: JavaFX scenes (`login.fxml`, `main.fxml`, `chat.fxml`, `register.fxml`, `splash.fxml`, `profile.fxml`, `search.fxml`, etc.).
* **resources/css/**: User interface styles.
* **resources/images/**: Icons and SVG assets for the UI.

---

## **Server**

The server forms the **business/application layer**, handling authentication, message routing, temporary storage with TTL, auditing, and brute-force protection.
It maintains **true end-to-end encryption**, never decrypting message contents, while supporting **key exchange, certificates, and security policies**.
Persistent storage is implemented with **MySQL/SQLite**, featuring access control and event logging.

**Folder structure:**

```
server/
   └── src/
       └── main/
           ├── java/
           │   └── com/haf/server/
           │       ├── config/
           │       ├── core/
           │       ├── db/
           │       ├── handlers/
           │       ├── router/
           │       ├── ingress/
           │       └── metrics/
           └── resources/
               └── config/
```

* **config/**: Server configuration, certificates, policy logs, and environment settings.
* **core/**: Server startup and management of socket/WebSocket connections.
* **handlers/**: Authentication, message routing, and session/token management.
* **router/**: Message routing logic, including message encryption and decryption.
* **ingress/**: Message ingestion from the client, including key exchange and certificate management.
* **metrics/**: Prometheus metrics exporter for monitoring and alerting.
* **db/**: MySQL access, encrypted blob and metadata storage.
* **resources/config/**: Server configuration, certificates, policy logs, and environment settings.

---

## **Shared Module**

The shared module provides **common DTOs**, **protocol constants**, and **utility functions**, ensuring consistency between client and server.
It unifies packet definitions and timing/network parameters to prevent configuration mismatches, while offering reusable cryptographic and serialization routines.

**Folder structure:**

```
shared/
   └── src/
       └── main/
           ├── java/
           │   └── com/haf/shared/
           │       ├── constants/
           │       ├── crypto/
           │       ├── dto/
           │       ├── requests/
           │       ├── responses/
           │       ├── exceptions/
           │       ├── keystore/
           │       └── utils/
           └── resources/
```

* **dto/**: Core wire-format DTOs (`EncryptedMessage`, `EncryptedFileDTO`, `KeyMetadata`, attachment payload types).
* **requests/**: Client-to-server request DTOs (`LoginRequest`, `RegisterRequest`, `AddContactRequest`, `Attachment*Request`).
* **responses/**: Server-to-client response DTOs (`LoginResponse`, `RegisterResponse`, `ContactsResponse`, `Attachment*Response`, `PublicKeyResponse`, etc.).
* **constants/**: Shared constants (`CryptoConstants`, `MessageHeader`, `AttachmentConstants`) for protocol configuration consistency.
* **crypto/**: Shared cryptographic implementations (`CryptoService`, `MessageEncryptor`, `MessageDecryptor`, `CryptoECC`, `AadCodec`).
* **keystore/**: Key lifecycle management (`UserKeystore`, `KeystoreBootstrap`, `KeystoreSealing`, `KeystoreRoot`, `KeyProvider`).
* **exceptions/**: Typed exceptions covering crypto, keystore, validation, and JSON operations.
* **utils/**: Shared helper functions (`JsonCodec`, `MessageValidator`, `EccKeyIO`, `FingerprintUtil`, `PemCodec`, `FilePerms`, clock providers).
* **resources/**: Common resources not specific to client or server.

---

## **Docs**

The **docs** folder contains all technical documentation, the main **README**, and **architecture/flow diagrams** for understanding and maintaining the system.
It serves as the single source of truth for **security specifications**, **build/deploy procedures**, and **system overviews**.

**Folder structure:**

```
docs/
    ├── client/
    ├── server/
    ├── shared/
    └── misc/
```

* **README.md**: Central documentation covering functionality, architecture, security, and deployment.
* **architecture-diagram.png**: Client–server and key-exchange flow diagram for operational understanding.

---

# FINAL TREE

### Final tree

```
haf-messenger/
├── .idea/
├── .mvn/
│   └── wrapper/
│
├── client/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   ├── module-info.java
│       │   │   └── com/haf/client/
│       │   │       ├── ui/
│       │   │       ├── controllers/
│       │   │       ├── viewmodel/
│       │   │       ├── crypto/
│       │   │       ├── network/
│       │   │       ├── models/
│       │   │       └── utils/
│       │   └── resources/
│       │       ├── fxml/
│       │       ├── css/
│       │       └── images/
│       └── test/
│           ├── java/
│           │   └── com/haf/client/
│           │       ├── controllers/
│           │       ├── crypto/
│           │       ├── network/
│           │       ├── services/
│           │       ├── utils/
│           │       └── viewmodels/
│           └── resources/
│               ├── test-fxml/
│               ├── test-keystores/
│               └── config/
│
├── server/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   ├── module-info.java
│       │   │   └── com/haf/server/
│       │   │       ├── config/
│       │   │       ├── core/
│       │   │       ├── db/
│       │   │       ├── handlers/
│       │   │       ├── router/
│       │   │       ├── ingress/
│       │   │       └── metrics/
│       │   └── resources/
│       │       └── config/
│       │       │   └── certificates/
│       │       └── db/
│       │           └── migrations/
│       └── test/
│           ├── java/
│           │   └── com/haf/
│           │       ├── integration_test/
│           │       └── server/
│           │           ├── config/
│           │           ├── core/
│           │           ├── db/
│           │           ├── handlers/
│           │           ├── router/
│           │           ├── ingress/
│           │           └── metrics/
│           └── resources/
│               ├── config/
│               │   └── certificates/
│               └── db/
│                   └── migrations/
│
├── shared/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   ├── module-info.java
│       │   │   └── com/haf/shared/
│       │   │       ├── constants/
│       │   │       ├── crypto/
│       │   │       ├── dto/
│       │   │       ├── requests/
│       │   │       ├── responses/
│       │   │       ├── exceptions/
│       │   │       ├── keystore/
│       │   │       └── utils/
│       │   └── resources/
│       └── test/
│           ├── java/
│           │   └── com/haf/
│           │       ├── integration_test/
│           │       └── shared/
│           │           ├── constants/
│           │           ├── crypto/
│           │           ├── dto/
│           │           ├── exceptions/
│           │           ├── keystore/
│           │           └── utils/
│           └── resources/
│               └── vectors/
│
├── docs/
│   ├── client/
│   ├── server/
│   ├── shared/
│   └── misc/
│
├── scripts/  
│
├── pom.xml
├── mvnw
├── mvnw.cmd
└── .gitignore
   
        
```
The above does not allow direct client↔server dependency, but only through shared for single DTO/constants, which is critical for the stability and security of the protocol.

### POM files
Minimal poms are provided for pure separation, with Java 21 and JavaFX only on the client and no GUI/DB dependencies on the shared to remain neutral.
```xml
<!-- haf-messenger/pom.xml (parent) -->
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.haf</groupId>
  <artifactId>haf-messenger</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>

  <modules>
    <module>shared</module>
    <module>client</module>
    <module>server</module>
  </modules>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>
</project>
```

```xml
<!-- shared/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.haf</groupId>
    <artifactId>haf-messenger</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>shared</artifactId>
  <name>haf-shared</name>
  <dependencies>
  </dependencies>
</project>
```

```xml
<!-- client/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.haf</groupId>
    <artifactId>haf-messenger</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>client</artifactId>
  <name>haf-client</name>

  <properties>
    <javafx.version>21</javafx.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.haf</groupId>
      <artifactId>shared</artifactId>
      <version>1.0.0</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-controls</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-fxml</artifactId>
      <version>${javafx.version}</version>
    </dependency>
  </dependencies>
</project>
```

```xml
<!-- server/pom.xml -->
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.haf</groupId>
    <artifactId>haf-messenger</artifactId>
    <version>1.0.0</version>
  </parent>
  <artifactId>server</artifactId>
  <name>haf-server</name>

  <dependencies>
    <dependency>
      <groupId>com.haf</groupId>
      <artifactId>shared</artifactId>
      <version>1.0.0</version>
    </dependency>
    <!-- add JDBC/WebSocket libs per DB/transport choice -->
  </dependencies>
</project>
```
Maintaining client-only JavaFX dependencies and shared DTOs exclusively minimizes pairing and the risk of UI/DB leakage at the wrong level.

### module-info.java files
Module descriptors define what is required and what is exported, by opening controllers to javafx.fxml for reflection when loading FXML.
```java
// shared/src/main/java/module-info.java
module shared {
    exports com.haf.shared.dto;
    exports com.haf.shared.requests;
    exports com.haf.shared.responses;
    exports com.haf.shared.constants;
    exports com.haf.shared.utils;
    exports com.haf.shared.crypto;
    exports com.haf.shared.exceptions;
    exports com.haf.shared.keystore;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
}
```

```java
// client/src/main/java/module-info.java
module client {
    requires transitive shared;
    requires transitive javafx.graphics;
    requires transitive javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires java.logging;
    requires com.jfoenix;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires java.prefs;

    exports com.haf.client.models;
    exports com.haf.client.controllers;
    exports com.haf.client.core;
    exports com.haf.client.utils;
    exports com.haf.client.crypto;
    exports com.haf.client.viewmodels;
    exports com.haf.client.network;
    exports com.haf.client.services;

    opens com.haf.client.controllers to javafx.fxml;
    opens com.haf.client.core to javafx.graphics;
}
```

```java
// server/src/main/java/module-info.java
module server {
    requires com.zaxxer.hikari;
    requires flyway.core;
    requires java.sql;
    requires jdk.httpserver;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires org.java_websocket;
    requires io.github.cdimascio.dotenv.java;
    requires password4j;
    requires org.slf4j;
    requires transitive shared;

    opens com.haf.server.ingress to com.fasterxml.jackson.databind;
}
```
Limiting exports to the minimum necessary APIs and selective opens only to javafx.fxml reduces the attack surface and stabilizes encapsulation in JPMS.

### Practical steps
- Moved the existing code to the corresponding client/server/shared folders while maintaining com.haf.* package nomenclature, and updated module-info per module to build without split-packages.
- Place FXML/CSS/images exclusively on the client/resources, and transfer DTO/constants to shared so that the client and server speak the same protocol without duplicates.
- Build from the root with mvn -am -pl shared,client,server verify to verify the client↔shared and server↔shared dependency chain without a direct client↔server link.

### Theory, options, and security issues
- The Client–Server–Shared distinction enforces a pure data contract and allows E2E crypto to the client with X25519 key agreement and AES content, while the server only handles metadata and policies without content decryption.
- Transport alternatives are raw TCP with framing or WebSocket over TLS, where WebSocket facilitates duplex messaging and connectivity checks in a firewall environment, while raw TCP gives a lower overhead but requires your own heartbeat and backpressure.
- Common issues include incorrect exports/opens blocking FXML loading, mixing UI/logic on the controller instead of ViewModel, and double DTO definition on client/server instead of shared share, leading to protocol incompatibilities.

### Unit Flow Chart
Dependency and data flow follows the following, with MVVM bindings and the use of shared DTOs for protocol stability.
```
FXML (client/resources)
 -> Controller (client/controllers)
 -> ViewModel (client/viewmodels)
 -> Services (client/services)
 -> Network/Crypto (client/network, client/crypto)
 -> Ingress (server/ingress via TLS/WebSocket)
 -> Router (server/router)
 -> DAO (server/db)
 -> Response DTO (shared/dto, shared/responses)
 -> ViewModel update
 -> UI refresh
```
This flow is applied for every feature following the WORKFLOW, ensuring clean boundaries and ease of end‑to‑end testing per feature.

---
# STRUCTURE BREAKDOWN

This document describes the folder structure and purpose of each module in **HAF Messenger**, following an **MVVM (Model–View–ViewModel)** architecture with a secure **Client–Server** design.

## **1. client**

This is the **desktop app** — the JavaFX interface that users interact with.

### **1.1 core**

Application entry point and session management containers.

Key classes: `Launcher` (delegates to `ClientApp.main()`), `ClientApp` (JavaFX Application, registers primary stage), `ChatSession` (active chat context), `NetworkSession` (active network connection container), `CurrentUserSession` (stores authenticated user state).

### **1.2 controllers**

FXML controllers — the Java classes handling UI events and interactions, one per scene.

Key classes: `LoginController`, `RegisterController`, `SplashController`, `MainController`, `ChatController`, `SearchController`, `ProfileController`, `ContactCell`, `MainContentLoader`, `PreviewController`, `SearchContactActions`.

### **1.3 viewmodels**

Implements the **MVVM pattern logic** — connects the UI with the application logic, exposing observable JavaFX properties for data binding.

Key classes: `LoginViewModel`, `RegisterViewModel`, `ChatViewModel`, `MainViewModel`, `SearchViewModel`, `MessageViewModel`, `SplashViewModel`.

### **1.4 services**

Business service interfaces and their default implementations. Keeps business logic out of controllers.

Key classes: `LoginService` / `DefaultLoginService`, `RegistrationService` / `DefaultRegistrationService`, `MainSessionService` / `DefaultMainSessionService`, `ChatAttachmentService` / `DefaultChatAttachmentService`.

### **1.5 crypto**

Client-side key provisioning — bridges the shared keystore with the network layer.

Key class: `UserKeystoreKeyProvider` — implements the shared `KeyProvider` interface to load private keys from `UserKeystore` and look up recipient public keys via the directory service.

### **1.6 network**

TLS/WebSocket communication layer — sending and receiving encrypted message frames.

Key classes: `WebSocketAdapter` (manages WebSocket lifecycle with TLS 1.3, retry, backpressure), `MessageSender` / `DefaultMessageSender` (validates + serializes + transmits `EncryptedMessage`), `MessageReceiver` / `DefaultMessageReceiver` (receives, validates, and dispatches incoming frames).



### **1.7 models**

Client-side view data classes representing UI-layer data (not shared DTOs).

Key classes: `ContactInfo`, `MessageVM` (ViewModel representation of a single message), `MessageType` (enum: TEXT, IMAGE, FILE), `UserProfileInfo`.



### **1.8 exceptions**

Typed client-side exceptions for clear error propagation.

Key classes: `SslConfigurationException`, `RegistrationFlowException`, `HttpCommunicationException`.

### **1.9 utils**

Shared helper classes for navigation, configuration, UI construction, and formatting.

Key classes: `ViewRouter` (centralized FXML navigation), `ContextMenuBuilder`, `ImageSaveSupport`, `WindowResizeHelper`, `UiConstants`, `SslContextUtils`, `MessageBubbleFactory`.

---

## **2. server**

The **backend** — handles connections, authentication, message routing, and security.



### **2.1 core**

Server entry point and bootstrap orchestration.

Key class: `Main` — reads `ServerConfig`, initialises HikariCP DataSource, runs Flyway migrations, loads TLS keystore, builds all services, starts `HttpIngressServer` and `WebSocketIngressServer`, and registers a graceful shutdown hook.



### **2.2 handlers**

Message validation before persistence — operates on envelope metadata only, never decrypts content.

Key class: `EncryptedMessageValidator` — validates required fields, Base64 integrity, TTL bounds, payload size, and timestamp before any DB write.



### **2.3 ingress**

HTTP and WebSocket ingress endpoints. Both enforce TLS 1.3, share the same validation and rate-limiting pipeline, and route to `MailboxRouter`.

Key classes: `HttpIngressServer` (HTTPS REST on `HAF_HTTP_PORT`, path `/api/v1/messages`), `WebSocketIngressServer` (WSS on `HAF_WS_PORT`, path `/ws`), `PresenceRegistry` (tracks connected WebSocket clients for push delivery).



### **2.4 router**

In-memory message queuing and routing from ingress to recipient mailboxes.

Key classes: `MailboxRouter` (routes envelopes, manages WebSocket subscriptions, triggers push delivery), `RateLimiterService` (database-backed sliding-window quota enforcement), `QueuedEnvelope` (internal DTO for the routing queue).

### **2.5 db**

MySQL data access via HikariCP connection pooling and Flyway migrations.

Key classes: `EnvelopeDAO` (insert/fetch/markDelivered/deleteExpired for `message_envelopes`), `UserDAO`, `ContactDAO`, `SessionDAO`, `FileUploadDAO`, `AttachmentDAO`. All use `PreparedStatement` — no plaintext payload is ever read or logged.



### **2.6 config**

Server configuration sourced exclusively from environment variables — fail-fast if mandatory variables are absent.

Key class: `ServerConfig` — exposes typed accessors for DB credentials, TLS keystore path/password, HTTP/WS ports, max message bytes, and log level.

### **2.7 metrics**

Observability and compliance layer.

Key classes: `AuditLogger` (Log4j2-based structured JSON logging for all audit events), `MetricsRegistry` (in-memory atomic counters for ingress rate, reject codes, queue depth, and delivery latency).

### **2.8 exceptions**

Typed server exceptions for clear error propagation.

Key classes: `ConfigurationException`, `DatabaseOperationException`, `RateLimitException`, `StartupException`.

---

## **3. shared**

Contains code used by both client and server to avoid duplication.



### **3.1 dto**

Data Transfer Objects sent over the network.

**Example: `MessageDTO.java`**

```java
package com.haf.shared.dto;

import java.io.Serializable;

public class MessageDTO implements Serializable {
    public String sender;
    public String content;
    public String timestamp;
}
```



### **3.2 constants**

Shared configuration values.

**Example: `Constants.java`**

```java
package com.haf.shared.constants;

public class Constants {
    public static final int SERVER_PORT = 5000;
    public static final String AES_ALGORITHM = "AES";
}
```



### **3.3 utils**

Shared utilities like serialization and encoding.

**Example: `Serializer.java`**

```java
package com.haf.shared.utils;

import java.io.*;

public class Serializer {
    public static byte[] toBytes(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }
}
```

---

## **4. docs**

Contains documentation for the project.

* `README.md` → main project description
* `architecture-diagram.png` → system diagram (client ↔ server ↔ database)
