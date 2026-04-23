package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WebSocketAdapterTest {

    private static final URI SERVER_URI = URI.create("wss://localhost:8444/ws");

    @Test
    void get_authenticated_rewrites_uri_and_forwards_bearer() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueResponse(response(200, "{\"ok\":true}", URI.create("https://localhost:8443/api/v1/ping")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-1", client);
        String body = adapter.getAuthenticated("/api/v1/ping").join();

        assertEquals("{\"ok\":true}", body);
        assertEquals(1, client.requests.size());
        HttpRequest request = client.requests.getFirst();
        assertEquals("https", request.uri().getScheme());
        assertEquals(8443, request.uri().getPort());
        assertEquals("/api/v1/ping", request.uri().getPath());
        assertEquals("Bearer session-1", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void get_authenticated_uses_explicit_http_base_uri_when_provided() {
        FakeHttpClient client = new FakeHttpClient();
        URI wsUri = URI.create("wss://prod-ws.example.test/ws");
        URI httpBaseUri = URI.create("https://prod-api.example.test:9443");
        client.enqueueResponse(response(200, "{\"ok\":true}", httpBaseUri.resolve("/api/v1/ping")));

        WebSocketAdapter adapter = new WebSocketAdapter(wsUri, httpBaseUri, "session-explicit", client);
        adapter.getAuthenticated("/api/v1/ping").join();

        HttpRequest request = client.requests.getFirst();
        assertEquals("https", request.uri().getScheme());
        assertEquals("prod-api.example.test", request.uri().getHost());
        assertEquals(9443, request.uri().getPort());
        assertEquals("/api/v1/ping", request.uri().getPath());
        assertEquals("Bearer session-explicit", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void post_authenticated_sets_json_content_type_and_bearer() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueResponse(response(200, "{}", URI.create("https://localhost:8443/api/v1/test")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-2", client);
        adapter.postAuthenticated("/api/v1/test", "{\"x\":1}").join();

        HttpRequest request = client.requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("Bearer session-2", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void delete_authenticated_sets_bearer_header() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueResponse(response(200, "{}", URI.create("https://localhost:8443/api/v1/test")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-3", client);
        adapter.deleteAuthenticated("/api/v1/test").join();

        HttpRequest request = client.requests.getFirst();
        assertEquals("DELETE", request.method());
        assertEquals("Bearer session-3", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void authenticated_http_uses_updated_access_token() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueResponse(response(200, "{\"ok\":true}", URI.create("https://localhost:8443/api/v1/ping")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-old", client);
        adapter.updateAccessToken("session-new");
        adapter.getAuthenticated("/api/v1/ping").join();

        HttpRequest request = client.requests.getFirst();
        assertEquals("Bearer session-new", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void authenticated_http_retries_once_on_connection_error() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueError(new ConnectException("down"));
        client.enqueueResponse(response(200, "{\"ok\":1}", URI.create("https://localhost:8443/api/v1/retry")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-4", client);
        String body = adapter.getAuthenticated("/api/v1/retry").join();

        assertEquals("{\"ok\":1}", body);
        assertEquals(2, client.requests.size());
    }

    @Test
    void authenticated_http_retries_once_on_timeout_error() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueError(new HttpTimeoutException("timed out"));
        client.enqueueResponse(response(200, "{\"ok\":2}", URI.create("https://localhost:8443/api/v1/retry-timeout")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-timeout", client);
        String body = adapter.getAuthenticated("/api/v1/retry-timeout").join();

        assertEquals("{\"ok\":2}", body);
        assertEquals(2, client.requests.size());
    }

    @Test
    void authenticated_http_maps_non_2xx_to_http_communication_exception() {
        FakeHttpClient client = new FakeHttpClient();
        client.enqueueResponse(response(500, "boom", URI.create("https://localhost:8443/api/v1/fail")));

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-5", client);

        CompletableFuture<String> future = adapter.getAuthenticated("/api/v1/fail");
        CompletionException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                CompletionException.class,
                future::join);
        HttpCommunicationException cause = assertInstanceOf(HttpCommunicationException.class, thrown.getCause());
        assertEquals(500, cause.getStatusCode());
        assertEquals("boom", cause.getResponseBody());
    }

    @Test
    void connect_sends_authorization_header_and_handles_fragmented_text() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        FakeWebSocket fakeWebSocket = new FakeWebSocket();
        client.webSocketBuilder.nextWebSocketFuture = CompletableFuture.completedFuture(fakeWebSocket);

        AtomicReference<String> received = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-ws", client);
        adapter.connect(received::set, error::set);

        assertEquals("Bearer session-ws", client.webSocketBuilder.headers.get("Authorization"));
        assertEquals(SERVER_URI, client.webSocketBuilder.lastUri);
        assertNotNull(client.webSocketBuilder.lastListener);

        client.webSocketBuilder.lastListener.onOpen(fakeWebSocket);
        client.webSocketBuilder.lastListener.onText(fakeWebSocket, "hel", false);
        client.webSocketBuilder.lastListener.onText(fakeWebSocket, "lo", true);

        assertEquals("hello", received.get());
        assertEquals(null, error.get());
        assertTrue(adapter.isConnected());
        assertTrue(fakeWebSocket.requestCalls >= 3);
    }

    @Test
    void oversized_inbound_message_triggers_error_callback() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        FakeWebSocket ws = new FakeWebSocket();
        client.webSocketBuilder.nextWebSocketFuture = CompletableFuture.completedFuture(ws);

        AtomicReference<Throwable> error = new AtomicReference<>();
        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-over", client);
        adapter.connect(msg -> {
        }, error::set);

        client.webSocketBuilder.lastListener.onOpen(ws);
        int maxInbound = getStaticIntField(WebSocketAdapter.class, "MAX_INBOUND_MESSAGE_BYTES");
        String huge = "x".repeat(maxInbound + 10);
        client.webSocketBuilder.lastListener.onText(ws, huge, true);

        assertNotNull(error.get());
        assertInstanceOf(IOException.class, error.get());
        assertTrue(error.get().getMessage().contains("too large"));
    }

    @Test
    void oversized_fragmented_message_discards_tail_until_last_and_recovers() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        FakeWebSocket ws = new FakeWebSocket();
        client.webSocketBuilder.nextWebSocketFuture = CompletableFuture.completedFuture(ws);

        AtomicReference<String> received = new AtomicReference<>();
        AtomicInteger errors = new AtomicInteger();
        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-over-frag", client);
        adapter.connect(received::set, err -> errors.incrementAndGet());

        client.webSocketBuilder.lastListener.onOpen(ws);
        int maxInbound = getStaticIntField(WebSocketAdapter.class, "MAX_INBOUND_MESSAGE_BYTES");
        String nearLimit = "x".repeat(Math.max(1, maxInbound - 5));

        client.webSocketBuilder.lastListener.onText(ws, nearLimit, false);
        client.webSocketBuilder.lastListener.onText(ws, "overflow-fragment", false);
        client.webSocketBuilder.lastListener.onText(ws, "ignored-tail", true);

        assertEquals(1, errors.get());
        assertEquals(null, received.get());

        client.webSocketBuilder.lastListener.onText(ws, "ok", true);
        assertEquals("ok", received.get());
        assertEquals(1, errors.get());
    }

    @Test
    void heartbeat_task_aborts_stale_connection() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        FakeWebSocket ws = new FakeWebSocket();
        client.webSocketBuilder.nextWebSocketFuture = CompletableFuture.completedFuture(ws);

        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-heart", client);
        adapter.connect(msg -> {
        }, err -> {
        });
        client.webSocketBuilder.lastListener.onOpen(ws);

        setField(adapter, "lastPongAtNanos", System.nanoTime() - Duration.ofSeconds(20).toNanos());
        invokePrivateNoArg(adapter, "heartbeatTask");

        assertEquals(1, ws.pingCalls);
        assertEquals(1, ws.abortCalls);
        assertFalse(adapter.isConnected());
    }

    @Test
    void schedule_reconnect_does_not_run_when_user_closed_and_still_runs_after_many_attempts() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        AtomicInteger scheduledTasks = new AtomicInteger();

        WebSocketAdapter adapter = new WebSocketAdapter(
                SERVER_URI,
                "session-reconnect",
                client,
                task -> scheduledTasks.incrementAndGet(),
                millis -> {
                });

        // userClosed=true path
        adapter.close();
        invokePrivateNoArg(adapter, "scheduleReconnect");
        assertEquals(0, scheduledTasks.get());

        // many-attempts path: reconnect should continue with capped backoff.
        setField(adapter, "userClosed", false);
        setField(adapter, "retryAttempts", 3);
        invokePrivateNoArg(adapter, "scheduleReconnect");
        assertEquals(1, scheduledTasks.get());
    }

    @Test
    void schedule_reconnect_runs_connect_when_allowed() throws Exception {
        FakeHttpClient client = new FakeHttpClient();
        FakeWebSocket first = new FakeWebSocket();
        FakeWebSocket second = new FakeWebSocket();
        client.webSocketBuilder.nextWebSocketFuture = CompletableFuture.completedFuture(first);

        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();
        WebSocketAdapter adapter = new WebSocketAdapter(
                SERVER_URI,
                "session-reconnect-ok",
                client,
                scheduledTask::set,
                millis -> {
                });

        adapter.connect(msg -> {
        }, err -> {
        });
        assertEquals(1, client.webSocketBuilder.buildAsyncCalls);

        client.webSocketBuilder.nextWebSocketFuture = CompletableFuture.completedFuture(second);
        setField(adapter, "isConnected", false);
        invokePrivateNoArg(adapter, "scheduleReconnect");

        assertNotNull(scheduledTask.get());
        scheduledTask.get().run();

        assertEquals(2, client.webSocketBuilder.buildAsyncCalls);
        assertEquals("Bearer session-reconnect-ok", client.webSocketBuilder.headers.get("Authorization"));
    }

    @Test
    void send_text_throws_when_not_connected_and_close_is_safe() {
        FakeHttpClient client = new FakeHttpClient();
        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-send", client);

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> adapter.sendText("hello"));
        assertDoesNotThrow(adapter::close);
    }

    @Test
    void retry_policy_behaves_as_expected() {
        FakeHttpClient client = new FakeHttpClient();
        WebSocketAdapter adapter = new WebSocketAdapter(SERVER_URI, "session-retry", client);

        assertTrue(adapter.shouldRetry());
        assertTrue(adapter.shouldRetry());
        assertTrue(adapter.shouldRetry());
        assertTrue(adapter.shouldRetry());

        adapter.resetRetryCounter();
        assertTrue(adapter.shouldRetry());
        assertEquals(1000L, adapter.getRetryDelayMs());

        setField(adapter, "retryAttempts", 100);
        assertEquals(30_000L, adapter.getRetryDelayMs());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage(), e);
        }
    }

    private static void invokePrivateNoArg(Object target, String methodName) {
        try {
            var method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            method.invoke(target);
        } catch (Exception e) {
            fail("Failed to invoke method '" + methodName + "': " + e.getMessage(), e);
        }
    }

    private static int getStaticIntField(Class<?> target, String fieldName) {
        try {
            var field = target.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (Exception e) {
            fail("Failed to read static int field '" + fieldName + "': " + e.getMessage(), e);
            return -1;
        }
    }

    private static HttpResponse<String> response(int statusCode, String body, URI uri) {
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of(), (name, value) -> true);
            }

            @Override
            public String body() {
                return body;
            }

            @Override
            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ArrayDeque<Object> asyncResponses = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();
        private final FakeWebSocketBuilder webSocketBuilder = new FakeWebSocketBuilder();

        void enqueueResponse(HttpResponse<String> response) {
            asyncResponses.add(response);
        }

        void enqueueError(Throwable throwable) {
            asyncResponses.add(throwable);
        }

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
            try {
                return SSLContext.getDefault();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException("sync send is not used in these tests");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            if (asyncResponses.isEmpty()) {
                return CompletableFuture.failedFuture(new IllegalStateException("No queued HTTP response"));
            }

            Object next = asyncResponses.removeFirst();
            if (next instanceof Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }

            return CompletableFuture.completedFuture((HttpResponse<T>) next);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return webSocketBuilder;
        }
    }

    private static final class FakeWebSocketBuilder implements WebSocket.Builder {
        private final Map<String, String> headers = new HashMap<>();
        private URI lastUri;
        private WebSocket.Listener lastListener;
        private int buildAsyncCalls;
        private CompletableFuture<WebSocket> nextWebSocketFuture = CompletableFuture
                .failedFuture(new IllegalStateException("No queued websocket future"));

        @Override
        public WebSocket.Builder header(String name, String value) {
            headers.put(name, value);
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
            buildAsyncCalls++;
            this.lastUri = uri;
            this.lastListener = listener;
            return nextWebSocketFuture;
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private int requestCalls;
        private int pingCalls;
        private int abortCalls;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            pingCalls++;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            requestCalls++;
        }

        @Override
        public String getSubprotocol() {
            return null;
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
            abortCalls++;
        }
    }
}
