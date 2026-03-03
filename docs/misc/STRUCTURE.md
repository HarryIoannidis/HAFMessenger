# STRUCTURE

## **Project Structure**

```
haf-messenger/
├── client/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/haf/client/
│           │       ├── ui/
│           │       ├── controllers/
│           │       ├── viewmodel/
│           │       ├── crypto/
│           │       ├── network/
│           │       ├── models/
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
           │       ├── ui/
           │       ├── controllers/
           │       ├── viewmodel/
           │       ├── crypto/
           │       ├── network/
           │       ├── models/
           │       └── utils/
           └── resources/
               ├── fxml/
               ├── css/
               └── images/
```               

* **ui/**: FXML views and loaders for the JavaFX UI, without business logic.
* **controllers/**: Connects UI with ViewModel, handles events and validation.
* **viewmodel/**: MVVM bindings, business state, and commands for the views.
* **crypto/**: AES-256 content encryption, X25519 key agreement, and key management.
* **network/**: TLS/WebSocket client, sending/receiving packets with E2E encryption.
* **models/**: Object definitions such as User, Message, Session, independent of the UI.
* **utils/**: Logging, configuration, and shared helper functions.
* **resources/fxml/**: JavaFX scenes (e.g., login, main chat).
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
           │       ├── exceptions/
           │       ├── keystore/            
           │       └── utils/
           └── resources/
```

* **dto/**: Common Data Transfer Objects for uniform client–server communication.
* **constants/**: Shared constants (ports, timeouts, protocol flags) for configuration consistency.
* **utils/**: Shared helper functions (encryption, serialization, utilities).
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
│           │       ├── ui/
│           │       ├── controllers/
│           │       ├── viewmodel/
│           │       ├── crypto/
│           │       ├── network/
│           │       ├── models/
│           │       └── utils/
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
```
// shared/src/main/java/module-info.java
module com.haf.shared {
    exports com.haf.shared.dto;
    exports com.haf.shared.constants;
    exports com.haf.shared.utils;
}
```

```java
// client/src/main/java/module-info.java
module com.haf.client {
    requires com.haf.shared;
    requires javafx.controls;
    requires javafx.fxml;

    exports com.haf.client.ui;
    exports com.haf.client.viewmodel;

    opens com.haf.client.controllers to javafx.fxml;
}
```

```java
// server/src/main/java/module-info.java
module com.haf.server {
    requires com.haf.shared;

    exports com.haf.server.core;
    exports com.haf.server.handlers;
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
 -> ViewModel (client/viewmodel) 
 -> Network/Crypto (client/network, client/crypto) 
 -> Handlers (server/handlers via TLS/WebSocket) 
 -> Response DTO (shared/dto) 
 -> ViewModel update 
 -> UI refresh
```
This flow is applied for every feature following the WORKFLOW, ensuring clean boundaries and ease of end‑to‑end testing per feature.

---
# STRUCTURE BREAKDOWN

This document describes the folder structure and purpose of each module in **HAF Messenger**, following an **MVVM (Model–View–ViewModel)** architecture with a secure **Client–Server** design.

## **1. client**

This is the **desktop app** — the JavaFX interface that users interact with.

### **1.1 ui**

Contains FXML controllers — the Java classes handling UI events and interactions.

**Example: `LoginController.java`**

```java
package com.haf.client.ui;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import com.haf.client.viewmodel.LoginViewModel;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    private final LoginViewModel viewModel = new LoginViewModel();

    @FXML
    private void onLoginButtonClick() {
        viewModel.login(usernameField.getText(), passwordField.getText());
    }
}
```

### **1.2 viewmodel**

Implements the **MVVM pattern logic** — connects the UI with the application logic.

**Example: `LoginViewModel.java`**

```java
package com.haf.client.viewmodel;

import com.haf.client.network.ClientConnection;

public class LoginViewModel {
    private final ClientConnection connection = new ClientConnection();

    public void login(String username, String password) {
        connection.sendLogin(username, password);
    }
}
```

### **1.3 crypto**

Handles encryption and decryption on the client side.

**Example: `CryptoManager.java`**

```java
package com.haf.client.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoManager {
    private static final String ALGO = "AES";

    public static String encrypt(String data, String key) throws Exception {
        Cipher c = Cipher.getInstance(ALGO);
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key.getBytes(), ALGO));
        return Base64.getEncoder().encodeToString(c.doFinal(data.getBytes()));
    }
}
```

### **1.4 network**

Handles client-server communication via Sockets or WebSockets.

**Example: `ClientConnection.java`**

```java
package com.haf.client.network;

import java.io.*;
import java.net.Socket;

public class ClientConnection {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public ClientConnection() {
        try {
            socket = new Socket("127.0.0.1", 5000);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendLogin(String user, String pass) {
        out.println("LOGIN:" + user + ":" + pass);
    }
}
```



### **1.5 models**

Plain Java classes representing application data.

**Example: `Message.java`**

```java
package com.haf.client.models;

import java.time.LocalDateTime;

public class Message {
    private String sender;
    private String content;
    private LocalDateTime timestamp;

    // getters and setters
}
```



### **1.6 utils**

Helper classes for configuration, logging, and formatting.

**Example: `ConfigLoader.java`**

```java
package com.haf.client.utils;

import java.util.Properties;

public class ConfigLoader {
    public static Properties loadConfig() {
        // load from resources/config.properties
        return new Properties();
    }
}
```

---

## **2. server**

The **backend** — handles connections, authentication, message routing, and security.



### **2.1 core**

Main server entry point and socket manager.

**Example: `ServerApp.java`**

```java
package com.haf.server.core;

import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {
    public static void main(String[] args) throws Exception {
        ServerSocket server = new ServerSocket(5000);
        System.out.println("Server started on port 5000...");

        while (true) {
            Socket client = server.accept();
            new Thread(new ClientHandler(client)).start();
        }
    }
}
```



### **2.2 handlers**

Manage specific types of requests like login or message handling.

**Example: `LoginHandler.java`**

```java
package com.haf.server.handlers;

import com.haf.server.db.DatabaseManager;

public class LoginHandler {
    public boolean authenticate(String username, String password) {
        return DatabaseManager.checkUser(username, password);
    }
}
```



### **2.3 crypto**

Server-side encryption, key management, and decryption (X25519, AES).



### **2.4 db**

Manages database connections (SQLite/MySQL).

**Example: `DatabaseManager.java`**

```java
package com.haf.server.db;

import java.sql.*;

public class DatabaseManager {
    public static boolean checkUser(String user, String pass) {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:haf.db")) {
            PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE username=? AND password=?");
            ps.setString(1, user);
            ps.setString(2, pass);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }
}
```



### **2.5 security**

Monitors and blocks suspicious behavior (failed logins, brute force).

**Example: `BruteForceProtector.java`**

```java
package com.haf.server.security;

import java.util.HashMap;

public class BruteForceProtector {
    private static final HashMap<String, Integer> attempts = new HashMap<>();

    public static boolean check(String ip) {
        int count = attempts.getOrDefault(ip, 0) + 1;
        attempts.put(ip, count);
        return count < 5;
    }
}
```

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
