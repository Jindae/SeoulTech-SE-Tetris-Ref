package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class BattleEventSinkTest {
    @Test
    void noOpSinkAllowsSinglePlayerToIgnoreBattleEvents() {
        assertDoesNotThrow(() -> {
            BattleEventSink.NONE.scoreChanged(100);
            BattleEventSink.NONE.linesCleared(4);
            BattleEventSink.NONE.timeExpired(100);
            BattleEventSink.NONE.localTopOut(100);
            BattleEventSink.NONE.matchDecided(
                MatchJudge.decide(MatchEndReason.TIME_EXPIRED, 100, 90));
        });
    }
}
