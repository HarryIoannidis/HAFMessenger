package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.ContactDAO;
import com.haf.server.db.FileUploadDAO;
import com.haf.server.db.AttachmentDAO;
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
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.responses.AttachmentBindResponse;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.responses.AttachmentChunkResponse;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.responses.AttachmentCompleteResponse;
import com.haf.shared.responses.AttachmentDownloadResponse;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.responses.AttachmentInitResponse;
import com.haf.shared.responses.MessagingPolicyResponse;
import com.haf.shared.responses.PublicKeyResponse;
import com.haf.shared.requests.LoginRequest;
import com.haf.shared.responses.LoginResponse;
import com.haf.shared.requests.RegisterRequest;
import com.haf.shared.responses.RegisterResponse;
import com.haf.shared.responses.UserSearchResponse;
import com.haf.shared.dto.UserSearchResultDTO;
import com.haf.shared.responses.ContactsResponse;
import com.haf.shared.requests.AddContactRequest;
import com.haf.shared.constants.AttachmentConstants;
import com.haf.shared.utils.JsonCodec;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.haf.shared.constants.CryptoConstants;
import com.password4j.Password;
import com.password4j.Argon2Function;
import org.java_websocket.WebSocket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@SuppressWarnings("java:S1075")
public final class HttpIngressServer {

    private static final int MAX_BODY_BYTES = 10 * 1024 * 1024;
    private static final String MESSAGES_PATH = "/api/v1/messages";
    private static final String REGISTER_PATH = "/api/v1/register";
    private static final String LOGIN_PATH = "/api/v1/login";
    private static final String LOGOUT_PATH = "/api/v1/logout";
    private static final String USERS_PATH = "/api/v1/users";
    private static final String SEARCH_PATH = "/api/v1/search";
    private static final String CONTACTS_PATH = "/api/v1/contacts";
    private static final String HEALTH_PATH = "/api/v1/health";
    private static final String ADMIN_KEY_PATH = "/api/v1/config/admin-key";
    private static final String MESSAGING_CONFIG_PATH = "/api/v1/config/messaging";
    private static final String ATTACHMENTS_PATH = "/api/v1/attachments";
    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    private static final String X_FRAME_OPTIONS = "X-Frame-Options";
    private static final String X_REQUEST_ID = "X-Request-Id";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";
    private static final String REQUEST_BODY_EXCEEDS_LIMIT = "Request body exceeds limit";
    private static final String STRICT_TRANSPORT_SECURITY_VALUE = "max-age=63072000; includeSubDomains; preload";
    private static final String CONTENT_SECURITY_POLICY_VALUE = "default-src 'none'; frame-ancestors 'none'; base-uri 'none';";
    private static final String X_CONTENT_TYPE_OPTIONS_VALUE = "nosniff";
    private static final String X_FRAME_OPTIONS_VALUE = "DENY";
    private static final String METHOD_NOT_ALLOWED = "method not allowed";
    private static final String INTERNAL_SERVER_ERROR = "internal server error";
    private static final String MISSING_OR_INVALID_AUTH = "missing or invalid auth";
    private static final String INVALID_SESSION = "invalid session";
    private static final String INVALID_REQUEST_PATH = "invalid request path";
    private static final String INVALID_CONTACT_ID = "invalid contactId";
    private static final String INVALID_EMAIL_PASSWORD = "Invalid email or password";
    private static final String ACCOUNT_ALREADY_LOGGED_IN = "Account is already logged in.";
    private static final String JSON_ERROR_PREFIX = "{\"error\":\"";
    private static final String JSON_ERROR_SUFFIX = "\"}";
    private static final Argon2Function ARGON2 = Argon2Function.getInstance(
            CryptoConstants.ARGON2_MEMORY_KB,
            CryptoConstants.ARGON2_ITERATIONS,
            CryptoConstants.ARGON2_PARALLELISM,
            CryptoConstants.ARGON2_OUTPUT_LENGTH,
            com.password4j.types.Argon2.ID);

