package com.haf.client.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RememberedCredentialsStoreTest {

    private Preferences testNode;

    @AfterEach
    void cleanupPreferences() throws Exception {
        if (testNode != null) {
            testNode.removeNode();
            testNode.flush();
        }
    }

    @Test
    void persist_enabled_saves_email_and_password() {
        Preferences prefs = newTestNode();
        FakeVault vault = new FakeVault();
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);

        store.persistRememberedCredentials(true, "Pilot@haf.gr", "secret123");

        assertTrue(prefs.getBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, false));
        assertEquals("Pilot@haf.gr", prefs.get(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, ""));
        assertEquals("secret123", vault.passwords.get("pilot@haf.gr"));
        assertTrue(vault.deletedAccounts.isEmpty());
    }

    @Test
    void persist_disabled_clears_preferences_and_deletes_old_credentials() {
        Preferences prefs = newTestNode();
        prefs.putBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, true);
        prefs.put(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, "old@haf.gr");

        FakeVault vault = new FakeVault();
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);

        store.persistRememberedCredentials(false, "new@haf.gr", "ignored");

        assertFalse(prefs.getBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, true));
        assertEquals("", prefs.get(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, ""));
        assertTrue(vault.deletedAccounts.contains("old@haf.gr"));
        assertTrue(vault.deletedAccounts.contains("new@haf.gr"));
    }

    @Test
    void persist_enabled_rotates_password_when_email_changes() {
        Preferences prefs = newTestNode();
        prefs.putBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, true);
        prefs.put(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, "first@haf.gr");

        FakeVault vault = new FakeVault();
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);

        store.persistRememberedCredentials(true, "second@haf.gr", "newpass");

        assertEquals("second@haf.gr", prefs.get(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, ""));
        assertTrue(vault.deletedAccounts.contains("first@haf.gr"));
        assertEquals("newpass", vault.passwords.get("second@haf.gr"));
    }

    @Test
    void load_enabled_returns_prefilled_credentials() {
        Preferences prefs = newTestNode();
        prefs.putBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, true);
        prefs.put(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, "pilot@haf.gr");

        FakeVault vault = new FakeVault();
        vault.passwords.put("pilot@haf.gr", "secret123");
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);

        String email = store.loadRememberedEmail();
        String password = store.loadRememberedPassword(email);

        assertTrue(store.isRememberCredentialsEnabled());
        assertEquals("pilot@haf.gr", email);
        assertEquals("secret123", password);
    }

    @Test
    void load_enabled_returns_email_only_when_password_missing() {
        Preferences prefs = newTestNode();
        prefs.putBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, true);
        prefs.put(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, "pilot@haf.gr");

        FakeVault vault = new FakeVault();
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);

        String email = store.loadRememberedEmail();
        String password = store.loadRememberedPassword(email);

        assertTrue(store.isRememberCredentialsEnabled());
        assertEquals("pilot@haf.gr", email);
        assertEquals("", password);
    }

    @Test
    void setRememberDisabled_clears_email_and_vault_entry() {
        Preferences prefs = newTestNode();
        prefs.putBoolean(RememberedCredentialsStore.PREF_REMEMBER_ME, true);
        prefs.put(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, "pilot@haf.gr");

        FakeVault vault = new FakeVault();
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);

        store.setRememberCredentialsEnabled(false);

        assertFalse(store.isRememberCredentialsEnabled());
        assertEquals("", prefs.get(RememberedCredentialsStore.PREF_REMEMBERED_EMAIL, ""));
        assertTrue(vault.deletedAccounts.contains("pilot@haf.gr"));
    }

    private Preferences newTestNode() {
        testNode = Preferences.userRoot().node("/com/haf/client/test/remembered-" + UUID.randomUUID());
        return testNode;
    }

    private static final class FakeVault implements SecurePasswordVault {

        private final Map<String, String> passwords = new HashMap<>();
        private final List<String> deletedAccounts = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public boolean savePassword(String accountKey, String password) {
            passwords.put(accountKey, password);
            return true;
        }

        @Override
        public Optional<String> loadPassword(String accountKey) {
            return Optional.ofNullable(passwords.get(accountKey));
        }

        @Override
        public boolean deletePassword(String accountKey) {
            deletedAccounts.add(accountKey);
            passwords.remove(accountKey);
            return true;
        }
    }
}
