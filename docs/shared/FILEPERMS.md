# FILEPERMS

## Purpose

Document filesystem-permission helpers used by keystore storage.

## Current Implementation

- `FilePerms.ensureDir700(...)` for secure directory creation/hardening.
- `FilePerms.writeFile600(...)` for secure file writes.
- Used by keystore bootstrap and user-key storage operations.
- Non-POSIX fallback verifies write access and raises `AccessDeniedException` when secure access cannot be guaranteed.

## Key Types/Interfaces

- `shared.utils.FilePerms`
- `shared.keystore.KeystoreBootstrap`
- `shared.keystore.UserKeystore`

## Flow

1. Resolve keystore root.
2. Ensure root/key directories with strict permissions.
3. Write key artifacts (`public.pem`, `private.enc`, `metadata.json`) with secure file mode.
4. Re-apply permission hardening on each write/update path.

## Error/Security Notes

- Unix mode targets are 700 (dirs) and 600 (files).
- Platform differences (for example Windows ACL behavior) are handled defensively in utility logic.

## Related Files

- `shared/src/main/java/com/haf/shared/utils/FilePerms.java`
- `shared/src/main/java/com/haf/shared/keystore/UserKeystore.java`
- `shared/src/main/java/com/haf/shared/keystore/KeystoreBootstrap.java`
