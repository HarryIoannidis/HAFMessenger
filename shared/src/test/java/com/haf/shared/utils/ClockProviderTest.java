package com.haf.shared.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClockProviderTest {

    @Test
    void systemClockProvider_returns_current_time() {
        ClockProvider clock = SystemClockProvider.getInstance();
        long time1 = clock.currentTimeMillis();
        long time2 = time1;
        long deadlineNanos = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(1);

        while (time2 - time1 < 10 && System.nanoTime() < deadlineNanos) {
            java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L); // 1ms
            if (Thread.interrupted()) {
                fail("Interrupted while waiting for time to advance");
            }
            time2 = clock.currentTimeMillis();
        }
        
        assertTrue(time2 > time1, "Time should advance");
        assertTrue(time2 - time1 >= 10, "Time difference should be at least 10ms");
    }

    @Test
    void fixedClockProvider_returns_fixed_time() {
        long fixedTime = 1000000L;
        ClockProvider clock = new FixedClockProvider(fixedTime);
        
        assertEquals(fixedTime, clock.currentTimeMillis());
        assertEquals(fixedTime, clock.currentTimeMillis());
        assertEquals(fixedTime, clock.currentTimeMillis());
    }

    @Test
    void fixedClockProvider_getFixedTimeMillis() {
        long fixedTime = 2000000L;
        FixedClockProvider clock = new FixedClockProvider(fixedTime);
        
        assertEquals(fixedTime, clock.getFixedTimeMillis());
        assertEquals(fixedTime, clock.currentTimeMillis());
    }

    @Test
    void systemClockProvider_is_singleton() {
        ClockProvider clock1 = SystemClockProvider.getInstance();
        ClockProvider clock2 = SystemClockProvider.getInstance();
        
        assertSame(clock1, clock2, "SystemClockProvider should be a singleton");
    }
}

