package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.FileUploadDAO;
import com.haf.server.db.SessionDAO;
import com.haf.server.db.UserDAO;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.server.router.RateLimiterService.RateLimitDecision;
import com.haf.shared.dto.EncryptedFileDTO;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.dto.PublicKeyResponse;
import com.haf.shared.dto.LoginRequest;
import com.haf.shared.dto.LoginResponse;
import com.haf.shared.dto.RegisterRequest;
import com.haf.shared.dto.RegisterResponse;
import com.haf.shared.dto.UserSearchResponse;
import com.haf.shared.dto.UserSearchResult;
import com.haf.shared.utils.JsonCodec;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.mindrot.jbcrypt.BCrypt;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class HttpIngressServer {

    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final String MESSAGES_PATH = "/api/v1/messages";
    private static final String REGISTER_PATH = "/api/v1/register";
    private static final String LOGIN_PATH = "/api/v1/login";
    private static final String USERS_PATH = "/api/v1/users";
    private static final String SEARCH_PATH = "/api/v1/search";
    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String TEST_USER_ID = "test-user";
    private static final int BCRYPT_LOG_ROUNDS = 12;

    private final ServerConfig config;
    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiterService;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;
    private final EncryptedMessageValidator validator;
    private final UserDAO userDAO;
    private final SessionDAO sessionDAO;
    private final FileUploadDAO fileUploadDAO;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private HttpsServer server;

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
            UserDAO userDAO,
            SessionDAO sessionDAO,
            FileUploadDAO fileUploadDAO) throws IOException {
        this.config = config;
        this.mailboxRouter = mailboxRouter;
        this.rateLimiterService = rateLimiterService;
        this.auditLogger = auditLogger;
        this.metricsRegistry = metricsRegistry;
        this.validator = validator;
        this.userDAO = userDAO;
        this.sessionDAO = sessionDAO;
        this.fileUploadDAO = fileUploadDAO;
        this.server = createServer(sslContext);
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
        httpsServer.createContext(REGISTER_PATH, new RegistrationHandler());
        httpsServer.createContext(LOGIN_PATH, new LoginHandler());
        httpsServer.createContext(USERS_PATH, new UserKeyHandler());
        httpsServer.createContext(SEARCH_PATH, new SearchHandler());
        httpsServer.createContext(HEALTH_PATH, new HealthHandler());
        httpsServer.createContext("/api/v1/config/admin-key", new AdminKeyHandler());
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
         *
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
                            "{\"error\":\"rate limit\",\"retryAfterSeconds\":" + rateDecision.retryAfterSeconds()
                                    + "}");
                    return;
                }

                QueuedEnvelope envelope = mailboxRouter.ingress(message);
                auditLogger.logIngressAccepted(
                        requestId,
                        userId,
                        message.recipientId,
                        202,
                        Duration.ofNanos(System.nanoTime() - start).toMillis());

                String response = JsonCodec
                        .toJson(new IngressResponse(envelope.envelopeId(), validationResult.expiresAtMillis()));
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
                    throw new IOException("Request body exceeds limit");
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
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
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
     * Handles registration requests.
     */
    private final class RegistrationHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add("X-Request-Id", requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, JsonCodec.toJson(RegisterResponse.error("method not allowed")));
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
                if (userDAO.existsByEmail(request.email)) {
                    respond(exchange, requestId, 409,
                            JsonCodec.toJson(RegisterResponse.error("Email already registered")));
                    return;
                }

                // Hash password and insert user first (photos link back to this user_id)
                String hashedPassword = BCrypt.hashpw(request.password, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
                String userId = userDAO.insert(request, hashedPassword);

                // Store E2E-encrypted photos using the real userId (respects FK constraint)
                String idPhotoId = storePhoto(request.idPhoto, userId);
                String selfiePhotoId = storePhoto(request.selfiePhoto, userId);

                // Link photos to user row if any were provided
                if (idPhotoId != null || selfiePhotoId != null) {
                    userDAO.updatePhotoIds(userId, idPhotoId, selfiePhotoId);
                }

                auditLogger.logRegistration(requestId, userId, request.email);

                respond(exchange, requestId, 201, JsonCodec.toJson(RegisterResponse.success(userId)));
            } catch (Exception ex) {
                auditLogger.logError("registration_error", requestId, null, ex);
                respond(exchange, requestId, 500, JsonCodec.toJson(RegisterResponse.error("internal server error")));
            } finally {
                exchange.close();
            }
        }

        private String validateRegistration(RegisterRequest r) {
            if (isBlank(r.fullName))
                return "fullName is required";
            if (isBlank(r.email))
                return "email is required";
            if (isBlank(r.password))
                return "password is required";
            if (r.password.length() < 6)
                return "password must be at least 6 characters";
            if (isBlank(r.publicKeyPem))
                return "publicKeyPem is required";
            if (isBlank(r.publicKeyFingerprint))
                return "publicKeyFingerprint is required";
            return null;
        }

        /** Persists a photo blob if present, returns file_id or null. */
        private String storePhoto(EncryptedFileDTO dto, String userId) {
            if (dto == null || dto.ciphertextB64 == null || dto.ciphertextB64.isBlank()) {
                return null;
            }
            return fileUploadDAO.insert(dto, userId);
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

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

        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("Content-Type", "application/json");

            if (!responseHeaders.containsKey("X-Request-Id")) {
                responseHeaders.add("X-Request-Id", requestId);
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
    private record IngressResponse(String envelopeId, long expiresAt) {
    }

    /**
     * Handles login requests.
     */
    private final class LoginHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add("X-Request-Id", requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, JsonCodec.toJson(LoginResponse.error("method not allowed")));
                    return;
                }

                String body = readBody(exchange.getRequestBody());
                LoginRequest request = JsonCodec.fromJson(body, LoginRequest.class);

                // Validate required fields
                if (isBlank(request.email) || isBlank(request.password)) {
                    respond(exchange, requestId, 400,
                            JsonCodec.toJson(LoginResponse.error("email and password are required")));
                    return;
                }

                // Look up user by email
                UserDAO.UserRecord user = userDAO.findByEmail(request.email);
                if (user == null) {
                    respond(exchange, requestId, 401,
                            JsonCodec.toJson(LoginResponse.error("Invalid email or password")));
                    return;
                }

                // Verify password
                if (!BCrypt.checkpw(request.password, user.passwordHash())) {
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

                // Create session
                String sessionId = sessionDAO.createSession(user.userId());

                auditLogger.logLogin(requestId, user.userId(), request.email);

                respond(exchange, requestId, 200,
                        JsonCodec.toJson(LoginResponse.success(
                                user.userId(), sessionId, user.fullName(), user.rank(), user.status())));
            } catch (Exception ex) {
                auditLogger.logError("login_error", requestId, null, ex);
                respond(exchange, requestId, 500, JsonCodec.toJson(LoginResponse.error("internal server error")));
            } finally {
                exchange.close();
            }
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }

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

        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        private void respond(HttpExchange exchange, String requestId, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            Headers responseHeaders = exchange.getResponseHeaders();
            responseHeaders.add("Content-Type", "application/json");

            if (!responseHeaders.containsKey("X-Request-Id")) {
                responseHeaders.add("X-Request-Id", requestId);
            }

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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("X-Request-Id", UUID.randomUUID().toString());
            applySecurityHeaders(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }

            String pem = config.getAdminPublicKeyPem();
            String body = pem != null
                    ? "{\"adminPublicKeyPem\":\"" + pem.replace("\n", "\\n") + "\"}"
                    : "{\"adminPublicKeyPem\":null}";

            sendPlain(exchange, 200, body);
            exchange.close();
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add("X-Request-Id", requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, JsonCodec.toJson(PublicKeyResponse.error("method not allowed")));
                return;
            }

            // Path must match /api/v1/users/{userId}/key
            String path = exchange.getRequestURI().getPath();
            if (path == null || !path.endsWith("/key")) {
                sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error("invalid request path")));
                return;
            }

            // Extract userId
            String prefix = USERS_PATH + "/";
            if (!path.startsWith(prefix)) {
                sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error("invalid request path")));
                return;
            }

            String remainder = path.substring(prefix.length());
            int slashIndex = remainder.indexOf('/');
            if (slashIndex == -1) {
                sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error("missing userId")));
                return;
            }
            String targetUserId = remainder.substring(0, slashIndex);

            try {
                UserDAO.PublicKeyRecord record = userDAO.getPublicKey(targetUserId);
                if (record == null) {
                    sendPlain(exchange, 404, JsonCodec.toJson(PublicKeyResponse.error("user not found")));
                    return;
                }

                sendPlain(exchange, 200, JsonCodec.toJson(
                        PublicKeyResponse.success(targetUserId, record.publicKeyPem(), record.fingerprint())));
            } catch (Exception ex) {
                auditLogger.logError("user_key_lookup_error", requestId, null, ex);
                sendPlain(exchange, 500, JsonCodec.toJson(PublicKeyResponse.error("internal server error")));
            }
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

        private static final int MAX_RESULTS = 20;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add("X-Request-Id", requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, JsonCodec.toJson(UserSearchResponse.error("method not allowed")));
                return;
            }

            // Authenticate via session
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                sendPlain(exchange, 401, JsonCodec.toJson(UserSearchResponse.error("missing or invalid auth")));
                return;
            }
            String sessionId = authHeader.substring("Bearer ".length());
            String callerId = sessionDAO.getUserIdForSession(sessionId);
            if (callerId == null) {
                sendPlain(exchange, 401, JsonCodec.toJson(UserSearchResponse.error("invalid session")));
                return;
            }

            // Extract query
            String rawQuery = exchange.getRequestURI().getQuery(); // e.g. "q=john"
            String searchTerm = "";
            if (rawQuery != null) {
                for (String param : rawQuery.split("&")) {
                    if (param.startsWith("q=")) {
                        searchTerm = java.net.URLDecoder.decode(param.substring(2), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
            if (searchTerm.isBlank()) {
                sendPlain(exchange, 200, JsonCodec.toJson(UserSearchResponse.success(List.of())));
                return;
            }

            try {
                List<UserDAO.SearchRecord> records = userDAO.searchUsers(searchTerm, callerId, MAX_RESULTS);
                List<UserSearchResult> results = records.stream()
                        .map(r -> new UserSearchResult(r.userId(), r.fullName(), r.regNumber(), r.email(), r.rank()))
                        .toList();
                sendPlain(exchange, 200, JsonCodec.toJson(UserSearchResponse.success(results)));
            } catch (Exception ex) {
                auditLogger.logError("search_error", requestId, callerId, ex);
                sendPlain(exchange, 500, JsonCodec.toJson(UserSearchResponse.error("internal server error")));
            }
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("X-Request-Id", UUID.randomUUID().toString());
            applySecurityHeaders(exchange.getResponseHeaders());

            String method = exchange.getRequestMethod();
            if ("HEAD".equalsIgnoreCase(method) || "GET".equalsIgnoreCase(method)) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, -1);
            } else {
                sendPlain(exchange, 405, "{\"error\":\"method not allowed\"}");
            }
            exchange.close();
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
            headers.add("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none';");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add("X-Frame-Options", "DENY");
        }

        private void sendPlain(HttpExchange exchange, int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }
}
