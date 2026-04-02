package com.haf.client.security;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Linux Secret Service-backed password vault using {@code secret-tool}.
 */
final class LinuxSecretToolPasswordVault implements SecurePasswordVault {

    private static final String COMMAND = "secret-tool";
    private static final long COMMAND_TIMEOUT_MS = 3_000L;

    private final String serviceName;
    private final CommandExecutor commandExecutor;
    private final boolean linuxPlatform;
    private volatile Boolean availableCache;

    /**
     * Creates a default Linux secret-tool vault.
     *
     * @param serviceName Secret Service label/service name
     */
    LinuxSecretToolPasswordVault(String serviceName) {
        this(serviceName, LinuxSecretToolPasswordVault::executeCommand, isLinuxPlatform());
    }

    /**
     * Creates a Linux Secret Service vault with injected command execution and
     * platform detection (test seam).
     *
     * @param serviceName     Secret Service label/service name
     * @param commandExecutor command executor used to invoke {@code secret-tool}
     * @param linuxPlatform   whether current platform should be treated as Linux
     */
    LinuxSecretToolPasswordVault(String serviceName, CommandExecutor commandExecutor, boolean linuxPlatform) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor");
        this.linuxPlatform = linuxPlatform;
    }

    /**
     * Indicates whether the adapter can access Linux Secret Service on this host.
     *
     * @return {@code true} when command execution may be attempted
     */
    @Override
    public boolean isAvailable() {
        if (!linuxPlatform) {
            return false;
        }

        Boolean cached = availableCache;
        if (cached != null) {
            return cached;
        }

        CommandResult probe = commandExecutor.run(List.of(COMMAND), null);
        boolean available = !probe.commandMissing();
        availableCache = available;
        return available;
    }

    /**
     * Saves or updates a password through {@code secret-tool store}.
     *
     * @param accountKey account key used as secret attribute
     * @param password   plaintext password to store
     * @return {@code true} when save succeeds
     */
    @Override
    public boolean savePassword(String accountKey, String password) {
        if (!isAvailable()) {
            return false;
        }

        CommandResult result = commandExecutor.run(List.of(
                COMMAND,
                "store",
                "--label=" + serviceName + " remembered password",
                "service",
                serviceName,
                "account",
                normalizeAccountKey(accountKey)), (password == null ? "" : password));
        return result.exitCode() == 0;
    }

    /**
     * Loads a password through {@code secret-tool lookup}.
     *
     * @param accountKey account key used as secret attribute
     * @return stored password when present
     */
    @Override
    public Optional<String> loadPassword(String accountKey) {
        if (!isAvailable()) {
            return Optional.empty();
        }

        CommandResult result = commandExecutor.run(List.of(
                COMMAND,
                "lookup",
                "service",
                serviceName,
                "account",
                normalizeAccountKey(accountKey)), null);

        if (result.exitCode() != 0) {
            return Optional.empty();
        }

        return Optional.ofNullable(result.stdout())
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }

    /**
     * Deletes a password through {@code secret-tool clear}.
     *
     * @param accountKey account key used as secret attribute
     * @return {@code true} when deletion succeeds or entry is already absent
     */
    @Override
    public boolean deletePassword(String accountKey) {
        if (!isAvailable()) {
            return false;
        }

        CommandResult result = commandExecutor.run(List.of(
                COMMAND,
                "clear",
                "service",
                serviceName,
                "account",
                normalizeAccountKey(accountKey)), null);

        if (result.exitCode() == 0) {
            return true;
        }

        String stderr = result.stderr() == null ? "" : result.stderr().toLowerCase(Locale.ROOT);
        return stderr.contains("no matching") || stderr.contains("not found");
    }

    /**
     * Executes a CLI command and captures result streams for vault operations.
     *
     * @param command command and arguments
     * @param stdin   optional stdin payload
     * @return command execution result
     */
    private static CommandResult executeCommand(List<String> command, String stdin) {
        List<String> safeCommand = command == null ? List.of() : new ArrayList<>(command);
        if (safeCommand.isEmpty()) {
            return new CommandResult(-1, "", "", true);
        }

        Process process;
        try {
            process = new ProcessBuilder(safeCommand).start();
        } catch (IOException ex) {
            return commandStartFailure(ex);
        } catch (Exception ex) {
            return new CommandResult(-1, "", ex.getMessage() == null ? "" : ex.getMessage(), false);
        }

        try {
            if (stdin != null) {
                try (OutputStream stream = process.getOutputStream()) {
                    stream.write(stdin.getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                }
            } else {
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(-1, "", "timeout", false);
            }

            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(process.exitValue(), stdout, stderr, false);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return new CommandResult(-1, "", "interrupted", false);
        } catch (Exception ex) {
            process.destroyForcibly();
            return new CommandResult(-1, "", ex.getMessage() == null ? "" : ex.getMessage(), false);
        }
    }

    /**
     * Maps process-start failures into a command result.
     *
     * @param ex startup exception thrown by {@link ProcessBuilder}
     * @return normalized command result describing the failure
     */
    private static CommandResult commandStartFailure(IOException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        boolean missing = ex instanceof FileNotFoundException
                || message.toLowerCase(Locale.ROOT).contains("no such file");
        return new CommandResult(-1, "", message, missing);
    }

    /**
     * Normalizes a logical account key for case-insensitive vault lookup.
     *
     * @param accountKey source account key
     * @return lowercase trimmed account key
     */
    private static String normalizeAccountKey(String accountKey) {
        return accountKey == null ? "" : accountKey.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Detects whether the host operating system is Linux.
     *
     * @return {@code true} when current OS name contains {@code linux}
     */
    private static boolean isLinuxPlatform() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    @FunctionalInterface
    interface CommandExecutor {
        /**
         * Executes a command with optional stdin payload.
         *
         * @param command command and arguments
         * @param stdin   optional stdin payload, or {@code null}
         * @return command execution result
         */
        CommandResult run(List<String> command, String stdin);
    }

    /**
     * Result envelope for command execution in this vault adapter.
     *
     * @param exitCode       process exit code
     * @param stdout         command stdout text
     * @param stderr         command stderr text
     * @param commandMissing whether command executable was unavailable
     */
    record CommandResult(int exitCode, String stdout, String stderr, boolean commandMissing) {
    }
}
