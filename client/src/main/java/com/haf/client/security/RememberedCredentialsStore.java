package com.haf.client.security;

import java.util.Locale;
import java.util.Objects;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preferences-backed remembered-credentials store with password data delegated
 * to an OS secure vault.
 */
public final class RememberedCredentialsStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(RememberedCredentialsStore.class);

    static final String PREF_REMEMBER_ME = "remember_me";
    static final String PREF_REMEMBERED_EMAIL = "remembered_email";
    static final String SERVICE_NAME = "HAFMessenger";

    private final Preferences preferences;
    private final SecurePasswordVault passwordVault;

    /**
     * Creates a remembered-credentials store using the given preferences node and
     * password vault.
     *
     * @param preferences preferences node that stores remember flags and email
     * @param passwordVault secure vault used for password read/write/delete
     */
    public RememberedCredentialsStore(Preferences preferences, SecurePasswordVault passwordVault) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.passwordVault = Objects.requireNonNull(passwordVault, "passwordVault");
    }

    /**
     * Creates a store with OS-specific default vault selection.
     *
     * @param preferences preferences node that stores remember flags and email
     * @return configured remembered-credentials store
     */
    public static RememberedCredentialsStore createDefault(Preferences preferences) {
        return new RememberedCredentialsStore(preferences, createDefaultVault());
    }

    /**
     * Loads remembered email when remember-credentials is enabled.
     *
     * @return remembered email, or empty when disabled/missing
     */
    public String loadRememberedEmail() {
        if (!isRememberCredentialsEnabled()) {
            return "";
        }
        return normalizeEmail(preferences.get(PREF_REMEMBERED_EMAIL, ""));
    }

    /**
     * Loads remembered password from secure storage for the provided email.
     *
     * @param email remembered account email
     * @return remembered password, or empty when unavailable
     */
    public String loadRememberedPassword(String email) {
        if (!isRememberCredentialsEnabled()) {
            return "";
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || !passwordVault.isAvailable()) {
            return "";
        }
        try {
            return passwordVault.loadPassword(normalizeAccountKey(normalizedEmail)).orElse("");
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to load remembered password from secure vault: {}", ex.getMessage());
            return "";
        }
    }

    /**
     * Persists remember-credentials state after a successful login attempt.
     *
     * @param rememberEnabled whether remember-credentials is enabled
     * @param email           current login email
     * @param password        current login password
     */
    public void persistRememberedCredentials(boolean rememberEnabled, String email, String password) {
        String previousEmail = normalizeEmail(preferences.get(PREF_REMEMBERED_EMAIL, ""));
        String currentEmail = normalizeEmail(email);

        if (!rememberEnabled) {
            preferences.putBoolean(PREF_REMEMBER_ME, false);
            preferences.remove(PREF_REMEMBERED_EMAIL);
            deleteVaultPassword(previousEmail);
            if (!previousEmail.equalsIgnoreCase(currentEmail)) {
                deleteVaultPassword(currentEmail);
            }
            return;
        }

        preferences.putBoolean(PREF_REMEMBER_ME, true);
        preferences.put(PREF_REMEMBERED_EMAIL, currentEmail);

        if (!previousEmail.isBlank() && !previousEmail.equalsIgnoreCase(currentEmail)) {
            deleteVaultPassword(previousEmail);
        }

        if (currentEmail.isBlank()) {
            return;
        }

        String normalizedPassword = normalizePassword(password);
        if (normalizedPassword.isBlank()) {
            deleteVaultPassword(currentEmail);
            return;
        }

        saveVaultPassword(currentEmail, normalizedPassword);
    }

    /**
     * Updates remember-credentials enablement and clears stored data when
     * disabled.
     *
     * @param enabled desired remember-credentials state
     */
    public void setRememberCredentialsEnabled(boolean enabled) {
        preferences.putBoolean(PREF_REMEMBER_ME, enabled);
        if (enabled) {
            return;
        }

        String previousEmail = normalizeEmail(preferences.get(PREF_REMEMBERED_EMAIL, ""));
        preferences.remove(PREF_REMEMBERED_EMAIL);
        deleteVaultPassword(previousEmail);
    }

    /**
     * Reads whether remember-credentials is currently enabled.
     *
     * @return {@code true} when credential persistence is enabled
     */
    public boolean isRememberCredentialsEnabled() {
        return preferences.getBoolean(PREF_REMEMBER_ME, false);
    }

    /**
     * Resolves an OS-specific secure password vault implementation.
     *
     * @return vault suitable for the current host platform
     */
    private static SecurePasswordVault createDefaultVault() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            return new WindowsCredentialManagerPasswordVault(SERVICE_NAME);
        }
        if (osName.contains("mac") || osName.contains("darwin")) {
            return new MacOsKeychainPasswordVault(SERVICE_NAME);
        }
        if (osName.contains("linux")) {
            return new LinuxSecretToolPasswordVault(SERVICE_NAME);
        }
        return new UnsupportedPasswordVault();
    }

    /**
     * Attempts to save a remembered password in secure storage.
     *
     * @param email    remembered email
     * @param password plaintext password to store
     */
    private void saveVaultPassword(String email, String password) {
        if (!passwordVault.isAvailable()) {
            return;
        }
        try {
            boolean saved = passwordVault.savePassword(normalizeAccountKey(email), password);
            if (!saved) {
                LOGGER.warn("Secure vault password save failed for remembered credentials.");
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to save remembered password in secure vault: {}", ex.getMessage());
        }
    }

    /**
     * Attempts to delete a remembered password from secure storage.
     *
     * @param email remembered email
     */
    private void deleteVaultPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        if (!passwordVault.isAvailable()) {
            return;
        }
        try {
            boolean deleted = passwordVault.deletePassword(normalizeAccountKey(email));
            if (!deleted) {
                LOGGER.warn("Secure vault password delete failed for remembered credentials.");
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to delete remembered password from secure vault: {}", ex.getMessage());
        }
    }

    /**
     * Normalizes an email string for preference persistence.
     *
     * @param email source email
     * @return trimmed email, or empty string when null
     */
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim();
    }

    /**
     * Normalizes a password value for vault persistence.
     *
     * @param password source password
     * @return original password, or empty string when null
     */
    private static String normalizePassword(String password) {
        return password == null ? "" : password;
    }

    /**
     * Converts an email into a canonical account key for vault operations.
     *
     * @param email source email
     * @return lowercase normalized account key
     */
    private static String normalizeAccountKey(String email) {
        return normalizeEmail(email).toLowerCase(Locale.ROOT);
    }
}
