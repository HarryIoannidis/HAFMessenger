package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AuthHttpClientTest {

    @Test
    void get_authenticated_uses_base_uri_and_bearer() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(response(200, "{\"ok\":true}", URI.create("https://api.test/api/v1/ping")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-1", fakeHttpClient);
        String body = client.getAuthenticated("/api/v1/ping").join();

        assertEquals("{\"ok\":true}", body);
        HttpRequest request = fakeHttpClient.requests.getFirst();
        assertEquals("GET", request.method());
        assertEquals("https://api.test/api/v1/ping", request.uri().toString());
        assertEquals("Bearer session-1", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void post_authenticated_sets_content_type_and_extra_headers() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(response(200, "{}", URI.create("https://api.test/api/v1/contacts")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-2", fakeHttpClient);
        client.postAuthenticated(
                "/api/v1/contacts",
                "{\"x\":1}",
                Map.of("X-Test", "abc"))
                .join();

        HttpRequest request = fakeHttpClient.requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("Bearer session-2", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("abc", request.headers().firstValue("X-Test").orElse(null));
    }

    @Test
    void post_authenticated_bytes_sets_content_type_extra_headers_and_body_publisher() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(response(200, "{}", URI.create("https://api.test/api/v1/attachments/a/chunk")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-bin", fakeHttpClient);
        client.postAuthenticatedBytes(
                "/api/v1/attachments/a/chunk",
                new byte[] { 1, 2, 3 },
                "application/octet-stream",
                Map.of("X-Attachment-Chunk-Index", "2"))
                .join();

        HttpRequest request = fakeHttpClient.requests.getFirst();
        assertEquals("POST", request.method());
        assertEquals("application/octet-stream", request.headers().firstValue("Content-Type").orElse(null));
        assertEquals("Bearer session-bin", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("2", request.headers().firstValue("X-Attachment-Chunk-Index").orElse(null));
    }

    @Test
    void get_authenticated_bytes_preserves_response_headers_and_body() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(byteResponse(
                200,
                new byte[] { 4, 5, 6 },
                URI.create("https://api.test/api/v1/attachments/a"),
                Map.of("X-Attachment-Id", List.of("a"))));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-download", fakeHttpClient);
        HttpResponse<byte[]> response = client.getAuthenticatedBytes("/api/v1/attachments/a").join();

        HttpRequest request = fakeHttpClient.requests.getFirst();
        assertEquals("GET", request.method());
        assertEquals("Bearer session-download", request.headers().firstValue("Authorization").orElse(null));
        assertEquals("a", response.headers().firstValue("X-Attachment-Id").orElse(null));
        assertEquals(3, response.body().length);
    }

    @Test
    void delete_authenticated_sets_bearer_header() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(response(200, "{}", URI.create("https://api.test/api/v1/logout")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-3", fakeHttpClient);
        client.deleteAuthenticated("/api/v1/logout").join();

        HttpRequest request = fakeHttpClient.requests.getFirst();
        assertEquals("DELETE", request.method());
        assertEquals("Bearer session-3", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void authenticated_http_uses_updated_access_token() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(response(200, "{\"ok\":true}", URI.create("https://api.test/api/v1/ping")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "old-token", fakeHttpClient);
        client.updateAccessToken("new-token");
        client.getAuthenticated("/api/v1/ping").join();

        HttpRequest request = fakeHttpClient.requests.getFirst();
        assertEquals("Bearer new-token", request.headers().firstValue("Authorization").orElse(null));
    }

    @Test
    void authenticated_http_retries_once_on_connection_error() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueError(new ConnectException("down"));
        fakeHttpClient.enqueueResponse(response(200, "{\"ok\":1}", URI.create("https://api.test/api/v1/retry")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-4", fakeHttpClient);
        String body = client.getAuthenticated("/api/v1/retry").join();

        assertEquals("{\"ok\":1}", body);
        assertEquals(2, fakeHttpClient.requests.size());
    }

    @Test
    void authenticated_http_maps_non_2xx_to_http_communication_exception() {
        FakeHttpClient fakeHttpClient = new FakeHttpClient();
        fakeHttpClient.enqueueResponse(response(500, "boom", URI.create("https://api.test/api/v1/fail")));

        AuthHttpClient client = new AuthHttpClient(URI.create("https://api.test"), "session-5", fakeHttpClient);
        Throwable thrown = org.junit.jupiter.api.Assertions.assertThrows(
                java.util.concurrent.CompletionException.class,
                () -> client.getAuthenticated("/api/v1/fail").join());

        HttpCommunicationException cause = assertInstanceOf(HttpCommunicationException.class, thrown.getCause());
        assertEquals(500, cause.getStatusCode());
        assertEquals("boom", cause.getResponseBody());
    }

    private static HttpResponse<String> response(int status, String body, URI uri) {
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
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
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static HttpResponse<byte[]> byteResponse(
            int status,
            byte[] body,
            URI uri,
            Map<String, List<String>> headers) {
        HttpRequest request = HttpRequest.newBuilder().uri(uri).build();
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public byte[] body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return uri;
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private static final class FakeHttpClient extends HttpClient {
        private final List<HttpRequest> requests = new ArrayList<>();
        private final ArrayDeque<Object> queue = new ArrayDeque<>();

        void enqueueResponse(HttpResponse<?> response) {
            queue.addLast(new QueuedResponse(
                    response.statusCode(),
                    responseBodyBytes(response.body()),
                    response.uri(),
                    response.headers(),
                    response.version()));
        }

        void enqueueError(Throwable throwable) {
            queue.addLast(throwable);
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
            return null;
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
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("Not used by tests");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            if (queue.isEmpty()) {
                return CompletableFuture.failedFuture(new AssertionError("No queued fake response"));
            }
            Object next = queue.removeFirst();
            if (next instanceof Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
            if (!(next instanceof QueuedResponse response)) {
                return CompletableFuture.failedFuture(new AssertionError("Unsupported queued fake response"));
            }
            try {
                return decodeResponse(request, responseBodyHandler, response);
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private static byte[] responseBodyBytes(Object body) {
            if (body == null) {
                return new byte[0];
            }
            if (body instanceof byte[] bytes) {
                return bytes.clone();
            }
            if (body instanceof String text) {
                return text.getBytes(StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("Unsupported fake response body type: " + body.getClass().getName());
        }

        private static <T> CompletableFuture<HttpResponse<T>> decodeResponse(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                QueuedResponse response) {
            BodySubscriber<T> subscriber = responseBodyHandler.apply(new FakeResponseInfo(response));
            subscriber.onSubscribe(new NoopSubscription());
            if (response.body().length > 0) {
                subscriber.onNext(List.of(ByteBuffer.wrap(response.body())));
            }
            subscriber.onComplete();
            return subscriber.getBody()
                    .toCompletableFuture()
                    .thenApply(body -> responseFor(request, response, body));
        }

        private static <T> HttpResponse<T> responseFor(HttpRequest request, QueuedResponse response, T body) {
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return response.statusCode();
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return response.headers();
                }

                @Override
                public T body() {
                    return body;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return response.uri();
                }

                @Override
                public Version version() {
                    return response.version();
                }
            };
        }
    }

    private record QueuedResponse(
            int statusCode,
            byte[] body,
            URI uri,
            HttpHeaders headers,
            HttpClient.Version version) {
    }

    private record FakeResponseInfo(QueuedResponse response) implements HttpResponse.ResponseInfo {
        @Override
        public int statusCode() {
            return response.statusCode();
        }

        @Override
        public HttpHeaders headers() {
            return response.headers();
        }

        @Override
        public HttpClient.Version version() {
            return response.version();
        }
    }

    private static final class NoopSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            // BodySubscribers used here receive the complete in-memory fake body immediately.
        }

        @Override
        public void cancel() {
            // Nothing is allocated by this fake subscription, so cancellation has no work to do.
        }
    }
}
