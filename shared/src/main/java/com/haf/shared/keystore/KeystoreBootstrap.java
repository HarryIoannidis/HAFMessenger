package com.haf.shared.keystore;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;

public final class KeystoreBootstrap {

    private KeystoreBootstrap() {
    }

    public static Path run() throws Exception {
        return run(null, null);
    }

    public static Path run(String userId) throws Exception {
        return run(userId, null);
    }

    /**
     * Bootstraps the keystore if needed for a specific user.
     *
     * @param userId the user ID (can be null for shared)
     * @return the root directory of the keystore
     * @throws Exception
     */
    public static Path run(String userId, char[] passphrase) throws Exception {
        Path root = KeystoreRoot.preferred(userId);

        try {
            FilePerms.ensureDir700(root);
        } catch (Exception e) {
            root = KeystoreRoot.userFallback(userId);
            FilePerms.ensureDir700(root);
        }

        firstRunIfMissing(root, passphrase);

        System.out.println("DEBUG: Bootstrap resolving root to: " + root.toAbsolutePath());
        return root;
    }

    /**
     * Bootstraps the keystore if needed.
     *
     * @param root the root directory of the keystore
     * @throws Exception
     */
    private static void firstRunIfMissing(Path root, char[] passphrase) throws Exception {
        try (var s = Files.list(root)) {
            if (s.findAny().isPresent())
                return;
        }

        String keyId = UserKeystore.todayKeyId();
        Path dir = root.resolve(keyId);
        FilePerms.ensureDir700(dir);

        KeyPair kp = EccKeyIO.generate();
        FilePerms.writeFile600(dir.resolve("public.pem"),
                EccKeyIO.publicPem(kp.getPublic()).getBytes(StandardCharsets.US_ASCII));

        char[] pass = (passphrase != null && passphrase.length > 0) ? passphrase : getPass();
        byte[] prvPem = EccKeyIO.privatePem(kp.getPrivate()).getBytes(StandardCharsets.US_ASCII);
        byte[] sealed = KeystoreSealing.sealWithPass(pass, prvPem);
        FilePerms.writeFile600(dir.resolve("private.enc"), sealed);

        String fp = FingerprintUtil
                .sha256Hex(EccKeyIO.publicDer(kp.getPublic()));
        KeyMetadata meta = new KeyMetadata(keyId, CryptoConstants.X25519_CURVE, fp, "Primary-" + keyId,
                System.currentTimeMillis() / 1000, "CURRENT");
        FilePerms.writeFile600(dir.resolve("metadata.json"), JsonCodec.toJson(meta).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the passphrase for the first key.
     *
     * @return the passphrase
     */
    private static char[] getPass() {
        String p = System.getenv("HAF_KEY_PASS");
        if (p == null || p.isBlank()) {
            p = "dev-pass-change";
        }

        return p.toCharArray();
    }

}
