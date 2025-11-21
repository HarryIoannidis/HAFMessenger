package com.haf.shared.keystore;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.RsaKeyIO;

public final class KeystoreBootstrap {

    private KeystoreBootstrap() {}

    /**
     * Bootstraps the keystore if needed.
     * @return the root directory of the keystore
     * @throws Exception
     */
    public static Path run() throws Exception {
        Path root = KeystoreRoot.preferred();
        try {
            FilePerms.ensureDir700(root);
        } catch (AccessDeniedException e) {
            root = KeystoreRoot.userFallback();
            FilePerms.ensureDir700(root);
        }

        firstRunIfMissing(root);
        return root;
    }

    /**
     * Bootstraps the keystore if needed.
     * @param root the root directory of the keystore
     * @throws Exception
     */
    private static void firstRunIfMissing(Path root) throws Exception {
        try (var s = Files.list(root)) {
            if (s.findAny().isPresent()) return;
        }

        String keyId = UserKeystore.todayKeyId();
        Path dir = root.resolve(keyId);
        FilePerms.ensureDir700(dir);

        KeyPair kp = RsaKeyIO.generate(2048);
        FilePerms.writeFile600(dir.resolve("public.pem"),
                RsaKeyIO.publicPem(kp.getPublic()).getBytes(StandardCharsets.US_ASCII));

        char[] pass = getPass();
        byte[] prvPem = RsaKeyIO.privatePem(kp.getPrivate()).getBytes(StandardCharsets.US_ASCII);
        byte[] sealed = KeystoreSealing.sealWithPass(pass, prvPem);
        FilePerms.writeFile600(dir.resolve("private.enc"), sealed);

        String fp = com.haf.shared.utils.FingerprintUtil.sha256Hex(com.haf.shared.utils.RsaKeyIO.publicDer(kp.getPublic()));
        KeyMetadata meta = new KeyMetadata(keyId, CryptoConstants.RSA_ALGORITHM, fp, "Primary-" + keyId, System.currentTimeMillis()/1000, "CURRENT");
        FilePerms.writeFile600(dir.resolve("metadata.json"), JsonCodec.toJson(meta).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Gets the passphrase for the first key.
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
