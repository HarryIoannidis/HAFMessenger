package com.haf.integration_test;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.crypto.MessageDecryptor;
import com.haf.shared.crypto.MessageEncryptor;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.ClockProvider;
import com.haf.shared.utils.FixedClockProvider;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.JsonCodec;
import org.junit.jupiter.api.Test;
import java.security.KeyPair;
import static org.junit.jupiter.api.Assertions.*;

class AadConsistencyIT {

    @Test
    void testAadConsistencyAfterHydration() throws Exception {
        KeyPair recipientKP = EccKeyIO.generate();
        FixedClockProvider clock = new FixedClockProvider(123456789L);

        MessageEncryptor encryptor = new MessageEncryptor(recipientKP.getPublic(), "sender", "recipient", clock);
        byte[] payload = "test message".getBytes();
        long ttl = 3600;

        EncryptedMessage original = encryptor.encrypt(payload, "text/plain", ttl);

        // Mimic EnvelopeDAO hydration
        EncryptedMessage hydrated = new EncryptedMessage();
        hydrated.setVersion(MessageHeader.VERSION); // Hardcoded in EnvelopeDAO
        hydrated.setAlgorithm(MessageHeader.ALGO_AEAD); // Hardcoded in EnvelopeDAO
        hydrated.setSenderId(original.getSenderId());
        hydrated.setRecipientId(original.getRecipientId());
        hydrated.setCiphertextB64(original.getCiphertextB64());
        hydrated.setEphemeralPublicB64(original.getEphemeralPublicB64());
        hydrated.setIvB64(original.getIvB64());
        hydrated.setTagB64(original.getTagB64());
        hydrated.setContentType(original.getContentType());
        hydrated.setContentLength(original.getContentLength());
        hydrated.setTimestampEpochMs(original.getTimestampEpochMs());
        hydrated.setTtlSeconds(original.getTtlSeconds());

        // Mimic server-to-client JSON transmission
        java.util.Map<String, Object> envelope = new java.util.HashMap<>();
        envelope.put("type", "message");
        envelope.put("payload", hydrated);
        String json = JsonCodec.toJson(envelope);

        // Mimic client's DefaultMessageReceiver deserialization
        java.util.Map<?, ?> clientEnvelope = JsonCodec.fromJson(json, java.util.Map.class);
        Object payloadObj = clientEnvelope.get("payload");
        EncryptedMessage fromJson = JsonCodec.fromJson(
                JsonCodec.toJson(payloadObj),
                EncryptedMessage.class);

        // Attempt decryption
        MessageDecryptor decryptor = new MessageDecryptor(recipientKP.getPrivate(), clock);
        byte[] decrypted = decryptor.decryptMessage(fromJson);

        assertArrayEquals(payload, decrypted, "Decryption should succeed after JSON cycle");

        // Fuzzing/Mutation tests: Change any AAD-contributing field and verify failure
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setVersion("2"));
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setAlgorithm("AES-GCM-WRONG"));
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setSenderId("someone_else"));
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setRecipientId("wrong_recipient"));
        testTampering(recipientKP.getPrivate(), clock, fromJson,
                m -> m.setTimestampEpochMs(m.getTimestampEpochMs() + 1));
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setTtlSeconds(m.getTtlSeconds() + 1));
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setContentType("application/octet-stream"));
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setContentLength(m.getContentLength() + 1));

        // Case sensitivity tests
        testTampering(recipientKP.getPrivate(), clock, fromJson, m -> m.setSenderId(m.getSenderId().toUpperCase()));
        testTampering(recipientKP.getPrivate(), clock, fromJson,
                m -> m.setRecipientId(m.getRecipientId().toUpperCase()));
    }

    private void testTampering(java.security.PrivateKey key, ClockProvider clock,
            EncryptedMessage original, java.util.function.Consumer<EncryptedMessage> mutator) throws Exception {
        // Create a copy by serializing and deserializing
        String json = JsonCodec.toJson(original);
        EncryptedMessage copy = JsonCodec.fromJson(json, EncryptedMessage.class);

        mutator.accept(copy);

        MessageDecryptor decryptor = new MessageDecryptor(key, clock);
        assertThrows(Exception.class, () -> {
            decryptor.decryptMessage(copy);
        }, "Modification of AAD field should be detected (either by validator or by tag check)");
    }
}
