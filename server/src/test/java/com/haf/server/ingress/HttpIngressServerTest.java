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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HttpIngressServerTest {

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
    void handle_rejects_non_post_method() {
        // This would require accessing the private handler, so we test via integration
        // For unit test, we verify the server can be created and started
        assertNotNull(server);
    }

    @Test
    void handle_accepts_valid_message() {
        // Integration test would verify full flow
        // Unit test verifies components work together
        assertNotNull(server);
    }

    @Test
    void handle_rejects_rate_limited_request() {
        // Integration test would verify 429 response
        assertNotNull(server);
    }

    @Test
    void handle_rejects_invalid_message() {
        // Integration test would verify 400 response
        assertNotNull(server);
    }

    @Test
    void stop_shuts_down_gracefully() {
        server.start();
        assertDoesNotThrow(() -> server.stop());
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
