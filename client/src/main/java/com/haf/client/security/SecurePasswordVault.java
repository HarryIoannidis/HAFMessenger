package com.haf.client.security;

import java.util.Optional;

/**
 * Secure storage abstraction for remembered login passwords.
 */
public interface SecurePasswordVault {

    /**
     * Indicates whether this vault is currently usable on the host machine.
     *
     * @return {@code true} when read/write operations can be attempted
     */
    boolean isAvailable();

    /**
     * Saves or updates a password for a logical account key.
     *
     * @param accountKey logical account key (for example normalized email)
     * @param password   plaintext password to store in OS secure storage
     * @return {@code true} when save succeeded
     */
    boolean savePassword(String accountKey, String password);

    /**
     * Loads a password for a logical account key.
     *
     * @param accountKey logical account key
     * @return stored password when present
     */
    Optional<String> loadPassword(String accountKey);

    /**
     * Deletes a password for a logical account key.
     *
     * @param accountKey logical account key
     * @return {@code true} when delete succeeded or value was absent
     */
    boolean deletePassword(String accountKey);
}
