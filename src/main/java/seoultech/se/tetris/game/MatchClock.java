package seoultech.se.tetris.game;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Monotonic deadline clock, independent from Swing repaint and timer frequency. */
public final class MatchClock {
    private final long durationNanos;
    private final LongSupplier nanoTime;
    private long deadlineNanos;
    private boolean started;

    public MatchClock(Duration duration) {
        this(duration, System::nanoTime);
    }

    public MatchClock(Duration duration, LongSupplier nanoTime) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        durationNanos = duration.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public void start() {
        if (started) return;
        started = true;
        deadlineNanos = Math.addExact(nanoTime.getAsLong(), durationNanos);
    }

    public boolean isExpired() {
        return started && remainingNanos() == 0;
    }

    public long remainingSeconds() {
        long remaining = remainingNanos();
        return remaining == 0 ? 0 : Math.addExact(remaining, 999_999_999L) / 1_000_000_000L;
    }

    public long remainingNanos() {
        if (!started) return durationNanos;
        return Math.max(0, deadlineNanos - nanoTime.getAsLong());
    }
}
