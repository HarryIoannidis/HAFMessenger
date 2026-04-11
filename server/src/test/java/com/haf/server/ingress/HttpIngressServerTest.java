package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.Attachment;
import com.haf.server.db.Contact;
import com.haf.server.db.FileUpload;
import com.haf.server.db.Session;
import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.QueuedEnvelope;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.constants.CryptoConstants;
import com.haf.shared.constants.MessageHeader;
import com.haf.shared.dto.EncryptedMessage;
import com.haf.shared.requests.AddContactRequest;
import com.haf.shared.requests.AttachmentBindRequest;
import com.haf.shared.requests.AttachmentChunkRequest;
import com.haf.shared.requests.AttachmentCompleteRequest;
import com.haf.shared.requests.AttachmentInitRequest;
import com.haf.shared.requests.RegisterRequest;
import com.haf.shared.responses.UserSearchResponse;
import com.haf.shared.utils.EccKeyIO;
import com.haf.shared.utils.FingerprintUtil;
import com.haf.shared.utils.JsonCodec;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.password4j.Argon2Function;
import com.password4j.Password;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class HttpIngressServerTest {

    private static final Argon2Function ARGON2 = Argon2Function.getInstance(
            CryptoConstants.ARGON2_MEMORY_KB,
            CryptoConstants.ARGON2_ITERATIONS,
            CryptoConstants.ARGON2_PARALLELISM,
            CryptoConstants.ARGON2_OUTPUT_LENGTH,
            com.password4j.types.Argon2.ID);

    private MetricsRegistry metricsRegistry;
    private EncryptedMessageValidator validator;
    private SSLContext sslContext;
    private HttpIngressServer server;

    @Mock
    private ServerConfig config;

    @Mock
    private MailboxRouter mailboxRouter;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private AuditLogger auditLogger;

    @Mock
    private User userDAO;

    @Mock
    private Session sessionDAO;

    @Mock
    private FileUpload fileUploadDAO;

    @Mock
    private Attachment attachmentDAO;

    @Mock
    private Contact contactDAO;

    private PresenceRegistry presenceRegistry;

    @BeforeEach
    void setUp() throws Exception {
        metricsRegistry = new MetricsRegistry();
        validator = new EncryptedMessageValidator();
        sslContext = createTestSSLContext();
        presenceRegistry = new PresenceRegistry();

        when(config.getHttpPort()).thenReturn(0);
        lenient().when(config.isDevMode()).thenReturn(true);
        lenient().when(config.getAdminPublicKeyPem())
                .thenReturn("-----BEGIN PUBLIC KEY-----\\nabc\\n-----END PUBLIC KEY-----");

        lenient().when(config.getSearchPageSize()).thenReturn(20);
        lenient().when(config.getSearchMaxPageSize()).thenReturn(50);
        lenient().when(config.getSearchMinQueryLength()).thenReturn(3);
        lenient().when(config.getSearchMaxQueryLength()).thenReturn(128);
        lenient().when(config.isTrustProxy()).thenReturn(false);
        lenient().when(config.getTrustedProxyCidrs()).thenReturn(List.of());
        lenient().when(config.getIngressExecutorThreads()).thenReturn(4);
        lenient().when(config.getIngressExecutorQueueCapacity()).thenReturn(64);
        lenient().when(config.getSearchCursorSecret()).thenReturn("test-search-secret");

        lenient().when(config.getAttachmentMaxBytes()).thenReturn(10L * 1024L * 1024L);
        lenient().when(config.getAttachmentInlineMaxBytes()).thenReturn(1024L);
        lenient().when(config.getAttachmentChunkBytes()).thenReturn(512);
        lenient().when(config.getAttachmentAllowedTypes()).thenReturn(List.of("application/pdf"));
        lenient().when(config.getAttachmentUnboundTtlSeconds()).thenReturn(1800L);
        lenient().when(rateLimiterService.checkAndConsumeLoginAttempt(anyString(), anyString(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());
        lenient().when(rateLimiterService.checkAndConsumeApi(anyString(), any(), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());

        try {
            server = new HttpIngressServer(
                    config,
                    sslContext,
                    mailboxRouter,
                    rateLimiterService,
                    auditLogger,
                    metricsRegistry,
                    validator,
                    userDAO,
                    sessionDAO,
                    fileUploadDAO,
                    attachmentDAO,
                    contactDAO,
                    presenceRegistry);
        } catch (java.net.SocketException socketEx) {
            Assumptions.assumeTrue(false, "Socket bind is not permitted in this execution environment");
        }
    }

    @Test
    void start_starts_server() {
        assertDoesNotThrow(() -> server.start());
        server.stop();
    }

    @Test
    void stop_shuts_down_gracefully() {
        server.start();
        assertDoesNotThrow(() -> server.stop());
    }

    @Test
    void ingress_rejects_missing_auth() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/messages", JsonCodec.toJson(validMessage("s", "r")));

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("missing or invalid auth"));
    }

    @Test
    void ingress_accepts_valid_payload() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        EncryptedMessage message = validMessage("sender-1", "recipient-1");

        ExchangeHarness exchange = newExchange("POST", "/api/v1/messages", JsonCodec.toJson(message));
        authenticate(exchange, "session-ok", "sender-1");

        when(rateLimiterService.checkAndConsume(anyString(), eq("sender-1")))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());
        when(mailboxRouter.ingress(any(EncryptedMessage.class))).thenReturn(
                new QueuedEnvelope("env-1", message, System.currentTimeMillis(), System.currentTimeMillis() + 60_000));

        handler.handle(exchange.exchange());

        assertEquals(202, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"envelopeId\":\"env-1\""));
        assertFalse(exchange.responseHeaders().containsKey("X-User-Id"));
        verify(mailboxRouter, times(1)).ingress(any(EncryptedMessage.class));
    }

    @Test
    void ingress_rejects_stale_recipient_key_fingerprint_header() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        EncryptedMessage message = validMessage("sender-1", "recipient-1");
        when(userDAO.getPublicKey("recipient-1")).thenReturn(new User.PublicKeyRecord("pem", "fp-current"));

        ExchangeHarness exchange = newExchange("POST", "/api/v1/messages", JsonCodec.toJson(message));
        exchange.requestHeaders().add("X-Recipient-Key-Fingerprint", "fp-stale");
        authenticate(exchange, "session-ok", "sender-1");

        handler.handle(exchange.exchange());

        assertEquals(409, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("stale_recipient_key"));
        verify(mailboxRouter, never()).ingress(any(EncryptedMessage.class));
    }

    @Test
    void ingress_fetches_pending_messages_for_authenticated_user() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        EncryptedMessage message = validMessage("sender-1", "recipient-1");
        when(rateLimiterService.checkAndConsume(anyString(), eq("recipient-1")))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());
        when(mailboxRouter.fetchUndelivered("recipient-1", 2)).thenReturn(
                List.of(new QueuedEnvelope("env-fetch-1", message, 10L, 20L)));

        ExchangeHarness exchange = newExchange("GET", "/api/v1/messages?limit=2", "");
        authenticate(exchange, "session-fetch", "recipient-1");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"messages\""));
        assertTrue(exchange.responseBodyAsString().contains("\"envelopeId\":\"env-fetch-1\""));
        assertTrue(exchange.responseBodyAsString().contains("\"type\":\"message\""));
        verify(mailboxRouter, times(1)).fetchUndelivered("recipient-1", 2);
    }

    @Test
    void ingress_acknowledges_messages_for_authenticated_user() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        when(rateLimiterService.checkAndConsume(anyString(), eq("recipient-1")))
                .thenReturn(RateLimiterService.RateLimitDecision.allow());
        when(mailboxRouter.acknowledgeOwned("recipient-1", List.of("env-a", "env-b")))
                .thenReturn(true);

        ExchangeHarness exchange = newExchange(
                "POST",
                "/api/v1/messages/ack",
                "{\"envelopeIds\":[\"env-a\",\"env-b\"]}");
        authenticate(exchange, "session-ack", "recipient-1");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"acknowledged\":true"));
        verify(mailboxRouter, times(1)).acknowledgeOwned("recipient-1", List.of("env-a", "env-b"));
    }

    @Test
    void ingress_fetch_pending_messages_is_rate_limited() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        when(rateLimiterService.checkAndConsume(anyString(), eq("recipient-1")))
                .thenReturn(RateLimiterService.RateLimitDecision.block(15));

        ExchangeHarness exchange = newExchange("GET", "/api/v1/messages?limit=2", "");
        authenticate(exchange, "session-fetch-limit", "recipient-1");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":15"));
        verify(mailboxRouter, never()).fetchUndelivered(anyString(), anyInt());
    }

    @Test
    void ingress_acknowledge_messages_is_rate_limited() throws Exception {
        HttpHandler handler = createHandler("IngressHandler");
        when(rateLimiterService.checkAndConsume(anyString(), eq("recipient-1")))
                .thenReturn(RateLimiterService.RateLimitDecision.block(12));

        ExchangeHarness exchange = newExchange(
                "POST",
                "/api/v1/messages/ack",
                "{\"envelopeIds\":[\"env-a\"]}");
        authenticate(exchange, "session-ack-limit", "recipient-1");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":12"));
        verify(mailboxRouter, never()).acknowledgeOwned(anyString(), any());
    }

    @Test
    void registration_rejects_non_post_method() throws Exception {
        HttpHandler handler = createHandler("RegistrationHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/register", "{}");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("method not allowed"));
    }

    @Test
    void registration_success_stores_user_and_updates_photo_ids() throws Exception {
        HttpHandler handler = createHandler("RegistrationHandler");

        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test Pilot");
        request.setEmail("pilot@haf.gr");
        request.setPassword("Strongpass1!");
        request.setPublicKeyPem("pem");
        request.setPublicKeyFingerprint("fingerprint");

        ExchangeHarness exchange = newExchange("POST", "/api/v1/register", JsonCodec.toJson(request));
        when(userDAO.existsByEmail("pilot@haf.gr")).thenReturn(false);
        when(userDAO.insert(any(RegisterRequest.class), anyString())).thenReturn("user-1");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"userId\":\"user-1\""));
        verify(userDAO, times(1)).updatePhotoIds("user-1", null, null);
    }

    @Test
    void registration_is_rate_limited_by_source_ip() throws Exception {
        HttpHandler handler = createHandler("RegistrationHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.REGISTER),
                anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.block(30));
        RegisterRequest request = new RegisterRequest();
        request.setFullName("Test Pilot");
        request.setEmail("pilot@haf.gr");
        request.setPassword("Strongpass1!");
        request.setPublicKeyPem("pem");
        request.setPublicKeyFingerprint("fingerprint");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/register", JsonCodec.toJson(request));

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":30"));
        verify(userDAO, never()).insert(any(RegisterRequest.class), anyString());
    }

    @Test
    void login_rejects_when_account_is_already_online() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        presenceRegistry.registerConnection(userId, mock(WebSocket.class));

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");

        handler.handle(exchange.exchange());

        assertEquals(409, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("Account is already logged in."));
        verify(sessionDAO, never()).createSessionTokens(anyString());
    }

    @Test
    void login_rejects_when_account_is_recently_active_in_prod_mode() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        when(config.isDevMode()).thenReturn(false);
        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        when(sessionDAO.isUserRecentlyActive(eq(userId), anyLong())).thenReturn(true);

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");

        handler.handle(exchange.exchange());

        assertEquals(409, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("Account is already logged in."));
        verify(sessionDAO, never()).createSessionTokens(anyString());
    }

    @Test
    void login_rejects_non_post_method() throws Exception {
        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/login", "");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("method not allowed"));
    }

    @Test
    void login_rejects_invalid_credentials() throws Exception {
        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{\"email\":\"pilot@haf.gr\",\"password\":\"wrong\"}");

        when(userDAO.findByEmail("pilot@haf.gr")).thenReturn(null);
        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("Invalid email or password"));
    }

    @Test
    void login_rejects_missing_email_or_password() throws Exception {
        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login", "{}");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("email and password are required"));
    }

    @Test
    void login_is_rate_limited_before_credential_check() throws Exception {
        HttpHandler handler = createHandler("LoginHandler");
        when(rateLimiterService.checkAndConsumeLoginAttempt(anyString(), eq("pilot@haf.gr"), anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.block(180));
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{\"email\":\"pilot@haf.gr\",\"password\":\"wrong\"}");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("Too many login attempts"));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":180"));
        verify(userDAO, never()).findByEmail(anyString());
    }

    @Test
    void login_ignores_x_forwarded_for_when_remote_is_not_trusted_proxy() throws Exception {
        when(config.isTrustProxy()).thenReturn(true);
        when(config.getTrustedProxyCidrs()).thenReturn(List.of("10.0.0.0/8"));

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange(
                "POST",
                "/api/v1/login",
                "{\"email\":\"pilot@haf.gr\",\"password\":\"wrong\"}");
        exchange.requestHeaders().add("X-Forwarded-For", "198.51.100.9");
        exchange.setRemoteAddress("203.0.113.11", 443);
        when(userDAO.findByEmail("pilot@haf.gr")).thenReturn(null);

        handler.handle(exchange.exchange());

        verify(rateLimiterService, times(1))
                .checkAndConsumeLoginAttempt(anyString(), eq("pilot@haf.gr"), eq("203.0.113.11"));
    }

    @Test
    void login_uses_forwarded_for_when_remote_is_trusted_proxy() throws Exception {
        when(config.isTrustProxy()).thenReturn(true);
        when(config.getTrustedProxyCidrs()).thenReturn(List.of("203.0.113.0/24"));

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange(
                "POST",
                "/api/v1/login",
                "{\"email\":\"pilot@haf.gr\",\"password\":\"wrong\"}");
        exchange.requestHeaders().add("X-Forwarded-For", "198.51.100.42, 203.0.113.77");
        exchange.setRemoteAddress("203.0.113.10", 443);
        when(userDAO.findByEmail("pilot@haf.gr")).thenReturn(null);

        handler.handle(exchange.exchange());

        verify(rateLimiterService, times(1))
                .checkAndConsumeLoginAttempt(anyString(), eq("pilot@haf.gr"), eq("198.51.100.42"));
    }

    @Test
    void login_password_helper_uses_sentinel_when_hash_missing() throws Exception {
        Class<?> loginHandler = Class.forName(HttpIngressServer.class.getName() + "$LoginHandler");
        java.lang.reflect.Method verifyMethod = loginHandler.getDeclaredMethod(
                "verifyPasswordWithSentinel",
                String.class,
                String.class);
        verifyMethod.setAccessible(true);

        String hash = Password.hash("correct horse battery staple")
                .addRandomSalt()
                .with(ARGON2)
                .getResult();
        boolean valid = (boolean) verifyMethod.invoke(null, "correct horse battery staple", hash);
        boolean invalid = (boolean) verifyMethod.invoke(null, "wrong", hash);
        boolean missing = (boolean) verifyMethod.invoke(null, "anything", null);

        assertTrue(valid);
        assertFalse(invalid);
        assertFalse(missing);
    }

    @Test
    void login_succeeds_when_account_is_offline() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        when(sessionDAO.createSessionTokens(userId))
                .thenReturn(new Session.SessionTokens("jwt-access-token", "refresh-token", 100L, 200L));

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"sessionId\":\"jwt-access-token\""));
        assertTrue(exchange.responseBodyAsString().contains("\"refreshToken\":\"refresh-token\""));
        verify(sessionDAO, times(1)).createSessionTokens(userId);
        verify(rateLimiterService, times(1)).clearLoginAttempts(eq(email), anyString());
    }

    @Test
    void login_force_takeover_rotates_key_revokes_sessions_and_creates_new_session() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        var takeoverPair = EccKeyIO.generate();
        String takeoverPem = EccKeyIO.publicPem(takeoverPair.getPublic());
        String takeoverFp = FingerprintUtil.sha256Hex(EccKeyIO.publicDer(takeoverPair.getPublic()));

        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        when(sessionDAO.createSessionTokens(userId))
                .thenReturn(new Session.SessionTokens("jwt-takeover", "refresh-takeover", 100L, 200L));

        WebSocket oldConnection = mock(WebSocket.class);
        presenceRegistry.registerConnection(userId, oldConnection);

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{"
                        + "\"email\":\"" + email + "\","
                        + "\"password\":\"" + password + "\","
                        + "\"forceTakeover\":true,"
                        + "\"takeoverPublicKeyPem\":\"" + takeoverPem.replace("\n", "\\n") + "\","
                        + "\"takeoverPublicKeyFingerprint\":\"" + takeoverFp + "\""
                        + "}");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"sessionId\":\"jwt-takeover\""));
        verify(userDAO, times(1)).updatePublicKey(userId, takeoverPem.trim(), takeoverFp);
        verify(sessionDAO, times(1)).revokeAllSessionsByUserId(userId);
        verify(sessionDAO, times(1)).createSessionTokens(userId);
        verify(oldConnection, times(1)).closeConnection(1000, "takeover");
    }

    @Test
    void login_force_takeover_rejects_when_key_fields_missing() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";

        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));

        HttpHandler handler = createHandler("LoginHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/login",
                "{"
                        + "\"email\":\"" + email + "\","
                        + "\"password\":\"" + password + "\","
                        + "\"forceTakeover\":true"
                        + "}");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("takeoverPublicKeyPem"));
        verify(sessionDAO, never()).createSessionTokens(anyString());
    }

    @Test
    void token_refresh_rotates_tokens() throws Exception {
        HttpHandler handler = createHandler("TokenRefreshHandler");
        when(sessionDAO.refreshSession("refresh-1"))
                .thenReturn(new Session.SessionTokens("jwt-2", "refresh-2", 300L, 600L));
        ExchangeHarness exchange = newExchange("POST", "/api/v1/token/refresh",
                "{\"refreshToken\":\"refresh-1\"}");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"sessionId\":\"jwt-2\""));
        assertTrue(exchange.responseBodyAsString().contains("\"refreshToken\":\"refresh-2\""));
    }

    @Test
    void token_refresh_rejects_invalid_refresh_token() throws Exception {
        HttpHandler handler = createHandler("TokenRefreshHandler");
        when(sessionDAO.refreshSession("refresh-invalid")).thenReturn(null);
        ExchangeHarness exchange = newExchange("POST", "/api/v1/token/refresh",
                "{\"refreshToken\":\"refresh-invalid\"}");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid session"));
    }

    @Test
    void token_refresh_rejects_revoked_refresh_token_with_takeover_reason() throws Exception {
        HttpHandler handler = createHandler("TokenRefreshHandler");
        when(sessionDAO.refreshSession("refresh-revoked")).thenReturn(null);
        when(sessionDAO.isRefreshSessionRevoked("refresh-revoked")).thenReturn(true);
        ExchangeHarness exchange = newExchange("POST", "/api/v1/token/refresh",
                "{\"refreshToken\":\"refresh-revoked\"}");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("session revoked by takeover"));
    }

    @Test
    void token_refresh_is_rate_limited_by_source_ip() throws Exception {
        HttpHandler handler = createHandler("TokenRefreshHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.TOKEN_REFRESH),
                anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.block(45));
        ExchangeHarness exchange = newExchange("POST", "/api/v1/token/refresh",
                "{\"refreshToken\":\"refresh-1\"}");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":45"));
        verify(sessionDAO, never()).refreshSession(anyString());
    }

    @Test
    void logout_rejects_missing_auth() throws Exception {
        HttpHandler handler = createHandler("LogoutHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/logout", "{}");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("missing or invalid auth"));
    }

    @Test
    void logout_rejects_invalid_session() throws Exception {
        HttpHandler handler = createHandler("LogoutHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/logout", "{}");
        exchange.requestHeaders().add("Authorization", "Bearer stale-session");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid session"));
    }

    @Test
    void logout_rejects_revoked_session_with_takeover_reason() throws Exception {
        HttpHandler handler = createHandler("LogoutHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/logout", "{}");
        exchange.requestHeaders().add("Authorization", "Bearer stale-session");
        when(sessionDAO.getUserIdForSessionAndTouch("stale-session")).thenReturn(null);
        when(sessionDAO.isAccessSessionRevoked("stale-session")).thenReturn(true);

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("session revoked by takeover"));
    }

    @Test
    void logout_success_revokes_session() throws Exception {
        HttpHandler handler = createHandler("LogoutHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/logout", "{}");
        authenticate(exchange, "sess-logout", "user-1");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"success\":true"));
        verify(sessionDAO, times(1)).revokeSession("sess-logout");
    }

    @Test
    void admin_key_rejects_non_get() throws Exception {
        HttpHandler handler = createHandler("AdminKeyHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/config/admin-key", "");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
    }

    @Test
    void admin_key_returns_configured_pem() throws Exception {
        HttpHandler handler = createHandler("AdminKeyHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/config/admin-key", "");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("adminPublicKeyPem"));
    }

    @Test
    void admin_key_is_public_but_rate_limited() throws Exception {
        HttpHandler handler = createHandler("AdminKeyHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.ADMIN_KEY),
                anyString()))
                .thenReturn(RateLimiterService.RateLimitDecision.block(20));
        ExchangeHarness exchange = newExchange("GET", "/api/v1/config/admin-key", "");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":20"));
    }

    @Test
    void user_key_rejects_missing_auth() throws Exception {
        HttpHandler handler = createHandler("UserKeyHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/users/u-1/key", "");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("missing or invalid auth"));
    }

    @Test
    void user_key_rejects_invalid_path() throws Exception {
        HttpHandler handler = createHandler("UserKeyHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/users/u-1", "");
        authenticate(exchange, "sess-user-key-path", "caller");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid request path"));
    }

    @Test
    void user_key_returns_public_key_payload() throws Exception {
        HttpHandler handler = createHandler("UserKeyHandler");
        when(userDAO.getPublicKey("u-1")).thenReturn(new User.PublicKeyRecord("PEM", "fp-1"));
        ExchangeHarness exchange = newExchange("GET", "/api/v1/users/u-1/key", "");
        authenticate(exchange, "sess-user-key", "caller");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"publicKeyPem\":\"PEM\""));
        assertTrue(exchange.responseBodyAsString().contains("\"fingerprint\":\"fp-1\""));
    }

    @Test
    void user_key_returns_not_found_when_user_missing() throws Exception {
        HttpHandler handler = createHandler("UserKeyHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/users/u-missing/key", "");
        authenticate(exchange, "sess-user-key-missing", "caller");

        handler.handle(exchange.exchange());

        assertEquals(404, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("user not found"));
    }

    @Test
    void admin_user_key_and_search_handlers_close_exchange() throws Exception {
        HttpHandler adminHandler = createHandler("AdminKeyHandler");
        ExchangeHarness adminExchange = newExchange("GET", "/api/v1/config/admin-key", "");
        adminHandler.handle(adminExchange.exchange());
        verify(adminExchange.exchange(), times(1)).close();

        HttpHandler userKeyHandler = createHandler("UserKeyHandler");
        ExchangeHarness userKeyExchange = newExchange("GET", "/api/v1/users/u-1/key", "");
        authenticate(userKeyExchange, "sess-user-key-close", "caller");
        when(userDAO.getPublicKey("u-1")).thenReturn(new User.PublicKeyRecord("PEM", "fp-1"));
        userKeyHandler.handle(userKeyExchange.exchange());
        verify(userKeyExchange.exchange(), times(1)).close();

        HttpHandler searchHandler = createHandler("SearchHandler");
        ExchangeHarness searchExchange = newExchange("GET", "/api/v1/search?q=%20", "");
        authenticate(searchExchange, "sess-search-close", "caller");
        searchHandler.handle(searchExchange.exchange());
        verify(searchExchange.exchange(), times(1)).close();
    }

    @Test
    void search_rejects_invalid_limit() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/search?q=alice&limit=bad", "");
        authenticate(exchange, "sess-search", "caller");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid limit"));
    }

    @Test
    void search_is_rate_limited_for_authenticated_caller() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.SEARCH),
                eq("caller")))
                .thenReturn(RateLimiterService.RateLimitDecision.block(15));
        ExchangeHarness exchange = newExchange("GET", "/api/v1/search?q=alice", "");
        authenticate(exchange, "sess-search-limit", "caller");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":15"));
    }

    @Test
    void search_rejects_short_query() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/search?q=ab", "");
        authenticate(exchange, "sess-search-short", "caller");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("query must be at least"));
    }

    @Test
    void search_returns_empty_success_for_blank_query() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/search?q=%20%20", "");
        authenticate(exchange, "sess-search-blank", "caller");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        UserSearchResponse response = JsonCodec.fromJson(exchange.responseBodyAsString(), UserSearchResponse.class);
        assertNotNull(response.getResults());
        assertTrue(response.getResults().isEmpty());
        assertFalse(response.isHasMore());
    }

    @Test
    void search_rejects_tampered_cursor_signature() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");

        User.SearchRecord row = new User.SearchRecord(
                "u-1",
                "Alice",
                "REG-1",
                "alice@haf.gr",
                "SMINIAS",
                "6900000000",
                "2026-01-01");
        when(userDAO.searchUsersPage("alice", "caller", 2, null, null))
                .thenReturn(new User.SearchPage(List.of(row), true, "Alice Z", "uid-last"));

        ExchangeHarness first = newExchange("GET", "/api/v1/search?q=alice&limit=2", "");
        authenticate(first, "sess-search-tamper", "caller");
        handler.handle(first.exchange());
        assertEquals(200, first.statusCode().get());

        Map<?, ?> firstMap = JsonCodec.fromJson(first.responseBodyAsString(), Map.class);
        String nextCursor = String.valueOf(firstMap.get("nextCursor"));
        String tampered = nextCursor.substring(0, nextCursor.length() - 1)
                + (nextCursor.endsWith("A") ? "B" : "A");

        ExchangeHarness second = newExchange("GET", "/api/v1/search?q=alice&limit=2&cursor=" + tampered, "");
        authenticate(second, "sess-search-tamper", "caller");
        handler.handle(second.exchange());

        assertEquals(400, second.statusCode().get());
        assertTrue(second.responseBodyAsString().contains("invalid cursor"));
    }

    @Test
    void search_success_returns_results() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/search?q=alice&limit=2", "");
        authenticate(exchange, "sess-search-ok", "caller");

        User.SearchRecord row = new User.SearchRecord(
                "u-1",
                "Alice",
                "REG-1",
                "alice@haf.gr",
                "SMINIAS",
                "6900000000",
                "2026-01-01");
        when(userDAO.searchUsersPage("alice", "caller", 2, null, null))
                .thenReturn(new User.SearchPage(List.of(row), false, null, null));

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"userId\":\"u-1\""));
    }

    @Test
    void search_cursor_roundtrip_encodes_and_decodes_signature() throws Exception {
        HttpHandler handler = createHandler("SearchHandler");

        User.SearchRecord row = new User.SearchRecord(
                "u-1",
                "Alice",
                "REG-1",
                "alice@haf.gr",
                "SMINIAS",
                "6900000000",
                "2026-01-01");

        when(userDAO.searchUsersPage("alice", "caller", 2, null, null))
                .thenReturn(new User.SearchPage(List.of(row), true, "Alice Z", "uid-last"));
        when(userDAO.searchUsersPage("alice", "caller", 2, "Alice Z", "uid-last"))
                .thenReturn(new User.SearchPage(List.of(), false, null, null));

        ExchangeHarness first = newExchange("GET", "/api/v1/search?q=alice&limit=2", "");
        authenticate(first, "sess-search-cursor", "caller");
        handler.handle(first.exchange());

        assertEquals(200, first.statusCode().get());
        Map<?, ?> firstMap = JsonCodec.fromJson(first.responseBodyAsString(), Map.class);
        String nextCursor = String.valueOf(firstMap.get("nextCursor"));
        assertNotNull(nextCursor);
        assertFalse(nextCursor.isBlank());

        ExchangeHarness second = newExchange("GET", "/api/v1/search?q=alice&limit=2&cursor=" + nextCursor, "");
        authenticate(second, "sess-search-cursor", "caller");
        handler.handle(second.exchange());

        assertEquals(200, second.statusCode().get());
        verify(userDAO, times(1)).searchUsersPage("alice", "caller", 2, "Alice Z", "uid-last");
    }

    @Test
    void health_rejects_non_get_or_head() throws Exception {
        HttpHandler handler = createHandler("HealthHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/health", "");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
    }

    @Test
    void health_accepts_get() throws Exception {
        HttpHandler handler = createHandler("HealthHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/health", "");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
    }

    @Test
    void contacts_rejects_missing_auth() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/contacts", "");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
    }

    @Test
    void contacts_rejects_invalid_session() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/contacts", "");
        exchange.requestHeaders().add("Authorization", "Bearer stale");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid session"));
    }

    @Test
    void contacts_rejects_method_not_allowed() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("PUT", "/api/v1/contacts", "");
        authenticate(exchange, "sess-contacts-put", "caller");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("method not allowed"));
    }

    @Test
    void contacts_is_rate_limited_for_authenticated_caller() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.CONTACTS),
                eq("caller")))
                .thenReturn(RateLimiterService.RateLimitDecision.block(21));
        ExchangeHarness exchange = newExchange("GET", "/api/v1/contacts", "");
        authenticate(exchange, "sess-contacts-limit", "caller");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":21"));
    }

    @Test
    void contacts_get_returns_contact_list() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/contacts", "");
        authenticate(exchange, "sess-contacts", "caller");

        when(contactDAO.getContacts("caller")).thenReturn(List.of(
                new Contact.ContactRecord("u-2", "Contact User", "REG-2", "c@haf.gr", "SMINIAS", "6900000002",
                        "2026-01-01")));

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"contacts\""));
        assertTrue(exchange.responseBodyAsString().contains("\"userId\":\"u-2\""));
    }

    @Test
    void contacts_get_uses_recent_session_presence_in_prod_mode() throws Exception {
        when(config.isDevMode()).thenReturn(false);
        when(sessionDAO.isUserRecentlyActive(eq("u-2"), anyLong())).thenReturn(true);

        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/contacts", "");
        authenticate(exchange, "sess-contacts-prod", "caller");

        when(contactDAO.getContacts("caller")).thenReturn(List.of(
                new Contact.ContactRecord("u-2", "Contact User", "REG-2", "c@haf.gr", "SMINIAS", "6900000002",
                        "2026-01-01")));

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"userId\":\"u-2\""));
        assertTrue(exchange.responseBodyAsString().contains("\"active\":true"));
    }

    @Test
    void contacts_post_adds_contact() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        AddContactRequest request = new AddContactRequest("u-2");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/contacts", JsonCodec.toJson(request));
        authenticate(exchange, "sess-contacts-post", "caller");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        verify(contactDAO, times(1)).addContact("caller", "u-2");
    }

    @Test
    void contacts_delete_removes_contact() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("DELETE", "/api/v1/contacts?contactId=u-2", "");
        authenticate(exchange, "sess-contacts-delete", "caller");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        verify(contactDAO, times(1)).removeContact("caller", "u-2");
    }

    @Test
    void contacts_delete_rejects_blank_contact_id() throws Exception {
        HttpHandler handler = createHandler("ContactsHandler");
        ExchangeHarness exchange = newExchange("DELETE", "/api/v1/contacts", "");
        authenticate(exchange, "sess-contacts-delete-empty", "caller");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid contactId"));
    }

    @Test
    void messaging_config_rejects_non_get() throws Exception {
        HttpHandler handler = createHandler("MessagingConfigHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/config/messaging", "");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
    }

    @Test
    void messaging_config_returns_policy() throws Exception {
        HttpHandler handler = createHandler("MessagingConfigHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/config/messaging", "");
        authenticate(exchange, "sess-msg-policy", "caller");

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("attachmentChunkBytes"));
        assertTrue(exchange.responseBodyAsString().contains("attachmentAllowedTypes"));
    }

    @Test
    void messaging_config_is_rate_limited_for_authenticated_caller() throws Exception {
        HttpHandler handler = createHandler("MessagingConfigHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.MESSAGING_CONFIG),
                eq("caller")))
                .thenReturn(RateLimiterService.RateLimitDecision.block(9));
        ExchangeHarness exchange = newExchange("GET", "/api/v1/config/messaging", "");
        authenticate(exchange, "sess-msg-policy-limit", "caller");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":9"));
    }

    @Test
    void attachments_rejects_missing_auth() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/attachments/init", "{}");

        handler.handle(exchange.exchange());

        assertEquals(401, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid auth"));
    }

    @Test
    void attachments_init_rejects_method_not_allowed() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/attachments/init", "");
        authenticate(exchange, "sess-attach-init-method", "caller");

        handler.handle(exchange.exchange());

        assertEquals(405, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("method not allowed"));
    }

    @Test
    void attachments_chunk_is_rate_limited() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        when(rateLimiterService.checkAndConsumeApi(anyString(), eq(RateLimiterService.ApiRateLimitScope.ATTACHMENTS_CHUNK),
                eq("caller")))
                .thenReturn(RateLimiterService.RateLimitDecision.block(7));
        AttachmentChunkRequest chunkReq = new AttachmentChunkRequest();
        chunkReq.setChunkIndex(0);
        chunkReq.setChunkDataB64(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));

        ExchangeHarness exchange = newExchange("POST", "/api/v1/attachments/att-1/chunk", JsonCodec.toJson(chunkReq));
        authenticate(exchange, "sess-attach-chunk-limit", "caller");

        handler.handle(exchange.exchange());

        assertEquals(429, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"rate limit\""));
        assertTrue(exchange.responseBodyAsString().contains("\"retryAfterSeconds\":7"));
        verify(attachmentDAO, never()).storeChunk(anyString(), anyString(), anyInt(), any(byte[].class));
    }

    @Test
    void attachments_chunk_rejects_invalid_base64() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        AttachmentChunkRequest chunkReq = new AttachmentChunkRequest();
        chunkReq.setChunkIndex(0);
        chunkReq.setChunkDataB64("%%%notbase64%%%");

        ExchangeHarness exchange = newExchange("POST", "/api/v1/attachments/att-1/chunk", JsonCodec.toJson(chunkReq));
        authenticate(exchange, "sess-attach-chunk-invalid", "caller");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("chunkDataB64 is not valid base64"));
    }

    @Test
    void attachments_complete_maps_illegal_state_to_conflict() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        AttachmentCompleteRequest completeReq = new AttachmentCompleteRequest();
        completeReq.setExpectedChunks(2);
        completeReq.setEncryptedSizeBytes(1024L);

        ExchangeHarness exchange = newExchange("POST", "/api/v1/attachments/att-1/complete",
                JsonCodec.toJson(completeReq));
        authenticate(exchange, "sess-attach-complete-conflict", "caller");
        when(attachmentDAO.completeUpload("caller", "att-1", 2, 1024L))
                .thenThrow(new IllegalStateException("upload not complete"));

        handler.handle(exchange.exchange());

        assertEquals(409, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("upload not complete"));
    }

    @Test
    void attachments_download_maps_security_exception_to_forbidden() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        ExchangeHarness exchange = newExchange("GET", "/api/v1/attachments/att-1", "");
        authenticate(exchange, "sess-attach-download-forbidden", "caller");
        when(attachmentDAO.loadForRecipient("caller", "att-1")).thenThrow(new SecurityException("forbidden"));

        handler.handle(exchange.exchange());

        assertEquals(403, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("forbidden"));
    }

    @Test
    void attachments_full_lifecycle_success_paths() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");

        // init
        AttachmentInitRequest initReq = new AttachmentInitRequest();
        initReq.setRecipientId("recipient-1");
        initReq.setContentType("application/vnd.haf.encrypted-message+json");
        initReq.setPlaintextSizeBytes(2048L);
        initReq.setEncryptedSizeBytes(1024L);
        initReq.setExpectedChunks(2);

        ExchangeHarness initExchange = newExchange("POST", "/api/v1/attachments/init", JsonCodec.toJson(initReq));
        authenticate(initExchange, "sess-attach", "caller");
        when(attachmentDAO.initUpload("caller", "recipient-1", "application/vnd.haf.encrypted-message+json", 1024L, 2,
                1800L))
                .thenReturn(new Attachment.UploadInitResult("att-1", 12345L));

        handler.handle(initExchange.exchange());
        assertEquals(200, initExchange.statusCode().get());
        assertTrue(initExchange.responseBodyAsString().contains("\"attachmentId\":\"att-1\""));

        // chunk
        AttachmentChunkRequest chunkReq = new AttachmentChunkRequest();
        chunkReq.setChunkIndex(0);
        chunkReq.setChunkDataB64(Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }));
        ExchangeHarness chunkExchange = newExchange("POST", "/api/v1/attachments/att-1/chunk",
                JsonCodec.toJson(chunkReq));
        authenticate(chunkExchange, "sess-attach", "caller");
        when(attachmentDAO.storeChunk(eq("caller"), eq("att-1"), eq(0), any(byte[].class)))
                .thenReturn(new Attachment.ChunkStoreResult(0, true));

        handler.handle(chunkExchange.exchange());
        assertEquals(200, chunkExchange.statusCode().get());
        assertTrue(chunkExchange.responseBodyAsString().contains("\"stored\":true"));

        // complete
        AttachmentCompleteRequest completeReq = new AttachmentCompleteRequest();
        completeReq.setExpectedChunks(2);
        completeReq.setEncryptedSizeBytes(1024L);
        ExchangeHarness completeExchange = newExchange("POST", "/api/v1/attachments/att-1/complete",
                JsonCodec.toJson(completeReq));
        authenticate(completeExchange, "sess-attach", "caller");
        when(attachmentDAO.completeUpload("caller", "att-1", 2, 1024L))
                .thenReturn(new Attachment.CompletionResult(2, 1024L, "COMPLETE"));

        handler.handle(completeExchange.exchange());
        assertEquals(200, completeExchange.statusCode().get());
        assertTrue(completeExchange.responseBodyAsString().contains("\"status\":\"COMPLETE\""));

        // bind
        AttachmentBindRequest bindReq = new AttachmentBindRequest();
        bindReq.setEnvelopeId("env-1");
        ExchangeHarness bindExchange = newExchange("POST", "/api/v1/attachments/att-1/bind", JsonCodec.toJson(bindReq));
        authenticate(bindExchange, "sess-attach", "caller");
        when(attachmentDAO.bindUploadToEnvelope("caller", "att-1", "env-1"))
                .thenReturn(new Attachment.BindResult("att-1", "env-1", 777L));

        handler.handle(bindExchange.exchange());
        assertEquals(200, bindExchange.statusCode().get());
        assertTrue(bindExchange.responseBodyAsString().contains("\"envelopeId\":\"env-1\""));

        // download
        ExchangeHarness downloadExchange = newExchange("GET", "/api/v1/attachments/att-1", "");
        authenticate(downloadExchange, "sess-attach", "caller");
        when(attachmentDAO.loadForRecipient("caller", "att-1"))
                .thenReturn(new Attachment.DownloadBlob(
                        "att-1",
                        "sender-1",
                        "caller",
                        "application/vnd.haf.encrypted-message+json",
                        3L,
                        1,
                        new byte[] { 9, 8, 7 }));

        handler.handle(downloadExchange.exchange());
        assertEquals(200, downloadExchange.statusCode().get());
        assertTrue(downloadExchange.responseBodyAsString().contains("\"attachmentId\":\"att-1\""));
    }

    @Test
    void attachments_rejects_invalid_request_path() throws Exception {
        HttpHandler handler = createHandler("AttachmentsHandler");
        ExchangeHarness exchange = newExchange("POST", "/api/v1/attachments/", "{}");
        authenticate(exchange, "sess-attach-fail", "caller");

        handler.handle(exchange.exchange());

        assertEquals(400, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("invalid request path"));
    }

    private HttpHandler createHandler(String nestedClassSimpleName) throws Exception {
        Class<?> handlerClass = Class.forName(HttpIngressServer.class.getName() + "$" + nestedClassSimpleName);
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(HttpIngressServer.class);
        constructor.setAccessible(true);
        return (HttpHandler) constructor.newInstance(server);
    }

    private void authenticate(ExchangeHarness exchange, String sessionId, String userId) {
        exchange.requestHeaders().add("Authorization", "Bearer " + sessionId);
        when(sessionDAO.getUserIdForSessionAndTouch(sessionId)).thenReturn(userId);
    }

    private static EncryptedMessage validMessage(String senderId, String recipientId) {
        EncryptedMessage message = new EncryptedMessage();
        message.setVersion(MessageHeader.VERSION);
        message.setAlgorithm(MessageHeader.ALGO_AEAD);
        message.setSenderId(senderId);
        message.setRecipientId(recipientId);
        message.setTimestampEpochMs(System.currentTimeMillis());
        message.setTtlSeconds((int) java.time.Duration.ofDays(1).toSeconds());
        message.setIvB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.IV_BYTES]));
        message.setEphemeralPublicB64(Base64.getEncoder().encodeToString(new byte[256]));
        message.setCiphertextB64(Base64.getEncoder().encodeToString("test".getBytes(StandardCharsets.UTF_8)));
        message.setTagB64(Base64.getEncoder().encodeToString(new byte[MessageHeader.GCM_TAG_BYTES]));
        message.setContentType("text/plain");
        message.setContentLength(4);
        message.setE2e(true);
        return message;
    }

    private static User.UserRecord approvedUser(String userId, String password) {
        String passwordHash = Password.hash(password).addRandomSalt().with(ARGON2).getResult();
        return new User.UserRecord(
                userId,
                passwordHash,
                "Pilot",
                "SMINIAS",
                "REG-001",
                "pilot@haf.gr",
                "6900000000",
                "2026-01-01",
                "APPROVED");
    }

    private ExchangeHarness newExchange(String method, String path, String body) throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        Headers responseHeaders = new Headers();
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        AtomicInteger statusCode = new AtomicInteger(-1);
        InetSocketAddress remoteAddress = new InetSocketAddress("203.0.113.10", 443);

        String payload = body == null ? "" : body;
        lenient().when(exchange.getRequestMethod()).thenReturn(method);
        lenient().when(exchange.getRequestURI()).thenReturn(URI.create(path));
        lenient().when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        lenient().when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        lenient().when(exchange.getRemoteAddress()).thenReturn(remoteAddress);
        lenient().when(exchange.getRequestBody())
                .thenReturn(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)));
        lenient().when(exchange.getResponseBody()).thenReturn(responseBody);
        org.mockito.Mockito.doAnswer(invocation -> {
            statusCode.set(invocation.getArgument(0, Integer.class));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());

        return new ExchangeHarness(exchange, requestHeaders, responseHeaders, responseBody, statusCode);
    }

    private record ExchangeHarness(HttpExchange exchange, Headers requestHeaders, Headers responseHeaders,
            ByteArrayOutputStream responseBody, AtomicInteger statusCode) {
        private String responseBodyAsString() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        private void setRemoteAddress(String host, int port) {
            lenient().when(exchange.getRemoteAddress()).thenReturn(new InetSocketAddress(host, port));
        }
    }

    private SSLContext createTestSSLContext() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
