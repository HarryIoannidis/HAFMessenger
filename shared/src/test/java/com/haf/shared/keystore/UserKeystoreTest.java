package com.haf.shared.keystore;

import com.haf.shared.dto.KeyMetadata;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.EccKeyIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UserKeystoreTest {

    private Path tmp;

    @AfterEach
    void cleanup() throws Exception {
        if (tmp != null && Files.exists(tmp)) {
            Files.walk(tmp)
                    .sorted(Comparator.reverseOrder()) // delete files before dirs
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    @Test
    void save_and_load_private_roundtrip_ok() throws Exception {
        tmp = Files.createTempDirectory("keystore-ut");
        UserKeystore ks = new UserKeystore(tmp);

        KeyPair kp = EccKeyIO.generate();
        char[] pass = "strong-passphrase".toCharArray();
        String keyId = "key-1";

        ks.saveKeypair(keyId, kp, pass);

        Path dir = tmp.resolve(keyId);
        Path pem = dir.resolve("public.pem");
        Path enc = dir.resolve("private.enc");
        Path meta = dir.resolve("metadata.json");

        assertTrue(Files.isRegularFile(pem));
        assertTrue(Files.isRegularFile(enc));
        assertTrue(Files.isRegularFile(meta));

        String pemText = Files.readString(pem, StandardCharsets.US_ASCII);
        assertTrue(pemText.contains("-----BEGIN PUBLIC KEY-----"));
        assertTrue(pemText.contains("-----END PUBLIC KEY-----"));

        byte[] blob = Files.readAllBytes(enc);
        String env = new String(blob, StandardCharsets.US_ASCII);
        assertTrue(env.startsWith("v1."), "ASCII envelope must start with v1.");
        String[] parts = env.split("\\.");
        assertEquals(4, parts.length, "Envelope must have 4 parts");
        byte[] salt = java.util.Base64.getDecoder().decode(parts[1]);
        byte[] iv = java.util.Base64.getDecoder().decode(parts[2]);
        byte[] ct = java.util.Base64.getDecoder().decode(parts[3]);
        assertEquals(16, salt.length, "salt len");
        assertEquals(12, iv.length, "iv len");
        assertTrue(ct.length >= 16, "ciphertext+tag too short");
        boolean saltNonZero = false;
        for (byte b : salt) {
            if (b != 0) {
                saltNonZero = true;
                break;
            }
        }
        boolean ivNonZero = false;
        for (byte b : iv) {
            if (b != 0) {
                ivNonZero = true;
                break;
            }
        }
        assertTrue(saltNonZero, "salt appears zeroed");
        assertTrue(ivNonZero, "iv appears zeroed");

        KeyMetadata m = JsonCodec.fromJson(Files.readString(meta), KeyMetadata.class);
        assertEquals(keyId, m.keyId());
        assertEquals("CURRENT", m.status());

        var prv = ks.loadPrivate(keyId, pass);
        assertArrayEquals(kp.getPrivate().getEncoded(), prv.getEncoded(),
                "PKCS#8 DER must round-trip via AES-GCM envelope");
    }

    @Test
    void load_with_wrong_passphrase_fails() throws Exception {
        tmp = Files.createTempDirectory("keystore-ut");
        UserKeystore ks = new UserKeystore(tmp);

        KeyPair kp = EccKeyIO.generate();
        ks.saveKeypair("key-1", kp, "right-pass".toCharArray());

        assertThrows(Exception.class, () -> ks.loadPrivate("key-1", "wrong-pass".toCharArray()),
                "GCM must fail with wrong passphrase");
    }

    @Test
    void list_metadata_and_rotation_ok() throws Exception {
        tmp = Files.createTempDirectory("keystore-ut");
        UserKeystore ks = new UserKeystore(tmp);

        ks.saveKeypair("key-2025Q4", EccKeyIO.generate(), "pass".toCharArray());

        KeyMetadata newCur = ks.rotate("key-2025Q4", "key-2026Q1", EccKeyIO.generate(), "pass".toCharArray());
        assertEquals("key-2026Q1", newCur.keyId());
        assertEquals("CURRENT", newCur.status());

        List<KeyMetadata> all = ks.listMetadata();
        assertEquals(2, all.size(), "Expected two key entries");

        KeyMetadata prev = all.stream().filter(m -> m.keyId().equals("key-2025Q4")).findFirst().orElseThrow();
        KeyMetadata cur = all.stream().filter(m -> m.keyId().equals("key-2026Q1")).findFirst().orElseThrow();

        assertEquals("PREVIOUS", prev.status());
        assertEquals("CURRENT", cur.status());

        KeyMetadata prevOnDisk = JsonCodec.fromJson(
                Files.readString(tmp.resolve("key-2025Q4").resolve("metadata.json")),
                KeyMetadata.class);
        KeyMetadata curOnDisk = JsonCodec.fromJson(
                Files.readString(tmp.resolve("key-2026Q1").resolve("metadata.json")),
                KeyMetadata.class);
        assertEquals("PREVIOUS", prevOnDisk.status());
        assertEquals("CURRENT", curOnDisk.status());
    }

    @Test
    void save_rejects_empty_passphrase() throws Exception {
        tmp = Files.createTempDirectory("keystore-ut");
        UserKeystore ks = new UserKeystore(tmp);
        KeyPair kp = EccKeyIO.generate();
        assertThrows(IllegalArgumentException.class, () -> ks.saveKeypair("key-1", kp, new char[0]));
    }

    @Test
    void load_rejects_empty_passphrase() throws Exception {
        tmp = Files.createTempDirectory("keystore-ut");
        UserKeystore ks = new UserKeystore(tmp);
        ks.saveKeypair("key-1", EccKeyIO.generate(), "x".toCharArray());
        assertThrows(IllegalArgumentException.class, () -> ks.loadPrivate("key-1", new char[0]));
    }

}
