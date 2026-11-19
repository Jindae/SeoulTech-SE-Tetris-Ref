package seoultech.se.tetris.game;

import java.util.List;

/** Stable result after a lock is resolved and before the queued block is promoted. */
public record LockResolution(
        long turnId,
        List<ClearBatch> clearBatches,
        int score,
        int clearedLines,
        int level) {
    public LockResolution {
        clearBatches = List.copyOf(clearBatches);
    }
}
