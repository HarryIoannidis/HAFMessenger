package com.haf.server.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal in-memory metrics registry used for observability.
 */
public final class MetricsRegistry {

    private final AtomicLong ingressCount = new AtomicLong();
    private final AtomicLong rejectCount = new AtomicLong();
    private final AtomicLong rateLimitRejectCount = new AtomicLong();
    private final AtomicLong queueDepth = new AtomicLong();
    private final AtomicLong totalDeliveryLatencyMs = new AtomicLong();
    private final AtomicLong deliveredCount = new AtomicLong();

    /**
     * Increments the number of ingressed messages.
     */
    public void incrementIngress() {
        ingressCount.incrementAndGet();
    }

    /**
     * Increments the number of rejected messages.
     */
    public void incrementRejects() {
        rejectCount.incrementAndGet();
    }

    /**
     * Increments the number of rejected messages due to rate limiting.
     */
    public void incrementRateLimitRejects() {
        rateLimitRejectCount.incrementAndGet();
    }

    /**
     * Increments the number of messages in the queue.
     */
    public void increaseQueueDepth() {
        queueDepth.incrementAndGet();
    }

    /**
     * Decrements the number of messages in the queue.
     * @param count the number of messages to remove from the queue.
     */
    public void decreaseQueueDepth(long count) {
        if (count <= 0) {
            return;
        }
        queueDepth.updateAndGet(current -> Math.max(0, current - count));
    }

    /**
     * Gets a snapshot of the current metrics.
     * @return a snapshot of the current metrics.
     */
    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
                ingressCount.get(),
                rejectCount.get(),
                rateLimitRejectCount.get(),
                queueDepth.get(),
                getAverageDeliveryLatencyMs(),
                deliveredCount.get()
        );
    }


    /**
     * Records the latency of message delivery and increments the delivered message count.
     * @param latencyMs the latency in milliseconds.
     */
    public void recordDeliveryLatency(long latencyMs) {
        totalDeliveryLatencyMs.addAndGet(latencyMs);
        deliveredCount.incrementAndGet();
    }

    /**
     * Gets the average latency of message delivery.
     * @return the average latency in milliseconds.
     */
    public double getAverageDeliveryLatencyMs() {
        long delivered = deliveredCount.get();
        return delivered == 0 ? 0.0 : (double) totalDeliveryLatencyMs.get() / delivered;
    }

    /**
     * Represents a snapshot of the current metrics.
     */
    public record MetricsSnapshot(long ingressCount,
                                  long rejectCount,
                                  long rateLimitRejectCount,
                                  long queueDepth,
                                  double avgDeliveryLatencyMs,
                                  long deliveredCount) {}
}

