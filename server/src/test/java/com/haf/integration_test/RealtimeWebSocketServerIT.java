package com.haf.integration_test;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.Contact;
import com.haf.server.db.Session;
import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.realtime.RealtimeWebSocketServer;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.RateLimiterService;
import com.haf.shared.utils.JsonCodec;
import com.haf.shared.websocket.RealtimeEvent;
import com.haf.shared.websocket.RealtimeEventType;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeWebSocketServerIT {

    private static final String REALTIME_PATH = "/ws/v1/realtime";
    private static final char[] KEY_PASSWORD = "changeit".toCharArray();

    @Test
    void loopback_wss_accepts_authorized_heartbeat_over_tls() throws Exception {
        int port = freePort();
        TestTlsContext tlsContext = createTestTlsContext();
        ServerConfig config = mock(ServerConfig.class);
        when(config.getWsPort()).thenReturn(port);
        when(config.getWsPath()).thenReturn(REALTIME_PATH);

        MailboxRouter mailboxRouter = mock(MailboxRouter.class);
        Session sessionDAO = mock(Session.class);
        Contact contactDAO = mock(Contact.class);
        when(sessionDAO.getUserIdForSessionAndTouch("it-token")).thenReturn("user-it");
        when(mailboxRouter.subscribe(eq("user-it"), any())).thenReturn(
                new MailboxRouter.MailboxSubscription("user-it", envelope -> {
                }));
        when(mailboxRouter.fetchUndelivered("user-it", 100)).thenReturn(List.of());
        when(mailboxRouter.fetchReceiptReplayForSender("user-it", 500)).thenReturn(List.of());
        when(contactDAO.getWatcherUserIds("user-it")).thenReturn(List.of());

        RealtimeWebSocketServer server = new RealtimeWebSocketServer(
                config,
                tlsContext.serverContext(),
                mailboxRouter,
                mock(RateLimiterService.class),
                mock(AuditLogger.class),
                new EncryptedMessageValidator(),
                mock(User.class),
                sessionDAO,
                contactDAO);
        java.net.http.WebSocket socket = null;
        try {
            server.start();
            Thread.sleep(300);

            CompletableFuture<RealtimeEvent> response = new CompletableFuture<>();
            HttpClient client = HttpClient.newBuilder()
                    .sslContext(tlsContext.clientContext())
                    .sslParameters(tls13Parameters())
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            socket = client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer it-token")
                    .buildAsync(URI.create("wss://localhost:" + port + REALTIME_PATH), new CapturingListener(response))
                    .get(5, TimeUnit.SECONDS);

            RealtimeEvent heartbeat = RealtimeEvent.outbound(RealtimeEventType.HEARTBEAT);
            socket.sendText(JsonCodec.toJson(heartbeat), true).get(5, TimeUnit.SECONDS);

            RealtimeEvent reply = response.get(5, TimeUnit.SECONDS);
            assertEquals(RealtimeEventType.HEARTBEAT, reply.eventType());
            assertEquals(heartbeat.getEventId(), reply.getCorrelationId());
            assertEquals("user-it", reply.getSenderId());
        } finally {
            if (socket != null) {
                socket.sendClose(1000, "done").get(5, TimeUnit.SECONDS);
            }
            server.close();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static SSLParameters tls13Parameters() {
        SSLParameters parameters = new SSLParameters();
        parameters.setProtocols(new String[] { "TLSv1.3" });
        return parameters;
    }

    private static TestTlsContext createTestTlsContext() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name("CN=localhost");
        Date notBefore = Date.from(Instant.now().minus(Duration.ofMinutes(1)));
        Date notAfter = Date.from(Instant.now().plus(Duration.ofDays(1)));
        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());
        certBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certBuilder.build(signer));
        certificate.verify(keyPair.getPublic());

        KeyStore serverStore = KeyStore.getInstance("PKCS12");
        serverStore.load(null, KEY_PASSWORD);
        serverStore.setKeyEntry("server", keyPair.getPrivate(), KEY_PASSWORD, new Certificate[] { certificate });
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(serverStore, KEY_PASSWORD);
        SSLContext serverContext = SSLContext.getInstance("TLSv1.3");
        serverContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, KEY_PASSWORD);
        trustStore.setCertificateEntry("server", certificate);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        SSLContext clientContext = SSLContext.getInstance("TLSv1.3");
        clientContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());

        return new TestTlsContext(serverContext, clientContext);
    }

    private record TestTlsContext(SSLContext serverContext, SSLContext clientContext) {
    }

    private static final class CapturingListener implements java.net.http.WebSocket.Listener {
        private final CompletableFuture<RealtimeEvent> response;
        private final StringBuilder incoming = new StringBuilder();

        private CapturingListener(CompletableFuture<RealtimeEvent> response) {
            this.response = response;
        }

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            incoming.append(data);
            if (last) {
                try {
                    response.complete(JsonCodec.fromJson(incoming.toString(), RealtimeEvent.class));
                } catch (Exception ex) {
                    response.completeExceptionally(ex);
                } finally {
                    incoming.setLength(0);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            response.completeExceptionally(error);
        }
    }
}
