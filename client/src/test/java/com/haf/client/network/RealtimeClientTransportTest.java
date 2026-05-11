package com.haf.client.network;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import com.haf.shared.websocket.RealtimeErrorCode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeClientTransportTest {

    @Test
    void session_replaced_close_is_terminal_and_does_not_reconnect() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient();
        RealtimeClientTransport transport = new RealtimeClientTransport(
                URI.create("wss://example.test/ws/v1/realtime"),
                () -> "token-1",
                httpClient);
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        transport.setErrorListener(errorFuture::complete);

        transport.start();
        ConnectionHandle connection = httpClient.connectionAt(0);
        connection.listener().onClose(connection.webSocket(), 1008, "session replaced").toCompletableFuture().join();
        Throwable error = errorFuture.get(1, TimeUnit.SECONDS);

        assertEquals(1, httpClient.connectAttempts.get());
        RealtimeClientTransport.RealtimeException realtimeError = assertInstanceOf(
                RealtimeClientTransport.RealtimeException.class,
                error);
        assertEquals(RealtimeErrorCode.SESSION_REPLACED, realtimeError.codeEnum());
        assertTrue(realtimeError.getMessage().contains("session revoked by takeover"));
        IOException sendError = assertThrows(
                IOException.class,
                () -> transport.sendTypingStart("recipient-1"));
        assertTrue(sendError.getMessage().contains("session revoked by takeover"));

        transport.close();
    }

    @Test
    void invalid_session_close_is_terminal_and_does_not_reconnect() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient();
        RealtimeClientTransport transport = new RealtimeClientTransport(
                URI.create("wss://example.test/ws/v1/realtime"),
                () -> "token-1",
                httpClient);
        CompletableFuture<Throwable> errorFuture = new CompletableFuture<>();
        transport.setErrorListener(errorFuture::complete);

        transport.start();
        ConnectionHandle connection = httpClient.connectionAt(0);
        connection.listener().onClose(connection.webSocket(), 1008, "invalid session").toCompletableFuture().join();
        Throwable error = errorFuture.get(1, TimeUnit.SECONDS);

        assertEquals(1, httpClient.connectAttempts.get());
        RealtimeClientTransport.RealtimeException realtimeError = assertInstanceOf(
                RealtimeClientTransport.RealtimeException.class,
                error);
        assertEquals(RealtimeErrorCode.INVALID_SESSION, realtimeError.codeEnum());

        transport.close();
    }

    @Test
    void stale_close_callback_after_reconnect_does_not_clear_active_socket() throws Exception {
        FakeHttpClient httpClient = new FakeHttpClient();
        RealtimeClientTransport transport = new RealtimeClientTransport(
                URI.create("wss://example.test/ws/v1/realtime"),
                () -> "token-1",
                httpClient);

        transport.start();
        ConnectionHandle first = httpClient.connectionAt(0);
        transport.reconnect();
        ConnectionHandle second = httpClient.connectionAt(1);

        first.listener().onClose(first.webSocket(), 1001, "going away").toCompletableFuture().join();
        transport.sendTypingStart("recipient-1");

        assertEquals(2, httpClient.connectAttempts.get());
        assertEquals(1, second.webSocket().sendTextCalls.get());
        transport.close();
    }

    private static final class FakeHttpClient extends HttpClient {
        private final AtomicInteger connectAttempts = new AtomicInteger();
        private final CopyOnWriteArrayList<ConnectionHandle> connections = new CopyOnWriteArrayList<>();

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return new FakeWebSocketBuilder();
        }

        private ConnectionHandle connectionAt(int index) {
            if (index < 0 || index >= connections.size()) {
                throw new IndexOutOfBoundsException("connection index " + index + " out of bounds");
            }
            return connections.get(index);
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }

        private final class FakeWebSocketBuilder implements WebSocket.Builder {
            @Override
            public WebSocket.Builder header(String name, String value) {
                return this;
            }

            @Override
            public WebSocket.Builder connectTimeout(Duration timeout) {
                return this;
            }

            @Override
            public WebSocket.Builder subprotocols(String mostPreferred, String... lesserPreferred) {
                return this;
            }

            @Override
            public CompletableFuture<WebSocket> buildAsync(URI uri, WebSocket.Listener listener) {
                int attempt = connectAttempts.incrementAndGet();
                FakeWebSocket socket = new FakeWebSocket(attempt);
                ConnectionHandle handle = new ConnectionHandle(socket, listener);
                connections.add(handle);
                listener.onOpen(socket);
                return CompletableFuture.completedFuture(socket);
            }
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final AtomicInteger sendTextCalls = new AtomicInteger();
        private volatile boolean inputClosed;
        private volatile boolean outputClosed;

        private FakeWebSocket(int connectionId) {
            // identifier is reserved for debugging and future assertions
            if (connectionId <= 0) {
                throw new IllegalArgumentException("connectionId");
            }
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sendTextCalls.incrementAndGet();
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            inputClosed = true;
            outputClosed = true;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            // No operation for fake
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return outputClosed;
        }

        @Override
        public boolean isInputClosed() {
            return inputClosed;
        }

        @Override
        public void abort() {
            inputClosed = true;
            outputClosed = true;
        }
    }

    private record ConnectionHandle(FakeWebSocket webSocket, WebSocket.Listener listener) {
        private ConnectionHandle {
            assertNotNull(webSocket);
            assertNotNull(listener);
        }
    }
}
