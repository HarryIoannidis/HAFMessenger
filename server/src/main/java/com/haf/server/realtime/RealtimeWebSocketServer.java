package com.haf.server.realtime;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.Contact;
import com.haf.server.db.Session;
import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.websocket.RealtimeEvent;
import com.haf.shared.websocket.RealtimeEventType;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.SSLParametersWebSocketServerFactory;
import org.java_websocket.server.WebSocketServer;

/**
 * TLS WebSocket gateway for authenticated realtime messaging events.
 */
public final class RealtimeWebSocketServer extends WebSocketServer implements AutoCloseable {

    private static final Logger LOGGER = LogManager.getLogger(RealtimeWebSocketServer.class);
    private static final String AUTHORIZATION = "Authorization";
    private static final int POLICY_CLOSE = 1008;
    private static final int NORMAL_CLOSE = 1000;
    private static final String SESSION_REPLACED_REASON = "session replaced";
    private static final int MAX_EVENT_BYTES = 10 * 1024 * 1024;
    private static final long REPLAY_WINDOW_MS = 120_000L;
    private static final int BACKLOG_LIMIT = 100;
    private static final int RECEIPT_BACKLOG_LIMIT = 500;
    private static final int MAX_RECEIPT_BATCH = 200;

    private final MailboxRouter mailboxRouter;
    private final Session sessionDAO;
    private final Contact contactDAO;
    private final AuditLogger auditLogger;
    private final MessageIngressService messageIngressService;
    private final String realtimePath;
    private final ConcurrentHashMap<WebSocket, SocketSession> sessionsBySocket = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SocketSession> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReplayGuard> userReplayGuards = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EventWindow> userEventWindows = new ConcurrentHashMap<>();

    /**
     * Create a TLS WebSocket server for authenticated realtime messaging.
     *
     * @param config             server configuration (port, TLS settings)
     * @param sslContext         TLS context for securing WebSocket connections
     * @param mailboxRouter      message routing and mailbox subscription service
     * @param rateLimiterService per-user rate limiter for ingress events
     * @param auditLogger        structured audit logging service
     * @param validator          encrypted message validation service
     * @param userDAO            user lookup (for verifying sender keys)
     * @param sessionDAO         session authentication and touch service
     * @param contactDAO         contact relationship lookup service
     */
    public RealtimeWebSocketServer(
            ServerConfig config,
            SSLContext sslContext,
            MailboxRouter mailboxRouter,
            RateLimiterService rateLimiterService,
            AuditLogger auditLogger,
            EncryptedMessageValidator validator,
            User userDAO,
            Session sessionDAO,
            Contact contactDAO) {
        super(new InetSocketAddress(config.getWsPort()));
        this.mailboxRouter = Objects.requireNonNull(mailboxRouter, "mailboxRouter");
        this.sessionDAO = Objects.requireNonNull(sessionDAO, "sessionDAO");
        this.contactDAO = Objects.requireNonNull(contactDAO, "contactDAO");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger");
        this.realtimePath = Objects.requireNonNull(config.getWsPath(), "wsPath");
        this.messageIngressService = new MessageIngressService(
                validator,
                userDAO,
                mailboxRouter,
                rateLimiterService,
                auditLogger);
        SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
        sslParameters.setProtocols(new String[] { "TLSv1.3" });
        sslParameters.setCipherSuites(new String[] {
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256"
        });
        setWebSocketFactory(new SSLParametersWebSocketServerFactory(sslContext, sslParameters));
    }

    /**
     * Handle new WebSocket connection. Validates the resource path, extracts
     * and verifies the bearer token, subscribes the user to their mailbox,
     * and pushes any undelivered backlog.
     *
     * @param conn      the new WebSocket connection
     * @param handshake the client handshake containing auth headers
     */
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String requestId = UUID.randomUUID().toString();
        String resource = handshake == null ? "" : handshake.getResourceDescriptor();
        if (!isValidRealtimeResource(resource)) {
            conn.close(POLICY_CLOSE, "invalid realtime path");
            return;
        }

        String token = extractBearerToken(handshake);
        if (token == null) {
            conn.close(POLICY_CLOSE, "missing auth");
            return;
        }

        String userId = sessionDAO.getUserIdForSessionAndTouch(token);
        if (userId == null) {
            conn.close(POLICY_CLOSE, "invalid session");
            return;
        }

