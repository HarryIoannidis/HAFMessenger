package com.haf.client.security;

import java.util.Optional;

/**
 * No-op secure password vault used when no OS-backed credential manager exists.
 */
final class UnsupportedPasswordVault implements SecurePasswordVault {

    /**
     * This adapter is intentionally unavailable on unsupported platforms.
     *
     * @return always {@code false}
     */
    @Override
    public boolean isAvailable() {
        return false;
    }

    /**
     * Never persists password data on unsupported platforms.
     *
     * @param accountKey ignored
     * @param password   ignored
     * @return always {@code false}
     */
    @Override
    public boolean savePassword(String accountKey, String password) {
        return false;
    }

    /**
     * Never returns a stored password on unsupported platforms.
     *
     * @param accountKey ignored
     * @return always empty
     */
    @Override
    public Optional<String> loadPassword(String accountKey) {
        return Optional.empty();
    }

    /**
     * No-op delete on unsupported platforms.
     *
     * @param accountKey ignored
     * @return always {@code false}
     */
    @Override
    public boolean deletePassword(String accountKey) {
        return false;
    }
}
