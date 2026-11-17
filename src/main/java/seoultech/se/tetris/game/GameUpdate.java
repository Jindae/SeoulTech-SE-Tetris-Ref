package seoultech.se.tetris.game;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record GameUpdate(
        GameSnapshot snapshot,
        Optional<LockResolution> lockResolution,
        Optional<GameEnd> gameEnd,
        OptionalLong nextWakeupNanos) {
    public GameUpdate {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(lockResolution, "lockResolution");
        Objects.requireNonNull(gameEnd, "gameEnd");
        Objects.requireNonNull(nextWakeupNanos, "nextWakeupNanos");
    }
}
