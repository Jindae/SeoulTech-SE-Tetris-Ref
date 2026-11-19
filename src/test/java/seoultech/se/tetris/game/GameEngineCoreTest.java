package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.blocks.IBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.RowBlock;
import seoultech.se.tetris.blocks.TBlock;

class GameEngineCoreTest {
    @Test
    void constructionIsInertAndStartConsumesExactlyTwoBlocks() {
        GameEngineFixture fixture = new GameEngineFixture(new TBlock(), new OBlock());

        assertEquals(GamePhase.NEW, fixture.engine.snapshot().phase());
        assertEquals(0, fixture.source.normalCalls);

        GameUpdate started = fixture.engine.start();
        GameUpdate duplicate = fixture.engine.start();

        assertEquals(2, fixture.source.normalCalls);
        assertEquals(GamePhase.ACTIVE, started.snapshot().phase());
        assertEquals("T", started.snapshot().current().kind());
        assertEquals("O", started.snapshot().next().kind());
        assertEquals(3, started.snapshot().x());
        assertEquals(0, started.snapshot().y());
        assertEquals(1_000_000_000L, started.snapshot().dropIntervalNanos());
        assertEquals(started.snapshot(), duplicate.snapshot());
    }

    @Test
    void movementRotationAndHardDropUseFixedGridWithoutMutatingIt() {
        GameEngineFixture fixture = new GameEngineFixture(new OBlock());
        fixture.seed(new IBlock(), new OBlock(), 3, 0);
        fixture.fixed(0, 19);

        fixture.engine.handle(GameCommand.LEFT);
        fixture.engine.handle(GameCommand.ROTATE);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        assertEquals(GamePhase.AWAITING_SPAWN, locked.snapshot().phase());
        assertTrue(locked.lockResolution().isPresent());
        assertEquals(16, locked.snapshot().score());
        assertNotNull(locked.snapshot().fixedCells().get(19).get(0));
        assertNull(locked.snapshot().current());
        assertEquals(0, fixture.source.normalCalls);
    }

