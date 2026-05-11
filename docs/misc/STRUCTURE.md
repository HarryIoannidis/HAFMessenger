# STRUCTURE

## Purpose

Describe the actual repository layout and module boundaries used in this codebase.

## Current Implementation

```text
HAFMessenger/
  client/
    pom.xml
    src/main/java/
      module-info.java
      com/haf/client/
        builders/
        controllers/
        core/
        crypto/
        exceptions/
        models/
        network/
        security/
        services/
        utils/
        viewmodels/
    src/main/resources/
      config/
      css/
      fonts/
      fxml/
      images/
    src/test/java/com/haf/client/
      controllers/
      crypto/
      network/
      security/
      services/
      utils/
      viewmodels/

  server/
    pom.xml
    src/main/java/
      module-info.java
      com/haf/server/
        config/
        core/
        db/
        exceptions/
        handlers/
        ingress/
        metrics/
        realtime/
        router/
        security/
    src/main/resources/
      config/
      db/
      log4j2.xml
    src/test/java/com/haf/
      integration_test/
      server/
        config/
        core/
        db/
        handlers/
        ingress/
        metrics/
        realtime/
        router/

  shared/
    pom.xml
    src/main/java/
      module-info.java
      com/haf/shared/
        constants/
        crypto/
        dto/
        exceptions/
        keystore/
        requests/
        responses/
        utils/
        websocket/
    src/test/java/com/haf/shared/
      constants/
      crypto/
      dto/
      exceptions/
      keystore/
      requests/
      responses/
      utils/

  docs/
    client/
      scenes/
    flows/
    misc/
    server/
    shared/

  scripts/
    package-linux-appimage.sh
    package-mac-app.sh
    package-windows-app.ps1

  .mvn/
    wrapper/

  logs/ (runtime output, git-ignored)
  pom.xml
  mvnw
  mvnw.cmd
  README.md
  .gitignore
```

## Key Types/Interfaces

- Client packages: `builders`, `controllers`, `viewmodels`, `services`, `network`, `crypto`, `security`, `utils`, `models`, `core`, `exceptions`.
- Server packages: `config`, `core`, `ingress`, `router`, `db`, `handlers`, `metrics`, `security`, `exceptions`.
- Shared packages: `dto`, `requests`, `responses`, `constants`, `crypto`, `keystore`, `utils`, `exceptions`.

## Flow

1. Parent Maven POM builds `shared`, then `client` and `server`.
2. `client` and `server` consume shared contracts.
3. Runtime flow is client UI/network -> server ingress/router/db -> client receive/decrypt.

## Error/Security Notes

- Keep dependency direction strict (`client/server` depend on `shared`; not vice versa).
- `logs/` is runtime output and should not be committed.

## Related Files

- `pom.xml`
- `client/pom.xml`
- `server/pom.xml`
- `shared/pom.xml`
- `client/src/main/java/module-info.java`
- `server/src/main/java/module-info.java`
- `shared/src/main/java/module-info.java`
- `client/src/main/java/com/haf/client/builders`
- `server/src/main/java/com/haf/server/security/JwtTokenService.java`
- `scripts/package-linux-appimage.sh`
- `scripts/package-mac-app.sh`
- `scripts/package-windows-app.ps1`
- `docs/misc/scripts.md`
- `.gitignore`
