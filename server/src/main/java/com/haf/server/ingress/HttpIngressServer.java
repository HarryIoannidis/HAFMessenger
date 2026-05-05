package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.Contact;
import com.haf.server.db.FileUpload;
import com.haf.server.db.Attachment;
import com.haf.server.db.Session;
import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.server.router.RateLimiterService.ApiRateLimitScope;
import com.haf.server.router.RateLimiterService.RateLimitDecision;
import com.haf.shared.dto.EncryptedFile;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.responses.PublicKeyResponse;
import com.haf.shared.requests.LoginRequest;
import com.haf.shared.responses.LoginResponse;
import com.haf.shared.requests.RefreshTokenRequest;
import com.haf.shared.responses.RefreshTokenResponse;
import com.haf.shared.requests.RegisterRequest;
import com.haf.shared.responses.RegisterResponse;
import com.haf.shared.responses.UserSearchResponse;
import com.haf.shared.dto.UserSearchResult;
import com.haf.shared.responses.ContactsResponse;
import com.haf.shared.requests.AddContactRequest;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.haf.shared.constants.CryptoConstants;
import com.password4j.Password;
import com.password4j.Argon2Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Handles HTTPS API ingress for authentication, messaging, and attachments.
 */
@SuppressWarnings("java:S1075")
public final class HttpIngressServer {

    private HttpsServer server;
    private final ServerConfig config;
    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiterService;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;
    private final EncryptedMessageValidator validator;
    private final User userDAO;
    private final Session sessionDAO;
    private final FileUpload fileUploadDAO;
    private final Attachment attachmentDAO;
    private final Contact contactDAO;
    private final ExecutorService executor;
    private final Object trustedProxyCacheLock = new Object();
    private final AtomicReference<TrustedProxyCache> trustedProxyCache = new AtomicReference<>(
            TrustedProxyCache.empty());
    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final String MESSAGES_PATH = apiPath("messages");
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String X_REQUEST_ID = "X-Request-Id";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String AUTHORIZATION = "Authorization";
    private static final String RECIPIENT_KEY_FINGERPRINT_HEADER = "X-Recipient-Key-Fingerprint";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String REQUEST_BODY_EXCEEDS_LIMIT = "Request body exceeds limit";
    private static final String STRICT_TRANSPORT_SECURITY_VALUE = "max-age=63072000; includeSubDomains; preload";
    private static final String CONTENT_SECURITY_POLICY_VALUE = "default-src 'none'; frame-ancestors 'none'; base-uri 'none';";
    private static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
    private static final String X_FRAME_OPTIONS_VALUE = "DENY";
    private static final String METHOD_NOT_ALLOWED = "method not allowed";
    private static final String INTERNAL_SERVER_ERROR = "internal server error";
    private static final String INVALID_REQUEST_PATH = "invalid request path";
    private static final long PROD_ACTIVE_WINDOW_SECONDS = 8L;
    private static final String JSON_ERROR_PREFIX = "{\"error\":\"";
    private static final String JSON_ERROR_SUFFIX = "\"}";
    private static final Argon2Function ARGON2 = Argon2Function.getInstance(
            CryptoConstants.ARGON2_MEMORY_KB,
            CryptoConstants.ARGON2_ITERATIONS,
            CryptoConstants.ARGON2_PARALLELISM,
            CryptoConstants.ARGON2_OUTPUT_LENGTH,
            com.password4j.types.Argon2.ID);
    private static final String LOGIN_SENTINEL_HASH = Password.hash("haf-login-sentinel-password")
            .addRandomSalt(CryptoConstants.SALT_LEN)
            .with(ARGON2)
            .getResult();

    /**
     * Builds an API path under {@code /api/v1}.
     *
     * @param segment first path segment
     * @return full API path
     */
    private static String apiPath(String segment) {
        return "/api/v1/" + segment;
    }

    /**
     * Builds a nested API path under {@code /api/v1}.
     *
     * @param parent parent segment
     * @param child  child segment
     * @return full API path
     */
    private static String apiPath(String parent, String child) {
        return "/api/v1/" + parent + "/" + child;
    }

    private enum AttachmentAction {
        DOWNLOAD,
        CHUNK,
        COMPLETE,
        BIND
    }

    private static final class AttachmentRoute {
        private final String attachmentId;
        private final AttachmentAction action;
        private final ApiRateLimitScope rateLimitScope;

        private AttachmentRoute(String attachmentId, AttachmentAction action, ApiRateLimitScope rateLimitScope) {
            this.attachmentId = attachmentId;
            this.action = action;
            this.rateLimitScope = rateLimitScope;
        }

        private String attachmentId() {
            return attachmentId;
        }

        private AttachmentAction action() {
            return action;
        }

        private ApiRateLimitScope rateLimitScope() {
            return rateLimitScope;
        }
    }

    /**
     * Resolves user active state from recent authenticated session activity.
     *
     * @param userId user id to evaluate
     * @return {@code true} when user is currently active
     */
    private boolean isUserRecentlyActive(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return sessionDAO.isUserRecentlyActive(userId, PROD_ACTIVE_WINDOW_SECONDS);
    }

