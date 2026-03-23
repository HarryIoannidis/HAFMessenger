package com.haf.server.ingress;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.AttachmentDAO;
import com.haf.server.db.ContactDAO;
import com.haf.server.db.FileUploadDAO;
import com.haf.server.db.SessionDAO;
import com.haf.server.db.UserDAO;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.constants.CryptoConstants;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.password4j.Argon2Function;
import com.password4j.Password;
import org.java_websocket.WebSocket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.security.KeyStore;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
    private UserDAO userDAO;

    @Mock
    private SessionDAO sessionDAO;

    @Mock
    private FileUploadDAO fileUploadDAO;

    @Mock
    private AttachmentDAO attachmentDAO;

    @Mock
    private ContactDAO contactDAO;

    private PresenceRegistry presenceRegistry;

    @BeforeEach
    void setUp() throws Exception {
        metricsRegistry = new MetricsRegistry();
        validator = new EncryptedMessageValidator();
        sslContext = createTestSSLContext();
        presenceRegistry = new PresenceRegistry();

        // Use port 0 to get a random available port for tests
        when(config.getHttpPort()).thenReturn(0);

        try {
            server = new HttpIngressServer(
                    config, sslContext, mailboxRouter, rateLimiterService,
                    auditLogger, metricsRegistry, validator, userDAO, sessionDAO, fileUploadDAO, attachmentDAO,
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
    void login_rejects_when_account_is_already_online() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        presenceRegistry.registerConnection(userId, mock(WebSocket.class));

        HttpHandler handler = createLoginHandler();
        ExchangeHarness exchange = newLoginExchange(email, password);

        handler.handle(exchange.exchange());

        assertEquals(409, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"error\":\"Account is already logged in.\""));
        verify(sessionDAO, never()).createSession(anyString());
        verify(auditLogger, never()).logLogin(anyString(), anyString(), anyString());
    }

    @Test
    void login_succeeds_when_account_is_offline() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        when(sessionDAO.createSession(userId)).thenReturn("session-1");

        HttpHandler handler = createLoginHandler();
        ExchangeHarness exchange = newLoginExchange(email, password);

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        assertTrue(exchange.responseBodyAsString().contains("\"sessionId\":\"session-1\""));
        assertTrue(exchange.responseBodyAsString().contains("\"regNumber\":\"REG-001\""));
        assertTrue(exchange.responseBodyAsString().contains("\"email\":\"pilot@haf.gr\""));
        assertTrue(exchange.responseBodyAsString().contains("\"telephone\":\"6900000000\""));
        assertTrue(exchange.responseBodyAsString().contains("\"joinedDate\":\"2026-01-01\""));
        verify(sessionDAO, times(1)).createSession(userId);
        verify(auditLogger, times(1)).logLogin(anyString(), eq(userId), eq(email));
    }

    @Test
    void login_is_not_blocked_by_other_user_being_online() throws Exception {
        String userId = "user-1";
        String email = "pilot@haf.gr";
        String password = "correct horse battery staple";
        when(userDAO.findByEmail(email)).thenReturn(approvedUser(userId, password));
        when(sessionDAO.createSession(userId)).thenReturn("session-1");
        presenceRegistry.registerConnection("different-user", mock(WebSocket.class));

        HttpHandler handler = createLoginHandler();
        ExchangeHarness exchange = newLoginExchange(email, password);

        handler.handle(exchange.exchange());

        assertEquals(200, exchange.statusCode().get());
        verify(sessionDAO, times(1)).createSession(userId);
    }

    @Test
    void stop_shuts_down_gracefully() {
        server.start();
        assertDoesNotThrow(() -> server.stop());
    }

    private HttpHandler createLoginHandler() throws Exception {
        Class<?> handlerClass = Class.forName(HttpIngressServer.class.getName() + "$LoginHandler");
        Constructor<?> constructor = handlerClass.getDeclaredConstructor(HttpIngressServer.class);
        constructor.setAccessible(true);
        return (HttpHandler) constructor.newInstance(server);
    }

    private ExchangeHarness newLoginExchange(String email, String password) throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        Headers responseHeaders = new Headers();
        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        AtomicInteger statusCode = new AtomicInteger(-1);
        String requestJson = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getRequestURI()).thenReturn(URI.create("/api/v1/login"));
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getRequestBody()).thenReturn(
                new ByteArrayInputStream(requestJson.getBytes(StandardCharsets.UTF_8)));
        when(exchange.getResponseBody()).thenReturn(responseBody);
        doAnswer(invocation -> {
            statusCode.set(invocation.getArgument(0, Integer.class));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());

        return new ExchangeHarness(exchange, responseBody, statusCode);
    }

    private static UserDAO.UserRecord approvedUser(String userId, String password) {
        String passwordHash = Password.hash(password).addRandomSalt().with(ARGON2).getResult();
        return new UserDAO.UserRecord(
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

    private record ExchangeHarness(HttpExchange exchange, ByteArrayOutputStream responseBody, AtomicInteger statusCode) {
        private String responseBodyAsString() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }
    }

    private SSLContext createTestSSLContext() throws Exception {
        // Create a minimal SSLContext for testing
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, "password".toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());

        kmf.init(keyStore, "password".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());

        tmf.init(keyStore);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ctx;
    }
}
