package com.haf.shared.utils;

/**
 * Provider interface for getting current time, allowing dependency injection for deterministic testing.
 */
public interface ClockProvider {
    /**
     * Returns the current time in milliseconds since epoch.
     *
     * @return current time in milliseconds
     */
    long currentTimeMillis();
}