    /**
     * Escapes a string for safe JSON literal embedding.
     *
     * @param value raw input value
     * @return escaped value or empty string for {@code null}
     */
    static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        escaped.append("\\u");
                        escaped.append(Character.forDigit((ch >> 12) & 0xF, 16));
                        escaped.append(Character.forDigit((ch >> 8) & 0xF, 16));
                        escaped.append(Character.forDigit((ch >> 4) & 0xF, 16));
                        escaped.append(Character.forDigit(ch & 0xF, 16));
                    } else {
                        escaped.append(ch);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /**
     * Creates an HttpIngressServer with a default MetricsRegistry.
     *
     * @param config             the ServerConfig
     * @param sslContext         the SSLContext
     * @param mailboxRouter      the MailboxRouter
     * @param rateLimiterService the RateLimiterService
     * @param auditLogger        the AuditLogger
     * @param validator          the EncryptedMessageValidator
     * @throws IOException if an error occurs while creating the HttpsServer
     */
    public HttpIngressServer(ServerConfig config,
            SSLContext sslContext,
            MailboxRouter mailboxRouter,
            RateLimiterService rateLimiterService,
            AuditLogger auditLogger,
            MetricsRegistry metricsRegistry,
            EncryptedMessageValidator validator,
            User userDAO,
            Session sessionDAO,
            FileUpload fileUploadDAO,
            Attachment attachmentDAO,
            Contact contactDAO) throws IOException {
        this.config = config;
        this.mailboxRouter = mailboxRouter;
        this.rateLimiterService = rateLimiterService;
        this.auditLogger = auditLogger;
        this.metricsRegistry = metricsRegistry;
        this.validator = validator;
        this.userDAO = userDAO;
        this.sessionDAO = sessionDAO;
        this.fileUploadDAO = fileUploadDAO;
        this.attachmentDAO = attachmentDAO;
        this.contactDAO = contactDAO;
        refreshTrustedProxyRanges(config.getTrustedProxyCidrs());
        this.executor = createIngressExecutor(config);
        this.server = createServer(sslContext);
    }

    /**
     * Creates a bounded ingress executor to prevent unbounded thread growth.
     *
     * @param config runtime config carrying thread/queue limits
     * @return bounded executor service
     */
    private static ExecutorService createIngressExecutor(ServerConfig config) {
        int threadCount = config.getIngressExecutorThreads();
        int queueCapacity = config.getIngressExecutorQueueCapacity();
        AtomicInteger threadIndex = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("haf-http-ingress-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Creates an HttpsServer with the given SSLContext.
     *
     * @param sslContext the SSLContext
     * @return the created HttpsServer
     * @throws IOException if an error occurs while creating the HttpsServer
     */
    private HttpsServer createServer(SSLContext sslContext) throws IOException {
        HttpsServer httpsServer = HttpsServer.create(new InetSocketAddress(config.getHttpPort()), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            /**
             * Applies strict TLS protocol/cipher restrictions on the HTTPS server endpoint.
             *
             * @param params mutable HTTPS parameters for the connection
             */
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
                sslParameters.setProtocols(new String[] { "TLSv1.3" });
                sslParameters.setCipherSuites(new String[] {
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_CHACHA20_POLY1305_SHA256"
                });

                params.setSSLParameters(sslParameters);
            }
        });

        httpsServer.createContext(MESSAGES_PATH, new IngressHandler());
        httpsServer.createContext(apiPath("register"), new RegistrationHandler());
        httpsServer.createContext(apiPath("login"), new LoginHandler());
        httpsServer.createContext(apiPath("token", "refresh"), new TokenRefreshHandler());
        httpsServer.createContext(apiPath("logout"), new LogoutHandler());
        httpsServer.createContext(apiPath("users"), new UserKeyHandler());
        httpsServer.createContext(apiPath("search"), new SearchHandler());
        httpsServer.createContext(apiPath("contacts"), new ContactsHandler());
        httpsServer.createContext(apiPath("health"), new HealthHandler());
        httpsServer.createContext(apiPath("config", "admin-key"), new AdminKeyHandler());
        httpsServer.createContext(apiPath("config", "messaging"), new MessagingConfigHandler());
        httpsServer.createContext(apiPath("attachments"), new AttachmentsHandler());
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
        private static final int DEFAULT_FETCH_LIMIT = 100;
        private static final int MAX_FETCH_LIMIT = 500;

        /**
         * Handles an HTTP request.
         *
         * @param exchange the HttpExchange
         * @throws IOException if an error occurs while handling the request
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            long start = System.nanoTime();
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            AuthResult authResult = resolveAuthenticatedCaller(exchange);
            if (!authResult.authenticated()) {
                respond(exchange, requestId, 401, JSON_ERROR_PREFIX + authResult.error() + JSON_ERROR_SUFFIX);
                return;
            }
            String userId = authResult.callerId();

            try {
                String method = exchange.getRequestMethod();
                String path = normalizePath(exchange.getRequestURI().getPath());

                if (METHOD_POST.equalsIgnoreCase(method) && MESSAGES_PATH.equals(path)) {
                    handleIngress(exchange, requestId, userId, start);
                    return;
                }
                if (METHOD_GET.equalsIgnoreCase(method) && MESSAGES_PATH.equals(path)) {
                    handleFetchUndelivered(exchange, requestId, userId);
                    return;
                }
                if (METHOD_POST.equalsIgnoreCase(method) && (MESSAGES_PATH + "/ack").equals(path)) {
                    handleAcknowledge(exchange, requestId, userId);
                    return;
                }

                if (!MESSAGES_PATH.equals(path) && !(MESSAGES_PATH + "/ack").equals(path)) {
                    respond(exchange, requestId, 404, JSON_ERROR_PREFIX + INVALID_REQUEST_PATH + JSON_ERROR_SUFFIX);
                    return;
                }

                respond(exchange, requestId, 405, JSON_ERROR_PREFIX + METHOD_NOT_ALLOWED + JSON_ERROR_SUFFIX);
            } catch (IllegalArgumentException ex) {
                respond(exchange, requestId, 400, JSON_ERROR_PREFIX + escapeJson(ex.getMessage()) + JSON_ERROR_SUFFIX);
            } catch (Exception ex) {
                auditLogger.logError("ingress_http_error", requestId, userId, ex);
                metricsRegistry.incrementRejects();
                respond(exchange, requestId, 500, "{\"error\":\"internal\"}");
            } finally {
                exchange.close();
            }
        }

        /**
         * Handles authenticated message ingress via HTTP POST.
         *
         * @param exchange  HTTP exchange
         * @param requestId request identifier
         * @param userId    authenticated caller id
         * @param start     request start time in nanos
         * @throws IOException when request handling fails
         */
        private void handleIngress(HttpExchange exchange, String requestId, String userId, long start)
                throws IOException {
            String body = readBody(exchange.getRequestBody());
            EncryptedMessage message = JsonCodec.fromJson(body, EncryptedMessage.class);

            EncryptedMessageValidator.ValidationResult validationResult = validator.validate(message);
            if (!validationResult.valid()) {
                auditLogger.logValidationFailure(requestId, userId, message.getRecipientId(),
                        validationResult.reason());
                respond(exchange, requestId, 400,
                        JSON_ERROR_PREFIX + validationResult.reason() + JSON_ERROR_SUFFIX);
                return;
            }

            if (isRecipientKeyFingerprintStale(exchange, message)) {
                respond(exchange, requestId, 409,
                        "{\"error\":\"recipient key is stale\",\"code\":\"stale_recipient_key\"}");
                return;
            }

            RateLimitDecision rateDecision = rateLimiterService.checkAndConsume(requestId, userId);
            if (!rateDecision.allowed()) {
                auditLogger.logRateLimit(requestId, userId, rateDecision.retryAfterSeconds());
                respond(exchange, requestId, 429,
                        "{\"error\":\"rate limit\",\"retryAfterSeconds\":" + rateDecision.retryAfterSeconds()
                                + "}");
                return;
            }

            QueuedEnvelope envelope = mailboxRouter.ingress(message);
            auditLogger.logIngressAccepted(
                    requestId,
                    userId,
                    message.getRecipientId(),
                    202,
                    Duration.ofNanos(System.nanoTime() - start).toMillis());

            String response = JsonCodec
                    .toJson(new IngressResponse(envelope.envelopeId(), validationResult.expiresAtMillis()));
            respond(exchange, requestId, 202, response);
        }

        /**
         * Compares caller-provided recipient fingerprint header against current
         * recipient key fingerprint.
         *
         * Missing header is treated as backward-compatible no-op.
         *
         * @param exchange HTTP exchange carrying optional fingerprint header
         * @param message  validated encrypted message payload
         * @return {@code true} when caller used a stale recipient key fingerprint
         */
        private boolean isRecipientKeyFingerprintStale(HttpExchange exchange, EncryptedMessage message) {
            String providedFingerprint = exchange.getRequestHeaders().getFirst(RECIPIENT_KEY_FINGERPRINT_HEADER);
            if (providedFingerprint == null || providedFingerprint.isBlank() || message == null) {
                return false;
            }
            String recipientId = message.getRecipientId();
            if (recipientId == null || recipientId.isBlank()) {
                return false;
            }
            User.PublicKeyRecord recipientKey = userDAO.getPublicKey(recipientId);
            if (recipientKey == null || recipientKey.fingerprint() == null || recipientKey.fingerprint().isBlank()) {
                return false;
            }
            return !recipientKey.fingerprint().trim().equalsIgnoreCase(providedFingerprint.trim());
        }

        /**
         * Handles authenticated undelivered-envelope fetches for HTTP-polling
         * fallback clients.
         *
         * @param exchange  HTTP exchange
         * @param requestId request identifier
         * @param userId    authenticated caller id
         * @throws IOException when response write fails
         */
        private void handleFetchUndelivered(HttpExchange exchange, String requestId, String userId) throws IOException {
            if (!allowRateLimitedRequest(exchange, requestId, userId)) {
                return;
            }
            int limit = resolveFetchLimit(exchange.getRequestURI().getRawQuery());
            List<QueuedEnvelope> pending = mailboxRouter.fetchUndelivered(userId, limit);
            List<Map<String, Object>> envelopes = pending.stream()
                    .map(this::toEnvelopeResponse)
                    .toList();
            respond(exchange, requestId, 200, JsonCodec.toJson(Map.of("messages", envelopes)));
        }

        /**
         * Handles authenticated envelope acknowledgements for HTTP-polling fallback
         * clients.
         *
         * @param exchange  HTTP exchange
         * @param requestId request identifier
         * @param userId    authenticated caller id
         * @throws IOException when response write fails
         */
        private void handleAcknowledge(HttpExchange exchange, String requestId, String userId) throws IOException {
            if (!allowRateLimitedRequest(exchange, requestId, userId)) {
                return;
            }
            String body = readBody(exchange.getRequestBody());
            Map<?, ?> ackMap = JsonCodec.fromJson(body, Map.class);
            List<String> envelopeIds = extractEnvelopeIds(ackMap == null ? null : ackMap.get("envelopeIds"));
            if (envelopeIds.isEmpty()) {
                throw new IllegalArgumentException("envelopeIds is required");
            }

            boolean acknowledged = mailboxRouter.acknowledgeOwned(userId, envelopeIds);
            respond(exchange, requestId, 200, "{\"acknowledged\":" + acknowledged + "}");
        }

        /**
         * Applies per-user rate limiting using the same policy as message ingress.
         *
         * @param exchange  HTTP exchange
         * @param requestId request id for audit logging
         * @param userId    authenticated user id
         * @return {@code true} when request is allowed
         * @throws IOException when rate-limit response write fails
         */
        private boolean allowRateLimitedRequest(HttpExchange exchange, String requestId, String userId)
                throws IOException {
            RateLimitDecision rateDecision = rateLimiterService.checkAndConsume(requestId, userId);
            if (rateDecision.allowed()) {
                return true;
            }
            auditLogger.logRateLimit(requestId, userId, rateDecision.retryAfterSeconds());
            respond(exchange, requestId, 429,
                    "{\"error\":\"rate limit\",\"retryAfterSeconds\":" + rateDecision.retryAfterSeconds() + "}");
            return false;
        }

        /**
         * Converts queued envelope to the polling API wire format.
         *
         * @param envelope queued envelope
         * @return map payload serializable by JsonCodec
         */
        private Map<String, Object> toEnvelopeResponse(QueuedEnvelope envelope) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "message");
            payload.put("envelopeId", envelope.envelopeId());
            payload.put("payload", envelope.payload());
            payload.put("expiresAt", envelope.expiresAtEpochMs());
            return payload;
        }

        /**
         * Resolves fetch limit from query-string parameters.
         *
         * @param rawQuery raw query string
         * @return bounded limit value
         */
        private int resolveFetchLimit(String rawQuery) {
            Map<String, String> params = parseQueryParams(rawQuery);
            String rawLimit = params.get("limit");
            if (rawLimit == null || rawLimit.isBlank()) {
                return DEFAULT_FETCH_LIMIT;
            }
            try {
                return Math.clamp(Integer.parseInt(rawLimit), 1, MAX_FETCH_LIMIT);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("invalid limit", ex);
            }
        }

        /**
         * Parses first-value-wins query parameters from raw URI query text.
         *
         * @param rawQuery raw query string
         * @return decoded key-value map
         */
        private Map<String, String> parseQueryParams(String rawQuery) {
            Map<String, String> params = new HashMap<>();
            if (rawQuery == null || rawQuery.isBlank()) {
                return params;
            }
            String[] pairs = rawQuery.split("&");
            for (String pair : pairs) {
                if (pair == null || pair.isBlank()) {
                    continue;
                }
                int equalsIndex = pair.indexOf('=');
                String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
                String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                params.putIfAbsent(key, value);
            }
            return params;
        }

        /**
         * Extracts non-empty envelope id strings from parsed ack payload.
         *
         * @param idsRaw raw envelopeIds node
         * @return normalized envelope id list
         */
        private List<String> extractEnvelopeIds(Object idsRaw) {
            if (!(idsRaw instanceof List<?> ids) || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(id -> !id.isBlank())
                    .toList();
        }

        /**
         * Normalizes request paths by stripping a trailing slash.
         *
         * @param path raw request path
         * @return normalized path
         */
        private String normalizePath(String path) {
            if (path == null || path.isBlank()) {
                return "";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                return path.substring(0, path.length() - 1);
            }
            return path;
        }

        /**
         * Escapes text for safe inclusion in compact JSON string literals.
         *
         * @param value source text
         * @return escaped text
         */
        private String escapeJson(String value) {
            return HttpIngressServer.escapeJson(value == null ? INTERNAL_SERVER_ERROR : value);
        }

        /**
         * Reads the body of an HTTP request.
         *
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
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Applies security headers to the given Headers.
         *
         * @param headers the Headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends a response to the given HttpExchange.
         *
         * @param exchange  the HttpExchange
         * @param requestId the request ID
         * @param status    the response status code
         * @param body      the response body
         * @throws IOException if an error occurs while sending the response
         */
        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add(CONTENT_TYPE, APPLICATION_JSON);

            if (!responseHeaders.containsKey(X_REQUEST_ID)) {
                responseHeaders.add(X_REQUEST_ID, requestId);
            }

            exchange.sendResponseHeaders(status, payload.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles registration requests.
     */
    private final class RegistrationHandler implements HttpHandler {

        /**
         * Handles user registration requests including validation, duplication checks,
         * and persistence.
         *
         * @param exchange HTTP exchange
         * @throws IOException when I/O operations fail
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, JsonCodec.toJson(RegisterResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }

                String sourceIp = resolveClientIp(exchange);
                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.REGISTER, sourceIp)) {
                    return;
                }

                String body = readBody(exchange.getRequestBody());
                RegisterRequest request = JsonCodec.fromJson(body, RegisterRequest.class);

                // Validate required fields
                String validationError = validateRegistration(request);
                if (validationError != null) {
                    respond(exchange, requestId, 400, JsonCodec.toJson(RegisterResponse.error(validationError)));
                    return;
                }

                // Check for duplicate email
                if (userDAO.existsByEmail(request.getEmail())) {
                    respond(exchange, requestId, 409,
                            JsonCodec.toJson(RegisterResponse.error("Email is already registered")));
                    return;
                }

                String hashedPassword = Password.hash(request.getPassword())
                        .addRandomSalt(CryptoConstants.SALT_LEN)
                        .with(ARGON2)
                        .getResult();

                // Store user (starts in PENDING)
                String userId = userDAO.insert(request, hashedPassword);

                // Store photos if present (e2e encrypted ciphertext)
                String idPhotoId = storePhoto(request.getIdPhoto(), userId);
                String selfiePhotoId = storePhoto(request.getSelfiePhoto(), userId);

                // Update user record with photo IDs
                userDAO.updatePhotoIds(userId, idPhotoId, selfiePhotoId);

                auditLogger.logRegistration(requestId, userId, request.getEmail());

                respond(exchange, requestId, 200,
                        JsonCodec.toJson(RegisterResponse.success(userId)));
            } catch (Exception ex) {
                auditLogger.logError("registration_error", requestId, null, ex);
                respond(exchange, requestId, 500, JsonCodec.toJson(RegisterResponse.error(INTERNAL_SERVER_ERROR)));
            } finally {
                exchange.close();
            }
        }

        /**
         * Validates required registration fields and basic constraints.
         *
         * @param r registration request payload
         * @return validation error message, or {@code null} when payload is valid
         */
        private String validateRegistration(RegisterRequest r) {
            if (isBlank(r.getFullName()))
                return "fullName is required";
            if (isBlank(r.getEmail()))
                return "email is required";
            if (isBlank(r.getPassword()))
                return "password is required";
            String passwordValidation = validateStrongPassword(r.getPassword());
            if (passwordValidation != null)
                return passwordValidation;
            if (isBlank(r.getPublicKeyPem()))
                return "publicKeyPem is required";
            if (isBlank(r.getPublicKeyFingerprint()))
                return "publicKeyFingerprint is required";
            return null;
        }

        /**
         * Validates strong password policy.
         *
         * @param password password value
         * @return validation error, or {@code null} when valid
         */
        private String validateStrongPassword(String password) {
            if (password == null || password.length() < 8) {
                return "password must be at least 8 characters";
            }
            boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
            boolean hasNumber = password.chars().anyMatch(Character::isDigit);
            boolean hasSpecial = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
            if (!hasUppercase || !hasNumber || !hasSpecial) {
                return "password must include at least 1 uppercase letter, 1 number, and 1 special character";
            }
            return null;
        }

        /**
         * Persists a photo blob if present, returns file_id or null.
         * 
         * @param dto    the photo to store
         * @param userId the user ID
         * @return the file ID or null
         */
        private String storePhoto(EncryptedFile dto, String userId) {
            if (dto == null || dto.getCiphertextB64() == null || dto.getCiphertextB64().isBlank()) {
                return null;
            }
            return fileUploadDAO.insert(dto, userId);
        }

        /**
         * Checks whether a string is null or blank.
         *
         * @param value string value to test
         * @return {@code true} when value is null/blank
         */
        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        /**
         * Reads request body with hard byte-limit enforcement.
         *
         * @param body request body stream
         * @return UTF-8 decoded body text
         * @throws IOException when read fails or body exceeds configured limit
         */
        private String readBody(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Applies baseline security headers for registration responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response with request id propagation.
         *
         * @param exchange  HTTP exchange
         * @param requestId request id for tracing
         * @param status    HTTP status code
         * @param body      JSON body
         * @throws IOException when response write fails
         */
        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add(CONTENT_TYPE, APPLICATION_JSON);

            if (!responseHeaders.containsKey(X_REQUEST_ID)) {
                responseHeaders.add(X_REQUEST_ID, requestId);
            }

            exchange.sendResponseHeaders(status, payload.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Represents the response to an ingress request.
     *
     * @param envelopeId the envelope ID
     * @param expiresAt  the expiration time of the envelope
     */
    public record IngressResponse(String envelopeId, long expiresAt) {
    }

    /**
     * Handles login requests.
     */
    private final class LoginHandler implements HttpHandler {

        /**
         * Handles login requests, credential verification, account-status checks, and
         * session creation.
         *
         * @param exchange HTTP exchange
         * @throws IOException when I/O operations fail
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, JsonCodec.toJson(LoginResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }

                String body = readBody(exchange.getRequestBody());
                LoginRequest request = JsonCodec.fromJson(body, LoginRequest.class);
                String sourceIp = resolveClientIp(exchange);

                // Validate required fields
                if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
                    respond(exchange, requestId, 400,
                            JsonCodec.toJson(LoginResponse.error("email and password are required")));
                    return;
                }

                RateLimitDecision loginRateDecision = rateLimiterService.checkAndConsumeLoginAttempt(
                        requestId,
                        request.getEmail(),
                        sourceIp);
                if (!loginRateDecision.allowed()) {
                    respond(exchange, requestId, 429, JsonCodec.toJson(
                            LoginResponse.error("Too many login attempts", loginRateDecision.retryAfterSeconds())));
                    return;
                }

                // Look up user by email
                User.UserRecord user = userDAO.findByEmail(request.getEmail());
                boolean passwordValid = verifyPasswordWithSentinel(
                        request.getPassword(),
                        user == null ? null : user.passwordHash());
                if (user == null || !passwordValid) {
                    respond(exchange, requestId, 401,
                            JsonCodec.toJson(LoginResponse.error("Invalid email or password")));
                    return;
                }

                // Check account status
                if (!"APPROVED".equals(user.status())) {
                    respond(exchange, requestId, 403,
                            JsonCodec.toJson(LoginResponse.error(
                                    "Account is " + user.status().toLowerCase()
                                            + ". Please contact an administrator.")));
                    return;
                }

                boolean forceTakeover = Boolean.TRUE.equals(request.getForceTakeover());

                if (!forceTakeover && isDuplicateLoginAttempt(user.userId())) {
                    respond(exchange, requestId, 409,
                            JsonCodec.toJson(LoginResponse.error("Account is already logged in.")));
                    return;
                }

                if (forceTakeover) {
                    validateTakeoverKeyRequest(request);
                    applyForcedTakeover(user.userId(), request.getTakeoverPublicKeyPem(),
                            request.getTakeoverPublicKeyFingerprint());
                }

                // Create session
                Session.SessionTokens sessionTokens = sessionDAO.createSessionTokens(user.userId());
                rateLimiterService.clearLoginAttempts(request.getEmail(), sourceIp);

                auditLogger.logLogin(requestId, user.userId(), request.getEmail());

                respond(exchange, requestId, 200,
                        JsonCodec.toJson(LoginResponse.success(
                                user.userId(),
                                sessionTokens.accessToken(),
                                sessionTokens.refreshToken(),
                                sessionTokens.accessExpiresAtEpochSeconds(),
                                sessionTokens.refreshExpiresAtEpochSeconds(),
                                user.fullName(),
                                user.rank(),
                                user.regNumber(),
                                user.email(),
                                user.telephone(),
                                user.joinedDate(),
                                user.status())));
            } catch (IllegalArgumentException ex) {
                respond(exchange, requestId, 400, JsonCodec.toJson(LoginResponse.error(ex.getMessage())));
            } catch (Exception ex) {
                auditLogger.logError("login_error", requestId, null, ex);
                respond(exchange, requestId, 500, JsonCodec.toJson(LoginResponse.error(INTERNAL_SERVER_ERROR)));
            } finally {
                exchange.close();
            }
        }

        /**
         * Validates required takeover key fields and fingerprint consistency.
         *
         * @param request login request payload
         */
        private void validateTakeoverKeyRequest(LoginRequest request) {
            String takeoverPublicKeyPem = request.getTakeoverPublicKeyPem();
            String takeoverFingerprint = request.getTakeoverPublicKeyFingerprint();
            if (isBlank(takeoverPublicKeyPem) || isBlank(takeoverFingerprint)) {
                throw new IllegalArgumentException(
                        "takeoverPublicKeyPem and takeoverPublicKeyFingerprint are required when forceTakeover=true");
            }

            String normalizedProvidedFingerprint = takeoverFingerprint.trim();
            String normalizedCalculatedFingerprint = calculateFingerprint(takeoverPublicKeyPem);
            if (!normalizedCalculatedFingerprint.equalsIgnoreCase(normalizedProvidedFingerprint)) {
                throw new IllegalArgumentException("takeover key fingerprint mismatch");
            }
        }

        /**
         * Applies forced takeover effects: key rotation and session revocation.
         *
         * @param userId       authenticated user id
         * @param publicKeyPem takeover public key PEM
         * @param fingerprint  takeover key fingerprint
         */
        private void applyForcedTakeover(
                String userId,
                String publicKeyPem,
                String fingerprint) {
            userDAO.updatePublicKey(userId, publicKeyPem.trim(), fingerprint.trim());
            sessionDAO.revokeAllSessionsByUserId(userId);
        }

        /**
         * Computes SHA-256 fingerprint for a PEM-encoded public key.
         *
         * @param publicKeyPem public key in PEM format
         * @return lowercase fingerprint hex
         */
        private String calculateFingerprint(String publicKeyPem) {
            try {
                PublicKey publicKey = EccKeyIO.publicFromPem(publicKeyPem);
                return FingerprintUtil.sha256Hex(EccKeyIO.publicDer(publicKey));
            } catch (Exception ex) {
                throw new IllegalArgumentException("invalid takeoverPublicKeyPem", ex);
            }
        }

        /**
         * Verifies password hash while consuming Argon2 work even when account is
         * missing.
         *
         * @param providedPassword caller-provided password
         * @param storedHash       account hash, or {@code null} when account is unknown
         * @return {@code true} when stored hash is present and password matches
         */
        static boolean verifyPasswordWithSentinel(String providedPassword, String storedHash) {
            String hashToCheck = storedHash == null ? LOGIN_SENTINEL_HASH : storedHash;
            boolean matches = Password.check(providedPassword, hashToCheck).with(ARGON2);
            return storedHash != null && matches;
        }

        /**
         * Checks whether a string is null or blank.
         *
         * @param value string value to test
         * @return {@code true} when value is null/blank
         */
        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        /**
         * Checks duplicate-login state using recent session activity.
         *
         * @param userId user attempting login
         * @return {@code true} when user is considered currently active
         */
        private boolean isDuplicateLoginAttempt(String userId) {
            return isUserRecentlyActive(userId);
        }

        /**
         * Reads request body with hard byte-limit enforcement.
         *
         * @param body request body stream
         * @return UTF-8 decoded body text
         * @throws IOException when read fails or body exceeds configured limit
         */
        private String readBody(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Applies baseline security headers for login responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response with request id propagation.
         *
         * @param exchange  HTTP exchange
         * @param requestId request id for tracing
         * @param status    HTTP status code
         * @param body      JSON body
         * @throws IOException when response write fails
         */
        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add(CONTENT_TYPE, APPLICATION_JSON);

            if (!responseHeaders.containsKey(X_REQUEST_ID)) {
                responseHeaders.add(X_REQUEST_ID, requestId);
            }

            exchange.sendResponseHeaders(status, payload.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles token refresh requests.
     */
    private final class TokenRefreshHandler implements HttpHandler {

        /**
         * Handles refresh-token rotation requests.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response write fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, JsonCodec.toJson(RefreshTokenResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }

                String sourceIp = resolveClientIp(exchange);
                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.TOKEN_REFRESH, sourceIp)) {
                    return;
                }

                String body = readBody(exchange.getRequestBody());
                RefreshTokenRequest request = JsonCodec.fromJson(body, RefreshTokenRequest.class);
                if (request == null || isBlank(request.getRefreshToken())) {
                    respond(exchange, requestId, 400,
                            JsonCodec.toJson(RefreshTokenResponse.error("refreshToken is required")));
                    return;
                }

                Session.SessionTokens rotated = sessionDAO.refreshSession(request.getRefreshToken());
                if (rotated == null) {
                    String reason = sessionDAO.isRefreshSessionRevoked(request.getRefreshToken())
                            ? "session revoked by takeover"
                            : "invalid session";
                    respond(exchange, requestId, 401,
                            JsonCodec.toJson(RefreshTokenResponse.error(reason)));
                    return;
                }

                respond(exchange, requestId, 200, JsonCodec.toJson(
                        RefreshTokenResponse.success(
                                rotated.accessToken(),
                                rotated.refreshToken(),
                                rotated.accessExpiresAtEpochSeconds(),
                                rotated.refreshExpiresAtEpochSeconds())));
            } catch (Exception ex) {
                auditLogger.logError("token_refresh_error", requestId, null, ex);
                respond(exchange, requestId, 500, JsonCodec.toJson(RefreshTokenResponse.error(INTERNAL_SERVER_ERROR)));
            } finally {
                exchange.close();
            }
        }

        /**
         * Checks whether string is null or blank.
         *
         * @param value value to check
         * @return true when value is blank
         */
        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

        /**
         * Reads request body with limit enforcement.
         *
         * @param body request stream
         * @return body text
         * @throws IOException when read fails
         */
        private String readBody(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Applies baseline security headers.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response with request id propagation.
         *
         * @param exchange  HTTP exchange
         * @param requestId request id
         * @param status    status code
         * @param body      body JSON
         * @throws IOException on write failure
         */
        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add(CONTENT_TYPE, APPLICATION_JSON);
            if (!responseHeaders.containsKey(X_REQUEST_ID)) {
                responseHeaders.add(X_REQUEST_ID, requestId);
            }
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles logout requests.
     */
    private final class LogoutHandler implements HttpHandler {

        /**
         * Handles logout by revoking the caller session token.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response writing fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendPlain(exchange, 405, JSON_ERROR_PREFIX + METHOD_NOT_ALLOWED + JSON_ERROR_SUFFIX);
                    return;
                }

                AuthResult authResult = resolveAuthenticatedCaller(exchange);
                if (!authResult.authenticated()) {
                    sendPlain(exchange, 401, JSON_ERROR_PREFIX + authResult.error() + JSON_ERROR_SUFFIX);
                    return;
                }
                String sessionId = authResult.sessionId();

                sessionDAO.revokeSession(sessionId);
                sendPlain(exchange, 200, "{\"success\":true}");
            } catch (Exception ex) {
                auditLogger.logError("logout_error", requestId, null, ex);
                sendPlain(exchange, 500, JSON_ERROR_PREFIX + INTERNAL_SERVER_ERROR + JSON_ERROR_SUFFIX);
            } finally {
                exchange.close();
            }
        }

        /**
         * Applies baseline security headers for logout responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response for logout endpoint.
         *
         * @param exchange HTTP exchange
         * @param status   HTTP status code
         * @param body     JSON body
         * @throws IOException when response write fails
         */
        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Serves the Admin's X25519 public key so clients can E2E-encrypt
     * registration photos before sending them.
     */
    private final class AdminKeyHandler implements HttpHandler {

        /**
         * Handles public admin-key fetch requests.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response writing fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendPlain(exchange, 405, JSON_ERROR_PREFIX + METHOD_NOT_ALLOWED + JSON_ERROR_SUFFIX);
                    return;
                }

                String sourceIp = resolveClientIp(exchange);
                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.ADMIN_KEY, sourceIp)) {
                    return;
                }

                String pem = config.getAdminPublicKeyPem();
                String body = pem != null
                        ? "{\"adminPublicKeyPem\":\"" + pem.replace("\n", "\\n") + "\"}"
                        : "{\"adminPublicKeyPem\":null}";

                sendPlain(exchange, 200, body);
            } finally {
                exchange.close();
            }
        }

        /**
         * Applies baseline security headers for admin-key responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response for admin-key endpoint.
         *
         * @param exchange HTTP exchange
         * @param status   HTTP status code
         * @param body     JSON body
         * @throws IOException when response write fails
         */
        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles requests for users' public keys.
     */
    private final class UserKeyHandler implements HttpHandler {

        /**
         * Handles user public-key lookup by path {@code /api/v1/users/{userId}/key}.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response writing fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendPlain(exchange, 405, JsonCodec.toJson(PublicKeyResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }

                AuthResult authResult = resolveAuthenticatedCaller(exchange);
                if (!authResult.authenticated()) {
                    sendPlain(exchange, 401, JsonCodec.toJson(PublicKeyResponse.error(authResult.error())));
                    return;
                }
                String callerId = authResult.callerId();

                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.USER_KEY, callerId)) {
                    return;
                }

                // Path must match /api/v1/users/{userId}/key
                String path = exchange.getRequestURI().getPath();
                if (path == null || !path.endsWith("/key")) {
                    sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error(INVALID_REQUEST_PATH)));
                    return;
                }

                // Extract userId
                String prefix = apiPath("users") + "/";
                if (!path.startsWith(prefix)) {
                    sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error(INVALID_REQUEST_PATH)));
                    return;
                }

                String remainder = path.substring(prefix.length());
                int slashIndex = remainder.indexOf('/');
                if (slashIndex == -1) {
                    sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error("missing userId")));
                    return;
                }
                String targetUserId = remainder.substring(0, slashIndex);

                User.PublicKeyRecord keyRecord = userDAO.getPublicKey(targetUserId);
                if (keyRecord == null) {
                    sendPlain(exchange, 404, JsonCodec.toJson(PublicKeyResponse.error("user not found")));
                    return;
                }

                sendPlain(exchange, 200, JsonCodec.toJson(
                        PublicKeyResponse.success(targetUserId, keyRecord.publicKeyPem(), keyRecord.fingerprint())));
            } catch (Exception ex) {
                auditLogger.logError("user_key_lookup_error", requestId, null, ex);
                sendPlain(exchange, 500, JsonCodec.toJson(PublicKeyResponse.error(INTERNAL_SERVER_ERROR)));
            } finally {
                exchange.close();
            }
        }

        /**
         * Applies baseline security headers for key-lookup responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response for key-lookup endpoint.
         *
         * @param exchange HTTP exchange
         * @param status   HTTP status code
         * @param body     JSON body
         * @throws IOException when response write fails
         */
        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles search requests: {@code GET /api/v1/search?q=<query>}.
     * Requires a valid session via the {@code Authorization: Bearer <sessionId>}
     * header.
     */
    private final class SearchHandler implements HttpHandler {

        private static final String INVALID_CURSOR = "invalid cursor";

        /**
         * Handles authenticated user search with keyset pagination and signed cursors.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response writing fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendPlain(exchange, 405, JsonCodec.toJson(UserSearchResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }

                AuthResult authResult = resolveAuthenticatedCaller(exchange);
                if (!authResult.authenticated()) {
                    sendPlain(exchange, 401, JsonCodec.toJson(UserSearchResponse.error(authResult.error())));
                    return;
                }
                String callerId = authResult.callerId();

                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.SEARCH, callerId)) {
                    return;
                }

                Map<String, String> queryParams;
                try {
                    queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
                } catch (IllegalArgumentException _) {
                    sendPlain(exchange, 400, JsonCodec.toJson(UserSearchResponse.error("invalid query parameters")));
                    return;
                }
                String searchTerm = queryParams.getOrDefault("q", "");
                String normalizedQuery = searchTerm == null ? "" : searchTerm.trim();
                if (normalizedQuery.isBlank()) {
                    sendPlain(exchange, 200, JsonCodec.toJson(UserSearchResponse.success(List.of(), false, null)));
                    return;
                }

                if (normalizedQuery.length() < config.getSearchMinQueryLength()) {
                    sendPlain(exchange, 400, JsonCodec.toJson(UserSearchResponse
                            .error("query must be at least " + config.getSearchMinQueryLength() + " characters")));
                    return;
                }
                if (normalizedQuery.length() > config.getSearchMaxQueryLength()) {
                    normalizedQuery = normalizedQuery.substring(0, config.getSearchMaxQueryLength());
                }

                int effectiveLimit;
                try {
                    effectiveLimit = resolveLimit(queryParams.get("limit"));
                } catch (IllegalArgumentException _) {
                    sendPlain(exchange, 400, JsonCodec.toJson(UserSearchResponse.error("invalid limit")));
                    return;
                }

                String cursorToken = queryParams.get("cursor");
                CursorKey cursor;
                try {
                    cursor = decodeCursor(cursorToken);
                } catch (IllegalArgumentException _) {
                    sendPlain(exchange, 400, JsonCodec.toJson(UserSearchResponse.error(INVALID_CURSOR)));
                    return;
                }

                String queryHash = sha256Hex(normalizedQuery);
                auditLogger.logSearchRequest(
                        requestId,
                        callerId,
                        normalizedQuery.length(),
                        queryHash,
                        effectiveLimit,
                        cursor.isPresent());

                try {
                    User.SearchPage page = userDAO.searchUsersPage(
                            normalizedQuery,
                            callerId,
                            effectiveLimit,
                            cursor.fullName(),
                            cursor.userId());

                    List<UserSearchResult> results = page.results().stream()
                            .map(r -> new UserSearchResult(
                                    r.userId(),
                                    r.fullName(),
                                    r.regNumber(),
                                    r.email(),
                                    r.rank(),
                                    r.telephone(),
                                    r.joinedDate()))
                            .toList();
                    String nextCursor = page.hasMore() ? encodeCursor(page.lastFullName(), page.lastUserId()) : null;
                    sendPlain(exchange, 200,
                            JsonCodec.toJson(UserSearchResponse.success(results, page.hasMore(), nextCursor)));
                } catch (Exception ex) {
                    auditLogger.logError("search_error", requestId, callerId, ex, Map.of(
                            "queryLength", normalizedQuery.length(),
                            "queryHash", queryHash,
                            "limit", effectiveLimit,
                            "cursorSupplied", cursor.isPresent()));
                    sendPlain(exchange, 500, JsonCodec.toJson(UserSearchResponse.error(INTERNAL_SERVER_ERROR)));
                }
            } finally {
                exchange.close();
            }
        }

        /**
         * Resolves effective page limit from query string and configured bounds.
         *
         * @param rawLimit raw {@code limit} parameter
         * @return bounded page size
         * @throws IllegalArgumentException when raw limit is not numeric
         */
        private int resolveLimit(String rawLimit) {
            if (rawLimit == null || rawLimit.isBlank()) {
                return config.getSearchPageSize();
            }
            try {
                int requested = Integer.parseInt(rawLimit);
                return Math.clamp(requested, 1, config.getSearchMaxPageSize());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("invalid limit", ex);
            }
        }

        /**
         * Parses URL query parameters into a first-value-wins map.
         *
         * @param rawQuery raw query string (without leading {@code ?})
         * @return decoded query parameter map
         */
        private Map<String, String> parseQueryParams(String rawQuery) {
            Map<String, String> params = new HashMap<>();
            if (rawQuery == null || rawQuery.isBlank()) {
                return params;
            }

            String[] pairs = rawQuery.split("&");
            for (String pair : pairs) {
                if (pair == null || pair.isBlank()) {
                    continue;
                }
                int equalsIndex = pair.indexOf('=');
                String rawKey = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
                String rawValue = equalsIndex >= 0 ? pair.substring(equalsIndex + 1) : "";
                String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                params.putIfAbsent(key, value);
            }
            return params;
        }

        /**
         * Decodes and validates signed cursor token.
         *
         * @param cursorToken cursor token from query string
         * @return cursor key values or empty cursor when token is blank
         * @throws IllegalArgumentException when cursor is malformed or signature is
         *                                  invalid
         */
        private CursorKey decodeCursor(String cursorToken) {
            if (cursorToken == null || cursorToken.isBlank()) {
                return CursorKey.empty();
            }

            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursorToken), StandardCharsets.UTF_8);
                String[] parts = decoded.split("\\.", 3);
                if (parts.length != 3) {
                    throw new IllegalArgumentException(INVALID_CURSOR);
                }

                String payload = parts[0] + "." + parts[1];
                byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
                byte[] expectedSignature = hmac(payload);
                if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                    throw new IllegalArgumentException(INVALID_CURSOR);
                }

                String fullName = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                String userId = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                if (fullName.isBlank() || userId.isBlank()) {
                    throw new IllegalArgumentException(INVALID_CURSOR);
                }
                return new CursorKey(fullName, userId);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException(INVALID_CURSOR, ex);
            }
        }

        /**
         * Encodes cursor components into signed token.
         *
         * @param fullName last full-name key
         * @param userId   last user-id key
         * @return encoded cursor token or {@code null} when inputs are incomplete
         */
        private String encodeCursor(String fullName, String userId) {
            if (fullName == null || userId == null) {
                return null;
            }

            String fullNamePart = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(fullName.getBytes(StandardCharsets.UTF_8));
            String userIdPart = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(userId.getBytes(StandardCharsets.UTF_8));
            String payload = fullNamePart + "." + userIdPart;
            String signature = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(hmac(payload));
            String cursorPayload = payload + "." + signature;
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(cursorPayload.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Computes HMAC signature for cursor payload.
         *
         * @param payload unsigned cursor payload
         * @return signature bytes
         * @throws IllegalStateException when signing fails
         */
        private byte[] hmac(String payload) {
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                byte[] secretBytes = config.getSearchCursorSecret().getBytes(StandardCharsets.UTF_8);
                mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
                return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to sign search cursor", ex);
            }
        }

        /**
         * Computes SHA-256 hex digest used in search audit logs.
         *
         * @param value source value
         * @return lowercase hex digest
         * @throws IllegalStateException when SHA-256 is unavailable
         */
        private static String sha256Hex(String value) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
                StringBuilder hex = new StringBuilder(bytes.length * 2);
                for (byte b : bytes) {
                    hex.append(String.format("%02x", b));
                }
                return hex.toString();
            } catch (NoSuchAlgorithmException ex) {
                throw new IllegalStateException("SHA-256 is not available", ex);
            }
        }

        private record CursorKey(String fullName, String userId) {
            /**
             * Creates an empty cursor placeholder.
             *
             * @return cursor with null keys
             */
            static CursorKey empty() {
                return new CursorKey(null, null);
            }

            /**
             * Indicates whether cursor carries both pagination keys.
             *
             * @return {@code true} when fullName and userId are both present
             */
            boolean isPresent() {
                return fullName != null && userId != null;
            }
        }

        /**
         * Applies baseline security headers for search responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response for search endpoint.
         *
         * @param exchange HTTP exchange
         * @param status   HTTP status code
         * @param body     JSON body
         * @throws IOException when response write fails
         */
        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles health check requests.
     */
    private final class HealthHandler implements HttpHandler {

        /**
         * Handles health checks supporting GET and HEAD.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response write fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add(X_REQUEST_ID, UUID.randomUUID().toString());
            applySecurityHeaders(exchange.getResponseHeaders());

            String method = exchange.getRequestMethod();
            if ("HEAD".equalsIgnoreCase(method) || METHOD_GET.equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
                exchange.sendResponseHeaders(200, -1);
            } else {
                sendPlain(exchange, 405, JSON_ERROR_PREFIX + METHOD_NOT_ALLOWED + JSON_ERROR_SUFFIX);
            }
            exchange.close();
        }

        /**
         * Applies baseline security headers for health responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response for health endpoint.
         *
         * @param exchange HTTP exchange
         * @param status   HTTP status code
         * @param body     JSON body
         * @throws IOException when response write fails
         */
        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles contacts requests: {@code GET /api/v1/contacts},
     * {@code POST /api/v1/contacts}, and
     * {@code DELETE /api/v1/contacts?contactId=xyz}.
     */
    private final class ContactsHandler implements HttpHandler {

        /**
         * Handles authenticated contacts API requests.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response write fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            AuthResult authResult = resolveAuthenticatedCaller(exchange);
            if (!authResult.authenticated()) {
                sendPlain(exchange, 401, JsonCodec.toJson(ContactsResponse.error(authResult.error())));
                return;
            }
            String callerId = authResult.callerId();

            String method = exchange.getRequestMethod();

            try {
                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.CONTACTS, callerId)) {
                    return;
                }
                if (METHOD_GET.equalsIgnoreCase(method)) {
                    handleGet(exchange, callerId);
                } else if (METHOD_POST.equalsIgnoreCase(method)) {
                    handlePost(exchange, requestId, callerId);
                } else if ("DELETE".equalsIgnoreCase(method)) {
                    handleDelete(exchange, callerId);
                } else {
                    sendPlain(exchange, 405, JsonCodec.toJson(ContactsResponse.error(METHOD_NOT_ALLOWED)));
                }
            } catch (Exception ex) {
                auditLogger.logError("contacts_error", requestId, callerId, ex);
                sendPlain(exchange, 500, JsonCodec.toJson(ContactsResponse.error(INTERNAL_SERVER_ERROR)));
            } finally {
                exchange.close();
            }
        }

        /**
         * Returns current caller contacts with live presence projection.
         *
         * @param exchange HTTP exchange
         * @param callerId authenticated caller id
         * @throws IOException when response write fails
         */
        private void handleGet(HttpExchange exchange, String callerId) throws IOException {
            List<Contact.ContactRecord> records = contactDAO.getContacts(callerId);
            List<UserSearchResult> results = records.stream()
                    .map(r -> {
                        boolean active = isUserRecentlyActive(r.userId());
                        return new UserSearchResult(
                                r.userId(),
                                r.fullName(),
                                r.regNumber(),
                                r.email(),
                                r.rank(),
                                r.telephone(),
                                r.joinedDate(),
                                active);
                    })
                    .toList();
            sendPlain(exchange, 200, JsonCodec.toJson(ContactsResponse.success(results)));
        }

        /**
         * Adds a contact for the caller.
         *
         * @param exchange  HTTP exchange
         * @param requestId request id for diagnostics
         * @param callerId  authenticated caller id
         * @throws IOException when response write fails
         */
        private void handlePost(HttpExchange exchange, String requestId, String callerId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AddContactRequest request = JsonCodec.fromJson(body, AddContactRequest.class);
            String contactId = request != null && request.getContactId() != null ? request.getContactId().trim() : "";
            if (contactId.isBlank()) {
                sendPlain(exchange, 400, JsonCodec.toJson(ContactsResponse.error("invalid contactId")));
                return;
            }
            contactDAO.addContact(callerId, contactId);
            sendPlain(exchange, 200, "{}");
        }

        /**
         * Removes a caller contact identified by {@code contactId} query parameter.
         *
         * @param exchange HTTP exchange
         * @param callerId authenticated caller id
         * @throws IOException when response write fails
         */
        private void handleDelete(HttpExchange exchange, String callerId) throws IOException {
            String rawQuery = exchange.getRequestURI().getQuery();
            String contactId = "";
            if (rawQuery != null) {
                for (String param : rawQuery.split("&")) {
                    if (param.startsWith("contactId=")) {
                        contactId = java.net.URLDecoder.decode(param.substring(10), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            if (contactId.isBlank()) {
                sendPlain(exchange, 400, JsonCodec.toJson(ContactsResponse.error("invalid contactId")));
                return;
            }
            contactDAO.removeContact(callerId, contactId);
            sendPlain(exchange, 200, "{}");
        }

        /**
         * Reads request body with hard byte-limit enforcement.
         *
         * @param body request body stream
         * @return UTF-8 decoded body text
         * @throws IOException when read fails or body exceeds configured limit
         */
        private String readBody(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Applies baseline security headers for contacts responses.
         *
         * @param headers response headers
         */
        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

        /**
         * Sends JSON response for contacts endpoint.
         *
         * @param exchange HTTP exchange
         * @param status   HTTP status code
         * @param body     JSON body
         * @throws IOException when response write fails
         */
        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    /**
     * Handles authenticated messaging policy fetches.
     */
    private final class MessagingConfigHandler implements HttpHandler {
        /**
         * Handles authenticated messaging-policy fetch requests.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response write fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, JsonCodec.toJson(MessagingPolicyResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }

                String callerId = authenticateCaller(exchange);
                if (callerId == null) {
                    return;
                }

                if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.MESSAGING_CONFIG, callerId)) {
                    return;
                }

                MessagingPolicyResponse response = MessagingPolicyResponse.success(
                        config.getAttachmentMaxBytes(),
                        config.getAttachmentInlineMaxBytes(),
                        config.getAttachmentChunkBytes(),
                        config.getAttachmentAllowedTypes(),
                        config.getAttachmentUnboundTtlSeconds());
                sendJson(exchange, 200, JsonCodec.toJson(response));
            } catch (Exception ex) {
                auditLogger.logError("messaging_config_error", requestId, null, ex);
                sendJson(exchange, 500, JsonCodec.toJson(MessagingPolicyResponse.error(INTERNAL_SERVER_ERROR)));
            } finally {
                exchange.close();
            }
        }
    }

    /**
     * Handles attachment upload/download lifecycle endpoints.
     */
    private final class AttachmentsHandler implements HttpHandler {
        /**
         * Handles authenticated attachment lifecycle requests.
         *
         * @param exchange HTTP exchange
         * @throws IOException when response write fails
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            String callerId = authenticateCaller(exchange);
            if (callerId == null) {
                exchange.close();
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String suffix = path.substring(apiPath("attachments").length());

            try {
                dispatchRequest(exchange, requestId, callerId, method, suffix);
            } catch (SecurityException _) {
                sendJson(exchange, 403, jsonError("forbidden"));
            } catch (IllegalArgumentException ex) {
                sendJson(exchange, 400, jsonError(ex.getMessage()));
            } catch (IllegalStateException ex) {
                sendJson(exchange, 409, jsonError(ex.getMessage()));
            } catch (Exception ex) {
                auditLogger.logError("attachments_handler_error", requestId, callerId, ex);
                sendJson(exchange, 500, jsonError(INTERNAL_SERVER_ERROR));
            } finally {
                exchange.close();
            }
        }

        /**
         * Routes attachment request to init/chunk/complete/bind/download handlers.
         *
         * @param exchange  HTTP exchange
         * @param requestId request id for diagnostics and throttling
         * @param callerId  authenticated caller id
         * @param method    HTTP method
         * @param suffix    path suffix under attachments root
         * @throws IOException when response write fails
         */
        private void dispatchRequest(HttpExchange exchange, String requestId, String callerId, String method,
                String suffix)
                throws IOException {
            if ("/init".equals(suffix)) {
                handleInitRequest(exchange, requestId, callerId, method);
                return;
            }

            AttachmentRoute route = parseAttachmentRoute(suffix);
            if (route == null) {
                sendJson(exchange, 400, JsonCodec.toJson(AttachmentInitResponse.error(INVALID_REQUEST_PATH)));
                return;
            }

            if (route.action() == AttachmentAction.DOWNLOAD) {
                handleDownloadRequest(exchange, requestId, callerId, method, route.attachmentId());
                return;
            }

            if (!METHOD_POST.equalsIgnoreCase(method)) {
                sendJson(exchange, 400, JsonCodec.toJson(AttachmentInitResponse.error(INVALID_REQUEST_PATH)));
                return;
            }

            if (!enforceApiRateLimit(exchange, requestId, route.rateLimitScope(), callerId)) {
                return;
            }

            switch (route.action()) {
                case CHUNK -> handleChunk(exchange, callerId, route.attachmentId());
                case COMPLETE -> handleComplete(exchange, callerId, route.attachmentId());
                case BIND -> handleBind(exchange, callerId, route.attachmentId());
                default ->
                    sendJson(exchange, 400, JsonCodec.toJson(AttachmentInitResponse.error(INVALID_REQUEST_PATH)));
            }
        }

        /**
         * Handles attachment init route method checks and throttling.
         */
        private void handleInitRequest(
                HttpExchange exchange,
                String requestId,
                String callerId,
                String method) throws IOException {
            if (!METHOD_POST.equalsIgnoreCase(method)) {
                sendJson(exchange, 405, JsonCodec.toJson(AttachmentInitResponse.error(METHOD_NOT_ALLOWED)));
                return;
            }
            if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.ATTACHMENTS_INIT, callerId)) {
                return;
            }
            handleInit(exchange, callerId);
        }

        /**
         * Handles attachment download route method checks and throttling.
         */
        private void handleDownloadRequest(
                HttpExchange exchange,
                String requestId,
                String callerId,
                String method,
                String attachmentId) throws IOException {
            if (!METHOD_GET.equalsIgnoreCase(method)) {
                sendJson(exchange, 405, jsonError(METHOD_NOT_ALLOWED));
                return;
            }
            if (!enforceApiRateLimit(exchange, requestId, ApiRateLimitScope.ATTACHMENTS_DOWNLOAD, callerId)) {
                return;
            }
            handleDownload(exchange, callerId, attachmentId);
        }

        /**
         * Parses non-init attachment routes.
         *
         * @param suffix request suffix after attachments root
         * @return parsed route, or {@code null} when invalid
         */
        private AttachmentRoute parseAttachmentRoute(String suffix) {
            String[] parts = splitAttachmentPath(suffix);
            if (parts == null || parts.length == 0 || parts[0].isBlank()) {
                return null;
            }

            String attachmentId = parts[0];
            if (parts.length == 1) {
                return new AttachmentRoute(attachmentId, AttachmentAction.DOWNLOAD,
                        ApiRateLimitScope.ATTACHMENTS_DOWNLOAD);
            }

            if (parts.length != 2 || parts[1] == null || parts[1].isBlank()) {
                return null;
            }

            return switch (parts[1]) {
                case "chunk" -> new AttachmentRoute(attachmentId, AttachmentAction.CHUNK,
                        ApiRateLimitScope.ATTACHMENTS_CHUNK);
                case "complete" -> new AttachmentRoute(attachmentId, AttachmentAction.COMPLETE,
                        ApiRateLimitScope.ATTACHMENTS_COMPLETE_BIND);
                case "bind" -> new AttachmentRoute(attachmentId, AttachmentAction.BIND,
                        ApiRateLimitScope.ATTACHMENTS_COMPLETE_BIND);
                default -> null;
            };
        }

        /**
         * Handles upload initialization endpoint.
         *
         * @param exchange HTTP exchange
         * @param callerId authenticated caller id
         * @throws IOException when response write fails
         */
        private void handleInit(HttpExchange exchange, String callerId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AttachmentInitRequest request = JsonCodec.fromJson(body, AttachmentInitRequest.class);

            if (request == null) {
                throw new IllegalArgumentException("attachment init payload is required");
            }

            String recipientId = request.getRecipientId() == null ? "" : request.getRecipientId().trim();
            if (recipientId.isBlank()) {
                throw new IllegalArgumentException("recipientId is required");
            }
            if (request.getPlaintextSizeBytes() <= config.getAttachmentInlineMaxBytes()) {
                throw new IllegalArgumentException("chunked upload requires size above inline limit");
            }
            if (request.getPlaintextSizeBytes() > config.getAttachmentMaxBytes()) {
                throw new IllegalArgumentException("attachment exceeds maximum size");
            }
            if (!AttachmentConstants.CONTENT_TYPE_ENCRYPTED_BLOB.equals(request.getContentType())) {
                throw new IllegalArgumentException("unsupported attachment upload content type");
            }
            if (request.getEncryptedSizeBytes() <= 0) {
                throw new IllegalArgumentException("encryptedSizeBytes must be >= 1");
            }
            long maxEncryptedBytes = (config.getAttachmentMaxBytes() * 2L) + (1024L * 1024L);
            if (request.getEncryptedSizeBytes() > maxEncryptedBytes) {
                throw new IllegalArgumentException("encrypted attachment payload is too large");
            }

            int expectedChunks = request.getExpectedChunks();
            int computedChunks = (int) Math
                    .ceil(request.getEncryptedSizeBytes() / (double) config.getAttachmentChunkBytes());
            if (expectedChunks <= 0 || expectedChunks != computedChunks) {
                throw new IllegalArgumentException("expectedChunks does not match attachment size");
            }

            Attachment.UploadInitResult result = attachmentDAO.initUpload(
                    callerId,
                    recipientId,
                    request.getContentType(),
                    request.getEncryptedSizeBytes(),
                    expectedChunks,
                    config.getAttachmentUnboundTtlSeconds());

            sendJson(exchange, 200, JsonCodec.toJson(
                    AttachmentInitResponse.success(result.attachmentId(), config.getAttachmentChunkBytes(),
                            result.expiresAtEpochMs())));
        }

        /**
         * Handles upload chunk endpoint.
         *
         * @param exchange     HTTP exchange
         * @param callerId     authenticated caller id
         * @param attachmentId attachment id
         * @throws IOException when response write fails
         */
        private void handleChunk(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            int chunkIndex = parseChunkIndex(exchange);
            byte[] decodedChunk = readBodyBytes(exchange.getRequestBody());
            if (decodedChunk.length == 0 || decodedChunk.length > config.getAttachmentChunkBytes()) {
                throw new IllegalArgumentException("chunk size is out of bounds");
            }

            Attachment.ChunkStoreResult result = attachmentDAO.storeChunk(
                    callerId,
                    attachmentId,
                    chunkIndex,
                    decodedChunk);
            sendJson(exchange, 200, JsonCodec.toJson(
                    AttachmentChunkResponse.success(attachmentId, result.chunkIndex(), result.stored())));
        }

        /**
         * Handles upload completion endpoint.
         *
         * @param exchange     HTTP exchange
         * @param callerId     authenticated caller id
         * @param attachmentId attachment id
         * @throws IOException when response write fails
         */
        private void handleComplete(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AttachmentCompleteRequest request = JsonCodec.fromJson(body, AttachmentCompleteRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("attachment complete payload is required");
            }
            if (request.getExpectedChunks() <= 0) {
                throw new IllegalArgumentException("expectedChunks must be >= 1");
            }
            if (request.getEncryptedSizeBytes() <= 0) {
                throw new IllegalArgumentException("encryptedSizeBytes must be >= 1");
            }

            Attachment.CompletionResult result = attachmentDAO.completeUpload(
                    callerId,
                    attachmentId,
                    request.getExpectedChunks(),
                    request.getEncryptedSizeBytes());
            sendJson(exchange, 200, JsonCodec.toJson(
                    AttachmentCompleteResponse.success(
                            attachmentId,
                            result.receivedChunks(),
                            result.receivedBytes(),
                            result.status())));
        }

        /**
         * Handles attachment bind endpoint.
         *
         * @param exchange     HTTP exchange
         * @param callerId     authenticated caller id
         * @param attachmentId attachment id
         * @throws IOException when response write fails
         */
        private void handleBind(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AttachmentBindRequest request = JsonCodec.fromJson(body, AttachmentBindRequest.class);
            if (request == null || request.getEnvelopeId() == null || request.getEnvelopeId().isBlank()) {
                throw new IllegalArgumentException("envelopeId is required");
            }

            Attachment.BindResult result = attachmentDAO.bindUploadToEnvelope(
                    callerId,
                    attachmentId,
                    request.getEnvelopeId());
            sendJson(exchange, 200, JsonCodec.toJson(
                    AttachmentBindResponse.success(
                            result.attachmentId(),
                            result.envelopeId(),
                            result.expiresAtEpochMs())));
        }

        /**
         * Handles attachment download endpoint.
         *
         * @param exchange     HTTP exchange
         * @param callerId     authenticated caller id
         * @param attachmentId attachment id
         * @throws IOException when response write fails
         */
        private void handleDownload(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            Attachment.DownloadBlob blob = attachmentDAO.loadForRecipient(callerId, attachmentId);
            Headers headers = exchange.getResponseHeaders();
            headers.set(AttachmentConstants.HEADER_ATTACHMENT_ID, blob.attachmentId());
            headers.set(AttachmentConstants.HEADER_ATTACHMENT_ENCRYPTED_SIZE,
                    String.valueOf(blob.encryptedSizeBytes()));
            headers.set(AttachmentConstants.HEADER_ATTACHMENT_CHUNK_COUNT, String.valueOf(blob.chunkCount()));
            headers.set(AttachmentConstants.HEADER_ATTACHMENT_CONTENT_TYPE, blob.contentType());
            sendBinary(exchange, 200, blob.encryptedBlob(), AttachmentConstants.APPLICATION_OCTET_STREAM);
        }

        /**
         * Parses the required binary chunk index header.
         *
         * @param exchange HTTP exchange
         * @return zero-based chunk index
         */
        private int parseChunkIndex(HttpExchange exchange) {
            String value = exchange.getRequestHeaders().getFirst(AttachmentConstants.HEADER_CHUNK_INDEX);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("chunk index header is required");
            }
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed < 0) {
                    throw new IllegalArgumentException("chunkIndex must be >= 0");
                }
                return parsed;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("chunk index header is invalid");
            }
        }

        /**
         * Splits attachment path suffix into path parts.
         *
         * @param suffix path suffix after {@code /api/v1/attachments}
         * @return path segments
         */
        private String[] splitAttachmentPath(String suffix) {
            if (suffix == null || suffix.isBlank() || "/".equals(suffix)) {
                return new String[0];
            }
            String trimmed = suffix.startsWith("/") ? suffix.substring(1) : suffix;
            if (trimmed.isBlank()) {
                return new String[0];
            }
            return trimmed.split("/");
        }

        /**
         * Reads request body with hard byte-limit enforcement.
         *
         * @param body request body stream
         * @return UTF-8 decoded body text
         * @throws IOException when read fails or body exceeds configured limit
         */
        private String readBody(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        /**
         * Reads raw request body bytes with hard byte-limit enforcement.
         *
         * @param body request body stream
         * @return raw request bytes
         * @throws IOException when read fails or body exceeds configured limit
         */
        private byte[] readBodyBytes(InputStream body) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = body.read(chunk)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    /**
     * Resolves source IP for rate-limit keying.
     *
     * @param exchange HTTP exchange
     * @return normalized source IP token
     */
    private String resolveClientIp(HttpExchange exchange) {
        if (exchange == null) {
            return "unknown";
        }
        String remoteIp = resolveSocketRemoteIp(exchange);
        InetAddress remoteAddress = resolveSocketRemoteAddress(exchange);
        if (!config.isTrustProxy() || remoteAddress == null || !isTrustedProxyAddress(remoteAddress)) {
            return remoteIp;
        }

        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        String forwardedClientIp = resolveForwardedClientIp(forwardedFor);
        return forwardedClientIp == null ? remoteIp : forwardedClientIp;
    }

    /**
     * Resolves client IP from X-Forwarded-For chain by scanning right-to-left and
     * selecting first non-trusted hop.
     *
     * @param forwardedFor raw X-Forwarded-For header value
     * @return resolved client IP, or {@code null} when value cannot be resolved
     */
    private String resolveForwardedClientIp(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return null;
        }
        String[] hops = forwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            InetAddress candidate = parseLiteralIp(hops[i]);
            if (candidate == null) {
                continue;
            }
            if (!isTrustedProxyAddress(candidate)) {
                return candidate.getHostAddress();
            }
        }
        return null;
    }

    /**
     * Resolves socket remote IP string fallback.
     *
     * @param exchange HTTP exchange
     * @return IP/host token
     */
    private static String resolveSocketRemoteIp(HttpExchange exchange) {
        if (exchange == null || exchange.getRemoteAddress() == null) {
            return "unknown";
        }
        InetAddress address = exchange.getRemoteAddress().getAddress();
        if (address != null && address.getHostAddress() != null && !address.getHostAddress().isBlank()) {
            return address.getHostAddress();
        }
        if (exchange.getRemoteAddress().getHostString() != null
                && !exchange.getRemoteAddress().getHostString().isBlank()) {
            return exchange.getRemoteAddress().getHostString();
        }
        return "unknown";
    }

    /**
     * Resolves raw socket remote inet address.
     *
     * @param exchange HTTP exchange
     * @return remote inet address or {@code null}
     */
    private static InetAddress resolveSocketRemoteAddress(HttpExchange exchange) {
        if (exchange == null || exchange.getRemoteAddress() == null) {
            return null;
        }
        return exchange.getRemoteAddress().getAddress();
    }

    /**
     * Indicates whether provided address belongs to trusted proxy allowlist.
     *
     * @param address candidate address
     * @return {@code true} when address matches trusted proxy CIDRs
     */
    private boolean isTrustedProxyAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        for (CidrRange range : resolveTrustedProxyRanges()) {
            if (range.contains(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves trusted proxy CIDR ranges and refreshes cached parsing when
     * configured values change.
     *
     * @return parsed trusted proxy ranges
     */
    private List<CidrRange> resolveTrustedProxyRanges() {
        List<String> configured = config.getTrustedProxyCidrs();
        List<String> normalized = configured == null ? List.of() : List.copyOf(configured);
        TrustedProxyCache cache = trustedProxyCache.get();
        if (normalized.equals(cache.cidrSnapshot())) {
            return cache.ranges();
        }
        synchronized (trustedProxyCacheLock) {
            cache = trustedProxyCache.get();
            if (!normalized.equals(cache.cidrSnapshot())) {
                refreshTrustedProxyRanges(normalized);
                cache = trustedProxyCache.get();
            }
            return cache.ranges();
        }
    }

    /**
     * Rebuilds parsed trusted proxy CIDR ranges from configured literals.
     *
     * @param cidrValues configured CIDR/IP allowlist
     */
    private void refreshTrustedProxyRanges(List<String> cidrValues) {
        List<String> normalized = cidrValues == null ? List.of() : List.copyOf(cidrValues);
        List<CidrRange> parsed = new ArrayList<>(normalized.size());
        for (String cidr : normalized) {
            parsed.add(CidrRange.parse(cidr));
        }
        trustedProxyCache.set(new TrustedProxyCache(normalized, List.copyOf(parsed)));
    }

    /**
     * Immutable cache for trusted proxy CIDR snapshots and parsed ranges.
     *
     * @param cidrSnapshot normalized configured CIDR values
     * @param ranges       parsed CIDR ranges
     */
    private record TrustedProxyCache(List<String> cidrSnapshot, List<CidrRange> ranges) {
        static TrustedProxyCache empty() {
            return new TrustedProxyCache(List.of(), List.of());
        }
    }

    /**
     * Parses an IP literal from a candidate header token.
     *
     * @param value candidate token
     * @return parsed inet address or {@code null} when token is invalid/non-literal
     */
    private static InetAddress parseLiteralIp(String value) {
        if (value == null) {
            return null;
        }
        String candidate = value.trim();
        if (candidate.isBlank()) {
            return null;
        }

        int zoneIndex = candidate.indexOf('%');
        if (zoneIndex > 0) {
            candidate = candidate.substring(0, zoneIndex);
        }

        boolean looksLikeIpv4 = candidate.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.');
        boolean looksLikeIpv6 = candidate.indexOf(':') >= 0;
        if (!looksLikeIpv4 && !looksLikeIpv6) {
            return null;
        }

        try {
            return InetAddress.getByName(candidate);
        } catch (Exception _) {
            return null;
        }
    }

    /**
     * Represents one CIDR/IP range for trusted proxy matching.
     *
     * @param networkAddress normalized network address
     * @param prefixLength   prefix bits
     */
    private record CidrRange(InetAddress networkAddress, int prefixLength) {
        /**
         * Parses CIDR/IP candidate to a normalized range.
         *
         * @param value CIDR/IP value
         * @return parsed range
         */
        static CidrRange parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("CIDR value cannot be blank");
            }
            String[] parts = value.trim().split("/", -1);
            if (parts.length > 2) {
                throw new IllegalArgumentException("Invalid CIDR value: " + value);
            }
            try {
                String ipPart = parts[0].trim();
                if (ipPart.isBlank()) {
                    throw new IllegalArgumentException("Invalid CIDR value: " + value);
                }
                InetAddress parsed = InetAddress.getByName(ipPart);
                byte[] raw = parsed.getAddress();
                int maxBits = raw.length * 8;
                int prefix = maxBits;
                if (parts.length == 2) {
                    prefix = Integer.parseInt(parts[1].trim());
                }
                if (prefix < 0 || prefix > maxBits) {
                    throw new IllegalArgumentException("CIDR prefix out of range: " + value);
                }
                byte[] masked = maskNetwork(raw, prefix);
                return new CidrRange(InetAddress.getByAddress(masked), prefix);
            } catch (IllegalArgumentException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalArgumentException("Invalid CIDR value: " + value, ex);
            }
        }

        /**
         * Indicates whether candidate address belongs to this CIDR/IP range.
         *
         * @param address address to evaluate
         * @return {@code true} when candidate is in range
         */
        boolean contains(InetAddress address) {
            if (address == null) {
                return false;
            }
            byte[] networkBytes = networkAddress.getAddress();
            byte[] candidate = address.getAddress();
            if (candidate.length != networkBytes.length) {
                return false;
            }
            byte[] masked = maskNetwork(candidate, prefixLength);
            return MessageDigest.isEqual(masked, networkBytes);
        }

        private static byte[] maskNetwork(byte[] input, int prefixLength) {
            byte[] masked = input.clone();
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            if (fullBytes < masked.length && remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                masked[fullBytes] = (byte) (masked[fullBytes] & mask);
                for (int i = fullBytes + 1; i < masked.length; i++) {
                    masked[i] = 0;
                }
                return masked;
            }
            for (int i = fullBytes; i < masked.length; i++) {
                masked[i] = 0;
            }
            return masked;
        }
    }

    /**
     * Auth resolution result.
     *
     * @param sessionId bearer session id
     * @param callerId  resolved caller id
     * @param error     error message when authentication fails
     */
    private record AuthResult(String sessionId, String callerId, String error) {
        /**
         * Indicates whether auth resolution succeeded.
         *
         * @return {@code true} when no auth error exists
         */
        private boolean authenticated() {
            return error == null;
        }
    }

    /**
     * Resolves caller from bearer session token and touches session activity.
     *
     * @param exchange HTTP exchange
     * @return auth result containing either caller/session or an auth error reason
     */
    private AuthResult resolveAuthenticatedCaller(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return new AuthResult(null, null, "missing or invalid auth");
        }

        String sessionId = authHeader.substring("Bearer ".length());
        String callerId = sessionDAO.getUserIdForSessionAndTouch(sessionId);
        if (callerId == null) {
            String reason = sessionDAO.isAccessSessionRevoked(sessionId)
                    ? "session revoked by takeover"
                    : "invalid session";
            return new AuthResult(sessionId, null, reason);
        }
        return new AuthResult(sessionId, callerId, null);
    }

    /**
     * Applies endpoint-scoped API rate limit and emits standardized 429 payload.
     *
     * @param exchange  HTTP exchange
     * @param requestId request id
     * @param scope     rate-limit scope
     * @param principal user/ip principal key
     * @return {@code true} when request is allowed
     * @throws IOException when response write fails
     */
    private boolean enforceApiRateLimit(HttpExchange exchange, String requestId, ApiRateLimitScope scope,
            String principal) throws IOException {
        RateLimitDecision decision = rateLimiterService.checkAndConsumeApi(requestId, scope, principal);
        if (decision.allowed()) {
            return true;
        }
        sendJson(exchange, 429, jsonRateLimit(decision.retryAfterSeconds()));
        return false;
    }

    /**
     * Authenticates caller for helpers that emit generic JSON error payloads.
     *
     * @param exchange HTTP exchange
     * @return caller user id, or {@code null} when authentication fails (response
     *         already written)
     * @throws IOException when error response cannot be written
     */
    private String authenticateCaller(HttpExchange exchange) throws IOException {
        AuthResult authResult = resolveAuthenticatedCaller(exchange);
        if (!authResult.authenticated()) {
            sendJson(exchange, 401, jsonError(authResult.error()));
            return null;
        }
        return authResult.callerId();
    }

    /**
     * Builds standard JSON error payload with escaped message.
     *
     * @param message error message
     * @return JSON error object string
     */
    private String jsonError(String message) {
        String safe = message == null ? INTERNAL_SERVER_ERROR : message.replace("\"", "\\\"");
        return JSON_ERROR_PREFIX + safe + JSON_ERROR_SUFFIX;
    }

    /**
     * Builds standard JSON payload for rate-limit rejections.
     *
     * @param retryAfterSeconds retry-after value in seconds
     * @return JSON payload
     */
    private String jsonRateLimit(long retryAfterSeconds) {
        long safeRetryAfter = Math.max(1L, retryAfterSeconds);
        return "{\"error\":\"rate limit\",\"retryAfterSeconds\":" + safeRetryAfter + "}";
    }

    /**
     * Applies baseline security headers for shared handler helpers.
     *
     * @param headers response headers
     */
    private void applySecurityHeaders(Headers headers) {
        headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
        headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
        headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
        headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
    }

    /**
     * Sends JSON response body for shared handler helpers.
     *
     * @param exchange HTTP exchange
     * @param status   HTTP status code
     * @param body     JSON body
     * @throws IOException when response write fails
     */
    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }

    /**
     * Sends a binary response body for attachment blob downloads.
     *
     * @param exchange    HTTP exchange
     * @param status      HTTP status code
     * @param body        binary response bytes
     * @param contentType response content type
     * @throws IOException when response write fails
     */
    private void sendBinary(HttpExchange exchange, int status, byte[] body, String contentType) throws IOException {
        byte[] payload = body == null ? new byte[0] : body;
        exchange.getResponseHeaders().set(CONTENT_TYPE,
                contentType == null || contentType.isBlank()
                        ? AttachmentConstants.APPLICATION_OCTET_STREAM
                        : contentType);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
