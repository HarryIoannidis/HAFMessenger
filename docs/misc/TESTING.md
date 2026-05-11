# TESTING

## Purpose

Document how tests are currently organized and executed for shared, client, and server modules.

## Current Implementation

- Unit tests run with Surefire.
- Integration tests (`*IT`) run with Failsafe.
- Modules with tests:
  - `shared/src/test/java`
  - `client/src/test/java`
  - `server/src/test/java`
- Representative existing tests include crypto, keystore, ingress, routing, DAOs, controllers, and ViewModels.
- Typical commands: `./mvnw test` (unit) and `./mvnw verify` (unit + integration).

## Key Types/Interfaces

- Client examples: `ChatControllerTest`, `SearchViewModelTest`, `AuthHttpClientTest`.
- Server examples: `HttpIngressServerTest`, `MailboxRouterTest`, `RateLimiterServiceTest`, `MessageIngressServiceTest`, `RealtimeWebSocketServerTest`, DAO tests.
- Shared examples: `MessageValidatorTest`, `MessageEncryptorTest`, `UserKeystoreTest`, `AadCodecTest`.

## Flow

1. Fast pass: run unit tests across modules.
2. Full pass: run verify to include IT suites.
3. Inspect `target/surefire-reports` and `target/failsafe-reports` when failures occur.
4. Fix failing module tests locally before cross-module full verify.

## Error/Security Notes

- Test docs should reference real classes in the repo (avoid fictional samples that do not compile).
- Security-critical behavior should include negative-path tests (tamper, expiry, auth failures, rate limits).

## Related Files

- `pom.xml`
- `shared/pom.xml`
- `client/pom.xml`
- `server/pom.xml`
- `shared/src/test/java`
- `client/src/test/java`
- `server/src/test/java`
