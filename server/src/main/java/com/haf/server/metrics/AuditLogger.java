package com.haf.server.metrics;

import com.haf.server.metrics.MetricsRegistry.MetricsSnapshot;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StringMapMessage;
import java.time.Instant;
import java.util.Map;

public final class AuditLogger {

    private static final Logger LOGGER = LogManager.getLogger("AuditLogger");
    private final MetricsRegistry metricsRegistry;

    /**
     * Creates an AuditLogger with a MetricsRegistry.
     *
     * @param metricsRegistry the MetricsRegistry
     */
    private AuditLogger(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    /**
     * Creates an AuditLogger with a default MetricsRegistry.
     *
     * @return the AuditLogger
     */
    public static AuditLogger create(MetricsRegistry metricsRegistry) {
        return new AuditLogger(metricsRegistry);
    }

    /**
     * Logs an ingress accepted event.
     *
     * @param requestId   the ID of the request.
     * @param userId      the ID of the user.
     * @param recipientId the ID of the recipient.
     * @param status      the HTTP status code.
     * @param latencyMs   the latency in milliseconds.
     */
    public void logIngressAccepted(String requestId, String userId, String recipientId, int status, long latencyMs) {
        log(Level.INFO, "send_message", requestId, userId, Map.of(
                "recipientId", recipientId,
                "status", status,
                "latencyMs", latencyMs));
        metricsRegistry.incrementIngress();
    }

    /**
     * Logs an ingress rejected event.
     *
     * @param requestId the ID of the request.
     * @param userId    the ID of the user.
     * @param reason    the reason for the rejection.
     * @param status    the HTTP status code.
     */
    public void logIngressRejected(String requestId, String userId, String reason, int status) {
        log(Level.WARN, "send_message_rejected", requestId, userId, Map.of(
                "status", status,
                "reason", reason));
        metricsRegistry.incrementRejects();
    }

    /**
     * Logs a rate limit rejection event.
     *
     * @param requestId         the ID of the request.
     * @param userId            the ID of the user.
     * @param retryAfterSeconds the number of seconds to wait before retrying.
     */
    public void logRateLimit(String requestId, String userId, long retryAfterSeconds) {
        log(Level.WARN, "rate_limit", requestId, userId, Map.of(
                "status", 429,
                "retryAfterSeconds", retryAfterSeconds));
        metricsRegistry.incrementRateLimitRejects();
    }

    /**
     * Logs a validation failure event.
     *
     * @param requestId   the ID of the request.
     * @param userId      the ID of the user.
     * @param recipientId the ID of the recipient.
     * @param reason      the reason for the failure.
     */
    public void logValidationFailure(String requestId, String userId, String recipientId, String reason) {
        log(Level.WARN, "validation_failed", requestId, userId, Map.of(
                "recipientId", recipientId,
                "reason", reason));
    }

    /**
     * Logs a successful registration event.
     *
     * @param requestId the ID of the request.
     * @param userId    the ID of the newly created user.
     * @param email     the email of the registered user.
     */
    public void logRegistration(String requestId, String userId, String email) {
        log(Level.INFO, "user_registered", requestId, userId, Map.of(
                "email", email));
    }

    /**
     * Logs a successful login event.
     *
     * @param requestId the ID of the request.
     * @param userId    the ID of the authenticated user.
     * @param email     the email of the user.
     */
    public void logLogin(String requestId, String userId, String email) {
        log(Level.INFO, "user_login", requestId, userId, Map.of(
                "email", email));
    }

    /**
     * Logs a cleanup event.
     *
     * @param deleted    the number of messages deleted.
     * @param durationMs the duration of the cleanup in milliseconds.
     */
    public void logCleanup(int deleted, long durationMs) {
        log(Level.INFO, "ttl_cleanup", null, "system", Map.of(
                "deleted", deleted,
                "durationMs", durationMs));
    }

    /**
     * Logs a metrics snapshot event.
     *
     * @param snapshot the metrics snapshot.
     */
    public void logMetricsSnapshot(MetricsSnapshot snapshot) {
        log(Level.INFO, "metrics", null, "system", Map.of(
                "ingressCount", snapshot.ingressCount(),
                "rejectCount", snapshot.rejectCount(),
                "rateLimitRejectCount", snapshot.rateLimitRejectCount(),
                "queueDepth", snapshot.queueDepth(),
                "avgDeliveryLatencyMs", snapshot.avgDeliveryLatencyMs(),
                "deliveredCount", snapshot.deliveredCount()));
    }

    /**
     * Logs an error event.
     *
     * @param action    the action that caused the error.
     * @param requestId the ID of the request.
     * @param userId    the ID of the user.
     * @param error     the error.
     */
    public void logError(String action, String requestId, String userId, Throwable error) {
        StringMapMessage message = baseMessage(action, requestId, userId);
        LOGGER.error(message, error);
    }

    /**
     * Logs an audit event.
     *
     * @param level       the log level.
     * @param action      the action that was performed.
     * @param requestId   the ID of the request.
     * @param userId      the ID of the user.
     * @param extraFields extra fields to be logged.
     */
    private void log(Level level, String action, String requestId, String userId, Map<String, ?> extraFields) {
        StringMapMessage message = baseMessage(action, requestId, userId);

        if (extraFields != null) {
            extraFields.forEach((k, v) -> {
                if (v != null) {
                    message.put(k, String.valueOf(v));
                }
            });
        }

        LOGGER.log(level, message);
    }

    /**
     * Creates a base message for an audit event.
     *
     * @param action    the action that was performed.
     * @param requestId the ID of the request.
     * @param userId    the ID of the user.
     * @return the base message.
     */
    private StringMapMessage baseMessage(String action, String requestId, String userId) {
        StringMapMessage message = new StringMapMessage();
        message.put("timestamp", Instant.now().toString());
        message.put("action", action);

        if (requestId != null) {
            message.put("requestId", requestId);
        }

        if (userId != null) {
            message.put("userId", userId);
        }

        return message;
    }
}
