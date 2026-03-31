package com.haf.shared.keystore;

import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FilePerms;
import com.haf.shared.exceptions.KeystoreOperationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;

/**
 * Folder Structure:
 * <root>/<keyId>/metadata.json
 * <root>/<keyId>/public.pem
 * <root>/<keyId>/private.enc (ASCII envelope: v1.<b64(salt)>. <b64(iv)>.
 * <b64(GCM(ciphertext+tag))>)
 * Note: JavaDoc only in public methods.
 */
public final class UserKeystore {

    private final Path root;

    /**
     * Builds a key ID based on current date in yyyymmdd format prefixed with
     * "key-".
     *
     * @return key ID
     */
    public static String todayKeyId() {
        return "key-" + LocalDate.now().toString().replace("-", "");
    }

    /**
     * Constructs a UserKeystore instance with the specified root directory.
     *
     * @param root the root directory for the key store; must not be null
     * @throws NullPointerException if the root is null
     */
    public UserKeystore(Path root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    /**
     * Validates the passphrase for null or empty values.
     *
     * @param passphrase the passphrase to validate
     * @throws IllegalArgumentException if the passphrase is null or empty
     */
    private static void requirePassphrase(char[] passphrase) {
        if (passphrase == null || passphrase.length == 0) {
            throw new IllegalArgumentException("Passphrase must not be empty");
        }
    }

    /**
     * Stores X25519 pair: public.pem (PEM), private.enc (AES-GCM of PKCS#8),
     * and metadata.json (KeyMetadata with fingerprint/status).
     *
     * @param keyId      key ID (UUID/label)
     * @param kp         x25519 pair to save
     * @param passphrase secret phrase to protect the private
     * @throws Exception if encryption or I/O fails
     */
    public void saveKeypair(String keyId, KeyPair kp, char[] passphrase) throws Exception {
        requirePassphrase(passphrase);

        Path dir = root.resolve(keyId);
        FilePerms.ensureDir700(dir);

        FilePerms.writeFile600(dir.resolve("public.pem"),
                EccKeyIO.publicPem(kp.getPublic()).getBytes(StandardCharsets.US_ASCII));

        byte[] prvPem = EccKeyIO.privatePem(kp.getPrivate()).getBytes(StandardCharsets.US_ASCII);
        byte[] enc = KeystoreSealing.sealWithPass(passphrase, prvPem);
        FilePerms.writeFile600(dir.resolve("private.enc"), enc);

        String alg = algorithmName(kp.getPublic());
        String fp = FingerprintUtil.sha256Hex(EccKeyIO.publicDer(kp.getPublic()));
        long nowSec = Instant.now().getEpochSecond();
        KeyMetadata meta = new KeyMetadata(keyId, alg, fp, "Primary-" + keyId, nowSec, "CURRENT");

        FilePerms.writeFile600(dir.resolve("metadata.json"), JsonCodec.toJson(meta).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Loads private X25519 from private.enc:
     * salt|iv|GCM(ciphertext+tag) → PBKDF2‑SHA256→AES‑256‑GCM → PKCS#8 →
     * PrivateKey.
     *
     * @param keyId      key ID
     * @param passphrase decryption secret phrase
     * @return X25519 private key
     * @throws Exception if GCM/key/format fails
     */
    public PrivateKey loadPrivate(String keyId, char[] passphrase) throws Exception {
        requirePassphrase(passphrase);

        Path encPath = root.resolve(keyId).resolve("private.enc");
        byte[] blob = Files.readAllBytes(encPath);
        byte[] pem = KeystoreSealing.openWithPass(passphrase, blob);

        return EccKeyIO.privateFromPem(new String(pem, StandardCharsets.US_ASCII));
    }

    /**
     * Selects the most appropriate key directory based on the following priority:
     * 1. Most recent key with CURRENT status
     * 2. Most recent key with any metadata
     * 3. Most recently modified directory
     *
     * @return path to the selected key directory
     * @throws Exception if the keystore root is invalid or empty
     */
    private Path selectCurrentKeyDir() throws Exception {
        if (!Files.isDirectory(root)) {
            throw new KeystoreOperationException("keystore root not a directory: " + root);
        }
        List<Path> dirs;

        try (Stream<Path> s = Files.list(root)) {
            dirs = s.filter(Files::isDirectory).toList();
        }

        if (dirs.isEmpty()) {
            throw new KeystoreOperationException("no keys in keystore root: " + root);
        }

        record Entry(Path dir, KeyMetadata meta, long ts) {
        }
        List<Entry> entries = new ArrayList<>();

        for (Path d : dirs) {
            Path m = d.resolve("metadata.json");
            KeyMetadata km = null;
            long ts = 0L;

            if (Files.isRegularFile(m)) {
                try {
                    km = JsonCodec.fromJson(Files.readString(m), KeyMetadata.class);
                    ts = km.createdAtEpochSec();
                } catch (Exception ignored) {
                    // Ignore malformed metadata and fall back to directory timestamp.
                }
            }

            if (km == null) {
                try {
                    ts = Files.getLastModifiedTime(d).toMillis() / 1000L;
                } catch (Exception ignored) {
                    // Keep default timestamp when directory metadata is unavailable.
                }
            }

            entries.add(new Entry(d, km, ts));
        }

        Optional<Entry> cur = entries.stream()
                .filter(e -> e.meta != null && "CURRENT".equals(e.meta.status()))
                .max(Comparator.comparingLong(e -> e.ts));

        Optional<Entry> newestByMeta = entries.stream()
                .filter(e -> e.meta != null)
                .max(Comparator.comparingLong(e -> e.ts));

        return cur.or(() -> newestByMeta)
                .or(() -> entries.stream().max(Comparator.comparingLong(e -> e.ts)))
                .orElseThrow(() -> new KeystoreOperationException("no selectable key entries in: " + root))
                .dir();
    }

    /**
     * Selects the oldest key directory using the following priority:
     * 1. Oldest key with CURRENT status
     * 2. Oldest key with any metadata
     * 3. Oldest by directory last-modified timestamp
     *
     * @return path to the selected key directory
     * @throws Exception if the keystore root is invalid or empty
     */
    public Path selectOldestKeyDirPreferCurrent() throws Exception {
        if (!Files.isDirectory(root)) {
            throw new KeystoreOperationException("keystore root not a directory: " + root);
        }
        List<Path> dirs;

        try (Stream<Path> s = Files.list(root)) {
            dirs = s.filter(Files::isDirectory).toList();
        }

        if (dirs.isEmpty()) {
            throw new KeystoreOperationException("no keys in keystore root: " + root);
        }

        record Entry(Path dir, KeyMetadata meta, long tsMeta, long tsMtime) {
        }
        List<Entry> entries = new ArrayList<>();

        for (Path d : dirs) {
            Path m = d.resolve("metadata.json");
            KeyMetadata km = null;
            long tsMeta = Long.MAX_VALUE; // use MAX so missing meta is deprioritized
            long tsMtime = Long.MAX_VALUE;

            if (Files.isRegularFile(m)) {
                try {
                    km = JsonCodec.fromJson(Files.readString(m), KeyMetadata.class);
                    tsMeta = km.createdAtEpochSec();
                } catch (Exception ignored) {
                    // Ignore malformed metadata and rely on mtime fallback ordering.
                }
            }

            try {
                tsMtime = Files.getLastModifiedTime(d).toMillis();
            } catch (Exception ignored) {
                // Keep MAX value so directories with unreadable mtime are deprioritized.
            }

            entries.add(new Entry(d, km, tsMeta, tsMtime));
        }

        Comparator<Entry> oldestByMeta = Comparator.comparingLong(e -> e.tsMeta);
        Comparator<Entry> oldestByMtime = Comparator.comparingLong(e -> e.tsMtime);

        // 1) Oldest CURRENT by metadata timestamp, then by directory mtime as
        // tie-breaker
        Optional<Entry> oldestCurrent = entries.stream()
                .filter(e -> e.meta != null && "CURRENT".equals(e.meta.status()))
                .min(oldestByMeta.thenComparing(oldestByMtime));
        if (oldestCurrent.isPresent()) {
            return oldestCurrent.get().dir();
        }

        // 2) Oldest with any metadata
        Optional<Entry> oldestWithMeta = entries.stream()
                .filter(e -> e.meta != null)
                .min(oldestByMeta.thenComparing(oldestByMtime));
        if (oldestWithMeta.isPresent()) {
            return oldestWithMeta.get().dir();
        }

        // 3) Oldest by directory mtime
        return entries.stream().min(oldestByMtime).get().dir();
    }

    /**
     * Loads the current private key (status==CURRENT or fallback as described).
     *
     * @param passphrase passphrase used to decrypt the private key envelope
     * @return the private key
     * @throws Exception if loading fails
     */
    public PrivateKey loadCurrentPrivate(char[] passphrase) throws Exception {
        requirePassphrase(passphrase);

        Path dir = selectCurrentKeyDir();
        byte[] blob = Files.readAllBytes(dir.resolve("private.enc"));
        byte[] pem = KeystoreSealing.openWithPass(passphrase, blob);

        return EccKeyIO.privateFromPem(new String(pem, StandardCharsets.US_ASCII));
    }

    /**
     * Loads the current public key (status==CURRENT or fallback as described).
     *
     * @return the public key
     * @throws Exception if loading fails
     */
    public PublicKey loadCurrentPublic() throws Exception {
        Path dir = selectCurrentKeyDir();
        String pem = Files.readString(dir.resolve("public.pem"), StandardCharsets.US_ASCII);
        return EccKeyIO.publicFromPem(pem);
    }

    /**
     * Loads a public key by keyId.
     *
     * @param keyId the key identifier
     * @return the public key
     * @throws Exception if the key cannot be found or loaded
     */
    public PublicKey loadPublicKeyByKeyId(String keyId) throws Exception {
        Path keyDir = root.resolve(keyId);
        Path publicPemPath = keyDir.resolve("public.pem");

        if (!Files.exists(publicPemPath)) {
            throw new KeystoreOperationException("Public key not found for keyId: " + keyId);
        }

        String pem = Files.readString(publicPemPath, StandardCharsets.US_ASCII);
        return EccKeyIO.publicFromPem(pem);
    }

    /**
     * Reads <root>/<keyId>/metadata.json for all keyIds and returns KeyMetadata.
     *
     * @return list of KeyMetadata
     * @throws Exception if reading/JSON parsing fails
     */
    public List<KeyMetadata> listMetadata() throws Exception {
        if (!Files.isDirectory(root)) {
            return List.of();
        }

        try (Stream<Path> s = Files.list(root)) {
            List<KeyMetadata> out = new ArrayList<>();

            for (Path p : s.filter(Files::isDirectory).toList()) {
                Path m = p.resolve("metadata.json");

                if (Files.isRegularFile(m)) {
                    out.add(JsonCodec.fromJson(Files.readString(m), KeyMetadata.class));
                }
            }

            return out;
        }
    }

    /**
     * Makes rotation: current CURRENT → PREVIOUS and save new pair as CURRENT.
     *
     * @param currentKeyId the existing key that becomes PREVIOUS
     * @param newKeyId     the ID of the new pair
     * @param newPair      new X25519 KeyPair
     * @param passphrase   new private protection phrase
     * @return keyMetadata of the new CURRENT
     * @throws Exception if I/O or crypto fails
     */
    public KeyMetadata rotate(String currentKeyId, String newKeyId, KeyPair newPair, char[] passphrase)
            throws Exception {
        Path curMeta = root.resolve(currentKeyId).resolve("metadata.json");

        if (Files.isRegularFile(curMeta)) {
            KeyMetadata m = JsonCodec.fromJson(Files.readString(curMeta), KeyMetadata.class);
            KeyMetadata demoted = new KeyMetadata(m.keyId(), m.algorithm(), m.fingerprint(), m.label(),
                    m.createdAtEpochSec(), "PREVIOUS");
            Files.writeString(curMeta, JsonCodec.toJson(demoted), StandardOpenOption.TRUNCATE_EXISTING);
        }
        saveKeypair(newKeyId, newPair, passphrase);

        return JsonCodec.fromJson(Files.readString(root.resolve(newKeyId).resolve("metadata.json")), KeyMetadata.class);
    }

    /**
     * Returns the algorithm name for the given public key.
     *
     * @param pub the public key
     * @return the algorithm name
     */
    private static String algorithmName(PublicKey pub) {
        if ("X25519".equals(pub.getAlgorithm()) || "XDH".equals(pub.getAlgorithm())) {
            return "X25519";
        }
        return pub.getAlgorithm();
    }

}
