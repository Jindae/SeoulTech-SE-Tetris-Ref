package seoultech.se.tetris.game;

import java.util.Objects;

/** Deterministic winner policy used identically on both P2P participants. */
public final class MatchJudge {
    private MatchJudge() {}

    public static MatchDecision decide(MatchEndReason reason, int localScore, int remoteScore) {
        Objects.requireNonNull(reason, "reason");
        MatchOutcome outcome = switch (reason) {
            case LOCAL_TOP_OUT -> MatchOutcome.REMOTE_WIN;
            case REMOTE_TOP_OUT -> MatchOutcome.LOCAL_WIN;
            case SIMULTANEOUS_TOP_OUT -> MatchOutcome.DRAW;
            case TIME_EXPIRED -> compareScores(localScore, remoteScore);
        };
        return new MatchDecision(outcome, reason, localScore, remoteScore);
    }

    private static MatchOutcome compareScores(int localScore, int remoteScore) {
        if (localScore > remoteScore) return MatchOutcome.LOCAL_WIN;
        if (localScore < remoteScore) return MatchOutcome.REMOTE_WIN;
        return MatchOutcome.DRAW;
    }
}
