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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bootstraps and runs the server runtime and ingress components.
 */
public final class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private static final String FLYWAY_MIGRATION_LOCATION = "classpath:db/migration";

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
        AtomicBoolean shutdownOnce = new AtomicBoolean(false);

        ScheduledFuture<?> metricsFuture = null;
        ScheduledFuture<?> attachmentCleanupFuture = null;
        Thread shutdownHook = null;
        HttpIngressServer httpServer = null;
        MailboxRouter mailboxRouter = null;
        try {
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
            mailboxRouter = new MailboxRouter(envelopeDAO, scheduler, auditLogger, metricsRegistry);
            RateLimiterService rateLimiter = new RateLimiterService(dataSource, auditLogger);

            httpServer = new HttpIngressServer(
                    config, sslContext, mailboxRouter, rateLimiter, auditLogger, metricsRegistry, validator, userDAO,
                    sessionDAO, fileUploadDAO, attachmentDAO, contactDAO);

            metricsFuture = scheduler.scheduleAtFixedRate(
                    () -> auditLogger.logMetricsSnapshot(metricsRegistry.snapshot()),
                    60,
                    60,
                    TimeUnit.SECONDS);
            attachmentCleanupFuture = scheduler.scheduleAtFixedRate(
                    () -> {
                        int deleted = attachmentDAO.deleteExpiredUploads();
                        if (deleted > 0) {
                            LOGGER.info("Attachment cleanup removed {} expired uploads", deleted);
                        }
                    },
                    300,
                    300,
                    TimeUnit.SECONDS);

            final MailboxRouter finalMailboxRouter = mailboxRouter;
            final HttpIngressServer finalHttpServer = httpServer;
            final ScheduledFuture<?> finalMetricsFuture = metricsFuture;
            final ScheduledFuture<?> finalAttachmentCleanupFuture = attachmentCleanupFuture;
            shutdownHook = new Thread(
                    () -> shutdownRuntime(
                            "Shutdown initiated",
                            shutdownOnce,
                            shutdownLatch,
                            finalHttpServer,
                            finalMailboxRouter,
                            finalMetricsFuture,
                            finalAttachmentCleanupFuture,
                            scheduler,
                            dataSource),
                    "haf-shutdown-hook");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            mailboxRouter.start();
            httpServer.start();
            LOGGER.info("Server started on HTTP {}", config.getHttpPort());

            awaitShutdown(shutdownLatch);
        } catch (Exception e) {
            LOGGER.error("Server startup failed", e);

            throw new StartupException("Startup failed", e);
        } finally {
            removeShutdownHookSafely(shutdownHook);
            shutdownRuntime(
                    "Finalizing server resources",
                    shutdownOnce,
                    shutdownLatch,
                    httpServer,
                    mailboxRouter,
                    metricsFuture,
                    attachmentCleanupFuture,
                    scheduler,
                    dataSource);
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
                .locations(FLYWAY_MIGRATION_LOCATION)
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
    }

    /**
     * Creates a HikariDataSource instance for database connection.
     * 
     * @param config the server configuration.
     * @return configured datasource
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
     * Performs one-time runtime shutdown and ensures the shutdown latch is
     * released.
     *
     * @param reason                    log context for the shutdown path
     * @param shutdownOnce              single-execution guard
     * @param shutdownLatch             latch used by the main thread
     * @param httpServer                HTTP ingress server instance
     * @param mailboxRouter             mailbox router instance
     * @param metricsFuture             periodic metrics task
     * @param attachmentCleanupFuture   periodic attachment cleanup task
     * @param scheduler                 shared scheduler
     * @param dataSource                database datasource
     */
    private static void shutdownRuntime(
            String reason,
            AtomicBoolean shutdownOnce,
            CountDownLatch shutdownLatch,
            HttpIngressServer httpServer,
            MailboxRouter mailboxRouter,
            ScheduledFuture<?> metricsFuture,
            ScheduledFuture<?> attachmentCleanupFuture,
            ScheduledExecutorService scheduler,
            HikariDataSource dataSource) {
        if (!shutdownOnce.compareAndSet(false, true)) {
            shutdownLatch.countDown();
            return;
        }

        LOGGER.info(reason);
        if (httpServer != null) {
            httpServer.stop();
        }
        if (mailboxRouter != null) {
            mailboxRouter.close();
        }
        cancelScheduledTask(metricsFuture);
        cancelScheduledTask(attachmentCleanupFuture);
        scheduler.shutdownNow();
        dataSource.close();
        shutdownLatch.countDown();
    }

    /**
     * Cancels a scheduled task if it is present.
     *
     * @param task scheduled task reference
     */
    private static void cancelScheduledTask(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(true);
        }
    }

    /**
     * Removes a registered shutdown hook when the JVM is not already shutting
     * down.
     *
     * @param shutdownHook hook thread previously registered by this runtime
     */
    private static void removeShutdownHookSafely(Thread shutdownHook) {
        if (shutdownHook == null) {
            return;
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            LOGGER.debug("JVM shutdown already in progress; skipping shutdown hook removal.");
        }
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
