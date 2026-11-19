package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.attack.AttackEvent;
import seoultech.se.tetris.attack.AttackRow;
import seoultech.se.tetris.blocks.OBlock;

class GameEngineAttackTest {
    @ParameterizedTest
    @EnumSource(GameMode.class)
    void ordinaryTwoLineClearGeneratesExactAttackInEveryMode(GameMode mode) {
        GameEngineFixture fixture = sourceWithTwoLineClear(mode);

        GameUpdate update = fixture.engine.handle(GameCommand.HARD_DROP);

        AttackEvent attack = update.lockResolution().orElseThrow().attack().orElseThrow();
        assertEquals(2, attack.rows().size());
        for (AttackRow row : attack.rows()) {
            for (int column = 0; column < GameState.WIDTH; column++) {
                assertEquals(column != 4 && column != 5, row.occupiedAt(column));
            }
        }
    }

    @Test
    void receivedRowsWaitForMatchingTurnThenApplyExactlyOnceBeforeSpawn() {
        GameEngineFixture fixture = activeTarget();
        fixture.fixed(0, 17, "O", 0xff808080, 0);
        fixture.engine.receiveAttack(twoRowAttack(1, 2));

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        long turnId = locked.lockResolution().orElseThrow().turnId();
        fixture.engine.continueAfterLock(turnId + 1);
        assertEquals(2, fixture.engine.pendingAttackRows().size());
        assertEquals("O", fixture.cellText(0, 17));

        fixture.engine.continueAfterLock(turnId);
        assertTrue(fixture.engine.pendingAttackRows().isEmpty());
        assertEquals("O", fixture.cellText(0, 15));
        assertEquals("G", fixture.cellText(0, 18));
        assertEquals(null, fixture.cellText(1, 18));
        assertEquals(null, fixture.cellText(2, 19));

        GameSnapshot once = fixture.engine.snapshot();
        fixture.engine.continueAfterLock(turnId);
        assertEquals(once, fixture.engine.snapshot());
    }

    @Test
    void attackOverflowEndsWithoutPromotingOrDrawingAnotherBlock() {
        GameEngineFixture fixture = activeTarget();
        fixture.fixed(0, 0);
        fixture.engine.receiveAttack(twoRowAttack(1, 2));
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        GameUpdate ended = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId());

        assertEquals(GamePhase.GAME_OVER, ended.snapshot().phase());
        assertTrue(ended.gameEnd().isPresent());
        assertEquals(0, fixture.source.normalCalls, "overflow must not request another block");
        assertTrue(fixture.engine.pendingAttackRows().isEmpty());
        assertFalse(fixture.engine.continueAfterLock(1).gameEnd().isPresent(),
            "the same overflow must not emit a second end event");
    }

    @Test
    void attacksCannotLeakAcrossNewOrStoppedSessions() {
        GameEngineFixture fresh = new GameEngineFixture(new OBlock(), new OBlock());
        fresh.engine.receiveAttack(twoRowAttack(1, 2));
        assertTrue(fresh.engine.pendingAttackRows().isEmpty());

        GameEngineFixture stopped = activeTarget();
        stopped.engine.receiveAttack(twoRowAttack(1, 2));
        stopped.engine.stop();
        stopped.engine.receiveAttack(twoRowAttack(3, 4));
        assertTrue(stopped.engine.pendingAttackRows().isEmpty());
    }

    private static GameEngineFixture sourceWithTwoLineClear(GameMode mode) {
        GameEngineFixture fixture = new GameEngineFixture(
            new GameConfig(mode, Difficulty.NORMAL), new OBlock());
        fixture.seed(new OBlock(), new OBlock(), 4, 18);
        for (int row = 18; row <= 19; row++) {
            for (int column = 0; column < GameState.WIDTH; column++) {
                if (column != 4 && column != 5) fixture.fixed(column, row);
            }
        }
        return fixture;
    }

    private static GameEngineFixture activeTarget() {
        GameEngineFixture fixture = new GameEngineFixture(new OBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        return fixture;
    }

    private static AttackEvent twoRowAttack(int firstHole, int secondHole) {
        boolean[] first = new boolean[GameState.WIDTH];
        boolean[] second = new boolean[GameState.WIDTH];
        java.util.Arrays.fill(first, true);
        java.util.Arrays.fill(second, true);
        first[firstHole] = false;
        second[secondHole] = false;
        return new AttackEvent(List.of(new AttackRow(first), new AttackRow(second)));
    }
}
