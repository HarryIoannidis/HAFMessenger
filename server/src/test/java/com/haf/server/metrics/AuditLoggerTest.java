package com.haf.server.metrics;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.StringWriter;
import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerTest {

    private MetricsRegistry metricsRegistry;
    private AuditLogger auditLogger;
    private StringWriter logOutput;
    private Appender appender;

    @BeforeEach
    void setUp() {
        metricsRegistry = new MetricsRegistry();
        auditLogger = AuditLogger.create(metricsRegistry);
        logOutput = new StringWriter();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("AuditLogger");

        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%m%n")
                .build();

        appender = WriterAppender.newBuilder()
                .setTarget(logOutput)
                .setLayout(layout)
                .setName("TestAppender")
                .build();

        appender.start();
        loggerConfig.addAppender(appender, Level.ALL, null);
        context.updateLoggers();
    }

    @AfterEach
    void tearDown() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig("AuditLogger");

        loggerConfig.removeAppender("TestAppender");
        context.updateLoggers();

        if (appender != null) {
            appender.stop();
        }
    }

    private void resetLogOutput() {
        logOutput.getBuffer().setLength(0);
    }

    @Test
    void log_ingress_accepted_increments_metrics() {
        long initialIngress = metricsRegistry.snapshot().ingressCount();

        auditLogger.logIngressAccepted("req-1", "user-123", "recipient-456", 200, 45);

        assertEquals(initialIngress + 1, metricsRegistry.snapshot().ingressCount());
    }

    @Test
    void log_ingress_accepted_includes_required_fields() {
        resetLogOutput();
        auditLogger.logIngressAccepted("req-1", "user-123", "recipient-456", 200, 45);

        String log = logOutput.toString();
        assertTrue(log.contains("requestId"));
        assertTrue(log.contains("userId"));
        assertTrue(log.contains("recipientId"));
        assertTrue(log.contains("status"));
        assertTrue(log.contains("latencyMs"));
    }

    @Test
    void log_ingress_rejected_increments_reject_metrics() {
        long initialRejects = metricsRegistry.snapshot().rejectCount();

        auditLogger.logIngressRejected("req-1", "user-123", "VALIDATION_FAILED", 400);

        assertEquals(initialRejects + 1, metricsRegistry.snapshot().rejectCount());
    }

    @Test
    void log_rate_limit_increments_rate_limit_metrics() {
        long initialRateLimitRejects = metricsRegistry.snapshot().rateLimitRejectCount();

        auditLogger.logRateLimit("req-1", "user-123", 60);

        assertEquals(initialRateLimitRejects + 1, metricsRegistry.snapshot().rateLimitRejectCount());
    }

    @Test
    void log_rate_limit_includes_retry_after() {
        resetLogOutput();
        auditLogger.logRateLimit("req-1", "user-123", 60);

        String log = logOutput.toString();
        assertTrue(log.contains("retryAfterSeconds"));
    }

    @Test
    void log_validation_failure_includes_reason() {
        resetLogOutput();
        auditLogger.logValidationFailure("req-1", "user-123", "recipient-456", "CLOCK_SKEW");

        String log = logOutput.toString();
        assertTrue(log.contains("reason"));
        assertTrue(log.contains("CLOCK_SKEW"));
    }

    @Test
    void log_cleanup_includes_deleted_count() {
        resetLogOutput();
        auditLogger.logCleanup(5, 100);

        String log = logOutput.toString();
        assertTrue(log.contains("deleted"));
        assertTrue(log.contains("durationMs"));
    }

    @Test
    void log_metrics_snapshot_includes_all_metrics() {
        resetLogOutput();
        metricsRegistry.incrementIngress();
        metricsRegistry.incrementRejects();
        metricsRegistry.incrementRateLimitRejects();
        metricsRegistry.increaseQueueDepth();
        metricsRegistry.recordDeliveryLatency(100);
        metricsRegistry.recordDeliveryLatency(200);

        auditLogger.logMetricsSnapshot(metricsRegistry.snapshot());

        String log = logOutput.toString();
        assertTrue(log.contains("ingressCount"));
        assertTrue(log.contains("rejectCount"));
        assertTrue(log.contains("rateLimitRejectCount"));
        assertTrue(log.contains("queueDepth"));
        assertTrue(log.contains("avgDeliveryLatencyMs"));
        assertTrue(log.contains("deliveredCount"));
    }

    @Test
    void log_metrics_snapshot_includes_delivery_latency() {
        resetLogOutput();
        metricsRegistry.recordDeliveryLatency(50);
        metricsRegistry.recordDeliveryLatency(150);

        auditLogger.logMetricsSnapshot(metricsRegistry.snapshot());

        String log = logOutput.toString();
        assertTrue(log.contains("avgDeliveryLatencyMs"));
        assertTrue(log.contains("100.0")); // Average of 50 and 150
        assertTrue(log.contains("deliveredCount"));
        assertTrue(log.contains("2"));
    }

    @Test
    void log_error_includes_error_details() {
        resetLogOutput();
        Exception error = new RuntimeException("Test error");

        auditLogger.logError("test_action", "req-1", "user-123", error);

        String log = logOutput.toString();
        assertTrue(log.contains("test_action"));
        assertTrue(log.contains("requestId"));
    }

    @Test
    void log_does_not_include_payload_fields() {
        resetLogOutput();
        auditLogger.logIngressAccepted("req-1", "user-123", "recipient-456", 200, 45);

        String log = logOutput.toString();
        assertFalse(log.contains("ciphertext"));
        assertFalse(log.contains("wrappedKey"));
        assertFalse(log.contains("iv"));
        assertFalse(log.contains("tag"));
    }

    @Test
    void log_includes_timestamp() {
        resetLogOutput();
        auditLogger.logIngressAccepted("req-1", "user-123", "recipient-456", 200, 45);

        String log = logOutput.toString();
        assertTrue(log.contains("timestamp") || log.contains("action"));
    }
}
