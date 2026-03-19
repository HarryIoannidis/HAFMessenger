# TESTING
Testing ensures protocol stability, end‑to‑end encryption discipline, and operational readiness by validating each layer: shared contracts, client logic/UI, and server policies under TLS/WebSocket, with deterministic, reproducible procedures and artifacts for review.

---

## Test types
- Unit tests: Fast, isolated logic for DTOs, serializers, crypto helpers, ViewModels, and security utilities; no network or UI.
- Integration tests: Cross‑module behavior such as TLS/WebSocket handshake, handler–DB flow, persistence with TTL, and certificate pinning; runs under Maven Failsafe during verify.
- UI tests (headless): JavaFX bindings and controller wiring using lightweight test FXML under headless rendering; logic remains in ViewModels to keep controllers thin.
- Security/performance checks: TLS policy, crypto invariants, brute‑force thresholds, and throughput/backpressure; executed as unit/IT depending on scope.

---

## Full test tree (folders only)
```
haf-messenger/
├── client/
│   └── src/
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
├── server/
│   └── src/
│       └── test/
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
│               ├── config/
│               │   └── certificates/
│               └── db/
│                   └── migration/
├── shared/
│   └── src/
│       └── test/
│           ├── java/
│           │   └── com/haf/
│           │       ├── integration_test/
│           │       └── shared/
│           │           ├── constants/
│           │           ├── crypto/
│           │           ├── exceptions/
│           │           ├── keystore/           
│           │           ├── dto/
│           │           └── utils/
│           └── resources/
│               └── vectors/
├── scripts/  
└── docs/
```


---

