package com.haf.shared.utils;

/**
 * Test implementation of ClockProvider that returns a fixed timestamp.
 * Useful for deterministic testing, especially for message expiry checks.
 */
public final class FixedClockProvider implements ClockProvider {
    private final long fixedTimeMillis;

    /**
     * Creates a FixedClockProvider with the specified fixed time.
     *
     * @param fixedTimeMillis the fixed time in milliseconds since epoch
     */
    public FixedClockProvider(long fixedTimeMillis) {
        this.fixedTimeMillis = fixedTimeMillis;
    }

    /**
     * Returns the fixed timestamp configured for this provider.
     *
     * @return fixed epoch milliseconds value
     */
    @Override
    public long currentTimeMillis() {
        return fixedTimeMillis;
    }

    /**
     * Returns the fixed time value.
     *
     * @return the fixed time in milliseconds
     */
    public long getFixedTimeMillis() {
        return fixedTimeMillis;
    }
}
