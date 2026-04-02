package com.haf.client.security;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MacOsKeychainPasswordVaultTest {

    @Test
    void keychain_commands_succeed_when_tool_is_available() {
        QueueRunner runner = new QueueRunner(List.of(
                new MacOsKeychainPasswordVault.CommandResult(1, "", "usage", false),
                new MacOsKeychainPasswordVault.CommandResult(0, "", "", false),
                new MacOsKeychainPasswordVault.CommandResult(0, "secret123\n", "", false),
                new MacOsKeychainPasswordVault.CommandResult(0, "", "", false)));

        MacOsKeychainPasswordVault vault = new MacOsKeychainPasswordVault("HAFMessenger", runner, true);

        assertTrue(vault.isAvailable());
        assertTrue(vault.savePassword("pilot@haf.gr", "secret123"));
        assertEquals(Optional.of("secret123"), vault.loadPassword("pilot@haf.gr"));
        assertTrue(vault.deletePassword("pilot@haf.gr"));
    }

    @Test
    void keychain_vault_reports_unavailable_when_command_missing() {
        QueueRunner runner = new QueueRunner(List.of(
                new MacOsKeychainPasswordVault.CommandResult(-1, "", "not found", true)));

        MacOsKeychainPasswordVault vault = new MacOsKeychainPasswordVault("HAFMessenger", runner, true);

        assertFalse(vault.isAvailable());
        assertFalse(vault.savePassword("pilot@haf.gr", "secret"));
        assertEquals(Optional.empty(), vault.loadPassword("pilot@haf.gr"));
        assertFalse(vault.deletePassword("pilot@haf.gr"));
    }

    @Test
    void keychain_handles_non_zero_operation_exit_codes() {
        QueueRunner runner = new QueueRunner(List.of(
                new MacOsKeychainPasswordVault.CommandResult(1, "", "usage", false),
                new MacOsKeychainPasswordVault.CommandResult(1, "", "write failed", false),
                new MacOsKeychainPasswordVault.CommandResult(44, "", "missing", false),
                new MacOsKeychainPasswordVault.CommandResult(44, "", "The specified item could not be found", false)));

        MacOsKeychainPasswordVault vault = new MacOsKeychainPasswordVault("HAFMessenger", runner, true);

        assertTrue(vault.isAvailable());
        assertFalse(vault.savePassword("pilot@haf.gr", "secret"));
        assertEquals(Optional.empty(), vault.loadPassword("pilot@haf.gr"));
        assertTrue(vault.deletePassword("pilot@haf.gr"));
    }

    private static final class QueueRunner implements MacOsKeychainPasswordVault.CommandExecutor {

        private final Queue<MacOsKeychainPasswordVault.CommandResult> results;

        QueueRunner(List<MacOsKeychainPasswordVault.CommandResult> results) {
            this.results = new ArrayDeque<>(results);
        }

        @Override
        public MacOsKeychainPasswordVault.CommandResult run(List<String> command, String stdin) {
            if (results.isEmpty()) {
                return new MacOsKeychainPasswordVault.CommandResult(1, "", "unexpected command", false);
            }
            return results.remove();
        }
    }
}
