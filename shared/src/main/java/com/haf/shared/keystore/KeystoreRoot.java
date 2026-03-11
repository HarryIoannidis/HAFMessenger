package com.haf.shared.keystore;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class KeystoreRoot {
    private KeystoreRoot() {}

    /**
     * Preferred keystore root:
     * Win: %ProgramData%\HAF\keystore
     * Linux: /var/lib/haf/keystore)
     * Caller creates dir and applies 0700/ACLs.
     *
     * @return path
     */
    public static Path preferred() {
        return preferred(null);
    }

    /**
     * Preferred keystore root, optionally isolated for a specific user.
     *
     * @param userId the user ID to isolate for (if null, returns the shared root)
     * @return path
     */
    public static Path preferred(String userId) {
        Path root = getBasePreferred();
        return userId == null ? root : root.resolve("u-" + userId);
    }

    private static Path getBasePreferred() {
        String prop = System.getProperty("haf.keystore.root");
        if (prop != null && !prop.isBlank()) {
            return Paths.get(prop);
        }

        String env = System.getenv("HAF_KEYSTORE_ROOT");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String pd = System.getenv("ProgramData");

            return (pd != null && !pd.isBlank())
                    ? Paths.get(pd, "HAF", "keystore")
                    : Paths.get("C:\\ProgramData\\HAF\\keystore");
        }

        return Paths.get("/var/lib/haf/keystore");
    }

    /**
     * User-level fallback keystore root:
     * Win: %LOCALAPPDATA%\HAF\keystore or <home>\AppData\Local\HAF\keystore,
     * Linux: $XDG_DATA_HOME/haf/keystore or ~/.local/share/haf/keystore.
     * Caller creates dir and applies 0700/ACLs.
     *
     * @return path
     */
    public static Path userFallback() {
        return userFallback(null);
    }

    /**
     * User-level fallback keystore root, optionally isolated for a specific user.
     *
     * @param userId the user ID to isolate for (if null, returns the shared root)
     * @return path
     */
    public static Path userFallback(String userId) {
        Path root = getBaseUserFallback();
        return userId == null ? root : root.resolve("u-" + userId);
    }

    private static Path getBaseUserFallback() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null && !local.isBlank()) {
                return Paths.get(local, "HAF", "keystore");
            }

            return Paths.get(System.getProperty("user.home"), "AppData", "Local", "HAF", "keystore");
        }

        String xdg = System.getenv("XDG_DATA_HOME");
        if (xdg != null && !xdg.isBlank()) {
            return Paths.get(xdg, "haf", "keystore");
        }

        return Paths.get(System.getProperty("user.home"), ".local", "share", "haf", "keystore");
    }
}
