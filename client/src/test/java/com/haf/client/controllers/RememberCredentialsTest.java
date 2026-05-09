package com.haf.client.controllers;

import com.haf.client.security.RememberedCredentialsStore;
import com.haf.client.security.SecurePasswordVault;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RememberCredentialsTest {

    private static final String PREF_REMEMBER_ME = "remember_me";
    private static final String PREF_REMEMBERED_EMAIL = "remembered_email";

    private Preferences testNode;

    @AfterEach
    void cleanupPreferences() throws Exception {
        if (testNode != null) {
            testNode.removeNode();
            testNode.flush();
        }
    }

    @Test
    void disabling_remember_credentials_delegates_to_store() throws Exception {
        Preferences prefs = newTestNode();
        prefs.putBoolean(PREF_REMEMBER_ME, true);
        prefs.put(PREF_REMEMBERED_EMAIL, "pilot@haf.gr");

        FakeVault vault = new FakeVault();
        vault.passwords.put("pilot@haf.gr", "secret123");
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, vault);
        SettingsController controller = new SettingsController(store);

        invokePrivateSetRememberEnabled(controller, false);

        assertFalse(store.isRememberCredentialsEnabled());
        assertEquals("", prefs.get(PREF_REMEMBERED_EMAIL, ""));
        assertTrue(vault.deletedAccounts.contains("pilot@haf.gr"));
    }

    @Test
    void remember_enabled_reader_delegates_to_store() throws Exception {
        Preferences prefs = newTestNode();
        prefs.putBoolean(PREF_REMEMBER_ME, true);
        RememberedCredentialsStore store = new RememberedCredentialsStore(prefs, new FakeVault());
        SettingsController controller = new SettingsController(store);

        boolean enabled = invokePrivateIsRememberEnabled(controller);

        assertTrue(enabled);
    }

    private static void invokePrivateSetRememberEnabled(SettingsController controller, boolean enabled)
            throws Exception {
        Method method = SettingsController.class.getDeclaredMethod("setRememberCredentialsEnabled", boolean.class);
        method.setAccessible(true);
        method.invoke(controller, enabled);
    }

    private static boolean invokePrivateIsRememberEnabled(SettingsController controller) throws Exception {
        Method method = SettingsController.class.getDeclaredMethod("isRememberCredentialsEnabled");
        method.setAccessible(true);
        return (Boolean) method.invoke(controller);
    }

    private Preferences newTestNode() {
        testNode = Preferences.userRoot().node("/com/haf/client/test/settings-controller-" + UUID.randomUUID());
        return testNode;
    }

    private static final class FakeVault implements SecurePasswordVault {

        private final Map<String, String> passwords = new HashMap<>();
        private final Set<String> deletedAccounts = new HashSet<>();

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
