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
import com.haf.server.db.SessionDAO;
import org.java_websocket.framing.CloseFrame;
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

    @Mock
    private SessionDAO sessionDAO;

    private SSLContext sslContext;
    private WebSocketIngressServer server;

    @BeforeEach
    void setUp() throws Exception {
        sslContext = createTestSSLContext();

        // Use port 0 to get a random available port for tests
        when(config.getWsPort()).thenReturn(0);

        server = new WebSocketIngressServer(
                config, sslContext, mailboxRouter, rateLimiterService,
                auditLogger, metricsRegistry, sessionDAO);
    }

    @Test
    void on_open_subscribes_user_and_sends_pending() {
        String userId = "test-user";
        String sessionId = "valid-session";
        when(handshake.getFieldValue("Authorization")).thenReturn("Bearer " + sessionId);
        when(sessionDAO.getUserIdForSession(sessionId)).thenReturn(userId);

        QueuedEnvelope envelope = createQueuedEnvelope();
        when(mailboxRouter.subscribe(eq(userId), any()))
                .thenReturn(new MailboxSubscription(userId, mock(MailboxRouter.MailboxSubscriber.class)));
        when(mailboxRouter.fetchUndelivered(userId, 100)).thenReturn(List.of(envelope));

        server.onOpen(webSocket, handshake);

        verify(mailboxRouter, times(1)).subscribe(eq(userId), any());
        verify(mailboxRouter, times(1)).fetchUndelivered(userId, 100);
        verify(webSocket, atLeastOnce()).send(anyString());
    }

    @Test
    void on_open_rejects_unauthorized() {
        when(handshake.getFieldValue("Authorization")).thenReturn(null);

        server.onOpen(webSocket, handshake);

        verify(webSocket, times(1)).closeConnection(CloseFrame.POLICY_VALIDATION, "Unauthorized");
        verify(mailboxRouter, never()).subscribe(anyString(), any());
    }

    @Test
    void on_close_unsubscribes_user() {
        server.onClose(webSocket, 1000, "normal", true);

        verify(mailboxRouter, times(1)).unsubscribe(any());
    }

    @Test
    void on_message_processes_ack_when_allowed() throws Exception {
        setConnectionUser(webSocket, "test-user");
        String ackJson = "{\"envelopeIds\":[\"id1\",\"id2\"]}";
        when(rateLimiterService.checkAndConsume(anyString(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());

        server.onMessage(webSocket, ackJson);

        verify(mailboxRouter, times(1)).acknowledgeOwned(eq("test-user"), anyList());
    }

    @Test
    void on_message_rejects_when_rate_limited() throws Exception {
        setConnectionUser(webSocket, "test-user");
        String ackJson = "{\"envelopeIds\":[\"id1\"]}";
        when(rateLimiterService.checkAndConsume(anyString(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.block(60));

        server.onMessage(webSocket, ackJson);

        verify(auditLogger, times(1)).logRateLimit(anyString(), anyString(), eq(60L));
        verify(webSocket, times(1)).send(contains("rate_limit"));
        verify(mailboxRouter, never()).acknowledgeOwned(anyString(), anyList());
    }

    @Test
    void on_message_closes_on_exception() throws Exception {
        setConnectionUser(webSocket, "test-user");
        String invalidJson = "not json";
        when(rateLimiterService.checkAndConsume(anyString(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());

        server.onMessage(webSocket, invalidJson);

        verify(auditLogger, times(1)).logError(eq("ws_ingress_error"), anyString(), anyString(), any());
        verify(metricsRegistry, times(1)).incrementRejects();
        verify(webSocket, times(1)).close(1011, "internal");
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
                javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());
        javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }

    private QueuedEnvelope createQueuedEnvelope() {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId("sender-123");
        message.setRecipientId("recipient-456");
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds((int) Duration.ofDays(1).toSeconds());
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setContentType("text/plain");
        message.setContentLength(4);
        message.setAadB64(Base64.getEncoder().encodeToString("aad".getBytes(StandardCharsets.UTF_8)));
        message.setE2e(true);

        long createdAt = System.currentTimeMillis();
        long expiresAt = createdAt + 3600000;
        return new QueuedEnvelope("envelope-1", message, createdAt, expiresAt);
    }

    @SuppressWarnings("unchecked")
    private void setConnectionUser(WebSocket webSocket, String userId) throws Exception {
        java.lang.reflect.Field field = WebSocketIngressServer.class.getDeclaredField("connectionUsers");
        field.setAccessible(true);
        ((java.util.Map<WebSocket, String>) field.get(server)).put(webSocket, userId);
    }
}
