package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class MatchClockTest {
    @Test
    void deadlineDoesNotDependOnUpdateFrequencyAndRoundsDisplayUp() {
        AtomicLong now = new AtomicLong();
        MatchClock clock = new MatchClock(Duration.ofSeconds(3), now::get);

        assertEquals(3, clock.remainingSeconds());
        clock.start();
        now.set(2_001_000_000L);
        assertEquals(1, clock.remainingSeconds());
        assertFalse(clock.isExpired());
        now.set(3_000_000_000L);
        assertEquals(0, clock.remainingSeconds());
        assertTrue(clock.isExpired());
    }

    @Test
    void startIsIdempotentAndDurationMustBePositive() {
        AtomicLong now = new AtomicLong(10);
        MatchClock clock = new MatchClock(Duration.ofSeconds(1), now::get);
        clock.start();
        now.set(500_000_010L);
        clock.start();
        assertEquals(1, clock.remainingSeconds());
        assertThrows(IllegalArgumentException.class,
            () -> new MatchClock(Duration.ZERO, now::get));
    }
}
