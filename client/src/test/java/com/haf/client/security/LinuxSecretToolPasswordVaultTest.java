package com.haf.client.security;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxSecretToolPasswordVaultTest {

    @Test
    void secret_tool_commands_succeed_when_tool_is_available() {
        QueueRunner runner = new QueueRunner(List.of(
                new LinuxSecretToolPasswordVault.CommandResult(1, "", "usage", false),
                new LinuxSecretToolPasswordVault.CommandResult(0, "", "", false),
                new LinuxSecretToolPasswordVault.CommandResult(0, "secret123\n", "", false),
                new LinuxSecretToolPasswordVault.CommandResult(0, "", "", false)));

        LinuxSecretToolPasswordVault vault = new LinuxSecretToolPasswordVault("HAFMessenger", runner, true);

        assertTrue(vault.isAvailable());
        assertTrue(vault.savePassword("pilot@haf.gr", "secret123"));
        assertEquals(Optional.of("secret123"), vault.loadPassword("pilot@haf.gr"));
        assertTrue(vault.deletePassword("pilot@haf.gr"));
    }

    @Test
    void secret_tool_vault_reports_unavailable_when_command_missing() {
        QueueRunner runner = new QueueRunner(List.of(
                new LinuxSecretToolPasswordVault.CommandResult(-1, "", "not found", true)));

        LinuxSecretToolPasswordVault vault = new LinuxSecretToolPasswordVault("HAFMessenger", runner, true);

        assertFalse(vault.isAvailable());
        assertFalse(vault.savePassword("pilot@haf.gr", "secret"));
        assertEquals(Optional.empty(), vault.loadPassword("pilot@haf.gr"));
        assertFalse(vault.deletePassword("pilot@haf.gr"));
    }

    @Test
    void secret_tool_handles_non_zero_operation_exit_codes() {
        QueueRunner runner = new QueueRunner(List.of(
                new LinuxSecretToolPasswordVault.CommandResult(1, "", "usage", false),
                new LinuxSecretToolPasswordVault.CommandResult(2, "", "store failed", false),
                new LinuxSecretToolPasswordVault.CommandResult(1, "", "not found", false),
                new LinuxSecretToolPasswordVault.CommandResult(1, "", "No matching secret item found", false)));

        LinuxSecretToolPasswordVault vault = new LinuxSecretToolPasswordVault("HAFMessenger", runner, true);

        assertTrue(vault.isAvailable());
        assertFalse(vault.savePassword("pilot@haf.gr", "secret"));
        assertEquals(Optional.empty(), vault.loadPassword("pilot@haf.gr"));
        assertTrue(vault.deletePassword("pilot@haf.gr"));
    }

    private static final class QueueRunner implements LinuxSecretToolPasswordVault.CommandExecutor {

        private final Queue<LinuxSecretToolPasswordVault.CommandResult> results;

        QueueRunner(List<LinuxSecretToolPasswordVault.CommandResult> results) {
            this.results = new ArrayDeque<>(results);
        }

        @Override
        public LinuxSecretToolPasswordVault.CommandResult run(List<String> command, String stdin) {
            if (results.isEmpty()) {
                return new LinuxSecretToolPasswordVault.CommandResult(1, "", "unexpected command", false);
            }
            return results.remove();
        }
    }
}
