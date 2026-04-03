# BOOTSTRAP FLOW ANALYSIS

This document provides a deep-dive technical breakdown of the Startup/Bootstrap orchestrations for the HAFMessenger client, primarily localized in `SplashController.java` and `SplashViewModel.java`.

## 1. The Threading & UX Engine

When the application binary launches, JavaFX builds the initial `SPLASH` scene.

- Immediately, `SplashController` binds the view's progress bar, percentage text, status text, and version labels bidirectionally to `SplashViewModel`.
- A daemon thread (`splash-bootstrap`) is spawned via a `Task<Void>` executor. This ensures heavy I/O, crypto generation, and networking block in the background without freezing the UI thread.
- **Artificial Jitter:** To ensure the splash screen doesn't "stutter" or flash past the user too quickly on fast machines, the execution pipeline intentionally suspends between steps with a `delay()` helper. This helper injects random jitters (± 100 milliseconds) into a baseline sleep timer to mimic organic, smooth pacing.

## 2. The Four Stages of Bootstrapping

The background task executes 4 sequential gating steps. If any step fails, the bootstrap immediately aborts.

### Stage A: Version ConfigLoader (0% → 10%)

- It attempts to dynamically deduce the software version to display on the splash screen.
- It cascades through three fallbacks: it first probes the compiled `.jar` Manifest via JVM reflection (`getPackage().getImplementationVersion()`). If absent, it reads the OS environment variable `HAF_APP_VERSION`. If all else fails, it injects `"1.0.0"`.

### Stage B: Cryptographic Pre-flight (10% → 30%)

- Before even trying to connect to a server, the `SplashViewModel` validates that the localized JVM cryptography provider isn't crippled or operating on a depleted entropy pool—which would cause catastrophic security failures during live chat.
- It tests four critical primitives:
  1. **Entropy:** Forces `SecureRandom.getInstanceStrong()` to dump 16 bytes.
  2. **Symmetric:** Validates `AES/GCM/NoPadding` initialization.
  3. **Key Agreement:** Validates `X25519` is supported for Elliptic Curve Diffie-Hellman logic.
  4. **Hashing:** Validates `SHA-256`.

### Stage C: Resource Payload Checker (30% → 60%)

- HAFMessenger ensures the application package wasn't corrupted during download or unpacking.
- The `ResourceChecker` iterates a hardcoded "bill of materials". It executes dozens of internal `class.getResource()` lookups against `UiConstants`, explicitly validating the physical bytecode/URL presence of every necessary component.
- This mandates that over 12 FXML files, 10 CSS sheets, core structural SVGs/Images, the bespoke `Manrope` fonts, and exactly 15 military rank insignias (from *Yposminias* to *Pterarchos*) are physically readable. If one single icon throws a `null`, it halts.

### Stage D: Backend Network Probe (60% → 80%)

- The JVM loads `ClientRuntimeConfig` from `client.properties` and resolves the health-check base URI by runtime mode.
- In dev mode, health URL resolution supports `server.url` in properties, then `-Dhaf.server.url`, then `HAF_SERVER_URL` (fallback `https://localhost:8443`).
- In prod mode, health URL resolution uses `server.url.prod` with strict HTTPS validation.
- It constructs a Java 11 `HttpClient` with mode-aware SSL context (`SslContextUtils.getSslContextForMode(...)`) and tight boundaries: 2 seconds connection timeout, 3 seconds response timeout.
- Instead of a full `GET`, it fires a lightweight HTTP `HEAD` request to `[SERVER]/api/v1/health`.
- **Retries:** It allows exactly `3` attempts with a 500-millisecond backoff. If it receives a 400+ status code or timeout, it fails completely.

## 3. Transition or Fault Recovery (80% → 100%)

If the sequence ends flawlessly, the progress bar hits 100%, pauses for a final 200 milliseconds to render visually, and fires a callback to the `SplashController`. It invokes `ViewRouter.switchToTransparent(UiConstants.FXML_LOGIN)` discarding the splash stage entirely.

**If the bootstrap throws an Exception:**

1. The Task terminates to `BOOTSTRAP_FAILED`.
2. The `SplashController` intercepts the raw Exception and executes `rootCause()`, stripping away wrapper throwables to extract the core system error.
3. String-matching parsing (`classifyFailure()`) filters the technical Java stack trace into an understandable diagnostic failure condition for the user:
   - Identifies timeouts/host exceptions as: **"Cannot reach server"**
   - Identifies missing FXML/assets as: **"Application files missing"**
   - Identifies algorithm exceptions as: **"Security initialization failed"**
4. A customized alert `PopupMessageBuilder` overlays on the Splash screen showing the error reason, pinning the app in a controlled state, and trapping the user into exactly two options: "**Retry**" (restarts the background task blindly) or "**Exit**" (safely kills the JVM).
