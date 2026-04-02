package com.haf.client.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowsCredentialManagerPasswordVaultTest {

    @Test
    void windows_vault_reads_and_writes_through_credential_api() {
        FakeWindowsCredentialApi api = new FakeWindowsCredentialApi();
        WindowsCredentialManagerPasswordVault vault =
                new WindowsCredentialManagerPasswordVault("HAFMessenger", api, true);

        assertTrue(vault.isAvailable());
        assertTrue(vault.savePassword("Pilot@haf.gr", "secret123"));
        assertEquals(Optional.of("secret123"), vault.loadPassword("pilot@haf.gr"));
        assertTrue(vault.deletePassword("pilot@haf.gr"));
        assertEquals(Optional.empty(), vault.loadPassword("pilot@haf.gr"));

        assertEquals("HAFMessenger:pilot@haf.gr", api.lastTargetName);
    }

    @Test
    void windows_vault_is_disabled_when_platform_or_api_is_unavailable() {
        FakeWindowsCredentialApi api = new FakeWindowsCredentialApi();
        api.supported = false;

        WindowsCredentialManagerPasswordVault notSupportedVault =
                new WindowsCredentialManagerPasswordVault("HAFMessenger", api, true);
        assertFalse(notSupportedVault.isAvailable());
        assertFalse(notSupportedVault.savePassword("pilot@haf.gr", "secret"));
        assertEquals(Optional.empty(), notSupportedVault.loadPassword("pilot@haf.gr"));

        WindowsCredentialManagerPasswordVault nonWindowsVault =
                new WindowsCredentialManagerPasswordVault("HAFMessenger", new FakeWindowsCredentialApi(), false);
        assertFalse(nonWindowsVault.isAvailable());
        assertFalse(nonWindowsVault.deletePassword("pilot@haf.gr"));
    }

    @Test
    void windows_vault_surfaces_api_failures_as_false_or_empty() {
        FakeWindowsCredentialApi api = new FakeWindowsCredentialApi();
        api.writeSucceeds = false;
        api.deleteSucceeds = false;

        WindowsCredentialManagerPasswordVault vault =
                new WindowsCredentialManagerPasswordVault("HAFMessenger", api, true);

        assertFalse(vault.savePassword("pilot@haf.gr", "secret"));
        assertEquals(Optional.empty(), vault.loadPassword("pilot@haf.gr"));
        assertFalse(vault.deletePassword("pilot@haf.gr"));
    }

    private static final class FakeWindowsCredentialApi implements WindowsCredentialManagerPasswordVault.WindowsCredentialApi {

        private final Map<String, String> credentialsByTarget = new HashMap<>();
        private boolean supported = true;
        private boolean writeSucceeds = true;
        private boolean deleteSucceeds = true;
        private String lastTargetName;

        @Override
        public boolean isSupported() {
            return supported;
        }

        @Override
        public boolean writeGenericCredential(String targetName, String userName, String secret) {
            lastTargetName = targetName;
            if (!writeSucceeds) {
                return false;
            }
            credentialsByTarget.put(targetName, secret);
            return true;
        }

        @Override
        public Optional<String> readGenericCredential(String targetName) {
            lastTargetName = targetName;
            return Optional.ofNullable(credentialsByTarget.get(targetName));
        }

        @Override
        public boolean deleteGenericCredential(String targetName) {
            lastTargetName = targetName;
            if (!deleteSucceeds) {
                return false;
            }
            credentialsByTarget.remove(targetName);
            return true;
        }
    }
}
