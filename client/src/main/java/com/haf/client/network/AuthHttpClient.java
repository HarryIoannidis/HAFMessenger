package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.exceptions.SslConfigurationException;
import com.haf.client.utils.SslContextUtils;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * Authenticated HTTPS transport used by client messaging and session workflows.
 */
public class AuthHttpClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthHttpClient.class);
    private static final int CONNECTION_TIMEOUT_SECONDS = 5;

    private final URI httpBaseUri;
    private volatile String sessionId;
    private final HttpClient httpClient;

    /**
     * Creates an authenticated HTTPS transport client backed by the default
     * strict-TLS {@link HttpClient}.
     *
     * @param httpBaseUri base HTTPS API URI
     * @param sessionId   bearer access token
     */
    public AuthHttpClient(URI httpBaseUri, String sessionId) {
        this(httpBaseUri, sessionId, createDefaultHttpClient());
    }

    AuthHttpClient(URI httpBaseUri, String sessionId, HttpClient httpClient) {
        this.httpBaseUri = normalizeHttpBaseUri(httpBaseUri);
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    private static HttpClient createDefaultHttpClient() {
        SSLContext sslContext = createDefaultSslContext();
        SSLParameters sslParameters = SslContextUtils.createHttpsSslParameters();
        return HttpClient.newBuilder()
                .sslContext(sslContext)
                .sslParameters(sslParameters)
                .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT_SECONDS))
                .build();
    }

    private static SSLContext createDefaultSslContext() {
        try {
            return SslContextUtils.getStrictSslContext();
        } catch (Exception e) {
            throw new SslConfigurationException("Failed to create SSL context", e);
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
     * Executes an authenticated HTTP GET request.
     *
     * @param path relative API path
     * @return future with response body for successful 2xx responses
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
     * Executes an authenticated HTTP GET request and returns binary response bytes
     * together with response headers.
     *
     * @param path relative API path
     * @return future with validated binary response
     */
    public CompletableFuture<HttpResponse<byte[]>> getAuthenticatedBytes(String path) {
        URI requestUri = buildAuthenticatedRequestUri(path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .GET()
                .build();
        return sendBytesWithRetry(request, "GET");
    }

    /**
     * Callback interface for streaming download progress reporting.
     */
    @FunctionalInterface
    public interface DownloadProgressCallback {
        /**
         * Called after each chunk of data is read from the response stream.
         *
         * @param bytesRead  total bytes read so far
         * @param totalBytes expected total bytes from Content-Length, or {@code -1}
         *                   when unknown
         */
        void onProgress(long bytesRead, long totalBytes);
    }

    /**
     * Executes an authenticated HTTP GET request and returns binary response bytes
     * while reporting download progress through a callback.
     *
     * @param path             relative API path
     * @param progressCallback callback invoked after each read chunk; may be
     *                         {@code null}
     * @return validated binary response including headers
     * @throws IOException if network or I/O failure occurs
     */
    public HttpResponse<byte[]> getAuthenticatedBytesStreaming(
            String path,
            DownloadProgressCallback progressCallback) throws IOException {
        URI requestUri = buildAuthenticatedRequestUri(path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .GET()
                .build();
        return sendBytesStreaming(request, "GET", progressCallback);
    }

    /**
     * Executes an authenticated HTTP POST request with JSON content.
     *
     * @param path relative API path
     * @param body JSON request payload
     * @return future with response body for successful 2xx responses
     */
    public CompletableFuture<String> postAuthenticated(String path, String body) {
        return postAuthenticated(path, body, Map.of());
    }

    /**
     * Executes an authenticated HTTP POST request with JSON content and optional
     * extra headers.
     *
     * @param path         relative API path
     * @param body         JSON request payload
     * @param extraHeaders optional extra headers to append
     * @return future with response body for successful 2xx responses
     */
    public CompletableFuture<String> postAuthenticated(String path, String body, Map<String, String> extraHeaders) {
        URI requestUri = buildAuthenticatedRequestUri(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        addExtraHeaders(builder, extraHeaders);
        return sendWithRetry(builder.build(), "POST");
    }

    /**
     * Executes an authenticated HTTP POST request with binary request content.
     *
     * @param path         relative API path
     * @param body         request bytes
     * @param contentType  request content type
     * @param extraHeaders optional extra headers to append
     * @return future with response body for successful 2xx responses
     */
    public CompletableFuture<String> postAuthenticatedBytes(
            String path,
            byte[] body,
            String contentType,
            Map<String, String> extraHeaders) {
        URI requestUri = buildAuthenticatedRequestUri(path);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(requestUri)
                .header("Authorization", authorizationHeaderValue())
                .header("Content-Type", contentType == null || contentType.isBlank()
                        ? "application/octet-stream"
                        : contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body == null ? new byte[0] : body));
        addExtraHeaders(builder, extraHeaders);
        return sendWithRetry(builder.build(), "POST");
    }

    /**
     * Executes an authenticated HTTP DELETE request.
     *
     * @param path relative API path
     * @return future with response body for successful 2xx responses
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
     * Updates the bearer token used for future authenticated requests.
     *
     * @param accessToken new non-blank access token
     */
    public void updateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken");
        }
        this.sessionId = accessToken;
    }

    /**
     * Closes transport resources held by this client.
     * The current implementation does not hold a persistent socket and therefore
     * performs no action.
     */
    public void close() {
        // No persistent socket to close. Method retained for session cleanup symmetry.
    }

    private URI buildAuthenticatedRequestUri(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
        return httpBaseUri.resolve(path);
    }

    private String authorizationHeaderValue() {
        return "Bearer " + sessionId;
    }

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

    private CompletableFuture<HttpResponse<byte[]>> sendBytesWithRetry(HttpRequest request, String method) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> validateBytesResponse(response, method))
                .exceptionallyCompose(error -> {
                    if (isConnectionError(error)) {
                        LOGGER.warn("HTTP {} failed with connection error, retrying once", method);
                        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                                .thenApply(response -> validateBytesResponse(response, method));
                    }
                    return CompletableFuture.failedFuture(error);
                });
    }

    /**
     * Sends an HTTP request synchronously and reads the response body as a stream
     * in 8 KB chunks, invoking a progress callback after each chunk.
     *
     * @param request          HTTP request to send
     * @param method           HTTP method label used for error messages
     * @param progressCallback optional progress callback; may be {@code null}
     * @return validated HTTP response with fully-read body bytes
     * @throws IOException if the request or response streaming fails
     */
    private HttpResponse<byte[]> sendBytesStreaming(
            HttpRequest request,
            String method,
            DownloadProgressCallback progressCallback) throws IOException {
        try {
            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                byte[] errorBody;
                try (InputStream errorStream = response.body()) {
                    errorBody = errorStream == null ? new byte[0] : errorStream.readAllBytes();
                }
                String errorText = new String(errorBody, java.nio.charset.StandardCharsets.UTF_8);
                throw new HttpCommunicationException(
                        "HTTP " + method + " failed with status " + statusCode + ": " + errorText,
                        statusCode, errorText);
            }

            long totalBytes = response.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1L);

            try (InputStream inputStream = response.body()) {
                ByteArrayOutputStream outputBuffer = totalBytes > 0
                        ? new ByteArrayOutputStream((int) Math.min(totalBytes, Integer.MAX_VALUE))
                        : new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                long bytesRead = 0;
                int read;
                while ((read = inputStream.read(chunk)) != -1) {
                    outputBuffer.write(chunk, 0, read);
                    bytesRead += read;
                    if (progressCallback != null) {
                        progressCallback.onProgress(bytesRead, totalBytes);
                    }
                }
                byte[] body = outputBuffer.toByteArray();
                // Wrap the streamed body into an HttpResponse<byte[]> compatible result.
                return new StreamedBytesResponse<>(response, body);
            }
        } catch (HttpCommunicationException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP streaming download interrupted", ex);
        } catch (Exception ex) {
            throw new IOException("HTTP streaming download failed", ex);
        }
    }

    /**
     * Wraps an {@link HttpResponse} header carrier with separately buffered body
     * bytes so the streaming download result is compatible with existing
     * {@code HttpResponse<byte[]>} consumers.
     *
     * @param <T> original body type of the delegate response
     */
    private static final class StreamedBytesResponse<T> implements HttpResponse<byte[]> {
        private final HttpResponse<T> delegate;
        private final byte[] body;

        StreamedBytesResponse(HttpResponse<T> delegate, byte[] body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override public int statusCode() { return delegate.statusCode(); }
        @Override public HttpRequest request() { return delegate.request(); }
        @Override public java.util.Optional<HttpResponse<byte[]>> previousResponse() { return java.util.Optional.empty(); }
        @Override public HttpHeaders headers() { return delegate.headers(); }
        @Override public byte[] body() { return body; }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return delegate.sslSession(); }
        @Override public URI uri() { return delegate.uri(); }
        @Override public HttpClient.Version version() { return delegate.version(); }
    }

    private String validateResponse(HttpResponse<String> response, String method) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        throw new HttpCommunicationException(
                "HTTP " + method + " failed with status " + response.statusCode() + ": " + response.body(),
                response.statusCode(),
                response.body());
    }

    private HttpResponse<byte[]> validateBytesResponse(HttpResponse<byte[]> response, String method) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response;
        }
        byte[] body = response.body();
        String responseBody = body == null ? "" : new String(body, java.nio.charset.StandardCharsets.UTF_8);
        throw new HttpCommunicationException(
                "HTTP " + method + " failed with status " + response.statusCode() + ": " + responseBody,
                response.statusCode(),
                responseBody);
    }

    private static void addExtraHeaders(HttpRequest.Builder builder, Map<String, String> extraHeaders) {
        if (extraHeaders == null || extraHeaders.isEmpty()) {
            return;
        }
        extraHeaders.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null) {
                builder.header(name, value);
            }
        });
    }

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
}
