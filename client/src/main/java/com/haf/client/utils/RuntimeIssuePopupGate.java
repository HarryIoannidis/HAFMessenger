package com.haf.client.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deduplicates popup rendering for runtime issues within a cooldown window.
 */
public final class RuntimeIssuePopupGate {

    private final long cooldownMs;
    private final ConcurrentMap<String, Long> lastShownByKey = new ConcurrentHashMap<>();

    /**
     * Creates a popup dedupe gate.
     *
     * @param cooldownMs minimum delay between two popups with the same key
     */
    public RuntimeIssuePopupGate(long cooldownMs) {
        this.cooldownMs = Math.max(0L, cooldownMs);
    }

    /**
     * Checks whether a popup should be shown now for the given key.
     *
     * @param dedupeKey popup dedupe key
     * @return {@code true} when popup should be displayed
     */
    public boolean shouldShow(String dedupeKey) {
        return shouldShow(dedupeKey, System.currentTimeMillis());
    }

    /**
     * Checks whether a popup should be shown for the given key using explicit
     * timestamp.
     *
     * @param dedupeKey popup dedupe key
     * @param nowEpochMs current timestamp in milliseconds
     * @return {@code true} when popup should be displayed
     */
    public boolean shouldShow(String dedupeKey, long nowEpochMs) {
        String normalizedKey = normalizeKey(dedupeKey);
        AtomicBoolean shouldShow = new AtomicBoolean(false);
        lastShownByKey.compute(normalizedKey, (key, previous) -> {
            if (previous != null && nowEpochMs - previous < cooldownMs) {
                shouldShow.set(false);
                return previous;
            }
            shouldShow.set(true);
            return nowEpochMs;
        });
        return shouldShow.get();
    }

    /**
     * Normalizes the dedupe key used for popup rate-limiting.
     *
     * @param dedupeKey caller-provided key
     * @return normalized key value with default fallback
     */
    private static String normalizeKey(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return "runtime-issue";
        }
        return dedupeKey.trim();
    }
}
