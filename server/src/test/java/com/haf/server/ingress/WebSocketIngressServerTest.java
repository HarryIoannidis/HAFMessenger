package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.MailboxRouter.MailboxSubscription;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.net.ssl.SSLContext;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketIngressServerTest {

    @Mock
    private ServerConfig config;

    @Mock
    private MailboxRouter mailboxRouter;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private WebSocket webSocket;

    @Mock
    private ClientHandshake handshake;

    @Mock
    private MetricsRegistry metricsRegistry;

    private SSLContext sslContext;
    private WebSocketIngressServer server;

    @BeforeEach
    void setUp() throws Exception {
        sslContext = createTestSSLContext();

        // Use port 0 to get a random available port for tests
        when(config.getWsPort()).thenReturn(0);

        server = new WebSocketIngressServer(
            config, sslContext, mailboxRouter, rateLimiterService,
            auditLogger, metricsRegistry
        );
    }

    @Test
    void on_open_subscribes_user_and_sends_pending() {
        String userId = "test-user";
        QueuedEnvelope envelope = createQueuedEnvelope();
        when(mailboxRouter.subscribe(eq(userId), any())).thenReturn(new MailboxSubscription(userId, mock(MailboxRouter.MailboxSubscriber.class)));
        when(mailboxRouter.fetchUndelivered(eq(userId), eq(100))).thenReturn(List.of(envelope));

        server.onOpen(webSocket, handshake);

        verify(mailboxRouter, times(1)).subscribe(eq(userId), any());
        verify(mailboxRouter, times(1)).fetchUndelivered(eq(userId), eq(100));
        verify(webSocket, atLeastOnce()).send(anyString());
    }

    @Test
    void on_close_unsubscribes_user() {
        server.onClose(webSocket, 1000, "normal", true);

        verify(mailboxRouter, times(1)).unsubscribe(any());
    }

    @Test
    void on_message_processes_ack_when_allowed() {
        String ackJson = "{\"envelopeIds\":[\"id1\",\"id2\"]}";
        when(rateLimiterService.checkAndConsume(anyString(), anyString())).thenReturn(RateLimiterService.RateLimitDecision.allow());

        server.onMessage(webSocket, ackJson);

        verify(mailboxRouter, times(1)).acknowledge(anyList());
    }

    @Test
    void on_message_rejects_when_rate_limited() {
        String ackJson = "{\"envelopeIds\":[\"id1\"]}";
        when(rateLimiterService.checkAndConsume(anyString(), anyString()))
            .thenReturn(RateLimiterService.RateLimitDecision.block(60));

        server.onMessage(webSocket, ackJson);

        verify(auditLogger, times(1)).logRateLimit(anyString(), anyString(), eq(60L));
        verify(webSocket, times(1)).send(contains("rate_limit"));
        verify(mailboxRouter, never()).acknowledge(anyList());
    }

    @Test
    void on_message_closes_on_exception() {
        String invalidJson = "not json";
        when(rateLimiterService.checkAndConsume(anyString(), anyString())).thenReturn(RateLimiterService.RateLimitDecision.allow());

        server.onMessage(webSocket, invalidJson);

        verify(auditLogger, times(1)).logError(eq("ws_ingress_error"), anyString(), anyString(), any());
        verify(metricsRegistry, times(1)).incrementRejects();
        verify(webSocket, times(1)).close(eq(1011), eq("internal"));
    }

    @Test
    void on_error_logs_error() {
        Exception error = new RuntimeException("Test error");
        server.onError(webSocket, error);

        verify(auditLogger, times(1)).logError(eq("ws_error"), isNull(), any(), eq(error));
    }

    private SSLContext createTestSSLContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, "password".toCharArray());
        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
            javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm()
        );
        kmf.init(keyStore, "password".toCharArray());
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
            javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
        );
        tmf.init(keyStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private QueuedEnvelope createQueuedEnvelope() {
        EncryptedMessage message = new EncryptedMessage();
        message.version = MessageHeader.VERSION;
        message.algorithm = MessageHeader.ALGO_AEAD;
        message.senderId = "sender-123";
        message.recipientId = "recipient-456";
        message.timestampEpochMs = System.currentTimeMillis();
        message.ttlSeconds = (int) Duration.ofDays(1).toSeconds();
        message.ivB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]);
        message.wrappedKeyB64 = Base64.getEncoder().encodeToString(new byte[256]);
        message.ciphertextB64 = Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8));
        message.tagB64 = Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]);
        message.contentType = "text/plain";
        message.contentLength = 4;
        message.aadB64 = Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8));
        message.e2e = true;

        long createdAt = System.currentTimeMillis();
        long expiresAt = createdAt + 3600000;
        return new QueuedEnvelope("envelope-1", message, createdAt, expiresAt);
    }
}