        SocketSession session = new SocketSession(
                conn,
                userId,
                token,
                userReplayGuards.computeIfAbsent(userId, ignored -> new ReplayGuard()));
        MailboxRouter.MailboxSubscription subscription = mailboxRouter.subscribe(
                userId,
                envelope -> sendEnvelope(session, envelope));
        session.subscription = subscription;
        conn.setAttachment(session);
        sessionsBySocket.put(conn, session);

        SocketSession previous = sessionsByUser.put(userId, session);
        boolean wasOffline = previous == null;
        if (previous != null && previous.connection != conn) {
            notifySessionReplaced(previous);
            if (!previous.accessToken.equals(session.accessToken)) {
                sessionDAO.revokeSession(previous.accessToken);
            }
            closeSession(previous, POLICY_CLOSE, SESSION_REPLACED_REASON);
        }

        LOGGER.info("Realtime WSS connected requestId={} userId={}", requestId, userId);
        if (wasOffline) {
            announcePresence(userId, true);
        }
        pushBacklog(session);
        pushReceiptBacklog(session);
    }

    /**
     * Handle incoming WebSocket message. Deserializes the event, validates
     * the session, checks replay and rate-limit guards, then dispatches
     * to the appropriate handler.
     *
     * @param conn       the WebSocket connection that sent the message
     * @param rawMessage the raw JSON event payload
     */
    @Override
    public void onMessage(WebSocket conn, String rawMessage) {
        SocketSession session = sessionsBySocket.get(conn);
        if (session == null) {
            conn.close(POLICY_CLOSE, "unauthenticated");
            return;
        }

        String requestId = UUID.randomUUID().toString();
        long start = System.nanoTime();
        if (rawMessage == null
                || rawMessage.length() > MAX_EVENT_BYTES
                || rawMessage.getBytes(StandardCharsets.UTF_8).length > MAX_EVENT_BYTES) {
            sendError(session, requestId, null, "invalid_payload", "invalid payload", 0);
            return;
        }

        try {
            RealtimeEvent event = JsonCodec.fromJson(rawMessage, RealtimeEvent.class);
            RealtimeEventType type = event == null ? null : event.eventType();
            if (type == null) {
                sendError(session, requestId, null, "invalid_type", "invalid event type", 0);
                return;
            }
            requestId = safeRequestId(event.getEventId(), requestId);
            if (!revalidateSession(session)) {
                closeSession(session, POLICY_CLOSE, "invalid session");
                return;
            }
            if (!eventGuardAccepts(session, event, requestId)) {
                return;
            }
            if (!consumeRealtimeLimit(session, type)) {
                sendError(session, requestId, event.getEventId(), "rate_limit", "rate limit", 30);
                return;
            }

            switch (type) {
                case SEND_MESSAGE -> handleSendMessage(session, event, requestId, start);
                case MESSAGE_DELIVERED -> handleReceipt(session, event, RealtimeEventType.MESSAGE_DELIVERED);
                case MESSAGE_READ -> handleReceipt(session, event, RealtimeEventType.MESSAGE_READ);
                case TYPING_START, TYPING_STOP -> handleTyping(session, event, type);
                case HEARTBEAT -> handleHeartbeat(session, event);
                default ->
                    sendError(session, requestId, event.getEventId(), "unsupported_event", "unsupported event", 0);
            }
        } catch (Exception ex) {
            auditLogger.logError("wss_event_error", requestId, session.userId, ex, Map.of("event", "unknown"));
            sendError(session, requestId, null, "invalid_payload", "invalid payload", 0);
        }
    }

    /**
     * Reject binary websocket frames. The realtime protocol is JSON text only.
     *
     * @param conn    connection that sent the binary frame
     * @param message ignored binary payload
     */
    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        SocketSession session = sessionsBySocket.get(conn);
        if (session != null) {
            sendError(session, UUID.randomUUID().toString(), null, "unsupported_frame",
                    "binary frames are not supported", 0);
        }
        conn.close(POLICY_CLOSE, "binary frames unsupported");
    }

    /**
     * Handle WebSocket connection close. Cleans up the session and
     * announces offline presence to watchers.
     *
     * @param conn   the closed WebSocket connection
     * @param code   the WebSocket close code
     * @param reason the close reason string
     * @param remote true if the close was initiated by the remote peer
     */
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        SocketSession session = sessionsBySocket.remove(conn);
        if (session == null) {
            return;
        }
        cleanupSession(session);
        LOGGER.info("Realtime WSS disconnected userId={} code={}", session.userId, code);
    }

    /**
     * Handle WebSocket transport-level errors. Logs the error via the
     * audit logger for diagnostics.
     *
     * @param conn the WebSocket connection that encountered the error
     * @param ex   the exception that was thrown
     */
    @Override
    public void onError(WebSocket conn, Exception ex) {
        SocketSession session = conn == null ? null : sessionsBySocket.get(conn);
        auditLogger.logError(
                "wss_connection_error",
                null,
                session == null ? null : session.userId,
                ex);
    }

    /**
     * Called when the server is started and ready to accept connections.
     * Logs the server start event with the port number.
     */
    @Override
    public void onStart() {
        LOGGER.info("Realtime WSS server started on {}", getPort());
    }

    /**
     * Gracefully shut down the server. Closes all active sessions with a
     * normal close code and stops the underlying WebSocket server.
     */
    @Override
    public void close() {
        for (SocketSession session : List.copyOf(sessionsBySocket.values())) {
            closeSession(session, NORMAL_CLOSE, "server shutdown");
        }
        try {
            stop(0, "server shutdown");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Process a SEND_MESSAGE event by validating the client message ID,
     * delegating to the ingress service, and responding with an acceptance
     * or rejection event.
     *
     * @param session    authenticated socket session
     * @param event      the incoming SEND_MESSAGE event
     * @param requestId  unique request identifier for audit tracing
     * @param startNanos monotonic timestamp at message reception
     */
    private void handleSendMessage(SocketSession session, RealtimeEvent event, String requestId, long startNanos) {
        if (event.getClientMessageId() == null || event.getClientMessageId().isBlank()
                || event.getClientMessageId().length() > 128) {
            sendError(session, requestId, event.getEventId(), "invalid_client_message_id",
                    "invalid client message id", 0);
            return;
        }
        EncryptedMessage message = event.getEncryptedMessage();
        try {
            MessageIngressService.IngressAccepted accepted = messageIngressService.accept(
                    requestId,
                    session.userId,
                    message,
                    event.getRecipientKeyFingerprint(),
                    event.getClientMessageId(),
                    startNanos);
            RealtimeEvent response = RealtimeEvent.serverEvent(RealtimeEventType.SEND_ACCEPTED);
            response.setCorrelationId(event.getEventId());
            response.setSenderId(session.userId);
            response.setRecipientId(message == null ? null : message.getRecipientId());
            response.setEnvelopeId(accepted.envelopeId());
            response.setExpiresAtEpochMs(accepted.expiresAtEpochMs());
            response.setDuplicate(accepted.duplicate());
            sendSafe(session, response);
        } catch (MessageIngressService.IngressRejectedException rejected) {
            sendError(
                    session,
                    requestId,
                    event.getEventId(),
                    rejected.code(),
                    rejected.getMessage(),
                    rejected.retryAfterSeconds());
        }
    }

    /**
     * Process a delivery or read receipt event. Verifies envelope ownership,
     * acknowledges delivery if applicable, and forwards the receipt to the
     * original sender.
     *
     * @param session     authenticated socket session
     * @param event       the incoming receipt event
     * @param receiptType either {@link RealtimeEventType#MESSAGE_DELIVERED}
     *                    or {@link RealtimeEventType#MESSAGE_READ}
     */
    private void handleReceipt(SocketSession session, RealtimeEvent event, RealtimeEventType receiptType) {
        List<String> envelopeIds = normalizeEnvelopeIds(event);
        if (envelopeIds.isEmpty()) {
            sendError(session, event.getEventId(), event.getEventId(), "invalid_receipt", "envelopeIds required", 0);
            return;
        }

        List<QueuedEnvelope> owned = receiptType == RealtimeEventType.MESSAGE_DELIVERED
                ? mailboxRouter.acknowledgeOwnedAndReturn(session.userId, envelopeIds)
                : mailboxRouter.markReadOwnedAndReturn(session.userId, envelopeIds);
        if (owned.isEmpty()) {
            sendError(session, event.getEventId(), event.getEventId(), "unauthorized", "unauthorized receipt", 0);
            return;
        }

        Map<String, List<String>> envelopeIdsBySender = new HashMap<>();
        for (QueuedEnvelope envelope : owned) {
            String senderId = envelope.payload().getSenderId();
            if (!isContactAuthorized(session.userId, senderId)) {
                continue;
            }
            envelopeIdsBySender.computeIfAbsent(senderId, ignored -> new ArrayList<>()).add(envelope.envelopeId());
        }

        envelopeIdsBySender.forEach((senderId, ids) -> {
            RealtimeEvent outbound = RealtimeEvent.serverEvent(receiptType);
            outbound.setSenderId(session.userId);
            outbound.setRecipientId(senderId);
            outbound.setEnvelopeIds(ids);
            sendToUser(senderId, outbound);
        });
    }

    /**
     * Process a typing indicator event. Validates the recipient is a
     * mutual contact and forwards the typing state.
     *
     * @param session authenticated socket session
     * @param event   the incoming typing event
     * @param type    either {@link RealtimeEventType#TYPING_START}
     *                or {@link RealtimeEventType#TYPING_STOP}
     */
    private void handleTyping(SocketSession session, RealtimeEvent event, RealtimeEventType type) {
        String recipientId = normalizeId(event.getRecipientId());
        if (recipientId == null || recipientId.equals(session.userId)
                || !isContactAuthorized(session.userId, recipientId)) {
            sendError(session, event.getEventId(), event.getEventId(), "unauthorized", "unauthorized typing target", 0);
            return;
        }

        RealtimeEvent outbound = RealtimeEvent.serverEvent(type);
        outbound.setSenderId(session.userId);
        outbound.setRecipientId(recipientId);
        sendToUser(recipientId, outbound);
    }

    /**
     * Respond to a heartbeat event with a correlated heartbeat reply.
     *
     * @param session authenticated socket session
     * @param event   the incoming heartbeat event
     */
    private void handleHeartbeat(SocketSession session, RealtimeEvent event) {
        RealtimeEvent response = RealtimeEvent.serverEvent(RealtimeEventType.HEARTBEAT);
        response.setCorrelationId(event.getEventId());
        response.setSenderId(session.userId);
        sendSafe(session, response);
    }

    /**
     * Push undelivered mailbox backlog to a newly connected session.
     *
     * @param session the session to push backlog to
     */
    private void pushBacklog(SocketSession session) {
        List<QueuedEnvelope> pending = mailboxRouter.fetchUndelivered(session.userId, BACKLOG_LIMIT);
        for (QueuedEnvelope envelope : pending) {
            sendEnvelope(session, envelope);
        }
    }

    /**
     * Replay persisted delivery/read receipt state to a reconnecting sender.
     *
     * @param session sender's authenticated socket session
     */
    private void pushReceiptBacklog(SocketSession session) {
        List<MailboxRouter.ReceiptReplay> receipts = mailboxRouter.fetchReceiptReplayForSender(
                session.userId,
                RECEIPT_BACKLOG_LIMIT);
        Map<String, List<String>> deliveredByRecipient = new HashMap<>();
        Map<String, List<String>> readByRecipient = new HashMap<>();
        for (MailboxRouter.ReceiptReplay receipt : receipts) {
            if (receipt == null || receipt.envelopeId() == null || receipt.recipientId() == null
                    || !isContactAuthorized(session.userId, receipt.recipientId())) {
                continue;
            }
            Map<String, List<String>> target = receipt.read() ? readByRecipient : deliveredByRecipient;
            target.computeIfAbsent(receipt.recipientId(), ignored -> new ArrayList<>()).add(receipt.envelopeId());
        }
        deliveredByRecipient.forEach((recipientId, envelopeIds) -> sendReceiptReplay(session, recipientId, envelopeIds,
                RealtimeEventType.MESSAGE_DELIVERED));
        readByRecipient.forEach((recipientId, envelopeIds) -> sendReceiptReplay(session, recipientId, envelopeIds,
                RealtimeEventType.MESSAGE_READ));
    }

    private void sendReceiptReplay(
            SocketSession session,
            String originalRecipientId,
            List<String> envelopeIds,
            RealtimeEventType receiptType) {
        RealtimeEvent event = RealtimeEvent.serverEvent(receiptType);
        event.setSenderId(originalRecipientId);
        event.setRecipientId(session.userId);
        event.setEnvelopeIds(envelopeIds);
        sendSafe(session, event);
    }

    private void notifySessionReplaced(SocketSession session) {
        RealtimeEvent event = RealtimeEvent.error("session_replaced", "session revoked by takeover");
        sendSafe(session, event);
    }

    /**
     * Wrap a queued envelope in a NEW_MESSAGE realtime event and send it
     * to the session.
     *
     * @param session  target socket session
     * @param envelope the queued envelope to deliver
     */
    private void sendEnvelope(SocketSession session, QueuedEnvelope envelope) {
        if (session == null || envelope == null) {
            return;
        }
        RealtimeEvent event = RealtimeEvent.serverEvent(RealtimeEventType.NEW_MESSAGE);
        event.setSenderId(envelope.payload().getSenderId());
        event.setRecipientId(envelope.payload().getRecipientId());
        event.setEnvelopeId(envelope.envelopeId());
        event.setExpiresAtEpochMs(envelope.expiresAtEpochMs());
        event.setEncryptedMessage(envelope.payload());
        sendSafe(session, event);
    }

    /**
     * Broadcast a presence update to all watchers of the given user.
     *
     * @param userId the user whose presence changed
     * @param active true if the user came online, false if going offline
     */
    private void announcePresence(String userId, boolean active) {
        List<String> watchers = contactDAO.getWatcherUserIds(userId);
        for (String watcherId : watchers) {
            if (!isContactAuthorized(watcherId, userId)) {
                continue;
            }
            RealtimeEvent event = RealtimeEvent.serverEvent(RealtimeEventType.PRESENCE_UPDATE);
            event.setSenderId(userId);
            event.setRecipientId(watcherId);
            event.setActive(active);
            sendToUser(watcherId, event);
        }
    }

    /**
     * Send a realtime event to a connected user, if online.
     *
     * @param userId the target user's ID
     * @param event  the event to deliver
     */
    private void sendToUser(String userId, RealtimeEvent event) {
        SocketSession target = sessionsByUser.get(userId);
        if (target != null) {
            sendSafe(target, event);
        }
    }

    /**
     * Send an error event to the client and log the rejection.
     *
     * @param session           target socket session
     * @param requestId         request identifier for audit tracing
     * @param correlationId     the client event ID to correlate the error with
     * @param code              short error code string
     * @param message           human-readable error message
     * @param retryAfterSeconds suggested retry delay (0 if not applicable)
     */
    private void sendError(
            SocketSession session,
            String requestId,
            String correlationId,
            String code,
            String message,
            long retryAfterSeconds) {
        RealtimeEvent error = RealtimeEvent.error(code, message);
        error.setCorrelationId(correlationId);
        error.setRetryAfterSeconds(retryAfterSeconds);
        sendSafe(session, error);
        LOGGER.warn(
                "Realtime WSS rejected requestId={} userId={} code={}",
                requestId,
                session == null ? null : session.userId,
                code);
    }

    /**
     * Safely serialize and send a realtime event to the client. If the send
     * fails, the session is closed and the error is logged.
     *
     * @param session target socket session
     * @param event   the event to serialize and send
     */
    private void sendSafe(SocketSession session, RealtimeEvent event) {
        if (session == null || event == null || !session.connection.isOpen()) {
            return;
        }
        try {
            session.connection.send(JsonCodec.toJson(event));
        } catch (Exception ex) {
            auditLogger.logError("wss_send_error", event.getEventId(), session.userId, ex,
                    Map.of("eventType", event.getType()));
            closeSession(session, POLICY_CLOSE, "send failed");
        }
    }

    /**
     * Re-check that the session's access token is still valid by touching
     * the session store.
     *
     * @param session the session to revalidate
     * @return true if the session is still valid
     */
    private boolean revalidateSession(SocketSession session) {
        String currentUserId = sessionDAO.getUserIdForSessionAndTouch(session.accessToken);
        return session.userId.equals(currentUserId);
    }

    /**
     * Validate event metadata (eventId, nonce, timestamp) and check for
     * replayed events within the replay window.
     *
     * @param session   authenticated socket session
     * @param event     the incoming event to validate
     * @param requestId request identifier for error reporting
     * @return true if the event passes all guards
     */
    private boolean eventGuardAccepts(SocketSession session, RealtimeEvent event, String requestId) {
        if (event.getEventId() == null || event.getEventId().isBlank()
                || event.getNonce() == null || event.getNonce().isBlank()
                || event.getTimestampEpochMs() <= 0) {
            sendError(session, requestId, event.getEventId(), "invalid_event_metadata",
                    "invalid event metadata", 0);
            return false;
        }

        long now = System.currentTimeMillis();
        pruneReplayGuards(now);
        if (Math.abs(now - event.getTimestampEpochMs()) > REPLAY_WINDOW_MS) {
            sendError(session, requestId, event.getEventId(), "stale_event", "stale event", 0);
            return false;
        }

        String replayKey = event.getEventId().trim() + ":" + event.getNonce().trim();
        if (!session.replayGuard.accept(replayKey, now)) {
            sendError(session, requestId, event.getEventId(), "replay_rejected", "replay rejected", 0);
            return false;
        }
        return true;
    }

    /**
     * Check and consume a per-socket and per-user rate limit token for the
     * given event type.
     *
     * @param session the socket session owning the rate limiter
     * @param type    the event type to rate-limit
     * @return true if the event is within rate limits
     */
    private boolean consumeRealtimeLimit(SocketSession session, RealtimeEventType type) {
        long now = System.currentTimeMillis();
        pruneUserEventWindows(now);
        int limit = limitFor(type);
        String userKey = session.userId + ":" + type.name();
        return session.socketRateLimiter.accept(type, limit, now)
                && userEventWindows.computeIfAbsent(userKey, ignored -> new EventWindow()).accept(limit, now);
    }

    /**
     * Return the per-minute rate limit for the given event type.
     *
     * @param type the realtime event type
     * @return maximum allowed events per minute
     */
    private static int limitFor(RealtimeEventType type) {
        return switch (type) {
            case SEND_MESSAGE -> 100;
            case TYPING_START, TYPING_STOP -> 120;
            case MESSAGE_DELIVERED, MESSAGE_READ -> 300;
            case HEARTBEAT -> 120;
            default -> 180;
        };
    }

    /**
     * Close a socket session with the given close code and reason, then
     * clean up all associated state.
     *
     * @param session the session to close
     * @param code    WebSocket close code
     * @param reason  human-readable close reason
     */
    private void closeSession(SocketSession session, int code, String reason) {
        if (session == null) {
            return;
        }
        try {
            session.connection.close(code, reason);
        } finally {
            cleanupSession(session);
        }
    }

    /**
     * Remove server-side state for a disconnected socket. Per-user replay
     * guards and rate-limit windows are retained until their security window
     * expires so reconnects cannot reset them.
     *
     * @param session the session to clean up
     */
    private void cleanupSession(SocketSession session) {
        sessionsBySocket.remove(session.connection);
        boolean removedActiveSession = sessionsByUser.remove(session.userId, session);
        mailboxRouter.unsubscribe(session.subscription);
        session.socketRateLimiter.clear();
        if (removedActiveSession) {
            announcePresence(session.userId, false);
        }
    }

    private void pruneReplayGuards(long now) {
        userReplayGuards.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private void pruneUserEventWindows(long now) {
        userEventWindows.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    /**
     * Check whether a bidirectional contact relationship exists between
     * two users.
     *
     * @param userId    the first user
     * @param contactId the second user
     * @return true if either user can reach the other
     */
    private boolean isContactAuthorized(String userId, String contactId) {
        return contactDAO.canReach(userId, contactId) || contactDAO.canReach(contactId, userId);
    }

    /**
     * Extract the bearer token from the Authorization header in the
     * WebSocket handshake.
     *
     * @param handshake the client handshake
     * @return the bearer token string, or {@code null} if missing or invalid
     */
    private static String extractBearerToken(ClientHandshake handshake) {
        if (handshake == null) {
            return null;
        }
        String authHeader = handshake.getFieldValue(AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        return token.isBlank() ? null : token;
    }

    /**
     * Validate that the requested WebSocket resource path matches the
     * expected realtime endpoint and contains no query parameters.
     *
     * @param resource the resource descriptor from the handshake
     * @return true if the resource path is valid
     */
    private boolean isValidRealtimeResource(String resource) {
        if (resource == null || resource.isBlank() || resource.contains("?")) {
            return false;
        }
        return realtimePath.equals(resource);
    }

    /**
     * Collect and deduplicate envelope IDs from both the single
     * {@code envelopeId} field and the batch {@code envelopeIds} list,
     * capped at {@link #MAX_RECEIPT_BATCH}.
     *
     * @param event the realtime event containing envelope identifiers
     * @return deduplicated list of envelope ID strings
     */
    private static List<String> normalizeEnvelopeIds(RealtimeEvent event) {
        if (event == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        if (event.getEnvelopeId() != null && !event.getEnvelopeId().isBlank()) {
            ids.add(event.getEnvelopeId().trim());
        }
        Collection<String> eventEnvelopeIds = event.getEnvelopeIds();
        if (eventEnvelopeIds != null) {
            for (String id : eventEnvelopeIds) {
                if (id != null && !id.isBlank() && ids.size() < MAX_RECEIPT_BATCH) {
                    ids.add(id.trim());
                }
            }
        }
        return ids.stream().distinct().toList();
    }

    /**
     * Trim and normalize an identifier string.
     *
     * @param value the raw identifier
     * @return trimmed identifier, or {@code null} if blank
     */
    private static String normalizeId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * Return a non-blank request ID, preferring the event ID over the
     * generated fallback.
     *
     * @param eventId  the event-provided ID (may be null or blank)
     * @param fallback the generated fallback ID
     * @return the resolved request ID
     */
    private static String safeRequestId(String eventId, String fallback) {
        return eventId == null || eventId.isBlank() ? fallback : eventId.trim();
    }

    /**
     * Internal representation of an authenticated WebSocket session,
     * holding the connection, user identity, access token, per-user replay
     * guard, and per-socket rate-limit state.
     */
    private static final class SocketSession {
        private final WebSocket connection;
        private final String userId;
        private final String accessToken;
        private final ReplayGuard replayGuard;
        private final SocketRateLimiter socketRateLimiter = new SocketRateLimiter();
        private MailboxRouter.MailboxSubscription subscription;

        /**
         * Create a new socket session.
         *
         * @param connection  the underlying WebSocket connection
         * @param userId      the authenticated user's ID
         * @param accessToken the bearer token used for session revalidation
         */
        private SocketSession(WebSocket connection, String userId, String accessToken, ReplayGuard replayGuard) {
            this.connection = connection;
            this.userId = userId;
            this.accessToken = accessToken;
            this.replayGuard = replayGuard;
        }
    }

    /**
     * Sliding-window replay guard that rejects duplicate event keys within
     * the {@link #REPLAY_WINDOW_MS} window.
     */
    private static final class ReplayGuard {
        private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();
        private long lastTouchedMs;

        /**
         * Accept or reject an event key based on replay history.
         *
         * @param key composite key (eventId + nonce)
         * @param now current epoch millis
         * @return true if the key has not been seen within the window
         */
        private synchronized boolean accept(String key, long now) {
            lastTouchedMs = now;
            seen.entrySet().removeIf(entry -> now - entry.getValue() > REPLAY_WINDOW_MS);
            if (seen.containsKey(key)) {
                return false;
            }
            seen.put(key, now);
            return true;
        }

        private synchronized boolean isExpired(long now) {
            seen.entrySet().removeIf(entry -> now - entry.getValue() > REPLAY_WINDOW_MS);
            return seen.isEmpty() && lastTouchedMs > 0 && now - lastTouchedMs > REPLAY_WINDOW_MS;
        }
    }

    /**
     * Per-socket rate limiter that tracks a sliding one-minute event window
     * for each {@link RealtimeEventType}.
     */
    private static final class SocketRateLimiter {
        private final ConcurrentHashMap<RealtimeEventType, EventWindow> windows = new ConcurrentHashMap<>();

        /**
         * Check whether the event is within the per-type rate limit.
         *
         * @param type  the event type being rate-limited
         * @param limit maximum events per minute
         * @param now   current epoch millis
         * @return true if the event is within the limit
         */
        private boolean accept(RealtimeEventType type, int limit, long now) {
            return windows.computeIfAbsent(type, ignored -> new EventWindow()).accept(limit, now);
        }

        /** Clear all rate-limit windows. */
        private void clear() {
            windows.clear();
        }
    }

    /**
     * Simple one-minute sliding event counter used for rate limiting.
     */
    private static final class EventWindow {
        private long windowStartMs;
        private int count;

        /**
         * Record an event and check whether the count is within the limit.
         *
         * @param limit maximum events per window
         * @param now   current epoch millis
         * @return true if the event count is within the limit
         */
        private synchronized boolean accept(int limit, long now) {
            if (windowStartMs == 0 || now - windowStartMs >= 60_000L) {
                windowStartMs = now;
                count = 0;
            }
            count++;
            return count <= limit;
        }

        private synchronized boolean isExpired(long now) {
            return windowStartMs > 0 && now - windowStartMs >= 60_000L;
        }
    }
}
