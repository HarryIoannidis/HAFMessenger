### Purpose
- Describes using FilePerms to enforce 700/600 permissions on the root and keychain files, regardless of UMask/OS.

### Rules
- Folders: 700 with ensureDir700(Path).
- Files: 600 with writeFile600(Path, byte[]).
- Windows: ignore POSIX set, rely on ACLs; it doesn't fail.

### Feeds
- Root: ensureDir700(preferred) → in AccessDenied → ensureDir700(userFallback).
- Key set: ensureDir700(keyDir) → writeFile600(public.pem) → writeFile600(private.enc) → writeFile600(metadata.json).

### API
- FilePerms.ensureDir700(Path dir): creates/hardens folder to 700.
- FilePerms.writeFile600(Path file, byte[] data): creates/writes file to 600.

### Tests
- Verify that root dir is drwx------ and the -rw files------- in Unix, idempotent on the second run.