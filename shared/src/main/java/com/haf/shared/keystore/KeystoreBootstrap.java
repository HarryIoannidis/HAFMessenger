package com.haf.shared.keystore;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstraps keystore directories and initial key material for a user.
 */
public final class KeystoreBootstrap {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeystoreBootstrap.class);

    /**
     * Prevents instantiation of this utility class.
     */
    private KeystoreBootstrap() {
    }

    /**
     * Bootstraps the keystore if needed for a specific user.
     *
     * @param userId     the user ID (can be null for shared)
     * @param passphrase passphrase used to seal the generated private key
     * @return the root directory of the keystore
     * @throws IOException              when directory or file I/O fails
     * @throws GeneralSecurityException when cryptographic key sealing fails
     */
    public static Path run(String userId, char[] passphrase) throws IOException, GeneralSecurityException {
        requirePassphrase(passphrase);

        Path root = KeystoreRoot.preferred(userId);

        try {
            FilePerms.ensureDir700(root);
        } catch (IOException e) {
            root = KeystoreRoot.userFallback(userId);
            FilePerms.ensureDir700(root);
        }

        firstRunIfMissing(root, passphrase);

        LOGGER.debug("Bootstrap resolving root to: {}", root.toAbsolutePath());
        return root;
    }

    /**
     * Bootstraps the keystore if needed.
     *
     * @param root       resolved root directory of the keystore
     * @param passphrase passphrase used to seal the generated private key
     * @throws IOException              when directory or file I/O fails
     * @throws GeneralSecurityException when cryptographic key sealing fails
     */
    private static void firstRunIfMissing(Path root, char[] passphrase) throws IOException, GeneralSecurityException {
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

        byte[] prvPem = EccKeyIO.privatePem(kp.getPrivate()).getBytes(StandardCharsets.US_ASCII);
        byte[] sealed = KeystoreSealing.sealWithPass(passphrase, prvPem);
        FilePerms.writeFile600(dir.resolve("private.enc"), sealed);

        String fp = FingerprintUtil
                .sha256Hex(EccKeyIO.publicDer(kp.getPublic()));
        KeyMetadata meta = new KeyMetadata(keyId, CryptoConstants.X25519_CURVE, fp, "Primary-" + keyId,
                System.currentTimeMillis() / 1000, "CURRENT");
        FilePerms.writeFile600(dir.resolve("metadata.json"), JsonCodec.toJson(meta).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates that the supplied passphrase is non-empty for bootstrap operations.
     *
     * @param passphrase passphrase to validate
     */
    private static void requirePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Keystore bootstrap passphrase must not be empty");
        }
    }

}
