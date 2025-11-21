package com.haf.server.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MetricsRegistryTest {

    private MetricsRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new MetricsRegistry();
    }

    @Test
    void increment_ingress_increments_count() {
        assertEquals(0, registry.snapshot().ingressCount());
        registry.incrementIngress();
        assertEquals(1, registry.snapshot().ingressCount());
        registry.incrementIngress();
        assertEquals(2, registry.snapshot().ingressCount());
    }

    @Test
    void increment_rejects_increments_count() {
        assertEquals(0, registry.snapshot().rejectCount());
        registry.incrementRejects();
        assertEquals(1, registry.snapshot().rejectCount());
        registry.incrementRejects();
        assertEquals(2, registry.snapshot().rejectCount());
    }

    @Test
    void increment_rateLimitRejects_increments_count() {
        assertEquals(0, registry.snapshot().rateLimitRejectCount());
        registry.incrementRateLimitRejects();
        assertEquals(1, registry.snapshot().rateLimitRejectCount());
        registry.incrementRateLimitRejects();
        assertEquals(2, registry.snapshot().rateLimitRejectCount());
    }

    @Test
    void increase_queueDepth_increments_count() {
        assertEquals(0, registry.snapshot().queueDepth());
        registry.increaseQueueDepth();
        assertEquals(1, registry.snapshot().queueDepth());
        registry.increaseQueueDepth();
        assertEquals(2, registry.snapshot().queueDepth());
    }

    @Test
    void decrease_queueDepth_decrements_by_count() {
        registry.increaseQueueDepth();
        registry.increaseQueueDepth();
        registry.increaseQueueDepth();
        assertEquals(3, registry.snapshot().queueDepth());

        registry.decreaseQueueDepth(2);
        assertEquals(1, registry.snapshot().queueDepth());
    }

    @Test
    void decrease_queue_depth_does_not_go_below_zero() {
        registry.increaseQueueDepth();
        assertEquals(1, registry.snapshot().queueDepth());

        registry.decreaseQueueDepth(5);
        assertEquals(0, registry.snapshot().queueDepth());
    }

    @Test
    void decrease_queue_depth_ignores_negative_count() {
        registry.increaseQueueDepth();
        assertEquals(1, registry.snapshot().queueDepth());

        registry.decreaseQueueDepth(-5);
        assertEquals(1, registry.snapshot().queueDepth());
    }

    @Test
    void decrease_queue_depth_ignores_zero_count() {
        registry.increaseQueueDepth();
        assertEquals(1, registry.snapshot().queueDepth());

        registry.decreaseQueueDepth(0);
        assertEquals(1, registry.snapshot().queueDepth());
    }

    @Test
    void snapshot_returns_current_values() {
        registry.incrementIngress();
        registry.incrementIngress();
        registry.incrementRejects();
        registry.incrementRateLimitRejects();
        registry.increaseQueueDepth();
        registry.increaseQueueDepth();
        registry.recordDeliveryLatency(100);
        registry.recordDeliveryLatency(200);

        MetricsRegistry.MetricsSnapshot snapshot = registry.snapshot();
        assertEquals(2, snapshot.ingressCount());
        assertEquals(1, snapshot.rejectCount());
        assertEquals(1, snapshot.rateLimitRejectCount());
        assertEquals(2, snapshot.queueDepth());
        assertEquals(150.0, snapshot.avgDeliveryLatencyMs()); // (100+200)/2
        assertEquals(2, snapshot.deliveredCount());
    }

    @Test
    void snapshot_is_immutable_copy() {
        registry.incrementIngress();
        MetricsRegistry.MetricsSnapshot snapshot1 = registry.snapshot();
        registry.incrementIngress();
        MetricsRegistry.MetricsSnapshot snapshot2 = registry.snapshot();

        assertEquals(1, snapshot1.ingressCount());
        assertEquals(2, snapshot2.ingressCount());
    }

    @Test
    void metrics_snapshot_record_has_all_fields() {
        MetricsRegistry.MetricsSnapshot snapshot = new MetricsRegistry.MetricsSnapshot(10, 5, 3, 7, 125.5, 20);

        assertEquals(10, snapshot.ingressCount());
        assertEquals(5, snapshot.rejectCount());
        assertEquals(3, snapshot.rateLimitRejectCount());
        assertEquals(7, snapshot.queueDepth());
        assertEquals(125.5, snapshot.avgDeliveryLatencyMs());
        assertEquals(20, snapshot.deliveredCount());
    }

    @Test
    void record_deliveryLatency_calculates_average() {
        assertEquals(0.0, registry.snapshot().avgDeliveryLatencyMs());
        assertEquals(0, registry.snapshot().deliveredCount());

        registry.recordDeliveryLatency(100);
        assertEquals(100.0, registry.snapshot().avgDeliveryLatencyMs());
        assertEquals(1, registry.snapshot().deliveredCount());

        registry.recordDeliveryLatency(200);
        assertEquals(150.0, registry.snapshot().avgDeliveryLatencyMs()); // (100+200)/2
        assertEquals(2, registry.snapshot().deliveredCount());

        registry.recordDeliveryLatency(300);
        assertEquals(200.0, registry.snapshot().avgDeliveryLatencyMs()); // (100+200+300)/3
        assertEquals(3, registry.snapshot().deliveredCount());
    }

    @Test
    void record_delivery_latency_handles_zero_delivered() {
        assertEquals(0.0, registry.snapshot().avgDeliveryLatencyMs());
    }

    @Test
    void get_average_delivery_latencyMs_returns_zero_when_no_deliveries() {
        assertEquals(0.0, registry.getAverageDeliveryLatencyMs());
    }

    @Test
    void get_average_delivery_latencyMs_returns_correct_average() {
        registry.recordDeliveryLatency(50);
        registry.recordDeliveryLatency(150);
        assertEquals(100.0, registry.getAverageDeliveryLatencyMs());
    }

}