## How testing works (concept to execution)
- Design: Define feature impact on protocol (DTO fields and flags), security (auth, E2E, audit), and UI; write this as a short spec under docs to anchor tests and code.
- Contracts first: Implement/update DTOs and constants in shared and write unit tests to lock the wire format before client/server work; prevents divergence between modules.
- Server policies: Add integration tests that boot a test server with TLS 1.3, pinned certificates, and test DB; validate handlers, routing, TTL, and auditing without ever decrypting content.
- Client logic/UI: Add ViewModel unit tests for validation and state; add headless UI tests for bindings using test FXML; keep controllers glue‑only for maintainable, testable UI.
- Execution: Run unit tests via Surefire in test phase and integration/UI tests via Failsafe in verify phase; inspect reports under target/*-reports for pass/fail and stack traces.

---

## Practical workflow to add a test for a feature
- Plan
  - Identify modules impacted: shared (DTO), client (ViewModel/UI), server (handler/DB); define acceptance criteria and negative cases in a short checklist in docs.
- Write tests
  - Shared: Add DTO round‑trip and bounds tests under shared/src/test/java; add crypto vectors if cryptographic helpers change.
  - Server: Add an IT under server/src/test/java that boots the server with test config and performs the end‑to‑end route; assert encrypted blob storage and TTL.
  - Client: Add ViewModel unit tests and a headless UI test under client/src/test/java and client/src/test/resources/test-fxml; use test keystores for TLS.
- Implement feature
  - Update code to satisfy tests, maintaining E2E (client encrypts; server never decrypts); keep all secrets out of repo and generate test keystores locally/CI.
- Run and verify
  - Unit only (fast): mvn -q -am -pl shared,client,server test; check target/surefire-reports per module for Failures: 0.
  - Full suite: mvn -q -am -pl server,client verify; check target/failsafe-reports per module for 0 failures/errors; collect artifacts in CI.

---

## Commands to run tests and check results
- Run unit tests for all modules:  
  - mvn -q -am -pl shared,client,server test and check each module’s target/surefire-reports for .txt/.xml and “Failures: 0”.
- Run integration + UI tests:  
  - mvn -q -am -pl server,client verify and check target/failsafe-reports for “Tests run: … Failures: 0, Errors: 0”; UI runs headless via JavaFX settings in POM.
- Typical artifacts to inspect:  
  - target/surefire-reports and target/failsafe-reports for results and stack traces, plus any screenshots/logs captured by test harnesses; CI must archive them on failure for AARs.

---

## Example: “Send Encrypted Message” feature
This example shows the intent, test design, and representative code to add tests, run them, and evaluate success.

- Intent
  - When user sends a message, the client must AES‑256‑GCM encrypt content and derive key via X25519 ECDH; the server must accept the DTO, store only the opaque ciphertext blob with TTL, and never decrypt content; UI shows sent status upon server ACK.

- Tests to add
  - Shared unit: MessageDTO serialize/deserialize, size limits, and required fields; CryptoUtils AES‑GCM vector.
  - Server IT: Start server with TLS 1.3 and test CA, POST/route DTO over WebSocket/TCP, assert DB contains only ciphertext and TTL deletes on time; audit log entry exists.
  - Client unit: ChatViewModel validates non‑empty content and disabled “Send” until valid; crypto envelope built before send.
  - Client UI: Headless FXML binds text field to ViewModel; “Send” disabled until valid; status label updates after ACK.

- Representative test code (copy‑paste ready)

```java
// shared/src/test/java/com/haf/shared/utils/CryptoUtilsTest.java
package com.haf.shared.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.HexFormat;

public class CryptoUtilsTest {
    @Test
    void aes256Gcm_known_answer() {
        byte[] key = HexFormat.of().parseHex("603deb1015ca71be2b73aef0857d7781"
                                            + "1f352c073b6108d72d9810a30914dff4");
        byte[] iv  = HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9fafb");
        byte[] pt  = HexFormat.of().parseHex("6bc1bee22e409f96e93d7e117393172a");
        CryptoResult out = CryptoUtils.aes256GcmEncrypt(pt, key, iv);
        assertEquals(16, out.tag().length);
        byte[] dec = CryptoUtils.aes256GcmDecrypt(out.ciphertext(), key, iv, out.tag());
        assertArrayEquals(pt, dec);
    }
}
```


```java
// server/src/test/java/com/haf/server/handlers/MessageRoutingIT.java
package com.haf.server.handlers;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class MessageRoutingIT {
    static TestServer server;

    @BeforeAll
    static void start() {
        server = TestServer.start("src/test/resources/config/server-test.yaml");
    }
    @AfterAll
    static void stop() { server.stop(); }

    @Test
    void storesOpaqueCiphertextAndTTL() {
        byte[] ciphertext = new byte[]{1,2,3}; // fixture
        TestClient c = server.newTlsClient();
        String id = c.sendEncrypted("u1","u2", ciphertext, 5);
        DbRow row = server.db().getMessage(id);
        assertArrayEquals(ciphertext, row.contentBlob());
        assertTrue(row.meta().contains("E2E=true"));
        TestAwait.awaitTrue(() -> server.db().getMessage(id) == null, 10_000);
    }
}
```


```java
// client/src/test/java/com/haf/client/viewmodel/ChatViewModelTest.java
package com.haf.client.viewmodel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ChatViewModelTest {

    static class FakeCrypto implements CryptoGateway {
        @Override public byte[] encrypt(byte[] p) { return new byte[]{9,9,9}; }
    }
    static class FakeNet implements ChatGateway {
        @Override public boolean send(byte[] e2e) { return e2e.length == 3; }
    }

    @Test
    void sendDisabledUntilValid_thenEncryptsBeforeSend() {
        ChatViewModel vm = new ChatViewModel(new FakeCrypto(), new FakeNet());
        assertFalse(vm.sendEnabledProperty().get());
        vm.messageTextProperty().set("");
        assertFalse(vm.sendEnabledProperty().get());
        vm.messageTextProperty().set("hello");
        assertTrue(vm.sendEnabledProperty().get());
        vm.sendCommand().run();
        assertEquals("SENT", vm.statusProperty().get());
    }
}
```


- How to run and check
  - Execute unit tests: mvn -q -am -pl shared,client,server test and verify “Failures: 0” under each module’s target/surefire-reports.
  - Execute integration/UI: mvn -q -am -pl server,client verify and verify “Failures: 0, Errors: 0” under target/failsafe-reports for both modules; UI runs headless using configured JavaFX flags.

---

## Best practices
- Contracts first: shared tests precede client/server to avoid drift and rework; any DTO change must be unit‑tested in shared.
- E2E principle: enforce “server never decrypts” with an explicit negative test; treat any plaintext handling on server as a blocking failure.
- Headless UI: keep controllers free of business logic; bind to ViewModels and test properties, not pixels; store test FXML in test resources.
- Secrets hygiene: generate test keystores per run in CI and keep them under test resources for local; never commit production certs or passwords.
