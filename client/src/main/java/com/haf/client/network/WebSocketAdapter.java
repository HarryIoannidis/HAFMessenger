package com.haf.client.network;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.haf.client.utils.SslContextUtils;
import com.haf.shared.exceptions.SslConfigurationException;

/**
 * WebSocket adapter for client-server communication over TLS.
 * Features implement exponential backoff and certificate pinning.
 */
public class WebSocketAdapter {
    private final URI serverUri;
    private final String sessionId;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private Consumer<String> messageConsumer;
    private Consumer<Throwable> errorConsumer;
    private volatile boolean isConnected = false;

    // Inbound text accumulation (handle fragmented frames)
    private final StringBuilder inboundBuffer = new StringBuilder();
    private static final int MAX_INBOUND_MESSAGE_BYTES = 2 * 1024 * 1024; // 2 MB

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
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final long MAX_RETRY_DELAY_MS = 5000;
    private int retryAttempts = 0;

    /**
     * Creates a WebSocketAdapter for the specified server URI.
     *
     * @param serverUri the WebSocket server URI (e.g., "wss://server:8080/ws")
     * @param sessionId the authentication session ID to be passed as a bearer token
     */
    public WebSocketAdapter(URI serverUri, String sessionId) {
        this.serverUri = serverUri;
        this.sessionId = sessionId;

        // Create HTTP client with TLS configuration
        SSLContext sslContext = createSSLContext();
        SSLParameters sslParameters = createSSLParameters();

        this.httpClient = HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Creates SSL context for TLS connections.
     * Uses the custom truststore for certificate pinning.
     *
     * @return the SSL context
     */
    private SSLContext createSSLContext() {
        try {
            return SslContextUtils.getTrustingSslContext();
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
    private SSLParameters createSSLParameters() {
        SSLParameters params = new SSLParameters();
        params.setProtocols(new String[] { "TLSv1.3" });
        params.setCipherSuites(new String[] {
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256"
        });
        params.setEndpointIdentificationAlgorithm("HTTPS");
        return params;
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
                    .header("Authorization", "Bearer " + sessionId)
                    .buildAsync(serverUri, listener);

            // Wait for connection to complete (with 10 second timeout)
            this.webSocket = future.get(10, TimeUnit.SECONDS);
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
     * Executes an authenticated GET request against the server using the trusting
     * HttpClient.
     *
     * @param path The API path to request (e.g., "/api/v1/users/{id}/key")
     * @return CompletableFuture containing the response body string
     */
    public CompletableFuture<String> getAuthenticated(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(serverUri.resolve(path))
                .header("Authorization", "Bearer " + sessionId)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    } else {
                        throw new RuntimeException(
                                "HTTP GET failed with status " + response.statusCode() + ": " + response.body());
                    }
                });
    }

    private WebSocket.Listener createWebSocketListener() {
        return new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                isConnected = true;
                retryAttempts = 0;
                WebSocket.Listener.super.onOpen(webSocket);
                // Request next message
                webSocket.request(1);
                startHeartbeat();
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                handleIncomingText(webSocket, data, last);
                return WebSocket.Listener.super.onText(webSocket, data, last);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                isConnected = false;
                if (errorConsumer != null) {
                    errorConsumer.accept(error);
                }
                WebSocket.Listener.super.onError(webSocket, error);
                scheduleReconnect();
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                isConnected = false;
                WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
                stopHeartbeat();
                scheduleReconnect();
                return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, java.nio.ByteBuffer message) {
                lastPongAtNanos = System.nanoTime();
                return WebSocket.Listener.super.onPong(webSocket, message);
            }
        };
    }

    private void handleIncomingText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            // Accumulate fragments until last == true
            if (data != null) {
                // Guard against oversize messages (approx by char count)
                if (inboundBuffer.length() + data.length() > MAX_INBOUND_MESSAGE_BYTES) {
                    inboundBuffer.setLength(0);
                    if (errorConsumer != null) {
                        errorConsumer.accept(new IOException("Inbound message too large"));
                    }
                    // Drop this message and request next
                    return;
                }
                inboundBuffer.append(data);
            }
            if (last) {
                String message = inboundBuffer.toString();
                inboundBuffer.setLength(0);
                if (messageConsumer != null) {
                    try {
                        messageConsumer.accept(message);
                    } catch (Exception e) {
                        if (errorConsumer != null) {
                            errorConsumer.accept(e);
                        }
                    }
                }
            }
        } finally {
            // Request next message
            webSocket.request(1);
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
     * Reconnection policy check. Evaluates whether reconnection should be attempted
     * based on maximum retry limits.
     *
     * @return true if reconnection should be attempted
     */
    public boolean shouldRetry() {
        if (retryAttempts >= MAX_RETRY_ATTEMPTS) {
            return false;
        }
        retryAttempts++;
        return true;
    }

    /**
     * Gets the retry delay in milliseconds using exponential backoff.
     *
     * @return the retry delay in milliseconds
     */
    public long getRetryDelayMs() {
        // Exponential backoff with cap
        long base = INITIAL_RETRY_DELAY_MS * (1L << Math.max(0, retryAttempts - 1));
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
        if (userClosed) {
            return;
        }
        if (!shouldRetry()) {
            return;
        }
        long delay = getRetryDelayMs();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            Consumer<String> mc;
            Consumer<Throwable> ec;

            synchronized (stateLock) {
                if (isConnected || userClosed) {
                    return;
                }
                mc = messageConsumer;
                ec = errorConsumer;
            }

            try {
                // Reuse previous consumers outside the lock to avoid deadlocks.
                if (mc != null || ec != null) {
                    connect(mc, ec);
                }
            } catch (IOException e) {
                if (ec != null) {
                    ec.accept(e);
                }
                // Chain next retry if still allowed
                scheduleReconnect();
            }
        });
    }
}
