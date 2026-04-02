package com.haf.client.security;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Windows Credential Manager-backed secure vault via JNA WinCred APIs.
 */
final class WindowsCredentialManagerPasswordVault implements SecurePasswordVault {

    private static final int CRED_TYPE_GENERIC = 1;
    private static final int CRED_PERSIST_LOCAL_MACHINE = 2;
    private static final int ERROR_NOT_FOUND = 1168;

    private final String serviceName;
    private final WindowsCredentialApi credentialApi;
    private final boolean windowsPlatform;

    /**
     * Creates a default Windows Credential Manager vault.
     *
     * @param serviceName target service namespace
     */
    WindowsCredentialManagerPasswordVault(String serviceName) {
        this(serviceName, new JnaWindowsCredentialApi(), isWindowsPlatform());
    }

    /**
     * Creates a Windows vault with injected WinCred boundary and platform flag
     * (test seam).
     *
     * @param serviceName     target service namespace
     * @param credentialApi   WinCred boundary implementation
     * @param windowsPlatform whether current platform should be treated as Windows
     */
    WindowsCredentialManagerPasswordVault(
            String serviceName,
            WindowsCredentialApi credentialApi,
            boolean windowsPlatform) {
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName");
        this.credentialApi = Objects.requireNonNull(credentialApi, "credentialApi");
        this.windowsPlatform = windowsPlatform;
    }

    /**
     * Indicates whether the adapter can access WinCred on this host.
     *
     * @return {@code true} when WinCred calls may be attempted
     */
    @Override
    public boolean isAvailable() {
        return windowsPlatform && credentialApi.isSupported();
    }

    /**
     * Saves or updates a password in Windows Credential Manager.
     *
     * @param accountKey logical account key
     * @param password   plaintext password to store
     * @return {@code true} when save succeeds
     */
    @Override
    public boolean savePassword(String accountKey, String password) {
        if (!isAvailable()) {
            return false;
        }
        String normalizedAccount = normalizeAccountKey(accountKey);
        return credentialApi.writeGenericCredential(
                toTargetName(normalizedAccount),
                normalizedAccount,
                password == null ? "" : password);
    }

