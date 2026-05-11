package com.haf.client.network;

import com.haf.client.exceptions.SslConfigurationException;
import com.haf.client.utils.SslContextUtils;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.websocket.RealtimeEvent;
import com.haf.shared.websocket.RealtimeErrorCode;
import com.haf.shared.websocket.RealtimeEventType;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authenticated WSS transport for realtime chat events.
 */
public final class RealtimeClientTransport implements RealtimeTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(RealtimeClientTransport.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration SEND_ACK_TIMEOUT = Duration.ofSeconds(10);
    private static final long HEARTBEAT_SECONDS = 25L;

    private final URI realtimeUri;
    private final Supplier<String> accessTokenSupplier;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, CompletableFuture<RealtimeEvent>> pendingEvents = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private final AtomicReference<IOException> terminalFailure = new AtomicReference<>();
    private final AtomicReference<Consumer<RealtimeEvent>> eventListener = new AtomicReference<>(event -> {
    });
    private final AtomicReference<Consumer<Throwable>> errorListener = new AtomicReference<>(error -> {
    });
    private final Object sendLock = new Object();

    /**
     * Create a realtime transport with the default TLS-configured HTTP client.
     *
     * @param realtimeUri         the WSS endpoint URI
     * @param accessTokenSupplier supplier for the current bearer token
     */
    public RealtimeClientTransport(URI realtimeUri, Supplier<String> accessTokenSupplier) {
        this(realtimeUri, accessTokenSupplier, createDefaultHttpClient());
    }

    /**
     * Create a realtime transport with a custom HTTP client (for testing).
     *
     * @param realtimeUri         the WSS endpoint URI
     * @param accessTokenSupplier supplier for the current bearer token
     * @param httpClient          the HTTP client to use for WebSocket connections
     */
    RealtimeClientTransport(URI realtimeUri, Supplier<String> accessTokenSupplier, HttpClient httpClient) {
        this.realtimeUri = validateRealtimeUri(realtimeUri);
        this.accessTokenSupplier = Objects.requireNonNull(accessTokenSupplier, "accessTokenSupplier");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "message-wss-transport");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Set the listener for incoming realtime events (messages, presence,
     * typing indicators, receipts). Passing {@code null} resets to a no-op
     * listener.
     *
     * @param listener the event listener, or {@code null} for no-op
     */
    @Override
    public void setEventListener(Consumer<RealtimeEvent> listener) {
        this.eventListener.set(listener == null ? event -> {
        } : listener);
    }

    /**
     * Set the listener for transport-level errors. Passing {@code null}
     * resets to a no-op listener.
     *
     * @param listener the error listener, or {@code null} for no-op
     */
    @Override
    public void setErrorListener(Consumer<Throwable> listener) {
        this.errorListener.set(listener == null ? error -> {
        } : listener);
    }

    /**
     * Start the transport, establishing the WebSocket connection and
     * scheduling periodic heartbeats.
     *
     * @throws IOException if the initial connection fails
     */
    @Override
    public void start() throws IOException {
        if (running.compareAndSet(false, true)) {
            terminalFailure.set(null);
            connectNow();
            scheduler.scheduleWithFixedDelay(this::sendHeartbeatSafely, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    /**
     * Close the current WebSocket and reconnect if the transport is running.
     *
     * @throws IOException if the reconnection fails
     */
    @Override
    public void reconnect() throws IOException {
        IOException terminal = terminalFailure.get();
        if (terminal != null) {
            throw terminal;
        }
        closeSocketOnly(1000, "reconnect");
        if (running.get()) {
            connectNow();
        }
    }

    /**
     * Send an encrypted message over the realtime transport and wait for
     * server acknowledgement.
     *
     * @param encryptedMessage        the encrypted message payload
     * @param recipientKeyFingerprint client-provided recipient key fingerprint
     *                                for stale-key detection
     * @return the server's send result containing the envelope ID and expiry
     * @throws IOException if the send or acknowledgement fails
     */
    @Override
    public MessageSender.SendResult sendMessage(EncryptedMessage encryptedMessage, String recipientKeyFingerprint)
            throws IOException {
        RealtimeEvent event = RealtimeEvent.outbound(RealtimeEventType.SEND_MESSAGE);
        event.setEncryptedMessage(Objects.requireNonNull(encryptedMessage, "encryptedMessage"));
        event.setClientMessageId(UUID.randomUUID().toString());
        event.setRecipientKeyFingerprint(recipientKeyFingerprint);
        RealtimeEvent accepted = sendAndAwait(event);
        if (accepted.eventType() != RealtimeEventType.SEND_ACCEPTED) {
            throw new IOException("Unexpected realtime response: " + accepted.getType());
        }
        return new MessageSender.SendResult(accepted.getEnvelopeId(), accepted.getExpiresAtEpochMs());
    }

    /**
     * Send delivery receipts for the given envelope IDs.
     *
     * @param envelopeIds the envelope IDs to acknowledge as delivered
     * @param recipientId the sender of the original messages
     * @throws IOException if the send fails
     */
    @Override
    public void sendDeliveryReceipt(List<String> envelopeIds, String recipientId) throws IOException {
        sendReceipt(RealtimeEventType.MESSAGE_DELIVERED, envelopeIds, recipientId);
    }

    /**
     * Send read receipts for the given envelope IDs.
     *
     * @param envelopeIds the envelope IDs to mark as read
     * @param recipientId the sender of the original messages
     * @throws IOException if the send fails
     */
    @Override
    public void sendReadReceipt(List<String> envelopeIds, String recipientId) throws IOException {
        sendReceipt(RealtimeEventType.MESSAGE_READ, envelopeIds, recipientId);
    }

    /**
     * Send a typing-start indicator to the specified recipient.
     *
     * @param recipientId the recipient user ID
     * @throws IOException if the send fails
     */
    @Override
    public void sendTypingStart(String recipientId) throws IOException {
        sendTyping(RealtimeEventType.TYPING_START, recipientId);
    }

    /**
     * Send a typing-stop indicator to the specified recipient.
     *
     * @param recipientId the recipient user ID
     * @throws IOException if the send fails
     */
    @Override
    public void sendTypingStop(String recipientId) throws IOException {
        sendTyping(RealtimeEventType.TYPING_STOP, recipientId);
    }

    /**
     * Shut down the transport. Closes the WebSocket, stops the scheduler,
     * and fails all pending request futures.
     */
    @Override
    public void close() {
        running.set(false);
        terminalFailure.set(null);
        closeSocketOnly(1000, "client shutdown");
        scheduler.shutdownNow();
        failPending(new IOException("Realtime transport closed"));
    }

    /**
     * Send a delivery or read receipt event (fire-and-forget).
     *
     * @param type        the receipt event type
     * @param envelopeIds envelope IDs to include in the receipt
     * @param recipientId the original message sender
     * @throws IOException if the send fails
     */
    private void sendReceipt(RealtimeEventType type, List<String> envelopeIds, String recipientId) throws IOException {
        if (envelopeIds == null || envelopeIds.isEmpty()) {
            return;
        }
        RealtimeEvent event = RealtimeEvent.outbound(type);
        event.setEnvelopeIds(envelopeIds);
        event.setRecipientId(recipientId);
        sendFireAndForget(event);
    }

    /**
     * Send a typing indicator event (fire-and-forget).
     *
     * @param type        either TYPING_START or TYPING_STOP
     * @param recipientId the target user ID
     * @throws IOException if the send fails
     */
    private void sendTyping(RealtimeEventType type, String recipientId) throws IOException {
        if (recipientId == null || recipientId.isBlank()) {
            return;
        }
        RealtimeEvent event = RealtimeEvent.outbound(type);
        event.setRecipientId(recipientId);
        sendFireAndForget(event);
    }

    /**
     * Send an event and block until a correlated server response is received
     * or the timeout expires.
     *
     * @param event the outbound event to send
     * @return the correlated server response event
     * @throws IOException if the send fails or the response times out
     */
    private RealtimeEvent sendAndAwait(RealtimeEvent event) throws IOException {
        ensureConnected();
        CompletableFuture<RealtimeEvent> future = new CompletableFuture<>();
        pendingEvents.put(event.getEventId(), future);
        sendRaw(event);
        try {
            return future.orTimeout(SEND_ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).join();
        } catch (CompletionException ex) {
            throw normalizeIOException(ex.getCause() == null ? ex : ex.getCause(), "Realtime send failed");
        } finally {
            pendingEvents.remove(event.getEventId());
        }
    }

    /**
     * Send an event without waiting for a response.
     *
     * @param event the outbound event to send
     * @throws IOException if the send fails
     */
    private void sendFireAndForget(RealtimeEvent event) throws IOException {
        ensureConnected();
        sendRaw(event);
    }

    /**
     * Verify that the WebSocket is connected and reconnect if needed.
     *
     * @throws IOException if the reconnection attempt fails
     */
    private void ensureConnected() throws IOException {
        IOException terminal = terminalFailure.get();
        if (terminal != null) {
            throw terminal;
        }
        if (!running.get()) {
            throw new IOException("Realtime transport is not running");
        }
        WebSocket current = webSocket.get();
        if (current == null || current.isInputClosed() || current.isOutputClosed()) {
            connectNow();
        }
    }

    /**
     * Establish a new WebSocket connection using the current access token.
     * This method is synchronized to prevent concurrent connection attempts.
     *
     * @throws IOException if the connection handshake fails
     */
    private synchronized void connectNow() throws IOException {
        IOException terminal = terminalFailure.get();
        if (terminal != null) {
            throw terminal;
        }
        WebSocket current = webSocket.get();
        if (current != null && !current.isInputClosed() && !current.isOutputClosed()) {
            return;
        }
        String accessToken = accessTokenSupplier.get();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Missing access token for realtime connection");
        }
        try {
            webSocket.set(httpClient.newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .header("Authorization", "Bearer " + accessToken)
                    .buildAsync(realtimeUri, new Listener())
                    .join());
            reconnectAttempt.set(0);
            reconnectScheduled.set(false);
        } catch (CompletionException ex) {
            throw normalizeIOException(ex.getCause() == null ? ex : ex.getCause(),
                    "Failed to connect realtime transport");
        }
    }

    /**
     * Serialize and send a realtime event over the WebSocket.
     *
     * @param event the event to send
     * @throws IOException if the send fails
     */
    private void sendRaw(RealtimeEvent event) throws IOException {
        try {
            synchronized (sendLock) {
                WebSocket socket = webSocket.get();
                if (socket == null || socket.isInputClosed() || socket.isOutputClosed()) {
                    throw new IOException("Realtime connection is not available");
                }
                socket.sendText(JsonCodec.toJson(event), true).join();
            }
        } catch (CompletionException ex) {
            throw normalizeIOException(ex.getCause() == null ? ex : ex.getCause(), "Realtime send failed");
        }
    }

    /**
     * Send a heartbeat event to keep the connection alive. If the heartbeat
     * fails, a reconnect is scheduled.
     */
    private void sendHeartbeatSafely() {
        if (!running.get()) {
            return;
        }
        try {
            RealtimeEvent heartbeat = RealtimeEvent.outbound(RealtimeEventType.HEARTBEAT);
            sendFireAndForget(heartbeat);
        } catch (Exception ex) {
            LOGGER.debug("Realtime heartbeat failed: {}", ex.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * Dispatch an incoming server event. Correlated responses are matched
     * to pending futures; uncorrelated events are forwarded to the event
     * listener; errors are forwarded to the error listener.
     *
     * @param event the incoming realtime event
     */
    private void handleRealtimeEvent(RealtimeEvent event) {
        if (event == null || event.eventType() == null) {
            return;
        }
        String correlationId = event.getCorrelationId();
        if (correlationId != null && !correlationId.isBlank()) {
            CompletableFuture<RealtimeEvent> pending = pendingEvents.remove(correlationId);
            if (pending != null) {
                if (event.eventType() == RealtimeEventType.ERROR) {
                    pending.completeExceptionally(new RealtimeException(
                            event.getCode(),
                            event.getError(),
                            event.getRetryAfterSeconds()));
                } else {
                    pending.complete(event);
                }
                return;
            }
        }
        if (event.eventType() == RealtimeEventType.ERROR) {
            errorListener.get()
                    .accept(new RealtimeException(event.getCode(), event.getError(), event.getRetryAfterSeconds()));
            return;
        }
        eventListener.get().accept(event);
    }

    /**
     * Schedule an exponential-backoff reconnect attempt on the scheduler
     * thread. Only one reconnect can be scheduled at a time.
     */
    private void scheduleReconnect() {
        if (terminalFailure.get() != null || !running.get() || !reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        int attempt = reconnectAttempt.incrementAndGet();
        long delaySeconds = Math.min(15L, 1L << Math.min(attempt, 4));
        scheduler.schedule(() -> {
            reconnectScheduled.set(false);
            if (!running.get()) {
                return;
            }
            try {
                closeSocketOnly(1001, "reconnect");
                connectNow();
            } catch (IOException ex) {
                errorListener.get().accept(ex);
                scheduleReconnect();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    /**
     * Close the current WebSocket connection without stopping the transport.
     *
     * @param statusCode WebSocket close status code
     * @param reason     human-readable close reason
     */
    private void closeSocketOnly(int statusCode, String reason) {
        WebSocket current = webSocket.getAndSet(null);
        if (current != null) {
            try {
                current.sendClose(statusCode, reason).join();
            } catch (Exception ignored) {
                // Socket is already closing or closed.
            }
        }
    }

    private static IOException terminalCloseFailure(int statusCode, String reason) {
        String normalizedReason = reason == null ? "" : reason.trim().toLowerCase();
        if (statusCode == 1008 && normalizedReason.contains("session replaced")) {
            return new RealtimeException(RealtimeErrorCode.SESSION_REPLACED, "session revoked by takeover", 0);
        }
        if (statusCode == 1008 && normalizedReason.contains("invalid session")) {
            return new RealtimeException(RealtimeErrorCode.INVALID_SESSION, "invalid session", 0);
        }
        return null;
    }

    private boolean clearActiveSocketIfCurrent(WebSocket candidate) {
        return webSocket.compareAndSet(candidate, null);
    }

    /**
     * Complete all pending futures exceptionally with the given error and
     * clear the pending map.
     *
     * @param error the error to propagate to pending futures
     */
    private void failPending(Throwable error) {
        pendingEvents.forEach((ignored, future) -> future.completeExceptionally(error));
        pendingEvents.clear();
    }

    /**
     * Wrap a throwable as an {@link IOException}, returning it as-is if it
     * already is one.
     *
     * @param error    the original throwable
     * @param fallback fallback message if wrapping is needed
     * @return an IOException representing the error
     */
    private static IOException normalizeIOException(Throwable error, String fallback) {
        if (error instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(fallback, error);
    }

    /**
     * Create the default TLS-configured HTTP client for WebSocket connections.
     *
     * @return a new HTTP client with strict TLS settings
     * @throws SslConfigurationException if TLS context creation fails
     */
    private static HttpClient createDefaultHttpClient() {
        try {
            SSLContext sslContext = SslContextUtils.getStrictSslContext();
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .sslParameters(SslContextUtils.createHttpsSslParameters())
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create WSS SSL context", e);
        }
    }

    /**
     * Validate that the URI uses the {@code wss://} scheme and has no query
     * parameters.
     *
     * @param uri the candidate realtime URI
     * @return the validated URI
     * @throws IllegalArgumentException if the URI is invalid
     */
    private static URI validateRealtimeUri(URI uri) {
        URI candidate = Objects.requireNonNull(uri, "realtimeUri");
        if (!"wss".equalsIgnoreCase(candidate.getScheme()) || candidate.getHost() == null) {
            throw new IllegalArgumentException("Realtime URI must be absolute wss://");
        }
        if (candidate.getQuery() != null) {
            throw new IllegalArgumentException("Realtime URI must not contain query parameters");
        }
        return candidate;
    }

    /**
     * Exception indicating a server-side realtime error with an error code
     * and optional retry-after hint.
     */
    public static final class RealtimeException extends IOException {
        private final RealtimeErrorCode code;
        private final long retryAfterSeconds;

        /**
         * Create a new RealtimeException.
         *
         * @param code              short typed realtime error code
         * @param message           human-readable error message
         * @param retryAfterSeconds suggested retry delay in seconds
         */
        RealtimeException(RealtimeErrorCode code, String message, long retryAfterSeconds) {
            super(message == null || message.isBlank() ? "Realtime error" : message);
            this.code = code == null ? RealtimeErrorCode.UNKNOWN : code;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        /**
         * Returns the typed realtime error code.
         *
         * @return parsed code enum (or {@link RealtimeErrorCode#UNKNOWN})
         */
        public RealtimeErrorCode codeEnum() {
            return code;
        }

        /**
         * Returns the suggested retry delay in seconds.
         *
         * @return retry delay (0 if not applicable)
         */
        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /**
     * WebSocket listener that reassembles fragmented text frames,
     * dispatches complete events, and handles connection lifecycle.
     */
    private final class Listener implements WebSocket.Listener {
        private final StringBuilder incoming = new StringBuilder();

        /**
         * Called when the WebSocket is opened and ready to receive messages.
         * 
         * @param webSocket the WebSocket connection
         */
        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        /**
         * Called when a text message is received.
         * Appends the data to the incoming buffer and processes it when the last
         * frame is received.
         * 
         * @param webSocket the WebSocket connection
         * @param data      the text data received
         * @param last      true if this is the last frame of the message
         * @return a completion stage that completes when the message is processed
         */
        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                String json = incoming.toString();
                incoming.setLength(0);
                try {
                    handleRealtimeEvent(JsonCodec.fromJson(json, RealtimeEvent.class));
                } catch (Exception ex) {
                    errorListener.get().accept(new IOException("Failed to decode realtime event", ex));
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        /**
         * Called when the WebSocket is closed.
         * Clears the pending futures, fails any pending requests, and schedules a
         * reconnect if the transport is still running.
         * 
         * @param webSocket  the WebSocket connection
         * @param statusCode the close status code
         * @param reason     the close reason
         * @return a completion stage that completes when the close is processed
         */
        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (!clearActiveSocketIfCurrent(webSocket)) {
                return CompletableFuture.completedFuture(null);
            }
            IOException terminal = terminalCloseFailure(statusCode, reason);
            if (terminal != null) {
                terminalFailure.compareAndSet(null, terminal);
                running.set(false);
                reconnectScheduled.set(false);
                failPending(terminal);
                errorListener.get().accept(terminal);
            } else {
                failPending(new IOException("Realtime connection closed"));
            }
            if (terminal == null && running.get()) {
                scheduleReconnect();
            }
            return CompletableFuture.completedFuture(null);
        }

        /**
         * Called when an error occurs in the WebSocket connection.
         * Clears the pending futures, fails any pending requests, and schedules a
         * reconnect if the transport is still running.
         * 
         * @param webSocket the WebSocket connection
         * @param error     the error that occurred
         */
        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (!clearActiveSocketIfCurrent(webSocket)) {
                return;
            }
            failPending(error);
            errorListener.get().accept(error);
            scheduleReconnect();
        }
    }
}
