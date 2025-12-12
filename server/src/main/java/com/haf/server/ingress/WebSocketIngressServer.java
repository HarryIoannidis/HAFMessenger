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

        // Note: The SSLContext provided by Main is created with TLSv1.3 only.
        // DefaultSSLWebSocketServerFactory uses that context to create SSLEngine instances,
        // so TLSv1.3 and cipher selections are enforced by the context itself.
        // If a future change requires per-connection tuning, consider a custom factory
        // that applies SSLParameters to each SSLEngine.

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
        // TODO(auth): Replace TEST_USER_ID with authenticated principal once auth is wired.
        String userId = TEST_USER_ID;
        connectionUsers.put(conn, userId);

        MailboxSubscriber subscriber = envelope -> sendEnvelope(conn, envelope);
        MailboxSubscription subscription = mailboxRouter.subscribe(userId, subscriber);
        subscriptions.put(conn, subscription);

        List<QueuedEnvelope> pending = mailboxRouter.fetchUndelivered(userId, 100);
        // Push any backlog to the newly connected client.
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
                conn.send(rateLimitJson(decision.retryAfterSeconds()));
                return;
            }

            // Parse minimal ACK payload without relying on reflective record access.
            java.util.Map<?,?> ackMap = JsonCodec.fromJson(message, java.util.Map.class);
            Object ids = ackMap.get("envelopeIds");
            if (ids instanceof java.util.List<?> list && !list.isEmpty()) {
                java.util.List<String> asStrings = new java.util.ArrayList<>(list.size());
                for (Object o : list) {
                    if (o != null) {
                        asStrings.add(String.valueOf(o));
                    }
                }
                // Only allow acknowledging envelopes owned by this user.
                mailboxRouter.acknowledgeOwned(userId, asStrings);
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
        // Build a compact JSON message manually to avoid reflective access to inner record types.
        String payloadJson = JsonCodec.toJson(envelope.payload());
        String json = "{\"type\":\"message\",\"envelopeId\":\"" + escape(envelope.envelopeId()) +
                "\",\"payload\":" + payloadJson + ",\"expiresAt\":" + envelope.expiresAtEpochMs() + "}";
        conn.send(json);
    }

    // Avoid using inner records for JSON I/O to keep Jackson from requiring deep reflection on the module.
    // Minimal helper methods for small response payloads follow.

    /**
     * Builds a minimal JSON payload describing a rate-limit event.
     * @param retryAfterSeconds the number of seconds to wait before retrying
     * @return a compact JSON string
     */
    private String rateLimitJson(long retryAfterSeconds) {
        return "{\"type\":\"rate_limit\",\"retryAfterSeconds\":" + Math.max(1, retryAfterSeconds) + "}";
    }

    /**
     * Escapes a string for safe embedding in a JSON string literal.
     * Only minimal escaping needed for envelope IDs (backslash, quotes).
     * @param s the input string
     * @return the escaped string
     */
    private static String escape(String s) {
        if (s == null) return "";
        // Minimal JSON string escape for ids (quotes and backslashes).
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