    @Test
    void completedRowsScoreLevelAndCaptureImmutablePreRemovalFacts() {
        GameEngineFixture fixture = new GameEngineFixture(new TBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.engine.state.clearedLines = 8;
        for (int row : new int[] {18, 19}) {
            for (int column = 0; column < 10; column++) {
                if (column != 3 && column != 4) fixture.fixed(column, row);
            }
        }

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        LockResolution resolution = locked.lockResolution().orElseThrow();
        ClearBatch clear = resolution.clearBatches().get(0);

        assertEquals(300, locked.snapshot().score());
        assertEquals(10, locked.snapshot().clearedLines());
        assertEquals(2, locked.snapshot().level());
        assertEquals(List.of(18, 19), clear.rows());
        assertEquals(10, clear.rowsBefore().get(0).stream().filter(cell -> cell != null).count());
        assertEquals(List.of(false, false, false, true, true, false, false, false, false, false),
            clear.lockedPieceOriginMasks().get(0));
        assertEquals(List.of(18, 19), locked.snapshot().highlightedRows());
        assertThrows(UnsupportedOperationException.class, () -> clear.rows().add(18));
    }

    @Test
    void lockContinuationIsSingleUseAndDoesNotConsumeBeforeBoundary() {
        GameEngineFixture fixture = new GameEngineFixture(new TBlock());
        fixture.seed(new OBlock(), new IBlock(), 3, 18);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        long turnId = locked.lockResolution().orElseThrow().turnId();
        fixture.engine.handle(GameCommand.LEFT);
        fixture.engine.advance(2_000_000_000L);
        fixture.engine.continueAfterLock(turnId + 1);

        assertEquals(0, fixture.source.normalCalls);
        assertEquals(GamePhase.AWAITING_SPAWN, fixture.engine.snapshot().phase());

        GameUpdate continued = fixture.engine.continueAfterLock(turnId);
        fixture.engine.continueAfterLock(turnId);

        assertEquals(1, fixture.source.normalCalls);
        assertEquals(GamePhase.ACTIVE, continued.snapshot().phase());
        assertEquals("I", continued.snapshot().current().kind());
        assertEquals("T", continued.snapshot().next().kind());
    }

    @Test
    void spawnCollisionEndsOnceWithoutConsumingAnotherBlock() {
        GameEngineFixture fixture = new GameEngineFixture(new TBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.fixed(3, 0);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        long turnId = locked.lockResolution().orElseThrow().turnId();

        GameUpdate ended = fixture.engine.continueAfterLock(turnId);
        GameUpdate repeated = fixture.engine.continueAfterLock(turnId);

        assertEquals(GamePhase.GAME_OVER, ended.snapshot().phase());
        assertTrue(ended.gameEnd().isPresent());
        assertFalse(repeated.gameEnd().isPresent());
        assertEquals(0, fixture.source.normalCalls);
    }

    @Test
    void spawnCollisionAfterClearCancelsHighlightWakeup() {
        GameEngineFixture fixture = new GameEngineFixture(new TBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 18);
        fixture.fixed(3, 0);
        for (int column = 0; column < 10; column++) {
            if (column != 3 && column != 4) fixture.fixed(column, 19);
        }
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        GameUpdate ended = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId());

        assertEquals(GamePhase.GAME_OVER, ended.snapshot().phase());
        assertTrue(ended.snapshot().highlightedRows().isEmpty());
        assertTrue(ended.nextWakeupNanos().isEmpty());
        assertEquals(0, fixture.source.normalCalls);
    }

    @Test
    void manualLockPreservesPartialGravityPeriodWithoutReusingCurrentCallTime() {
        GameEngineFixture fixture = new GameEngineFixture(new TBlock());
        fixture.seed(new OBlock(), new OBlock(), 3, 0);

        fixture.engine.advance(400_000_000L);
        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.continueAfterLock(locked.lockResolution().orElseThrow().turnId());

        fixture.engine.advance(599_000_000L);
        assertEquals(0, fixture.engine.snapshot().y());
        fixture.engine.advance(1_000_000L);
        assertEquals(1, fixture.engine.snapshot().y());
    }

    @Test
    void snapshotsAreDeeplyImmutableAndRemainStableAfterProgress() {
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(new TBlock(), new OBlock(), 3, 0);
        fixture.fixed(0, 19);
        GameSnapshot before = fixture.engine.snapshot();

        fixture.engine.handle(GameCommand.RIGHT);

        assertEquals(3, before.x());
        assertEquals(4, fixture.engine.snapshot().x());
        assertNotNull(before.fixedCells().get(19).get(0));
        assertThrows(UnsupportedOperationException.class,
            () -> before.fixedCells().get(0).set(0, before.fixedCells().get(19).get(0)));
        assertThrows(UnsupportedOperationException.class,
            () -> before.current().cells().add(List.of()));
    }

    @Test
    void snapshotUsesBlockColorForOrdinaryCellsAndWhiteForItemMarkers() {
        GameEngineFixture fixture = new GameEngineFixture();
        RowBlock row = new RowBlock();
        fixture.seed(row, new OBlock(), 3, 0);

        GameSnapshot.Piece piece = fixture.engine.snapshot().current();
        for (int y = 0; y < row.height(); y++) {
            for (int x = 0; x < row.width(); x++) {
                GameSnapshot.Cell cell = piece.cells().get(y).get(x);
                if (cell == null) continue;
                int expected = row.getText(x, y).equals("O") ? row.getColor().getRGB() : 0xffffffff;
                assertEquals(expected, cell.argb());
            }
        }
    }

    @Test
    void advancePerformsAtMostOneGravityStepAndPauseOnlyAdvancesHighlightTime() {
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(new OBlock(), new OBlock(), 3, 0);
        fixture.engine.state.highlightedRows.set(19);

        fixture.engine.advance(3_000_000_000L);
        assertEquals(1, fixture.engine.snapshot().y());
        assertTrue(fixture.engine.snapshot().highlightedRows().isEmpty());

        fixture.engine.state.highlightedRows.set(18);
        fixture.engine.handle(GameCommand.TOGGLE_PAUSE);
        fixture.engine.advance(GameEngine.CLEAR_HIGHLIGHT_NANOS);

        assertEquals(1, fixture.engine.snapshot().y());
        assertTrue(fixture.engine.snapshot().paused());
        assertTrue(fixture.engine.snapshot().highlightedRows().isEmpty());
        assertTrue(fixture.engine.advance(0).nextWakeupNanos().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> fixture.engine.advance(-1));
    }

    @Test
    void stopIsIdempotentTerminalAndNeverPublishesNaturalEnd() {
        GameEngineFixture fixture = new GameEngineFixture(new OBlock(), new OBlock());
        fixture.engine.start();

        GameUpdate stopped = fixture.engine.stop();
        GameUpdate repeated = fixture.engine.stop();
        fixture.engine.handle(GameCommand.HARD_DROP);
        fixture.engine.advance(1_000_000_000L);

        assertEquals(GamePhase.STOPPED, stopped.snapshot().phase());
        assertFalse(stopped.gameEnd().isPresent());
        assertFalse(repeated.gameEnd().isPresent());
        assertEquals(GamePhase.STOPPED, fixture.engine.snapshot().phase());
        assertEquals(2, fixture.source.normalCalls);
    }
}
