package com.haf.server.core;

import com.haf.server.config.ServerConfig;
import com.haf.server.db.Contact;
import com.haf.server.db.Envelope;
import com.haf.server.db.FileUpload;
import com.haf.server.db.Attachment;
import com.haf.server.db.Session;
import com.haf.server.db.User;
import com.haf.server.handlers.EncryptedMessageValidator;
import com.haf.server.ingress.HttpIngressServer;
import com.haf.server.ingress.PresenceRegistry;
import com.haf.server.ingress.WebSocketIngressServer;
import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.MailboxRouter;
import com.haf.server.router.RateLimiterService;
import com.haf.server.security.JwtTokenService;
import com.haf.server.exceptions.StartupException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bootstraps and runs the server runtime and ingress components.
 */
public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);

    /**
     * Starts the HAF server.
     */
    public static void main(String[] args) {
        new Main().start();
    }

    /**
     * Starts the HAF server.
     */
    private void start() {
        ServerConfig config = ServerConfig.load();
        configureDbTlsTrustStore(config);
        runFlywayMigrations(config);

        HikariDataSource dataSource = createDataSource(config);
        ScheduledExecutorService scheduler = createScheduler();
        CountDownLatch shutdownLatch = new CountDownLatch(1);

        ScheduledFuture<?>[] metricsFutureRef = new ScheduledFuture<?>[1];
        ScheduledFuture<?>[] attachmentCleanupFutureRef = new ScheduledFuture<?>[1];
        try (dataSource; scheduler) {
            SSLContext sslContext = buildSslContext(config);
            MetricsRegistry metricsRegistry = new MetricsRegistry();
            AuditLogger auditLogger = AuditLogger.create(metricsRegistry);
            EncryptedMessageValidator validator = new EncryptedMessageValidator();

            Envelope envelopeDAO = new Envelope(dataSource, auditLogger);
            User userDAO = new User(dataSource, auditLogger);
            JwtTokenService jwtTokenService = new JwtTokenService(
                    config.getJwtSecret(),
                    "haf-server",
                    config.getJwtAccessTtlSeconds());
            Session sessionDAO = new Session(
                    dataSource,
                    auditLogger,
                    jwtTokenService,
                    config.getJwtRefreshTtlSeconds(),
                    config.getJwtAbsoluteTtlSeconds(),
                    config.getJwtIdleTtlSeconds());
            FileUpload fileUploadDAO = new FileUpload(dataSource);
            Attachment attachmentDAO = new Attachment(dataSource);
            Contact contactDAO = new Contact(dataSource);
            MailboxRouter mailboxRouter = new MailboxRouter(envelopeDAO, scheduler, auditLogger, metricsRegistry);
            RateLimiterService rateLimiter = new RateLimiterService(dataSource, auditLogger);
            PresenceRegistry presenceRegistry = new PresenceRegistry();

            HttpIngressServer httpServer = new HttpIngressServer(
                    config, sslContext, mailboxRouter, rateLimiter, auditLogger, metricsRegistry, validator, userDAO,
                    sessionDAO, fileUploadDAO, attachmentDAO, contactDAO, presenceRegistry);
            final WebSocketIngressServer webSocketServer = config.isDevMode()
                    ? new WebSocketIngressServer(
                            config, sslContext, mailboxRouter, rateLimiter, auditLogger, metricsRegistry, sessionDAO,
                            contactDAO, presenceRegistry)
                    : null;

            metricsFutureRef[0] = scheduler.scheduleAtFixedRate(
                    () -> auditLogger.logMetricsSnapshot(metricsRegistry.snapshot()),
                    60,
                    60,
                    TimeUnit.SECONDS);
            attachmentCleanupFutureRef[0] = scheduler.scheduleAtFixedRate(
                    () -> {
                        int deleted = attachmentDAO.deleteExpiredUploads();
                        if (deleted > 0) {
                            LOGGER.info("Attachment cleanup removed {} expired uploads", deleted);
                        }
                    },
                    300,
                    300,
                    TimeUnit.SECONDS);

            final ScheduledFuture<?> metricsFuture = metricsFutureRef[0];
            final ScheduledFuture<?> attachmentCleanupFuture = attachmentCleanupFutureRef[0];
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    LOGGER.info("Shutdown initiated");
                    if (webSocketServer != null) {
                        webSocketServer.stop();
                    }
                    httpServer.stop();
                    mailboxRouter.close();
                    metricsFuture.cancel(true);
                    attachmentCleanupFuture.cancel(true);
                    scheduler.shutdownNow();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Shutdown hook interrupted", e);
                } finally {
                    dataSource.close();
                    shutdownLatch.countDown();
                }
            }));

            mailboxRouter.start();
            if (webSocketServer != null) {
                webSocketServer.start();
                webSocketServer.awaitStartup(Duration.ofSeconds(10));
            }
            httpServer.start();

            if (webSocketServer != null) {
                LOGGER.info("Servers started on HTTP {} / WS {}", config.getHttpPort(), config.getWsPort());
            } else {
                LOGGER.info("Server started on HTTP {} (WS disabled: HAF_APP_IS_DEV=false)", config.getHttpPort());
            }

            awaitShutdown(shutdownLatch);
        } catch (Exception e) {
            LOGGER.error("Server startup failed", e);

            shutdownLatch.countDown();

            throw new StartupException("Startup failed", e);
        } finally {
            if (metricsFutureRef[0] != null) {
                metricsFutureRef[0].cancel(true);
            }
            if (attachmentCleanupFutureRef[0] != null) {
                attachmentCleanupFutureRef[0].cancel(true);
            }

            scheduler.shutdownNow();
            dataSource.close();
        }
    }

    /**
     * Runs Flyway database migrations.
     * 
     * @param config the server configuration.
     */
    private static void runFlywayMigrations(ServerConfig config) {
        Flyway flyway = Flyway.configure()
                .dataSource(config.getDbUrl(), config.getDbUser(), config.getDbPassword())
                .locations("filesystem:server/src/main/resources/db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
    }

    /**
     * Creates a HikariDataSource instance for database connection.
     * 
     * @param config the server configuration.
     */
    private static HikariDataSource createDataSource(ServerConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getDbUrl());
        hikariConfig.setUsername(config.getDbUser());
        hikariConfig.setPassword(config.getDbPassword());
        hikariConfig.setMaximumPoolSize(config.getDbPoolSize());
        hikariConfig.setMinimumIdle(Math.min(5, config.getDbPoolSize()));
        hikariConfig.setLeakDetectionThreshold(Duration.ofSeconds(60).toMillis());
        hikariConfig.setConnectionTimeout(Duration.ofSeconds(30).toMillis());
        hikariConfig.setIdleTimeout(Duration.ofMinutes(10).toMillis());
        hikariConfig.setMaxLifetime(Duration.ofMinutes(30).toMillis());
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Configures JVM truststore properties used by MySQL when strict TLS validation
     * is enabled.
     *
     * @param config server configuration
     */
    private static void configureDbTlsTrustStore(ServerConfig config) {
        Path truststorePath = config.getDbTruststorePath();
        char[] truststorePassword = config.getDbTruststorePassword();
        if (truststorePath == null || truststorePassword == null || truststorePassword.length == 0) {
            return;
        }

        System.setProperty("javax.net.ssl.trustStore", truststorePath.toAbsolutePath().toString());
        System.setProperty("javax.net.ssl.trustStoreType", config.getDbTruststoreType());
        System.setProperty("javax.net.ssl.trustStorePassword", new String(truststorePassword));
    }

    /**
     * Builds an SSLContext for the server.
     * 
     * @param config the server configuration.
     */
    private static SSLContext buildSslContext(ServerConfig config) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(config.getTlsKeystorePath())) {
            keyStore.load(in, config.getTlsKeystorePassword());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, config.getTlsKeystorePassword());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // Hardening: Enforce TLS 1.3 only, no fallback
        SSLParameters sslParams = sslContext.getDefaultSSLParameters();
        sslParams.setProtocols(new String[] { "TLSv1.3" });
        sslParams.setCipherSuites(new String[] {
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256"
        });

        return sslContext;
    }

    /**
     * Creates a ScheduledExecutorService for the server.
     */
    private static ScheduledExecutorService createScheduler() {
        AtomicInteger idx = new AtomicInteger();

        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("haf-scheduler-" + idx.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        return Executors.newScheduledThreadPool(2, factory);
    }

    /**
     * Blocks the main thread until shutdown is requested.
     *
     * @param shutdownLatch latch that is counted down by shutdown hooks/paths
     */
    private static void awaitShutdown(CountDownLatch shutdownLatch) {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Shutdown latch interrupted", e);
        }
    }
}
