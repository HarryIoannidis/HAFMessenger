package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.MailboxRouter.MailboxSubscriber;
import com.haf.server.router.MailboxRouter.MailboxSubscription;
import com.haf.server.router.RateLimiterService.RateLimitDecision;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.utils.JsonCodec;
import org.java_websocket.WebSocket;
import org.java_websocket.framing.CloseFrame;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WebSocketIngressServer extends WebSocketServer {

    private static final String TEST_USER_ID = "test-user";

    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiterService;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;

    private final Map<WebSocket, MailboxSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<WebSocket, String> connectionUsers = new ConcurrentHashMap<>();

    /**
     * Creates a WebSocketIngressServer with the given configuration and dependencies.
     * @param config the server configuration.
     * @param sslContext the SSL context.
     * @param mailboxRouter the mailbox router.
     * @param rateLimiterService the rate limiter service.
     * @param auditLogger the audit logger.
     * @param metricsRegistry the metrics registry.
     */
    public WebSocketIngressServer(ServerConfig config,
                                  SSLContext sslContext,
                                  MailboxRouter mailboxRouter,
                                  RateLimiterService rateLimiterService,
                                  AuditLogger auditLogger,
                                  MetricsRegistry metricsRegistry) {
        super(new InetSocketAddress(config.getWsPort()));
        setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));

        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setProtocols(new String[]{"TLSv1.3"});
        sslParams.setCipherSuites(new String[]{
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256"
        });

        this.mailboxRouter = mailboxRouter;
        this.rateLimiterService = rateLimiterService;
        this.auditLogger = auditLogger;
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * Handles the opening of a WebSocket connection.
     * @param conn the WebSocket instance this event is occurring on.
     * @param handshake the handshake of the websocket instance
     */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String userId = TEST_USER_ID;
        connectionUsers.put(conn, userId);

        MailboxSubscriber subscriber = envelope -> sendEnvelope(conn, envelope);
        MailboxSubscription subscription = mailboxRouter.subscribe(userId, subscriber);
        subscriptions.put(conn, subscription);

        List<QueuedEnvelope> pending = mailboxRouter.fetchUndelivered(userId, 100);
        pending.forEach(envelope -> sendEnvelope(conn, envelope));
    }

    /**
     * Handles the closing of a WebSocket connection.
     * @param conn the WebSocket instance this event is occurring on.
     * @param code  the codes can be looked up here: {@link CloseFrame}
     * @param reason additional information string
     * @param remote returns whether or not the closing of the connection was initiated by the remote host.
     */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        mailboxRouter.unsubscribe(subscriptions.remove(conn));
        connectionUsers.remove(conn);
    }

    /**
     * Handles the receipt of a message from a WebSocket connection.
     * @param conn the WebSocket instance this event is occurring on.
     * @param message the UTF-8 decoded message that was received.
     */
    @Override
    public void onMessage(WebSocket conn, String message) {
        String requestId = UUID.randomUUID().toString();
        String userId = connectionUsers.getOrDefault(conn, TEST_USER_ID);

        try {
            RateLimitDecision decision = rateLimiterService.checkAndConsume(requestId, userId);
            if (!decision.allowed()) {
                auditLogger.logRateLimit(requestId, userId, decision.retryAfterSeconds());
                conn.send(JsonCodec.toJson(new ErrorMessage("rate_limit", decision.retryAfterSeconds())));
                return;
            }

            AckMessage ack = JsonCodec.fromJson(message, AckMessage.class);
            if (ack.envelopeIds() != null && !ack.envelopeIds().isEmpty()) {
                mailboxRouter.acknowledge(ack.envelopeIds());
            }
        } catch (Exception ex) {
            auditLogger.logError("ws_ingress_error", requestId, userId, ex);
            metricsRegistry.incrementRejects();
            conn.close(1011, "internal");
        }
    }

    /**
     * Handles the error event.
     * @param conn can be null if there error does not belong to one specific websocket.
     * @param ex the exception causing this error
     */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        auditLogger.logError("ws_error", null, connectionUsers.get(conn), ex);
    }

    @Override
    public void onStart() {
        // no-op
    }

    /**
     * Sends an envelope to a WebSocket connection.
     * @param conn the WebSocket instance this event is occurring on.
     * @param envelope the envelope to send.
     */
    private void sendEnvelope(WebSocket conn, QueuedEnvelope envelope) {
        EnvelopeNotification notification = new EnvelopeNotification(
            "message",
            envelope.envelopeId(),
            envelope.payload(),
            envelope.expiresAtEpochMs()
        );
        conn.send(JsonCodec.toJson(notification));
    }

    /**
     * Represents an acknowledgement message.
     * @param envelopeIds the envelope IDs to acknowledge.
     */
    private record AckMessage(List<String> envelopeIds) {}

    /**
     * Represents an error message.
     * @param type the type of error.
     * @param retryAfterSeconds the number of seconds to wait before retrying.
     */
    private record ErrorMessage(String type, long retryAfterSeconds) {}

    /**
     * Represents an envelope notification.
     * @param type the type of notification.
     * @param envelopeId the envelope ID.
     * @param payload the payload.
     * @param expiresAt the expiration time of the envelope.
     */
    private record EnvelopeNotification(String type, String envelopeId, com.haf.shared.dto.EncryptedMessage payload, long expiresAt) {}
}

