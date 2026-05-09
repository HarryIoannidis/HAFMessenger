package com.haf.shared.crypto;

import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.SigningKeyIO;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageSignatureServiceTest {

    @Test
    void sign_and_verify_roundtrip_succeeds() {
        KeyPair keyPair = SigningKeyIO.generate();
        String fingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(keyPair.getPublic()));
        EncryptedMessage message = createMessage();

        MessageSignatureService.sign(message, keyPair.getPrivate(), fingerprint);

        assertTrue(MessageSignatureService.verify(message, keyPair.getPublic()));
    }

    @Test
    void verify_fails_when_message_is_tampered() {
        KeyPair keyPair = SigningKeyIO.generate();
        String fingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(keyPair.getPublic()));
        EncryptedMessage message = createMessage();

        MessageSignatureService.sign(message, keyPair.getPrivate(), fingerprint);
        message.setCiphertextB64(Base64.getEncoder().encodeToString("tampered".getBytes(StandardCharsets.UTF_8)));

        assertFalse(MessageSignatureService.verify(message, keyPair.getPublic()));
    }

    @Test
    void verify_fails_when_signature_algorithm_is_invalid() {
        KeyPair keyPair = SigningKeyIO.generate();
        String fingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(keyPair.getPublic()));
        EncryptedMessage message = createMessage();

        MessageSignatureService.sign(message, keyPair.getPrivate(), fingerprint);
        message.setSignatureAlgorithm("unknown");

        assertFalse(MessageSignatureService.verify(message, keyPair.getPublic()));
    }

    private static EncryptedMessage createMessage() {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId("sender-1");
        message.setRecipientId("recipient-1");
        message.setTimestampEpochMs(1_700_000_000_000L);
        message.setTtlSeconds(3600L);
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setContentType("text/plain");
        message.setContentLength(5L);
        return message;
    }
}
