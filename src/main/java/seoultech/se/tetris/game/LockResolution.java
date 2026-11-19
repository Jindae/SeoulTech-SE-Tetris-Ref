package seoultech.se.tetris.game;

import java.util.List;
import java.util.Optional;
import seoultech.se.tetris.attack.AttackEvent;

/** Stable result after a lock is resolved and before the queued block is promoted. */
public record LockResolution(
        long turnId,
        List<ClearBatch> clearBatches,
        Optional<AttackEvent> attack,
        int score,
        int clearedLines,
        int level) {
    public LockResolution {
        clearBatches = List.copyOf(clearBatches);
        attack = java.util.Objects.requireNonNull(attack, "attack");
    }
}
