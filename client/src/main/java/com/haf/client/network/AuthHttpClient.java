package com.haf.client.network;

import com.haf.client.exceptions.HttpCommunicationException;
import com.haf.client.exceptions.SslConfigurationException;
import com.haf.client.utils.SslContextUtils;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
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
        if (extraHeaders != null && !extraHeaders.isEmpty()) {
            extraHeaders.forEach((name, value) -> {
                if (name != null && !name.isBlank() && value != null) {
                    builder.header(name, value);
                }
            });
        }
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

    private String validateResponse(HttpResponse<String> response, String method) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        throw new HttpCommunicationException(
                "HTTP " + method + " failed with status " + response.statusCode() + ": " + response.body(),
                response.statusCode(),
                response.body());
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
