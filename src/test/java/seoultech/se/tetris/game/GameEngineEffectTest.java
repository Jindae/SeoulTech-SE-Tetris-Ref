package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.ColumnBlock;
import seoultech.se.tetris.blocks.CrossBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.RowBlock;

class GameEngineEffectTest {
    private static final GameConfig ITEM = new GameConfig(GameMode.ITEM, Difficulty.NORMAL);

    @Test
    void rowShowsFiveFramesThenCommitsOnTheFollowingTickWhilePaused() {
        RowBlock row = new RowBlock();
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(row, new OBlock(), 3, 20 - row.height());

        GameUpdate started = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);

        assertEquals(GamePhase.RESOLVING, started.snapshot().phase());
        assertEquals("ROW", started.snapshot().effect().kind());
        assertEquals(0, started.snapshot().effect().frame());
        assertEquals(10, started.snapshot().effect().overlayCells().size());
        assertEquals(50_000_000L, started.nextWakeupNanos().orElseThrow());

        GameUpdate fifth = fixture.advanceEffectFrames(5);
        assertEquals(GamePhase.RESOLVING, fifth.snapshot().phase());
        assertEquals(5, fifth.snapshot().effect().frame());
        assertTrue(fifth.snapshot().effect().overlayCells().stream()
            .allMatch(cell -> cell.text().equals(" ")));
        assertTrue(fifth.snapshot().paused());
        assertFalse(fifth.lockResolution().isPresent());

        GameUpdate committed = fixture.advanceEffectFrames(1);
        assertEquals(GamePhase.AWAITING_SPAWN, committed.snapshot().phase());
        assertEquals(100, committed.snapshot().score());
        assertEquals(1, committed.snapshot().clearedLines());
        assertNull(committed.snapshot().effect());
        assertEquals(ClearBatch.Cause.ROW_ITEM,
            committed.lockResolution().orElseThrow().clearBatches().get(0).cause());
        assertTrue(committed.snapshot().highlightedRows().isEmpty());
    }

    @Test
    void resolvingEffectIgnoresMovementAndStopCancelsWithoutLateCommit() {
        RowBlock row = new RowBlock();
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(row, new OBlock(), 3, 20 - row.height());
        fixture.engine.handle(GameCommand.HARD_DROP);

        fixture.engine.handle(GameCommand.LEFT);
        fixture.engine.handle(GameCommand.RIGHT);
        fixture.engine.handle(GameCommand.ROTATE);
        fixture.engine.handle(GameCommand.SOFT_DROP);
        fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate stopped = fixture.engine.stop();
        fixture.engine.advance(5_000_000_000L);

        assertEquals(GamePhase.STOPPED, stopped.snapshot().phase());
        assertNull(stopped.snapshot().effect());
        assertEquals(0, fixture.score());
        assertEquals(0, fixture.clearedLines());
        assertEquals(GamePhase.STOPPED, fixture.engine.snapshot().phase());
    }

    @Test
    void columnShowsTenFramesAndCommitsOnlyOnEleventhTick() {
        ColumnBlock column = new ColumnBlock();
        int landingY = 20 - column.height();
        int itemColumn = 3 + column.getItemX();
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(column, new OBlock(), 3, landingY);
        fixture.fixed(itemColumn, 2, "C", 0xff112233, 0);

        fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate tenth = fixture.advanceEffectFrames(10);

        assertEquals(GamePhase.RESOLVING, tenth.snapshot().phase());
        assertEquals(10, tenth.snapshot().effect().frame());
        assertFalse(tenth.lockResolution().isPresent());

        GameUpdate committed = fixture.advanceEffectFrames(1);
        assertEquals(GamePhase.AWAITING_SPAWN, committed.snapshot().phase());
        assertTrue(committed.lockResolution().orElseThrow().clearBatches().isEmpty());
        for (int row = 0; row < 20; row++) {
            assertNull(committed.snapshot().fixedCells().get(row).get(itemColumn));
        }
    }

    @Test
    void crossCapturesRowsAfterItsOriginalColumnsAreCleared() {
        GameEngineFixture fixture = new GameEngineFixture(ITEM);
        fixture.seed(new CrossBlock(), new OBlock(), 3, 18);
        for (int column = 0; column < 10; column++) fixture.fixed(column, 5);

        fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate tenth = fixture.advanceEffectFrames(10);
        assertEquals(10, tenth.snapshot().effect().frame());
        assertEquals(56, tenth.snapshot().effect().overlayCells().size());

        GameUpdate committed = fixture.advanceEffectFrames(1);
        ClearBatch clear = committed.lockResolution().orElseThrow().clearBatches().get(0);

        assertEquals(ClearBatch.Cause.CROSS_ITEM, clear.cause());
        assertEquals(List.of(5, 18, 19), clear.rows());
        assertNull(clear.rowsBefore().get(0).get(3));
        assertNull(clear.rowsBefore().get(0).get(4));
        assertEquals(8, clear.rowsBefore().get(0).stream().filter(cell -> cell != null).count());
        assertTrue(clear.lockedPieceOriginMasks().stream().flatMap(List::stream)
            .noneMatch(Boolean::booleanValue));
        assertEquals(600, committed.snapshot().score());
        assertEquals(3, committed.snapshot().clearedLines());
        assertEquals(List.of(5), committed.snapshot().highlightedRows());
    }
}
