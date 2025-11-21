# KEYSTORE_MANAGEMENT.md

### Purpose
- Single path policy, automatic keychain bootstrap and standardized sealing/IO so that dev/IDE works without overrides and production uses system path with strong private encryption.

### Policy
- Priority: JVM 'haf.keystore.root' → ENV 'HAF_KEYSTORE_ROOT' → OS default.
- OS default: Linux `/var/lib/haf/keystore`, Windows `%ProgramData%\HAF\keystore`.
- Fallback: Linux `~/.local/share/haf/keystore`, Windows `%LOCALAPPDATA%\HAF\keystore`.

### Feeds
- Resolve: `KeystoreRoot.preferred()`, ensure 700. In 'AccessDeniedException' → 'KeystoreRoot.userFallback()', ensure 700.
- First-run: if root empty, create '<keyId>/' with 'public.pem', 'private.enc', 'metadata.json'.
- Permissions: 700 folders, 600 files; in Windows ignoring POSIX and using ACLs.

### Encryption
- 'private.enc': AES-GCM 256, IV 12B, tag 128b, key from PBKDF2-HMAC-SHA256 (salt 16B, 200k iter).
- Envelope format: `v1. <salt_b64>. <iv_b64>.iphertext+t+tag_b64>`.
- Pass: taken from 'HAF_KEY_PASS' ENV or fallback '"dev-pass-change"' for dev.

### Keychain IO
- Structure: '<root>/<keyId>/{public.pem, private.enc, metadata.json}'.
- APIs:
    - `KeystoreBootstrap.run()`: auto-resolve root, fallback, first-run.
    - `UserKeyStore.todayKeyId()`: generate keyId (`"key-YYYYMMDD"`).
    - `RsaKeyIO.generate(2048)`: RSA keypair generation.
    - `RsaKeyIO.publicPem(PublicKey)`, `RsaKeyIO.privatePem(PrivateKey)`: PEM encoding.
    - `KeystoreSealing.sealWithPass(char[] pass, byte[] plaintext)`: seal private key.
    - `KeystoreSealing.openWithPass(char[] pass, byte[] envelope)`: unseal private key.

### API policy/bootstrap
- 'KeystoreRoot.preferred()': returns preferred path with priority: JVM prop → ENV → OS default.
- 'KeystoreRoot.userFallback()': returns user-scoped path.
- `KeystoreBootstrap.run()`:
    1. Try `preferred()` + `ensureDir700()`.
    2. Catch `AccessDeniedException` → `userFallback()` + `ensureDir700()`.
    3. 'firstRunIfMissing(root)': if empty, create keypair + metadata.
    4. Return root Path.

### Tests
- Policy: 'preferred()' adheres to JVM/ENV/OS, userFallback()' returns user-scoped path.
- Bootstrap: auto-fallback, generate 700/600, first-run files, idempotency on second run.
- Sealing/IO IT: E2E encrypt with 'public.pem' and decrypt with unsealed private, wrong-pass failure, tamper detection, permissions on Unix.