    private final ServerConfig config;
    private final MailboxRouter mailboxRouter;
    private final RateLimiterService rateLimiterService;
    private final AuditLogger auditLogger;
    private final MetricsRegistry metricsRegistry;
    private final EncryptedMessageValidator validator;
    private final UserDAO userDAO;
    private final SessionDAO sessionDAO;
    private final FileUploadDAO fileUploadDAO;
    private final AttachmentDAO attachmentDAO;
    private final ContactDAO contactDAO;
    private final PresenceRegistry presenceRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private HttpsServer server;

    static void pushContactPresenceToRequester(PresenceRegistry presenceRegistry,
            AuditLogger auditLogger,
            String requestId,
            String callerId,
            String contactId) {
        if (presenceRegistry == null || auditLogger == null) {
            return;
        }
        if (callerId == null || callerId.isBlank() || contactId == null || contactId.isBlank()) {
            return;
        }

        boolean active = presenceRegistry.isActive(contactId);
        String payload = presenceJson(contactId, active);
        for (WebSocket connection : presenceRegistry.getActiveConnections(callerId)) {
            try {
                connection.send(payload);
            } catch (Exception ex) {
                auditLogger.logError("contacts_presence_push_error", requestId, callerId, ex,
                        Map.of("contactId", contactId, "active", active));
            }
        }
    }

    static String presenceJson(String userId, boolean active) {
        return "{\"type\":\"presence\",\"userId\":\"" + escapeJson(userId) + "\",\"active\":" + active + "}";
    }

    static boolean isDuplicateLoginAttempt(PresenceRegistry presenceRegistry, String userId) {
        if (presenceRegistry == null || userId == null || userId.isBlank()) {
            return false;
        }
        return presenceRegistry.isActive(userId);
    }

