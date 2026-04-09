# REMEMBERED_CREDENTIALS_SECURITY

## Purpose

Document the security model for the new cross-platform remembered-password storage used by client login.

## Current Implementation

- The "Remember Credentials" feature stores:
  - remember-enabled flag (`remember_me`) in Java Preferences
  - remembered email (`remembered_email`) in Java Preferences
  - password only in an OS-backed secure vault abstraction (`SecurePasswordVault`)
- Default vault selection is platform-specific:
  1. Windows: `WindowsPasswordManager` (WinCred via JNA)
  2. macOS: `MacOsKeychainPasswordVault` (`security` CLI + Keychain generic password)
  3. Linux: `LinuxSecretToolPasswordVault` (`secret-tool` + Secret Service)
  4. Other/unsupported: `UnsupportedPasswordVault` (no password persistence)
- Service namespace for vault records is `HAFMessenger`.
- Account keys are normalized (trimmed + lowercased email) before vault read/write/delete.

## Flow

1. User enables/disables "Remember Credentials" from Login/Settings.
2. On successful login, `LoginController.persistRememberedCredentials()` delegates to `RememberedCredentialsStore`.
3. If enabled:
   - remember flag and email are persisted in Preferences
   - password is written to platform vault under normalized account key
   - old vault entry is deleted when email changes
4. If disabled:
   - remember flag is set false
   - remembered email is removed from Preferences
   - vault entries for prior/current email are deleted
5. On app startup/login view load:
   - email is restored from Preferences (when remember is enabled)
   - password is loaded from vault if available; otherwise left empty

## Error/Security Notes

- Password is not persisted in Java Preferences.
- Failures to access secure vault degrade safely to "no password restored" behavior.
- Vault operations are wrapped with exception handling and do not crash login UI.
- Linux/macOS vault adapters enforce command timeouts (3 seconds) to avoid UI hangs from blocked helpers.
- Delete operations treat "not found" as success for idempotent cleanup semantics.
- On unsupported platforms, remembered-password storage is intentionally unavailable (email may still be remembered).
- Known limitation: macOS adapter currently invokes `security ... -w <password>` with password on command arguments; this can expose secret material to local process-inspection tools during command lifetime.

## Key Types/Interfaces

- `com.haf.client.security.RememberedCredentialsStore`
- `com.haf.client.security.SecurePasswordVault`
- `com.haf.client.security.WindowsPasswordManager`
- `com.haf.client.security.MacOsKeychainPasswordVault`
- `com.haf.client.security.LinuxSecretToolPasswordVault`
- `com.haf.client.security.UnsupportedPasswordVault`

## Related Files

- `client/src/main/java/com/haf/client/security/RememberedCredentialsStore.java`
- `client/src/main/java/com/haf/client/security/SecurePasswordVault.java`
- `client/src/main/java/com/haf/client/security/WindowsPasswordManager.java`
- `client/src/main/java/com/haf/client/security/MacOsKeychainPasswordVault.java`
- `client/src/main/java/com/haf/client/security/LinuxSecretToolPasswordVault.java`
- `client/src/main/java/com/haf/client/controllers/LoginController.java`
- `client/src/main/java/com/haf/client/controllers/SettingsController.java`
- `client/src/test/java/com/haf/client/security/RememberedCredentialsStoreTest.java`
- `client/src/test/java/com/haf/client/security/WindowsPasswordManagerTest.java`
- `client/src/test/java/com/haf/client/security/MacOsKeychainPasswordVaultTest.java`
- `client/src/test/java/com/haf/client/security/LinuxSecretToolPasswordVaultTest.java`
