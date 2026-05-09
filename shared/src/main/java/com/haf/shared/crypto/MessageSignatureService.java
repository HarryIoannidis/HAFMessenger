package com.haf.shared.crypto;

import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

/**
 * Signs and verifies encrypted message envelopes with Ed25519.
 */
public final class MessageSignatureService {

    /**
     * Prevents instantiation of utility class.
     */
    private MessageSignatureService() {
    }

    /**
     * Signs a message envelope and stores signature fields in-place.
     *
     * @param message                message to sign
     * @param senderSigningPrivate   sender Ed25519 private key
     * @param senderSigningFingerprint sender signing key fingerprint
     */
    public static void sign(
            EncryptedMessage message,
            PrivateKey senderSigningPrivate,
            String senderSigningFingerprint) {
        if (message == null) {
            throw new IllegalArgumentException("message");
        }
        if (senderSigningPrivate == null) {
            throw new IllegalArgumentException("senderSigningPrivate");
        }
        if (senderSigningFingerprint == null || senderSigningFingerprint.isBlank()) {
            throw new IllegalArgumentException("senderSigningFingerprint");
        }

        message.setSignatureAlgorithm(MessageHeader.ALGO_SIGNATURE);
        message.setSenderSigningKeyFingerprint(senderSigningFingerprint.trim());

        try {
            Signature signature = Signature.getInstance(CryptoConstants.ED25519_SIGNATURE_ALGO);
            signature.initSign(senderSigningPrivate);
            signature.update(buildSigningInput(message));
            byte[] sig = signature.sign();
            message.setSignatureB64(Base64.getEncoder().encodeToString(sig));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign message", ex);
        }
    }

    /**
     * Verifies the signature of a message envelope.
     *
     * @param message            signed message envelope
     * @param senderSigningPublic sender Ed25519 public key
     * @return {@code true} when signature is valid
     */
    public static boolean verify(EncryptedMessage message, PublicKey senderSigningPublic) {
        if (message == null || senderSigningPublic == null) {
            return false;
        }
        if (!MessageHeader.ALGO_SIGNATURE.equals(message.getSignatureAlgorithm())) {
            return false;
        }
        if (message.getSignatureB64() == null || message.getSignatureB64().isBlank()) {
            return false;
        }
        try {
            byte[] provided = Base64.getDecoder().decode(message.getSignatureB64());
            Signature verifier = Signature.getInstance(CryptoConstants.ED25519_SIGNATURE_ALGO);
            verifier.initVerify(senderSigningPublic);
            verifier.update(buildSigningInput(message));
            return verifier.verify(provided);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Builds canonical bytes that are covered by the signature.
     *
     * @param m message envelope
     * @return canonical signing input bytes
     */
    public static byte[] buildSigningInput(EncryptedMessage m) {
        byte[] version = utf8(m.getVersion());
        byte[] algorithm = utf8(m.getAlgorithm());
        byte[] senderId = utf8(m.getSenderId());
        byte[] recipientId = utf8(m.getRecipientId());
        byte[] contentType = utf8(m.getContentType());
        byte[] ivB64 = utf8(m.getIvB64());
        byte[] ephemeralPublicB64 = utf8(m.getEphemeralPublicB64());
        byte[] ciphertextB64 = utf8(m.getCiphertextB64());
        byte[] tagB64 = utf8(m.getTagB64());
        byte[] signatureAlgorithm = utf8(m.getSignatureAlgorithm());
        byte[] senderSigningFp = utf8(m.getSenderSigningKeyFingerprint());

        int total = 4 + version.length
                + 4 + algorithm.length
                + 4 + senderId.length
                + 4 + recipientId.length
                + 8
                + 8
                + 4 + contentType.length
                + 8
                + 4 + ivB64.length
                + 4 + ephemeralPublicB64.length
                + 4 + ciphertextB64.length
                + 4 + tagB64.length
                + 4 + signatureAlgorithm.length
                + 4 + senderSigningFp.length;

        ByteBuffer bb = ByteBuffer.allocate(total);
        put(bb, version);
        put(bb, algorithm);
        put(bb, senderId);
        put(bb, recipientId);
        bb.putLong(m.getTimestampEpochMs());
        bb.putLong(m.getTtlSeconds());
        put(bb, contentType);
        bb.putLong(m.getContentLength());
        put(bb, ivB64);
        put(bb, ephemeralPublicB64);
        put(bb, ciphertextB64);
        put(bb, tagB64);
        put(bb, signatureAlgorithm);
        put(bb, senderSigningFp);
        return bb.array();
    }

    private static byte[] utf8(String s) {
        return s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8);
    }

    private static void put(ByteBuffer bb, byte[] value) {
        bb.putInt(value.length);
        if (value.length > 0) {
            bb.put(value);
        }
    }
}

