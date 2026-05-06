# MAIN

## Purpose

Document server bootstrap and shutdown orchestration performed by `Main`.

## Current Implementation

- Startup sequence:
  - load config
  - run Flyway migrations from classpath resources (`db/migration`)
  - build datasource/scheduler
  - initialize services/DAOs/router/ingress
  - start mailbox router + HTTPS ingress
- Scheduled jobs include metrics snapshots and attachment cleanup.
- Scheduler cadence is 60 seconds for metrics snapshots and 300 seconds for expired attachment cleanup.

## Key Types/Interfaces

- `server.core.Main`
- `server.config.ServerConfig`
- `server.router.MailboxRouter`
- `server.ingress.HttpIngressServer`

## Flow

1. `Main.main(...)` calls `start()`.
2. Configuration, migrations, and dependency graph are initialized.
3. Runtime services and ingress endpoints start (`MailboxRouter`, then HTTPS).
4. Shutdown hook closes ingress/router/scheduler/datasource.

## Error/Security Notes

- Any startup failure is logged and wrapped in `StartupException`.
- TLS context is initialized with strict protocol/cipher policy.
- Shutdown path cancels scheduled jobs and attempts graceful close before datasource teardown.

## Related Files

- `server/src/main/java/com/haf/server/core/Main.java`
- `server/src/main/java/com/haf/server/config/ServerConfig.java`
- `server/src/main/resources/db/migration`
