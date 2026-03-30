# STRUCTURE

## Purpose

Describe the real repository structure and module boundaries used in this codebase.

## Current Implementation

```text
HAFMessenger/
  client/
    src/main/java
    src/main/resources
    src/test/java
  server/
    src/main/java
    src/main/resources
    src/test/java
  shared/
    src/main/java
    src/test/java
  docs/
    client/
    server/
    shared/
    misc/
  pom.xml
  mvnw
  mvnw.cmd
```

## Key Types/Interfaces

- Client packages: `controllers`, `viewmodels`, `services`, `network`, `crypto`, `utils`, `models`, `core`, `exceptions`.
- Server packages: `config`, `core`, `ingress`, `router`, `db`, `handlers`, `metrics`, `exceptions`.
- Shared packages: `dto`, `requests`, `responses`, `constants`, `crypto`, `keystore`, `utils`, `exceptions`.

## Flow

1. Parent Maven POM builds `shared`, then `client` and `server`.
2. `client` and `server` consume shared contracts.
3. Runtime flow is client UI/network -> server ingress/router/dao -> client receive/decrypt.

## Error/Security Notes

- There is no `scripts/` directory in current repo state; docs should not assume it exists.
- Keep dependency direction strict (`client/server` depend on `shared`; not vice versa).

## Related Files

- `pom.xml`
- `client/pom.xml`
- `server/pom.xml`
- `shared/pom.xml`
- `client/src/main/java/module-info.java`
- `server/src/main/java/module-info.java`
- `shared/src/main/java/module-info.java`
