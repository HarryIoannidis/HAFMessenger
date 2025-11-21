package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.server.router.RateLimiterService.RateLimitDecision;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.utils.JsonCodec;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpIngressServer {

    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final String REQUEST_PATH = "/api/v1/messages";
    private static final String TEST_USER_ID = "test-user";

    private final ServerConfig config;
    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiterService;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;
    private final EncryptedMessageValidator validator;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private HttpsServer server;

    /**
     * Creates an HttpIngressServer with a default MetricsRegistry.
     * @param config the ServerConfig
     * @param sslContext the SSLContext
     * @param mailboxRouter the MailboxRouter
     * @param rateLimiterService the RateLimiterService
     * @param auditLogger the AuditLogger
     * @param validator the EncryptedMessageValidator
     * @throws IOException if an error occurs while creating the HttpsServer
     */
    public HttpIngressServer(ServerConfig config,
                             SSLContext sslContext,
                             MailboxRouter mailboxRouter,
                             RateLimiterService rateLimiterService,
                             AuditLogger auditLogger,
                             MetricsRegistry metricsRegistry,
                             EncryptedMessageValidator validator) throws IOException {
        this.config = config;
        this.mailboxRouter = mailboxRouter;
        this.rateLimiterService = rateLimiterService;
        this.auditLogger = auditLogger;
        this.metricsRegistry = metricsRegistry;
        this.validator = validator;
        this.server = createServer(sslContext);
    }

    /**
     * Creates an HttpsServer with the given SSLContext.
     * @param sslContext the SSLContext
     * @return the created HttpsServer
     * @throws IOException if an error occurs while creating the HttpsServer
     */
    private HttpsServer createServer(SSLContext sslContext) throws IOException {
        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(config.getHttpPort()), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
                sslParameters.setProtocols(new String[]{"TLSv1.3"});
                sslParameters.setCipherSuites(new String[]{
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_CHACHA20_POLY1305_SHA256"
                });

                params.setSSLParameters(sslParameters);
            }
        });

        httpsServer.createContext(REQUEST_PATH, new IngressHandler());
        httpsServer.setExecutor(executor);

        return httpsServer;
    }

    /**
     * Starts the HttpsServer.
     */
    public void start() {
        server.start();
    }

    /**
     * Stops the HttpsServer.
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        executor.shutdownNow();
    }

    /**
     * Handles HTTP requests.
     */
    private final class IngressHandler implements HttpHandler {

        /**
         * Handles an HTTP request.
         * @param exchange the HttpExchange
         * @throws IOException if an error occurs while handling the request
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long start = System.nanoTime();
            String requestId = UUID.randomUUID().toString();
            String userId = TEST_USER_ID;
            exchange.getResponseHeaders().add("X-Request-Id", requestId);
            exchange.getResponseHeaders().add("X-User-Id", userId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }

                String body = readBody(exchange.getRequestBody());
                EncryptedMessage message = JsonCodec.fromJson(body, EncryptedMessage.class);

                EncryptedMessageValidator.ValidationResult validationResult = validator.validate(message);
                if (!validationResult.valid()) {
                    auditLogger.logValidationFailure(requestId, userId, message.recipientId, validationResult.reason());
                    respond(exchange, requestId, 400, "{\"error\":\"" + validationResult.reason() + "\"}");
                    return;
                }

                RateLimitDecision rateDecision = rateLimiterService.checkAndConsume(requestId, userId);
                if (!rateDecision.allowed()) {
                    auditLogger.logRateLimit(requestId, userId, rateDecision.retryAfterSeconds());
                    respond(exchange, requestId, 429,
                        "{\"error\":\"rate limit\",\"retryAfterSeconds\":" + rateDecision.retryAfterSeconds() + "}");
                    return;
                }

                QueuedEnvelope envelope = mailboxRouter.ingress(message);
                auditLogger.logIngressAccepted(
                    requestId,
                    userId,
                    message.recipientId,
                    202,
                    Duration.ofNanos(System.nanoTime() - start).toMillis()
                );

                String response = JsonCodec.toJson(new IngressResponse(envelope.envelopeId(), validationResult.expiresAtMillis()));
                respond(exchange, requestId, 202, response);
            } catch (Exception ex) {
                auditLogger.logError("ingress_http_error", requestId, userId, ex);
                metricsRegistry.incrementRejects();
                respond(exchange, requestId, 500, "{\"error\":\"internal\"}");
            } finally {
                exchange.close();
            }
        }

        /**
         * Reads the body of an HTTP request.
         * @param body the InputStream
         * @return the body as a string
         * @throws IOException if an error occurs while reading the body
         */
        private String readBody(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("Request body exceeds limit");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Applies security headers to the given Headers.
         * @param headers the Headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        /**
         * Sends a response to the given HttpExchange.
         * @param exchange the HttpExchange
         * @param requestId the request ID
         * @param status the response status code
         * @param body the response body
         * @throws IOException if an error occurs while sending the response
         */
        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("Content-Type", "application/json");

            if (!responseHeaders.containsKey("X-Request-Id")) {
                responseHeaders.add("X-Request-Id", requestId);
            }

            if (!responseHeaders.containsKey("X-User-Id")) {
                responseHeaders.add("X-User-Id", TEST_USER_ID);
            }

            exchange.sendResponseHeaders(status, payload.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Represents the response to an ingress request.
     * @param envelopeId the envelope ID
     * @param expiresAt the expiration time of the envelope
     */
    private record IngressResponse(String envelopeId, long expiresAt) { }
}

