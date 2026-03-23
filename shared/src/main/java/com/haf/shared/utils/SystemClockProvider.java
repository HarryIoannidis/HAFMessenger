package com.haf.shared.utils;

public final class SystemClockProvider implements ClockProvider {
    private static final SystemClockProvider INSTANCE = new SystemClockProvider();

    /**
     * Prevents external instantiation and enforces singleton access.
     */
    private SystemClockProvider() {}

    /**
     * Returns the singleton instance.
     *
     * @return the SystemClockProvider instance
     */
    public static SystemClockProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Returns the current time in milliseconds.
     *
     * @return current wall-clock time in epoch milliseconds
     */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
