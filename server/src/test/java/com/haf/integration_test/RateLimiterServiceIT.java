package com.haf.integration_test;

import com.haf.server.metrics.AuditLogger;
import com.haf.server.metrics.MetricsRegistry;
import com.haf.server.router.RateLimiterService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Disabled("Requires Docker/Testcontainers; disabled on local dev without Docker")
@Testcontainers
class RateLimiterServiceIT {

    @Container
    @SuppressWarnings("resource")
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    private HikariDataSource dataSource;
    private RateLimiterService service;
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(mysql.getJdbcUrl());
        config.setUsername(mysql.getUsername());
        config.setPassword(mysql.getPassword());
        config.setMaximumPoolSize(5);
        dataSource = new HikariDataSource(config);

        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
        flyway.migrate();

        MetricsRegistry metricsRegistry = new MetricsRegistry();
        auditLogger = AuditLogger.create(metricsRegistry);
        service = new RateLimiterService(dataSource, auditLogger);
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void check_and_consume_allows_under_threshold() {
        String userId = "user-" + System.currentTimeMillis();

        for (int i = 0; i < 50; i++) {
            RateLimiterService.RateLimitDecision decision = service.checkAndConsume("req-" + i, userId);
            assertTrue(decision.allowed(), "Should allow message " + i);
        }
    }

    @Test
    void check_and_consume_rate_limits_over_threshold() {
        String userId = "user-" + System.currentTimeMillis();

        // Send 100 messages (threshold)
        for (int i = 0; i < 100; i++) {
            RateLimiterService.RateLimitDecision decision = service.checkAndConsume("req-" + i, userId);
            assertTrue(decision.allowed(), "Should allow message " + i);
        }

        // 101st should be rate limited
        RateLimiterService.RateLimitDecision decision = service.checkAndConsume("req-101", userId);
        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() > 0);
    }

    @Test
    void check_and_consume_resets_window_after_time() throws InterruptedException {
        String userId = "user-" + System.currentTimeMillis();

        // Send messages to fill window
        for (int i = 0; i < 50; i++) {
            service.checkAndConsume("req-" + i, userId);
        }

        // Wait for window to expire (61 seconds)
        Thread.sleep(61000);

        // Should be able to send again
        RateLimiterService.RateLimitDecision decision = service.checkAndConsume("req-new", userId);
        assertTrue(decision.allowed());
    }
}