    static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
            UserDAO userDAO,
            SessionDAO sessionDAO,
            FileUploadDAO fileUploadDAO,
            AttachmentDAO attachmentDAO,
            ContactDAO contactDAO,
            PresenceRegistry presenceRegistry) throws IOException {
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
        this.presenceRegistry = presenceRegistry;
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
        httpsServer.createContext(LOGOUT_PATH, new LogoutHandler());
        httpsServer.createContext(USERS_PATH, new UserKeyHandler());
        httpsServer.createContext(SEARCH_PATH, new SearchHandler());
        httpsServer.createContext(CONTACTS_PATH, new ContactsHandler());
        httpsServer.createContext(HEALTH_PATH, new HealthHandler());
        httpsServer.createContext(ADMIN_KEY_PATH, new AdminKeyHandler());
        httpsServer.createContext(MESSAGING_CONFIG_PATH, new MessagingConfigHandler());
        httpsServer.createContext(ATTACHMENTS_PATH, new AttachmentsHandler());
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
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            // Extract Authorization header
            String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                respond(exchange, requestId, 401, JSON_ERROR_PREFIX + MISSING_OR_INVALID_AUTH + JSON_ERROR_SUFFIX);
                return;
            }

            // Retrieve User ID from Session DAO
            String sessionId = authHeader.substring(BEARER_PREFIX.length());
            String userId = sessionDAO.getUserIdForSession(sessionId);
            if (userId == null) {
                respond(exchange, requestId, 401, JSON_ERROR_PREFIX + INVALID_SESSION + JSON_ERROR_SUFFIX);
                return;
            }

            exchange.getResponseHeaders().add("X-User-Id", userId);

            try {
                if (!METHOD_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
                    respond(exchange, requestId, 405, JSON_ERROR_PREFIX + METHOD_NOT_ALLOWED + JSON_ERROR_SUFFIX);
                    return;
                }

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

        private String validateRegistration(RegisterRequest r) {
            if (isBlank(r.getFullName()))
                return "fullName is required";
            if (isBlank(r.getEmail()))
                return "email is required";
            if (isBlank(r.getPassword()))
                return "password is required";
            if (r.getPassword().length() < 6)
                return "password must be at least 6 characters";
            if (isBlank(r.getPublicKeyPem()))
                return "publicKeyPem is required";
            if (isBlank(r.getPublicKeyFingerprint()))
                return "publicKeyFingerprint is required";
            return null;
        }

        /**
         * Persists a photo blob if present, returns file_id or null.
         * 
         * @param dto    the photo to store
         * @param userId the user ID
         * @return the file ID or null
         */
        private String storePhoto(EncryptedFileDTO dto, String userId) {
            if (dto == null || dto.getCiphertextB64() == null || dto.getCiphertextB64().isBlank()) {
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
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

                // Validate required fields
                if (isBlank(request.getEmail()) || isBlank(request.getPassword())) {
                    respond(exchange, requestId, 400,
                            JsonCodec.toJson(LoginResponse.error("email and password are required")));
                    return;
                }

                // Look up user by email
                UserDAO.UserRecord user = userDAO.findByEmail(request.getEmail());
                if (user == null) {
                    respond(exchange, requestId, 401,
                            JsonCodec.toJson(LoginResponse.error(INVALID_EMAIL_PASSWORD)));
                    return;
                }

                if (!Password.check(request.getPassword(), user.passwordHash()).with(ARGON2)) {
                    respond(exchange, requestId, 401,
                            JsonCodec.toJson(LoginResponse.error(INVALID_EMAIL_PASSWORD)));
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

                if (isDuplicateLoginAttempt(presenceRegistry, user.userId())) {
                    respond(exchange, requestId, 409,
                            JsonCodec.toJson(LoginResponse.error(ACCOUNT_ALREADY_LOGGED_IN)));
                    return;
                }

                // Create session
                String sessionId = sessionDAO.createSession(user.userId());

                auditLogger.logLogin(requestId, user.userId(), request.getEmail());

                respond(exchange, requestId, 200,
                        JsonCodec.toJson(LoginResponse.success(
                                user.userId(),
                                sessionId,
                                user.fullName(),
                                user.rank(),
                                user.regNumber(),
                                user.email(),
                                user.telephone(),
                                user.joinedDate(),
                                user.status())));
            } catch (Exception ex) {
                auditLogger.logError("login_error", requestId, null, ex);
                respond(exchange, requestId, 500, JsonCodec.toJson(LoginResponse.error(INTERNAL_SERVER_ERROR)));
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
                    throw new IOException(REQUEST_BODY_EXCEEDS_LIMIT);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

                String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
                if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                    sendPlain(exchange, 401, JSON_ERROR_PREFIX + MISSING_OR_INVALID_AUTH + JSON_ERROR_SUFFIX);
                    return;
                }

                String sessionId = authHeader.substring(BEARER_PREFIX.length());
                String callerId = sessionDAO.getUserIdForSession(sessionId);
                if (callerId == null) {
                    sendPlain(exchange, 401, JSON_ERROR_PREFIX + INVALID_SESSION + JSON_ERROR_SUFFIX);
                    return;
                }

                sessionDAO.revokeSession(sessionId);
                sendPlain(exchange, 200, "{\"success\":true}");
            } catch (Exception ex) {
                auditLogger.logError("logout_error", requestId, null, ex);
                sendPlain(exchange, 500, JSON_ERROR_PREFIX + INTERNAL_SERVER_ERROR + JSON_ERROR_SUFFIX);
            } finally {
                exchange.close();
            }
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add(X_REQUEST_ID, UUID.randomUUID().toString());
            applySecurityHeaders(exchange.getResponseHeaders());

            if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, JSON_ERROR_PREFIX + METHOD_NOT_ALLOWED + JSON_ERROR_SUFFIX);
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
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, JsonCodec.toJson(PublicKeyResponse.error(METHOD_NOT_ALLOWED)));
                return;
            }

            // Path must match /api/v1/users/{userId}/key
            String path = exchange.getRequestURI().getPath();
            if (path == null || !path.endsWith("/key")) {
                sendPlain(exchange, 400, JsonCodec.toJson(PublicKeyResponse.error(INVALID_REQUEST_PATH)));
                return;
            }

            // Extract userId
            String prefix = USERS_PATH + "/";
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

            try {
                UserDAO.PublicKeyRecord keyRecord = userDAO.getPublicKey(targetUserId);
                if (keyRecord == null) {
                    sendPlain(exchange, 404, JsonCodec.toJson(PublicKeyResponse.error("user not found")));
                    return;
                }

                sendPlain(exchange, 200, JsonCodec.toJson(
                        PublicKeyResponse.success(targetUserId, keyRecord.publicKeyPem(), keyRecord.fingerprint())));
            } catch (Exception ex) {
                auditLogger.logError("user_key_lookup_error", requestId, null, ex);
                sendPlain(exchange, 500, JsonCodec.toJson(PublicKeyResponse.error(INTERNAL_SERVER_ERROR)));
            }
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

        private static final String CURSOR_HMAC_ALGO = "HmacSHA256";
        private static final String INVALID_LIMIT = "invalid limit";
        private static final String INVALID_CURSOR = "invalid cursor";
        private static final String INVALID_QUERY_PARAMS = "invalid query parameters";

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            if (!METHOD_GET.equalsIgnoreCase(exchange.getRequestMethod())) {
                sendPlain(exchange, 405, JsonCodec.toJson(UserSearchResponse.error(METHOD_NOT_ALLOWED)));
                return;
            }

            // Authenticate via session
            String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                sendPlain(exchange, 401, JsonCodec.toJson(UserSearchResponse.error(MISSING_OR_INVALID_AUTH)));
                return;
            }
            String sessionId = authHeader.substring(BEARER_PREFIX.length());
            String callerId = sessionDAO.getUserIdForSession(sessionId);
            if (callerId == null) {
                sendPlain(exchange, 401, JsonCodec.toJson(UserSearchResponse.error(INVALID_SESSION)));
                return;
            }

            Map<String, String> queryParams;
            try {
                queryParams = parseQueryParams(exchange.getRequestURI().getRawQuery());
            } catch (IllegalArgumentException ex) {
                sendPlain(exchange, 400, JsonCodec.toJson(UserSearchResponse.error(INVALID_QUERY_PARAMS)));
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
            } catch (IllegalArgumentException ex) {
                sendPlain(exchange, 400, JsonCodec.toJson(UserSearchResponse.error(INVALID_LIMIT)));
                return;
            }

            String cursorToken = queryParams.get("cursor");
            CursorKey cursor;
            try {
                cursor = decodeCursor(cursorToken);
            } catch (IllegalArgumentException ex) {
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
                UserDAO.SearchPage page = userDAO.searchUsersPage(
                        normalizedQuery,
                        callerId,
                        effectiveLimit,
                        cursor.fullName(),
                        cursor.userId());

                List<UserSearchResultDTO> results = page.results().stream()
                        .map(r -> new UserSearchResultDTO(
                                r.userId(),
                                r.fullName(),
                                r.regNumber(),
                                r.email(),
                                r.rank(),
                                r.telephone(),
                                r.joinedDate()))
                        .toList();
                String nextCursor = page.hasMore() ? encodeCursor(page.lastFullName(), page.lastUserId()) : null;
                sendPlain(exchange, 200, JsonCodec.toJson(UserSearchResponse.success(results, page.hasMore(), nextCursor)));
            } catch (Exception ex) {
                auditLogger.logError("search_error", requestId, callerId, ex, Map.of(
                        "queryLength", normalizedQuery.length(),
                        "queryHash", queryHash,
                        "limit", effectiveLimit,
                        "cursorSupplied", cursor.isPresent()));
                sendPlain(exchange, 500, JsonCodec.toJson(UserSearchResponse.error(INTERNAL_SERVER_ERROR)));
            }
        }

        private int resolveLimit(String rawLimit) {
            if (rawLimit == null || rawLimit.isBlank()) {
                return config.getSearchPageSize();
            }
            try {
                int requested = Integer.parseInt(rawLimit);
                return Math.clamp(requested, 1, config.getSearchMaxPageSize());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(INVALID_LIMIT, ex);
            }
        }


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

        private byte[] hmac(String payload) {
            try {
                Mac mac = Mac.getInstance(CURSOR_HMAC_ALGO);
                byte[] secretBytes = config.getSearchCursorSecret().getBytes(StandardCharsets.UTF_8);
                mac.init(new SecretKeySpec(secretBytes, CURSOR_HMAC_ALGO));
                return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to sign search cursor", ex);
            }
        }

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
            static CursorKey empty() {
                return new CursorKey(null, null);
            }

            boolean isPresent() {
                return fullName != null && userId != null;
            }
        }

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestId = UUID.randomUUID().toString();
            exchange.getResponseHeaders().add(X_REQUEST_ID, requestId);
            applySecurityHeaders(exchange.getResponseHeaders());

            // Authenticate via session
            String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                sendPlain(exchange, 401, JsonCodec.toJson(ContactsResponse.error(MISSING_OR_INVALID_AUTH)));
                return;
            }
            String sessionId = authHeader.substring(BEARER_PREFIX.length());
            String callerId = sessionDAO.getUserIdForSession(sessionId);
            if (callerId == null) {
                sendPlain(exchange, 401, JsonCodec.toJson(ContactsResponse.error(INVALID_SESSION)));
                return;
            }

            String method = exchange.getRequestMethod();

            try {
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

        private void handleGet(HttpExchange exchange, String callerId) throws IOException {
            List<ContactDAO.ContactRecord> records = contactDAO.getContacts(callerId);
            List<UserSearchResultDTO> results = records.stream()
                    .map(r -> new UserSearchResultDTO(
                            r.userId(),
                            r.fullName(),
                            r.regNumber(),
                            r.email(),
                            r.rank(),
                            r.telephone(),
                            r.joinedDate(),
                            presenceRegistry.isActive(r.userId())))
                    .toList();
            sendPlain(exchange, 200, JsonCodec.toJson(ContactsResponse.success(results)));
        }

        private void handlePost(HttpExchange exchange, String requestId, String callerId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AddContactRequest request = JsonCodec.fromJson(body, AddContactRequest.class);
            String contactId = request != null && request.getContactId() != null ? request.getContactId().trim() : "";
            if (contactId.isBlank()) {
                sendPlain(exchange, 400, JsonCodec.toJson(ContactsResponse.error(INVALID_CONTACT_ID)));
                return;
            }
            contactDAO.addContact(callerId, contactId);
            pushContactPresenceToRequester(presenceRegistry, auditLogger, requestId, callerId, contactId);
            sendPlain(exchange, 200, "{}");
        }

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
                sendPlain(exchange, 400, JsonCodec.toJson(ContactsResponse.error(INVALID_CONTACT_ID)));
                return;
            }
            contactDAO.removeContact(callerId, contactId);
            sendPlain(exchange, 200, "{}");
        }

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

        private void applySecurityHeaders(Headers headers) {
            headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
            headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
            headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
            headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
        }

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
            String suffix = path.substring(ATTACHMENTS_PATH.length());

            try {
                dispatchRequest(exchange, callerId, method, suffix);
            } catch (SecurityException ex) {
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

        private void dispatchRequest(HttpExchange exchange, String callerId, String method, String suffix) throws IOException {
            if ("/init".equals(suffix)) {
                if (!METHOD_POST.equalsIgnoreCase(method)) {
                    sendJson(exchange, 405, JsonCodec.toJson(AttachmentInitResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }
                handleInit(exchange, callerId);
                return;
            }

            String[] parts = splitAttachmentPath(suffix);
            if (parts == null || parts.length == 0 || parts[0].isBlank()) {
                sendJson(exchange, 400, JsonCodec.toJson(AttachmentInitResponse.error(INVALID_REQUEST_PATH)));
                return;
            }
            String attachmentId = parts[0];

            if (parts.length == 1) {
                if (!METHOD_GET.equalsIgnoreCase(method)) {
                    sendJson(exchange, 405, JsonCodec.toJson(AttachmentDownloadResponse.error(METHOD_NOT_ALLOWED)));
                    return;
                }
                handleDownload(exchange, callerId, attachmentId);
                return;
            }

            if (parts.length != 2 || !METHOD_POST.equalsIgnoreCase(method)) {
                sendJson(exchange, 400, JsonCodec.toJson(AttachmentInitResponse.error(INVALID_REQUEST_PATH)));
                return;
            }

            switch (parts[1]) {
                case "chunk" -> handleChunk(exchange, callerId, attachmentId);
                case "complete" -> handleComplete(exchange, callerId, attachmentId);
                case "bind" -> handleBind(exchange, callerId, attachmentId);
                default -> sendJson(exchange, 400, JsonCodec.toJson(AttachmentInitResponse.error(INVALID_REQUEST_PATH)));
            }
        }

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
            int computedChunks = (int) Math.ceil(request.getEncryptedSizeBytes() / (double) config.getAttachmentChunkBytes());
            if (expectedChunks <= 0 || expectedChunks != computedChunks) {
                throw new IllegalArgumentException("expectedChunks does not match attachment size");
            }

            AttachmentDAO.UploadInitResult result = attachmentDAO.initUpload(
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

        private void handleChunk(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AttachmentChunkRequest request = JsonCodec.fromJson(body, AttachmentChunkRequest.class);
            if (request == null) {
                throw new IllegalArgumentException("attachment chunk payload is required");
            }

            if (request.getChunkIndex() < 0) {
                throw new IllegalArgumentException("chunkIndex must be >= 0");
            }
            if (request.getChunkDataB64() == null || request.getChunkDataB64().isBlank()) {
                throw new IllegalArgumentException("chunkDataB64 is required");
            }

            byte[] decodedChunk;
            try {
                decodedChunk = Base64.getDecoder().decode(request.getChunkDataB64());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("chunkDataB64 is not valid base64");
            }
            if (decodedChunk.length == 0 || decodedChunk.length > config.getAttachmentChunkBytes()) {
                throw new IllegalArgumentException("chunk size is out of bounds");
            }

            AttachmentDAO.ChunkStoreResult result = attachmentDAO.storeChunk(
                    callerId,
                    attachmentId,
                    request.getChunkIndex(),
                    decodedChunk);
            sendJson(exchange, 200, JsonCodec.toJson(
                    AttachmentChunkResponse.success(attachmentId, result.chunkIndex(), result.stored())));
        }

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

            AttachmentDAO.CompletionResult result = attachmentDAO.completeUpload(
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

        private void handleBind(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            String body = readBody(exchange.getRequestBody());
            AttachmentBindRequest request = JsonCodec.fromJson(body, AttachmentBindRequest.class);
            if (request == null || request.getEnvelopeId() == null || request.getEnvelopeId().isBlank()) {
                throw new IllegalArgumentException("envelopeId is required");
            }

            AttachmentDAO.BindResult result = attachmentDAO.bindUploadToEnvelope(
                    callerId,
                    attachmentId,
                    request.getEnvelopeId());
            sendJson(exchange, 200, JsonCodec.toJson(
                    AttachmentBindResponse.success(
                            result.attachmentId(),
                            result.envelopeId(),
                            result.expiresAtEpochMs())));
        }

        private void handleDownload(HttpExchange exchange, String callerId, String attachmentId) throws IOException {
            AttachmentDAO.DownloadBlob blob = attachmentDAO.loadForRecipient(callerId, attachmentId);
            AttachmentDownloadResponse response = AttachmentDownloadResponse.success(
                    blob.attachmentId(),
                    blob.senderId(),
                    blob.recipientId(),
                    blob.contentType(),
                    blob.encryptedSizeBytes(),
                    blob.chunkCount(),
                    Base64.getEncoder().encodeToString(blob.encryptedBlob()));
            sendJson(exchange, 200, JsonCodec.toJson(response));
        }

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
    }

    private String authenticateCaller(HttpExchange exchange) throws IOException {
        String authHeader = exchange.getRequestHeaders().getFirst(AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendJson(exchange, 401, jsonError(MISSING_OR_INVALID_AUTH));
            return null;
        }

        String sessionId = authHeader.substring(BEARER_PREFIX.length());
        String callerId = sessionDAO.getUserIdForSession(sessionId);
        if (callerId == null) {
            sendJson(exchange, 401, jsonError(INVALID_SESSION));
            return null;
        }
        return callerId;
    }

    private String jsonError(String message) {
        String safe = message == null ? INTERNAL_SERVER_ERROR : message.replace("\"", "\\\"");
        return JSON_ERROR_PREFIX + safe + JSON_ERROR_SUFFIX;
    }

    private void applySecurityHeaders(Headers headers) {
        headers.add(STRICT_TRANSPORT_SECURITY, STRICT_TRANSPORT_SECURITY_VALUE);
        headers.add(CONTENT_SECURITY_POLICY, CONTENT_SECURITY_POLICY_VALUE);
        headers.add(X_CONTENT_TYPE_OPTIONS, X_CONTENT_TYPE_OPTIONS_VALUE);
        headers.add(X_FRAME_OPTIONS, X_FRAME_OPTIONS_VALUE);
    }


    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(CONTENT_TYPE, APPLICATION_JSON);
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        }
    }
}
