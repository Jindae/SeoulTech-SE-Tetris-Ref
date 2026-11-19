package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MatchJudgeTest {
    @Test
    void timeExpiryUsesScoresAndSupportsDraws() {
        assertEquals(MatchOutcome.LOCAL_WIN,
            MatchJudge.decide(MatchEndReason.TIME_EXPIRED, 20, 10).outcome());
        assertEquals(MatchOutcome.REMOTE_WIN,
            MatchJudge.decide(MatchEndReason.TIME_EXPIRED, 10, 20).outcome());
        assertEquals(MatchOutcome.DRAW,
            MatchJudge.decide(MatchEndReason.TIME_EXPIRED, 20, 20).outcome());
    }

    @Test
    void topOutTakesPriorityOverScoreAndSimultaneousLossIsDraw() {
        assertEquals(MatchOutcome.REMOTE_WIN,
            MatchJudge.decide(MatchEndReason.LOCAL_TOP_OUT, 999, 0).outcome());
        assertEquals(MatchOutcome.LOCAL_WIN,
            MatchJudge.decide(MatchEndReason.REMOTE_TOP_OUT, 0, 999).outcome());
        assertEquals(MatchOutcome.DRAW,
            MatchJudge.decide(MatchEndReason.SIMULTANEOUS_TOP_OUT, 1, 2).outcome());
    }

    @Test
    void negativeNetworkScoresAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> MatchJudge.decide(MatchEndReason.TIME_EXPIRED, -1, 0));
    }
}