    /**
     * Loads a password from Windows Credential Manager.
     *
     * @param accountKey logical account key
     * @return stored password when present
     */
    @Override
    public Optional<String> loadPassword(String accountKey) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return credentialApi.readGenericCredential(toTargetName(normalizeAccountKey(accountKey)))
                .map(String::strip)
                .filter(value -> !value.isEmpty());
    }

    /**
     * Deletes a password from Windows Credential Manager.
     *
     * @param accountKey logical account key
     * @return {@code true} when delete succeeds or value is absent
     */
    @Override
    public boolean deletePassword(String accountKey) {
        if (!isAvailable()) {
            return false;
        }
        return credentialApi.deleteGenericCredential(toTargetName(normalizeAccountKey(accountKey)));
    }

    /**
     * Builds the WinCred target name for an account key.
     *
     * @param accountKey normalized account key
     * @return target name in the service/account namespace
     */
    private String toTargetName(String accountKey) {
        return serviceName + ":" + accountKey;
    }

    /**
     * Normalizes a logical account key for case-insensitive WinCred lookup.
     *
     * @param accountKey source account key
     * @return lowercase trimmed account key
     */
    private static String normalizeAccountKey(String accountKey) {
        return accountKey == null ? "" : accountKey.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Detects whether the host operating system is Windows.
     *
     * @return {@code true} when current OS name contains {@code win}
     */
    private static boolean isWindowsPlatform() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    interface WindowsCredentialApi {
        /**
         * Indicates whether the WinCred boundary is available.
         *
         * @return {@code true} when API calls can be attempted
         */
        boolean isSupported();

        /**
         * Writes a generic credential to Windows Credential Manager.
         *
         * @param targetName credential target key
         * @param userName   credential user name
         * @param secret     credential secret
         * @return {@code true} when write succeeds
         */
        boolean writeGenericCredential(String targetName, String userName, String secret);

        /**
         * Reads a generic credential from Windows Credential Manager.
         *
         * @param targetName credential target key
         * @return stored secret when present
         */
        Optional<String> readGenericCredential(String targetName);

        /**
         * Deletes a generic credential from Windows Credential Manager.
         *
         * @param targetName credential target key
         * @return {@code true} when deletion succeeds (or value was absent)
         */
        boolean deleteGenericCredential(String targetName);
    }

    static final class JnaWindowsCredentialApi implements WindowsCredentialApi {

        private volatile Boolean supportedCache;

        /**
         * Determines whether required JNA WinCred symbols can be loaded.
         *
         * @return {@code true} when WinCred bindings are available
         */
        @Override
        public boolean isSupported() {
            Boolean cached = supportedCache;
            if (cached != null) {
                return cached;
            }

            boolean supported;
            try {
                supported = WinCredLibrary.INSTANCE != null;
            } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
                supported = false;
            }
            supportedCache = supported;
            return supported;
        }

        /**
         * Writes a generic credential through the WinCred API.
         *
         * @param targetName target key
         * @param userName   credential user
         * @param secret     credential secret
         * @return {@code true} when write succeeds
         */
        @Override
        public boolean writeGenericCredential(String targetName, String userName, String secret) {
            if (!isSupported()) {
                return false;
            }

            byte[] secretBytes = (secret == null ? "" : secret).getBytes(StandardCharsets.UTF_16LE);
            Memory credentialBlob = secretBytes.length == 0 ? null : new Memory(secretBytes.length);
            if (credentialBlob != null) {
                credentialBlob.write(0, secretBytes, 0, secretBytes.length);
            }

            WinCredCredential credential = new WinCredCredential();
            credential.Flags = 0;
            credential.Type = CRED_TYPE_GENERIC;
            credential.TargetName = new WString(targetName);
            credential.Comment = null;
            credential.LastWritten = new FileTime();
            credential.CredentialBlobSize = secretBytes.length;
            credential.CredentialBlob = credentialBlob;
            credential.Persist = CRED_PERSIST_LOCAL_MACHINE;
            credential.AttributeCount = 0;
            credential.Attributes = Pointer.NULL;
            credential.TargetAlias = null;
            credential.UserName = new WString(userName == null ? "" : userName);
            credential.write();

            return WinCredLibrary.INSTANCE.CredWriteW(credential, 0);
        }

        /**
         * Reads a generic credential through the WinCred API.
         *
         * @param targetName target key
         * @return credential secret when present
         */
        @Override
        public Optional<String> readGenericCredential(String targetName) {
            if (!isSupported()) {
                return Optional.empty();
            }

            PointerByReference credentialPointer = new PointerByReference();
            boolean ok = WinCredLibrary.INSTANCE.CredReadW(new WString(targetName), CRED_TYPE_GENERIC, 0,
                    credentialPointer);
            if (!ok) {
                return Optional.empty();
            }

            Pointer rawPointer = credentialPointer.getValue();
            if (rawPointer == null) {
                return Optional.empty();
            }

            try {
                WinCredCredential credential = Structure.newInstance(WinCredCredential.class, rawPointer);
                credential.read();

                if (credential.CredentialBlob == null || credential.CredentialBlobSize <= 0) {
                    return Optional.of("");
                }

                byte[] bytes = credential.CredentialBlob.getByteArray(0, credential.CredentialBlobSize);
                String secret = new String(bytes, StandardCharsets.UTF_16LE);
                int nullTerminator = secret.indexOf('\0');
                if (nullTerminator >= 0) {
                    secret = secret.substring(0, nullTerminator);
                }
                return Optional.of(secret);
            } finally {
                WinCredLibrary.INSTANCE.CredFree(rawPointer);
            }
        }

        /**
         * Deletes a generic credential through the WinCred API.
         *
         * @param targetName target key
         * @return {@code true} when credential was deleted or already absent
         */
        @Override
        public boolean deleteGenericCredential(String targetName) {
            if (!isSupported()) {
                return false;
            }

            boolean deleted = WinCredLibrary.INSTANCE.CredDeleteW(new WString(targetName), CRED_TYPE_GENERIC, 0);
            if (deleted) {
                return true;
            }

            return Native.getLastError() == ERROR_NOT_FOUND;
        }

        /**
         * Low-level JNA mapping for required WinCred functions.
         */
        private interface WinCredLibrary extends StdCallLibrary {
            WinCredLibrary INSTANCE = Native.load("Advapi32", WinCredLibrary.class, W32APIOptions.DEFAULT_OPTIONS);

            /**
             * Writes a credential to the WinCred store.
             *
             * @param credential credential structure to write
             * @param flags      call flags (unused, pass 0)
             * @return {@code true} when write succeeds
             */
            boolean CredWriteW(WinCredCredential credential, int flags);

            /**
             * Reads a credential from the WinCred store.
             *
             * @param targetName target key
             * @param type       credential type
             * @param flags      call flags (unused, pass 0)
             * @param credential out pointer receiving the credential
             * @return {@code true} when a credential was found
             */
            boolean CredReadW(WString targetName, int type, int flags, PointerByReference credential);

            /**
             * Deletes a credential from the WinCred store.
             *
             * @param targetName target key
             * @param type       credential type
             * @param flags      call flags (unused, pass 0)
             * @return {@code true} when delete succeeds
             */
            boolean CredDeleteW(WString targetName, int type, int flags);

            /**
             * Frees memory returned by WinCred read calls.
             *
             * @param credential credential pointer to free
             */
            void CredFree(Pointer credential);
        }

        /**
         * JNA mapping for Windows {@code FILETIME}.
         * Fields must remain public and keep native naming for JNA interop.
         */
        public static class FileTime extends Structure {
            public int dwLowDateTime;
            public int dwHighDateTime;

            /**
             * @return JNA field order for {@code FILETIME}
             */
            @Override
            protected List<String> getFieldOrder() {
                return List.of("dwLowDateTime", "dwHighDateTime");
            }
        }

        /**
         * JNA mapping for Windows {@code CREDENTIALW}.
         * Fields must remain public and keep native naming for JNA interop.
         */
        public static class WinCredCredential extends Structure {
            public int Flags;
            public int Type;
            public WString TargetName;
            public WString Comment;
            public FileTime LastWritten;
            public int CredentialBlobSize;
            public Pointer CredentialBlob;
            public int Persist;
            public int AttributeCount;
            public Pointer Attributes;
            public WString TargetAlias;
            public WString UserName;

            /**
             * @return JNA field order for {@code CREDENTIALW}
             */
            @Override
            protected List<String> getFieldOrder() {
                return List.of(
                        "Flags",
                        "Type",
                        "TargetName",
                        "Comment",
                        "LastWritten",
                        "CredentialBlobSize",
                        "CredentialBlob",
                        "Persist",
                        "AttributeCount",
                        "Attributes",
                        "TargetAlias",
                        "UserName");
            }
        }
    }
}
