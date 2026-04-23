package com.haf.client.network;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import com.haf.client.utils.ClientRuntimeConfig;
import com.haf.client.utils.SslContextUtils;
import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.exceptions.SslConfigurationException;

/**
 * WebSocket adapter for client-server communication over TLS.
 * Features implement exponential backoff and mode-aware trust configuration.
 */
public class WebSocketAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketAdapter.class);

    private final URI serverUri;
    private final URI httpBaseUri;
    private volatile String sessionId;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private Consumer<String> messageConsumer;
    private Consumer<Throwable> errorConsumer;
    private volatile boolean isConnected = false;

    // HTTP constants
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;

    // Inbound text accumulation (handle fragmented frames)
    private final StringBuilder inboundBuffer = new StringBuilder();
    private static final int DEFAULT_MAX_INBOUND_MESSAGE_BYTES = 4 * 1024 * 1024; // 4 MB
    private static final int MAX_INBOUND_MESSAGE_BYTES = resolveMaxInboundMessageBytes();
    private boolean droppingOversizeInboundMessage;

    // Heartbeat (ping/pong)
    private ScheduledExecutorService scheduler;
    private volatile long lastPongAtNanos;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 5L;
    private static final long HEARTBEAT_MAX_SILENCE_MS = 15_000L;
    private volatile boolean heartbeatScheduled = false;

    // Reconnect state
    private final Object stateLock = new Object();
    private volatile boolean userClosed = false;

    // Reconnection policy
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long MAX_RETRY_DELAY_MS = 30_000;
    private static final int MAX_BACKOFF_EXPONENT = 5;
    private int retryAttempts = 0;

    @FunctionalInterface
    interface AsyncRunner {
        /**
         * Executes asynchronous work.
         *
         * @param task task to execute
         */
        void run(Runnable task);
    }

    @FunctionalInterface
    interface DelayStrategy {
        /**
         * Sleeps for the requested delay.
         *
         * @param millis delay in milliseconds
         * @throws InterruptedException when interrupted while waiting
         */
        void sleep(long millis) throws InterruptedException;
    }

    private final AsyncRunner asyncRunner;
    private final DelayStrategy delayStrategy;

    /**
     * Resolves max inbound message size from system property override.
     *
     * Property: {@code haf.ws.maxInboundBytes}
     *
     * @return validated max inbound message bytes
     */
    private static int resolveMaxInboundMessageBytes() {
        String configured = System.getProperty("haf.ws.maxInboundBytes");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_INBOUND_MESSAGE_BYTES;
        }
        try {
            int parsed = Integer.parseInt(configured.trim());
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default.
        }
        LOGGER.warn(
                "Invalid value for system property haf.ws.maxInboundBytes: {}. Falling back to default: {}",
                new Object[] { configured, DEFAULT_MAX_INBOUND_MESSAGE_BYTES });
        return DEFAULT_MAX_INBOUND_MESSAGE_BYTES;
    }

    /**
     * Creates a WebSocketAdapter for the specified server URI.
     *
     * @param serverUri the WebSocket server URI (e.g., "wss://server:8080/ws")
     * @param sessionId the authentication session ID to be passed as a bearer token
     */
    public WebSocketAdapter(URI serverUri, String sessionId) {
        this(serverUri, deriveLegacyHttpBaseUri(serverUri), sessionId, createDefaultHttpClient(),
                CompletableFuture::runAsync,
                Thread::sleep);
    }

    /**
     * Creates a WebSocketAdapter with explicit HTTP base URI for authenticated REST
     * helper calls.
     *
     * @param serverUri   WebSocket server URI (for WSS transport)
     * @param httpBaseUri HTTPS server base URI (for REST helper methods)
     * @param sessionId   bearer session id
     */
    public WebSocketAdapter(URI serverUri, URI httpBaseUri, String sessionId) {
        this(serverUri, httpBaseUri, sessionId, createDefaultHttpClient(),
                CompletableFuture::runAsync,
                Thread::sleep);
    }

    /**
     * Test seam constructor that accepts an externally created HTTP client.
     *
     * @param serverUri  WebSocket server URI
     * @param sessionId  bearer session id
     * @param httpClient injected HTTP client used by transport operations
     */
    WebSocketAdapter(URI serverUri, String sessionId, HttpClient httpClient) {
        this(serverUri, deriveLegacyHttpBaseUri(serverUri), sessionId, httpClient,
                CompletableFuture::runAsync,
                Thread::sleep);
    }

    /**
     * Test seam constructor with explicit HTTP base URI and injected HTTP client.
     *
     * @param serverUri   WebSocket server URI
     * @param httpBaseUri HTTPS server base URI for REST helper calls
     * @param sessionId   bearer session id
     * @param httpClient  injected HTTP client used by transport operations
     */
    WebSocketAdapter(URI serverUri, URI httpBaseUri, String sessionId, HttpClient httpClient) {
        this(serverUri, httpBaseUri, sessionId, httpClient,
                CompletableFuture::runAsync,
                Thread::sleep);
    }

    /**
     * Internal constructor with injectable async and delay strategies.
     *
     * @param serverUri     WebSocket server URI
     * @param sessionId     bearer session id
     * @param httpClient    injected HTTP client
     * @param asyncRunner   async execution strategy for reconnect attempts
     * @param delayStrategy delay strategy used by reconnect backoff
     */
    WebSocketAdapter(URI serverUri, String sessionId, HttpClient httpClient,
            AsyncRunner asyncRunner, DelayStrategy delayStrategy) {
        this(serverUri,
                deriveLegacyHttpBaseUri(serverUri),
                sessionId,
                httpClient,
                asyncRunner,
                delayStrategy);
    }

    /**
     * Internal constructor with injectable async and delay strategies.
     *
     * @param serverUri     WebSocket server URI
     * @param httpBaseUri   HTTPS base URI used by authenticated REST helper methods
     * @param sessionId     bearer session id
     * @param httpClient    injected HTTP client
     * @param asyncRunner   async execution strategy for reconnect attempts
     * @param delayStrategy delay strategy used by reconnect backoff
     */
    WebSocketAdapter(URI serverUri, URI httpBaseUri, String sessionId, HttpClient httpClient,
            AsyncRunner asyncRunner, DelayStrategy delayStrategy) {
        this.serverUri = Objects.requireNonNull(serverUri, "serverUri");
        this.httpBaseUri = normalizeHttpBaseUri(httpBaseUri);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.asyncRunner = Objects.requireNonNull(asyncRunner, "asyncRunner");
        this.delayStrategy = Objects.requireNonNull(delayStrategy, "delayStrategy");
    }

    /**
     * Creates the default TLS-configured HTTP client used by production
     * constructor.
     *
     * @return configured HTTP client
     */
    private static HttpClient createDefaultHttpClient() {
        SSLContext sslContext = createDefaultSslContext();
        SSLParameters sslParameters = createDefaultSslParameters();
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * Creates SSL context for TLS connections.
     * Uses the custom truststore for certificate pinning.
     *
     * @return the SSL context
     */
    private static SSLContext createDefaultSslContext() {
        try {
            ClientRuntimeConfig runtimeConfig = ClientRuntimeConfig.load();
            return SslContextUtils.getSslContextForMode(runtimeConfig.isDev());
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create SSL context", e);
        }
    }

    /**
     * Creates SSL parameters for TLS connections.
     * Ensures TLS 1.3 and specific secure cipher suites are used.
     *
     * @return the SSL parameters
     */
    private static SSLParameters createDefaultSslParameters() {
        return SslContextUtils.createHttpsSslParameters();
    }

    private static URI deriveLegacyHttpBaseUri(URI serverUri) {
        URI wsUri = Objects.requireNonNull(serverUri, "serverUri");
        try {
            return new URI("https",
                    wsUri.getUserInfo(),
                    wsUri.getHost(),
                    8443,
                    null,
                    null,
                    null);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalArgumentException("Failed to construct legacy HTTPS base URI", e);
        }
    }

    private static URI normalizeHttpBaseUri(URI httpBaseUri) {
        URI uri = Objects.requireNonNull(httpBaseUri, "httpBaseUri");
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("HTTP base URI must include scheme and host");
        }
        return uri;
    }

    /**
     * Connects to the WebSocket server.
     *
     * @param onMessage callback for incoming messages
     * @param onError   callback for errors
     * @throws IOException if connection fails
     */
    public void connect(Consumer<String> onMessage, Consumer<Throwable> onError) throws IOException {
        this.messageConsumer = onMessage;
        this.errorConsumer = onError;
        this.userClosed = false;

        WebSocket.Listener listener = createWebSocketListener();

        try {
            CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                    .header("Authorization", authorizationHeaderValue())
                    .buildAsync(serverUri, listener);

            // Wait for connection to complete (bounded by configured timeout)
            this.webSocket = future.get(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            isConnected = false;
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to connect WebSocket", cause);
        } catch (java.util.concurrent.TimeoutException e) {
            isConnected = false;
            throw new IOException("WebSocket connection timeout", e);
        } catch (InterruptedException e) {
            isConnected = false;
            Thread.currentThread().interrupt();
            throw new IOException("WebSocket connection interrupted", e);
        } catch (Exception e) {
            isConnected = false;
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to connect WebSocket", e);
        }
    }

    /**
     * Executes an authenticated GET request against the server using the configured
     * HttpClient.
     *
     * @param path The API path to request (e.g., "/api/v1/users/{id}/key")
     * @return CompletableFuture containing the response body string
     */
    public CompletableFuture<String> getAuthenticated(String path) {
        URI requestUri = buildAuthenticatedRequestUri(path);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .GET()
                .build();

        return sendWithRetry(request, "GET");
    }

    /**
     * Executes an authenticated POST request against the server using the
     * configured
     * HttpClient.
     *
     * @param path The API path to request
     * @param body The JSON body to send
     * @return CompletableFuture containing the response body string
     */
    public CompletableFuture<String> postAuthenticated(String path, String body) {
        return postAuthenticated(path, body, Map.of());
    }

    /**
     * Executes an authenticated POST request against the server using optional
     * additional headers.
     *
     * @param path         The API path to request
     * @param body         The JSON body to send
     * @param extraHeaders optional extra headers
     * @return CompletableFuture containing the response body string
     */
    public CompletableFuture<String> postAuthenticated(String path, String body, Map<String, String> extraHeaders) {
        URI requestUri = buildAuthenticatedRequestUri(path);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            extraHeaders.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null) {
                    builder.header(name, value);
                }
            });
        }
        HttpRequest request = builder.build();

        return sendWithRetry(request, "POST");
    }

    /**
     * Executes an authenticated DELETE request against the server using the
     * configured
     * HttpClient.
     *
     * @param path The API path to request
     * @return CompletableFuture containing the response body string
     */
    public CompletableFuture<String> deleteAuthenticated(String path) {
        URI requestUri = buildAuthenticatedRequestUri(path);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .DELETE()
                .build();

        return sendWithRetry(request, "DELETE");
    }

    /**
     * Builds request URI for authenticated helper calls.
     *
     * @param path API path
     * @return resolved request URI
     * @throws IllegalArgumentException when {@code path} is blank
     */
    private URI buildAuthenticatedRequestUri(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return httpBaseUri.resolve(path);
    }

    /**
     * Updates the bearer access token used by future HTTP/WebSocket handshake
     * requests.
     *
     * @param accessToken new access token
     * @throws IllegalArgumentException when {@code accessToken} is null/blank
     */
    public void updateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken");
        }
        this.sessionId = accessToken;
    }

    /**
     * Builds current bearer authorization header value.
     *
     * @return authorization header value
     */
    private String authorizationHeaderValue() {
        return "Bearer " + sessionId;
    }

    /**
     * Sends an HTTP request with a single retry on connection errors.
     * Java's HttpClient may reuse a stale pooled TCP connection that the server
     * has already closed, resulting in a ClosedChannelException. A retry forces
     * the client to open a fresh connection.
     */
    private CompletableFuture<String> sendWithRetry(HttpRequest request, String method) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> validateResponse(response, method))
                .exceptionallyCompose(error -> {
                    if (isConnectionError(error)) {
                        LOGGER.warn("HTTP {} failed with connection error, retrying once", method);
                        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                .thenApply(response -> validateResponse(response, method));
                    }
                    return CompletableFuture.failedFuture(error);
                });
    }

    /**
     * Validates HTTP response status for authenticated REST helpers.
     *
     * @param response HTTP response received from the server
     * @param method   HTTP method name used for error messaging
     * @return response body when status is successful (2xx)
     * @throws HttpCommunicationException when status code is non-successful
     */
    private String validateResponse(HttpResponse<String> response, String method) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        throw new HttpCommunicationException(
                "HTTP " + method + " failed with status " + response.statusCode() + ": " + response.body(),
                response.statusCode(), response.body());
    }

    /**
     * Detects recoverable connection-level errors used to trigger a one-time HTTP
     * retry.
     *
     * @param error throwable chain from async HTTP completion
     * @return {@code true} when throwable chain contains connect/closed-channel
     *         failures
     */
    private static boolean isConnectionError(Throwable error) {
        Throwable cause = error;
        while (cause != null) {
            if (cause instanceof java.net.ConnectException
                    || cause instanceof java.nio.channels.ClosedChannelException
                    || cause instanceof HttpTimeoutException
                    || cause instanceof SocketException
                    || cause instanceof UnknownHostException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Creates the websocket listener used for open/text/error/close/pong events.
     *
     * @return listener instance bound to this adapter's callback fields
     */
    private WebSocket.Listener createWebSocketListener() {
        return new WebSocket.Listener() {

            /**
             * Marks adapter as connected and starts heartbeat tracking.
             *
             * @param webSocket newly opened websocket connection
             */
            @Override
            public void onOpen(WebSocket webSocket) {
                isConnected = true;
                retryAttempts = 0;
                inboundBuffer.setLength(0);
                droppingOversizeInboundMessage = false;
                WebSocket.Listener.super.onOpen(webSocket);
                // Request next message
                webSocket.request(1);
                startHeartbeat();
            }

            /**
             * Handles inbound text frames from the websocket.
             *
             * @param webSocket websocket connection delivering the frame
             * @param data      frame payload chunk
             * @param last      whether this chunk is the final fragment
             * @return completion stage delegated to default listener behavior
             */
            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                handleIncomingText(webSocket, data, last);
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            /**
             * Handles websocket transport errors and triggers reconnect policy.
             *
             * @param webSocket websocket that raised the error
             * @param error     transport error
             */
            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                isConnected = false;
                if (errorConsumer != null) {
                    errorConsumer.accept(error);
                }
                WebSocket.Listener.super.onError(webSocket, error);
                scheduleReconnect();
            }

            /**
             * Handles websocket close events and schedules reconnect attempts.
             *
             * @param webSocket  websocket that closed
             * @param statusCode websocket close status code
             * @param reason     close reason text
             * @return completion stage delegated to default listener behavior
             */
            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                isConnected = false;
                WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                stopHeartbeat();
                scheduleReconnect();
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            /**
             * Updates last-pong timestamp used by heartbeat stale-connection checks.
             *
             * @param webSocket websocket that received pong
             * @param message   pong payload
             * @return completion stage delegated to default listener behavior
             */
            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, java.nio.ByteBuffer message) {
                lastPongAtNanos = System.nanoTime();
                return WebSocket.Listener.super.onPong(webSocket, message);
            }
        };
    }

    /**
     * Handles fragmented websocket text frames and dispatches complete messages.
     *
     * @param webSocket active websocket
     * @param data      incoming frame fragment
     * @param last      whether this frame is the final fragment of the message
     */
    private void handleIncomingText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            if (droppingOversizeInboundMessage) {
                if (last) {
                    droppingOversizeInboundMessage = false;
                }
                return;
            }
            if (data != null) {
                if (isMessageOversize(data)) {
                    handleOversizeError(last);
                    return;
                }
                inboundBuffer.append(data);
            }
            if (last) {
                processCompleteMessage();
            }
        } finally {
            webSocket.request(1);
        }
    }

    /**
     * Checks if adding a frame would exceed the inbound message size guard.
     *
     * @param data incoming frame data
     * @return {@code true} when message exceeds configured max inbound bytes
     */
    private boolean isMessageOversize(CharSequence data) {
        return inboundBuffer.length() + data.length() > MAX_INBOUND_MESSAGE_BYTES;
    }

    /**
     * Handles oversize inbound messages by clearing state and notifying error
     * callback.
     */
    private void handleOversizeError(boolean lastFragment) {
        inboundBuffer.setLength(0);
        droppingOversizeInboundMessage = !lastFragment;
        if (errorConsumer != null) {
            errorConsumer.accept(new IOException("Inbound message too large"));
        }
    }

    /**
     * Finalizes current inbound buffer as a full message and dispatches it.
     */
    private void processCompleteMessage() {
        String message = inboundBuffer.toString();
        inboundBuffer.setLength(0);
        if (messageConsumer != null) {
            notifyMessageConsumer(message);
        }
    }

    /**
     * Invokes the configured message consumer with protective error handling.
     *
     * @param message complete inbound message payload
     */
    private void notifyMessageConsumer(String message) {
        try {
            messageConsumer.accept(message);
        } catch (Exception e) {
            if (errorConsumer != null) {
                errorConsumer.accept(e);
            }
        }
    }

    /**
     * Sends a text message over the WebSocket.
     *
     * @param message the message to send
     * @throws IOException if the connection is not established or send fails
     */
    public void sendText(String message) throws IOException {
        if (webSocket == null || !isConnected) {
            throw new IOException("WebSocket is not connected");
        }

        CompletableFuture<WebSocket> sendFuture = webSocket.sendText(message, true);

        // Wait for send to complete (with timeout)
        try {
            sendFuture.get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException te) {
            throw new IOException("Failed to send message: timeout", te);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Failed to send message: interrupted", ie);
        } catch (Exception e) {
            throw new IOException("Failed to send message", e);
        }
    }

    /**
     * Closes the WebSocket connection.
     */
    public void close() {
        userClosed = true;
        stopHeartbeat();
        inboundBuffer.setLength(0);
        droppingOversizeInboundMessage = false;
        WebSocket ws = this.webSocket;
        this.webSocket = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing").get(3, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
                // Ignore failure during close
            } finally {
                isConnected = false;
            }
        } else {
            isConnected = false;
        }
    }

    /**
     * Checks if the WebSocket is connected.
     *
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Reconnection policy check.
     *
     * In normal network-loss scenarios we keep retrying with bounded backoff until
     * user closes the session or an auth failure (401/403) is detected.
     *
     * @return true if reconnection should be attempted
     */
    public boolean shouldRetry() {
        if (retryAttempts < Integer.MAX_VALUE) {
            retryAttempts++;
        }
        return true;
    }

    /**
     * Gets the retry delay in milliseconds using exponential backoff.
     *
     * @return the retry delay in milliseconds
     */
    public long getRetryDelayMs() {
        // Exponential backoff with cap
        int attemptIndex = retryAttempts - 1;
        int exponent = Math.clamp(attemptIndex, 0, MAX_BACKOFF_EXPONENT);
        long base = INITIAL_RETRY_DELAY_MS * (1L << exponent);
        if (base < INITIAL_RETRY_DELAY_MS) {
            base = INITIAL_RETRY_DELAY_MS;
        }
        return Math.min(base, MAX_RETRY_DELAY_MS);
    }

    /**
     * Resets the retry counter (called after successful connection).
     */
    public void resetRetryCounter() {
        retryAttempts = 0;
    }

    /**
     * Starts heartbeat scheduler (ping/pong) to detect half-open connections.
     */
    private void startHeartbeat() {
        synchronized (stateLock) {
            if (scheduler == null || scheduler.isShutdown()) {
                scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "ws-heartbeat");
                    t.setDaemon(true);
                    return t;
                });
            }
            lastPongAtNanos = System.nanoTime();
            if (!heartbeatScheduled) {
                scheduler.scheduleAtFixedRate(this::heartbeatTask, HEARTBEAT_INTERVAL_SECONDS,
                        HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
                heartbeatScheduled = true;
            }
        }
    }

    /**
     * Sends periodic ping frames and marks stale connections for reconnection.
     */
    private void heartbeatTask() {
        try {
            WebSocket ws = webSocket;
            if (ws != null && isConnected) {
                ws.sendPing(java.nio.ByteBuffer.wrap(new byte[] { 1 }));
            }
            long ageMs = (System.nanoTime() - lastPongAtNanos) / 1_000_000L;
            if (ageMs > HEARTBEAT_MAX_SILENCE_MS) {
                // Consider connection stale; close and attempt reconnect
                abortWebSocket(ws);
                isConnected = false;
            }
        } catch (Exception t) {
            if (errorConsumer != null) {
                errorConsumer.accept(t);
            }
        }
    }

    /**
     * Force-aborts the underlying websocket transport.
     *
     * @param ws websocket to abort
     */
    private void abortWebSocket(WebSocket ws) {
        try {
            if (ws != null) {
                ws.abort();
            }
        } catch (Exception ignored) {
            // Abort failed, ignore
        }
    }

    /**
     * Stops and cleans up heartbeat scheduler.
     */
    private void stopHeartbeat() {
        synchronized (stateLock) {
            if (scheduler != null) {
                scheduler.shutdownNow();
                scheduler = null;
            }
            heartbeatScheduled = false;
        }
    }

    /**
     * Schedules a reconnect attempt if policy allows and the user did not
     * explicitly close.
     */
    private void scheduleReconnect() {
        if (shouldSkipReconnect()) {
            return;
        }
        long delay = getRetryDelayMs();
        asyncRunner.run(() -> runReconnectAttempt(delay));
    }

    /**
     * Returns whether reconnect scheduling should be skipped.
     *
     * @return {@code true} when user closed the adapter or retry policy disallows
     *         reconnect scheduling
     */
    private boolean shouldSkipReconnect() {
        return userClosed || !shouldRetry();
    }

    /**
     * Executes one delayed reconnect attempt.
     *
     * @param delay reconnect delay in milliseconds
     */
    private void runReconnectAttempt(long delay) {
        if (!sleepReconnectDelay(delay)) {
            return;
        }

        ReconnectContext reconnectContext = resolveReconnectContext();
        if (reconnectContext == null) {
            return;
        }

        reconnectOrReschedule(reconnectContext);
    }

    /**
     * Sleeps until reconnect attempt should run.
     *
     * @param delay reconnect delay in milliseconds
     * @return {@code true} when delay elapsed normally
     */
    private boolean sleepReconnectDelay(long delay) {
        try {
            delayStrategy.sleep(delay);
            return true;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Resolves reconnect callback consumers only when reconnect is still valid.
     *
     * @return reconnect context, or {@code null} when reconnect should be aborted
     */
    private ReconnectContext resolveReconnectContext() {
        synchronized (stateLock) {
            if (isConnected || userClosed) {
                return null;
            }
            return new ReconnectContext(messageConsumer, errorConsumer);
        }
    }

    /**
     * Attempts reconnect and schedules another attempt on recoverable failure.
     *
     * @param reconnectContext callback context captured from adapter state
     */
    private void reconnectOrReschedule(ReconnectContext reconnectContext) {
        Consumer<String> mc = reconnectContext.messageConsumer();
        Consumer<Throwable> ec = reconnectContext.errorConsumer();
        try {
            // Reuse previous consumers outside the lock to avoid deadlocks.
            if (mc != null || ec != null) {
                connect(mc, ec);
            }
        } catch (IOException e) {
            if (ec != null) {
                ec.accept(e);
            }
            if (isAuthenticationFailure(e)) {
                LOGGER.info("Stopping reconnect attempts after authentication failure: {}", e.getMessage());
                return;
            }
            // Chain next retry if still allowed
            scheduleReconnect();
        }
    }

    /**
     * Detects authorization failures for WebSocket handshake reconnect attempts.
     *
     * @param error connection failure to inspect
     * @return {@code true} when session is no longer authorized (401/403)
     */
    private static boolean isAuthenticationFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof WebSocketHandshakeException handshakeException
                    && handshakeException.getResponse() != null) {
                int statusCode = handshakeException.getResponse().statusCode();
                if (statusCode == 401 || statusCode == 403) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Snapshot of reconnect callbacks captured from adapter state.
     *
     * @param messageConsumer inbound message callback
     * @param errorConsumer   transport error callback
     */
    private record ReconnectContext(Consumer<String> messageConsumer, Consumer<Throwable> errorConsumer) {
    }
}
