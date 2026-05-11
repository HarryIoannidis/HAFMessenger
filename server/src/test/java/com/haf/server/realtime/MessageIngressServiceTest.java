package com.haf.server.realtime;

import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.crypto.MessageSignatureService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.SigningKeyIO;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageIngressServiceTest {

    @Mock
    private User userDAO;
    @Mock
    private MailboxRouter mailboxRouter;
    @Mock
    private RateLimiterService rateLimiterService;
    @Mock
    private AuditLogger auditLogger;

    private MessageIngressService service;
    private KeyPair senderSigningPair;
    private String senderSigningFingerprint;
    private String senderSigningPem;

    @BeforeEach
    void setup() throws Exception {
        service = new MessageIngressService(
                new EncryptedMessageValidator(),
                userDAO,
                mailboxRouter,
                rateLimiterService,
                auditLogger);
        senderSigningPair = SigningKeyIO.generate();
        senderSigningFingerprint = FingerprintUtil.sha256Hex(SigningKeyIO.publicDer(senderSigningPair.getPublic()));
        senderSigningPem = SigningKeyIO.publicPem(senderSigningPair.getPublic());
    }

    @Test
    void accept_valid_encrypted_message_routes_opaque_payload_idempotently() throws Exception {
        EncryptedMessage message = signedMessage("sender-1", "recipient-1");
        QueuedEnvelope envelope = new QueuedEnvelope("env-1", message, 10L, 20L);
        when(userDAO.getPublicKey("sender-1"))
                .thenReturn(new User.PublicKeyRecord("enc-pem", "enc-fp", senderSigningPem,
                        senderSigningFingerprint));
        when(userDAO.getPublicKey("recipient-1"))
                .thenReturn(new User.PublicKeyRecord("recipient-pem", "recipient-fp", "sign-pem", "sign-fp"));
        when(rateLimiterService.checkAndConsume(anyString(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());
        when(mailboxRouter.ingressIdempotent(message, "client-1"))
                .thenReturn(new MailboxRouter.MailboxIngressResult(envelope, false));

        MessageIngressService.IngressAccepted accepted = service.accept(
                "request-1",
                "sender-1",
                message,
                "recipient-fp",
                "client-1",
                System.nanoTime());

        assertEquals("env-1", accepted.envelopeId());
        assertTrue(accepted.expiresAtEpochMs() > System.currentTimeMillis());
        verify(mailboxRouter).ingressIdempotent(message, "client-1");
    }

    @Test
    void rejects_sender_mismatch_before_rate_limit_or_routing() throws Exception {
        EncryptedMessage message = signedMessage("sender-2", "recipient-1");

        MessageIngressService.IngressRejectedException rejected = assertThrows(
                MessageIngressService.IngressRejectedException.class,
                () -> service.accept("request-1", "sender-1", message, null, "client-1", System.nanoTime()));

        assertEquals(403, rejected.statusCode());
        assertEquals("sender_mismatch", rejected.code());
        verify(rateLimiterService, never()).checkAndConsume(anyString(), anyString());
        verify(mailboxRouter, never()).ingressIdempotent(any(), anyString());
    }

    @Test
    void rejects_invalid_signature() throws Exception {
        EncryptedMessage message = signedMessage("sender-1", "recipient-1");
        message.setSignatureB64(Base64.getEncoder().encodeToString("bad".getBytes(StandardCharsets.UTF_8)));
        when(userDAO.getPublicKey("sender-1"))
                .thenReturn(new User.PublicKeyRecord("enc-pem", "enc-fp", senderSigningPem,
                        senderSigningFingerprint));

        MessageIngressService.IngressRejectedException rejected = assertThrows(
                MessageIngressService.IngressRejectedException.class,
                () -> service.accept("request-1", "sender-1", message, null, "client-1", System.nanoTime()));

        assertEquals(400, rejected.statusCode());
        assertEquals("invalid_signature", rejected.code());
        verify(mailboxRouter, never()).ingressIdempotent(any(), anyString());
    }

    @Test
    void rejects_stale_recipient_key_fingerprint() throws Exception {
        EncryptedMessage message = signedMessage("sender-1", "recipient-1");
        when(userDAO.getPublicKey("sender-1"))
                .thenReturn(new User.PublicKeyRecord("enc-pem", "enc-fp", senderSigningPem,
                        senderSigningFingerprint));
        when(userDAO.getPublicKey("recipient-1"))
                .thenReturn(new User.PublicKeyRecord("recipient-pem", "fresh-fp", "sign-pem", "sign-fp"));

        MessageIngressService.IngressRejectedException rejected = assertThrows(
                MessageIngressService.IngressRejectedException.class,
                () -> service.accept("request-1", "sender-1", message, "stale-fp", "client-1",
                        System.nanoTime()));

        assertEquals(409, rejected.statusCode());
        assertEquals("stale_recipient_key", rejected.code());
        verify(rateLimiterService, never()).checkAndConsume(anyString(), anyString());
        verify(mailboxRouter, never()).ingressIdempotent(any(), anyString());
    }

    @Test
    void rejects_rate_limit_without_routing() throws Exception {
        EncryptedMessage message = signedMessage("sender-1", "recipient-1");
        when(userDAO.getPublicKey("sender-1"))
                .thenReturn(new User.PublicKeyRecord("enc-pem", "enc-fp", senderSigningPem,
                        senderSigningFingerprint));
        when(rateLimiterService.checkAndConsume(anyString(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.block(15));

        MessageIngressService.IngressRejectedException rejected = assertThrows(
                MessageIngressService.IngressRejectedException.class,
                () -> service.accept("request-1", "sender-1", message, null, "client-1", System.nanoTime()));

        assertEquals(429, rejected.statusCode());
        assertEquals("rate_limit", rejected.code());
        assertEquals(15, rejected.retryAfterSeconds());
        verify(mailboxRouter, never()).ingressIdempotent(any(), anyString());
    }

    private EncryptedMessage signedMessage(String senderId, String recipientId) throws Exception {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId(senderId);
        message.setRecipientId(recipientId);
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds(3600);
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[32]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("ciphertext".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setAadB64(Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8)));
        message.setContentType("text/plain");
        message.setContentLength(10);
        message.setE2e(true);
        MessageSignatureService.sign(message, senderSigningPair.getPrivate(), senderSigningFingerprint);
        return message;
    }
}
