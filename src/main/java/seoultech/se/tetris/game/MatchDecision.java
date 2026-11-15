package seoultech.se.tetris.game;

import java.util.Objects;

/** Immutable final decision that can be serialized by the future P2P layer. */
public record MatchDecision(
    MatchOutcome outcome,
    MatchEndReason reason,
    int localScore,
    int remoteScore
) {
    public MatchDecision {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        if (localScore < 0 || remoteScore < 0) {
            throw new IllegalArgumentException("scores must not be negative");
        }
    }
}
