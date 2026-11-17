package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.IBlock;
import seoultech.se.tetris.blocks.JBlock;
import seoultech.se.tetris.blocks.LBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.SBlock;
import seoultech.se.tetris.blocks.TBlock;
import seoultech.se.tetris.blocks.ZBlock;
import seoultech.se.tetris.rules.CollisionRules;

/**
 * Engine replacements for the Classic rule cases formerly asserted through
 * Board's mutable fields in {@code RefactoringRegressionTest}.
 */
class LegacyEngineRegressionTest {
    @Test
    void collisionRuleQueriesArePureOnRectangularGrids() {
        int[][] grid = new int[4][6];
        OBlock block = new OBlock();

        assertTrue(CollisionRules.canLeft(grid, block, 4, 2));
        assertFalse(CollisionRules.canRight(grid, block, 4, 2));
        assertFalse(CollisionRules.canDown(grid, block, 4, 2));
        assertEquals(List.of(4, 2), asList(CollisionRules.rotationPosition(grid, block, 4, 2)));

        grid[2][3] = 1;
        int[][] before = copy(grid);
        assertFalse(CollisionRules.canLeft(grid, block, 4, 2));
        assertTrue(java.util.Arrays.deepEquals(before, grid));
        assertEquals("[[1, 1], [1, 1]]", shape(block));
    }

    @Test
    void everyShapeAndRotationLocksOnlyOccupiedCellsThenPromotesPreview() {
        for (Block current : classicBlocks()) {
            for (int rotation = 0; rotation < 4; rotation++) {
                String context = current.getClass().getSimpleName() + "/" + rotation;
                OBlock preview = new OBlock();
                GameEngineFixture fixture = new GameEngineFixture(new TBlock());
                fixture.seed(current, preview, 0, 20 - current.height());

                GameUpdate locked = fixture.engine.handle(GameCommand.SOFT_DROP);

                assertEquals(GamePhase.AWAITING_SPAWN, locked.snapshot().phase(), context);
                assertEquals(4, occupiedCount(locked.snapshot()), context + " occupied cells");
                for (int row = 0; row < current.height(); row++) {
                    for (int column = 0; column < current.width(); column++) {
                        GameSnapshot.Cell cell = locked.snapshot().fixedCells()
                            .get(20 - current.height() + row).get(column);
                        if (current.getShape(column, row) == 0) {
                            assertNull(cell, context + " shape hole");
                        } else {
                            assertNotNull(cell, context + " occupied shape cell");
                        }
                    }
                }

                GameUpdate continued = fixture.engine.continueAfterLock(
                    locked.lockResolution().orElseThrow().turnId());
                assertEquals(GamePhase.ACTIVE, continued.snapshot().phase(), context);
                assertSame(preview, fixture.engine.state.current, context + " preview promotion");
                assertEquals(3, continued.snapshot().x());
                assertEquals(0, continued.snapshot().y());
                assertEquals(1, fixture.source.normalCalls, context + " one replacement draw");
                current.rotate();
            }
        }
    }

    @Test
    void incompleteRowsRemainWhileSeparatedCompletedRowsCompressTogether() {
        IBlock vertical = new IBlock();
        vertical.rotate();
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(vertical, new OBlock(), 3, 16);
        fillRowExcept(fixture, 16, 3);
        fillRowExcept(fixture, 19, 3);
        fixed(fixture, 0, 17, "A", 0xffff0000);
        fixed(fixture, 1, 18, "B", 0xff0000ff);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        assertEquals(List.of(16, 19), locked.lockResolution().orElseThrow()
            .clearBatches().get(0).rows());
        assertEquals(300, locked.snapshot().score());
        assertEquals(2, locked.snapshot().clearedLines());
        assertEquals("A", locked.snapshot().fixedCells().get(18).get(0).text());
        assertEquals(0xffff0000, locked.snapshot().fixedCells().get(18).get(0).argb());
        assertEquals("B", locked.snapshot().fixedCells().get(19).get(1).text());
        assertEquals(0xff0000ff, locked.snapshot().fixedCells().get(19).get(1).argb());
        assertNull(locked.snapshot().fixedCells().get(19).get(0),
            "incomplete rows must preserve empty cells");
    }

    @Test
    void topRowClearsOnlyWhenEveryCellIsOccupied() {
        GameEngineFixture completed = new GameEngineFixture();
        completed.seed(new OBlock(), new TBlock(), 8, 0);
        for (int column = 0; column < 8; column++) completed.fixed(column, 0);
        completed.fixed(8, 2);
        completed.fixed(9, 2);
        fixed(completed, 0, 1, "A", 0xffff0000);

        GameUpdate cleared = completed.engine.handle(GameCommand.SOFT_DROP);

        assertEquals(List.of(0), cleared.lockResolution().orElseThrow()
            .clearBatches().get(0).rows());
        assertEquals(100, cleared.snapshot().score());
        assertEquals("A", cleared.snapshot().fixedCells().get(1).get(0).text());
        assertTrue(cleared.snapshot().fixedCells().get(0).stream().allMatch(cell -> cell == null));

        GameEngineFixture incomplete = new GameEngineFixture();
        incomplete.seed(new OBlock(), new TBlock(), 8, 0);
        for (int column = 0; column < 7; column++) incomplete.fixed(column, 0);
        incomplete.fixed(8, 2);
        incomplete.fixed(9, 2);

        GameUpdate retained = incomplete.engine.handle(GameCommand.SOFT_DROP);

        assertTrue(retained.lockResolution().orElseThrow().clearBatches().isEmpty());
        assertNull(retained.snapshot().fixedCells().get(0).get(7));
        assertEquals(0, retained.snapshot().score());
    }

    @Test
    void rowDeletionRunsBeforeQueuedSpawnCollisionDecision() {
        GameEngineFixture fixture = new GameEngineFixture(new IBlock());
        TBlock preview = new TBlock();
        fixture.seed(new OBlock(), preview, 0, 18);
        fillRow(fixture, 0);
        fillRow(fixture, 1);
        fixture.fixed(4, 2);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate continued = fixture.engine.continueAfterLock(
            locked.lockResolution().orElseThrow().turnId());

        assertEquals(List.of(0, 1), locked.lockResolution().orElseThrow()
            .clearBatches().get(0).rows());
        assertEquals(GamePhase.ACTIVE, continued.snapshot().phase());
        assertFalse(continued.gameEnd().isPresent());
        assertSame(preview, fixture.engine.state.current);
        assertNotNull(continued.snapshot().fixedCells().get(2).get(4),
            "the former row-2 blocker must move below the spawn area");
    }

    @Test
    void pauseRejectsMovementDropAndGravityUntilResumed() {
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(new TBlock(), new OBlock(), 3, 5);
        GameSnapshot paused = fixture.engine.handle(GameCommand.TOGGLE_PAUSE).snapshot();

        for (GameCommand command : List.of(GameCommand.LEFT, GameCommand.RIGHT,
                GameCommand.ROTATE, GameCommand.SOFT_DROP, GameCommand.HARD_DROP)) {
            assertEquals(paused, fixture.engine.handle(command).snapshot(), command.toString());
        }
        assertEquals(paused, fixture.engine.advance(paused.dropIntervalNanos()).snapshot());

        GameSnapshot resumed = fixture.engine.handle(GameCommand.TOGGLE_PAUSE).snapshot();
        assertFalse(resumed.paused());
        fixture.engine.advance(resumed.dropIntervalNanos());
        assertEquals(6, fixture.engine.snapshot().y());
        assertEquals(1, fixture.engine.snapshot().score());
    }

    @Test
    void naturalGameOverIsPublishedOnceWithoutPromotingOrDrawingAnotherBlock() {
        OBlock current = new OBlock();
        TBlock preview = new TBlock();
        GameEngineFixture fixture = new GameEngineFixture(new IBlock());
        fixture.seed(current, preview, 0, 18);
        fixture.fixed(4, 0);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        long turnId = locked.lockResolution().orElseThrow().turnId();
        GameUpdate ended = fixture.engine.continueAfterLock(turnId);
        GameUpdate repeated = fixture.engine.continueAfterLock(turnId);

        assertEquals(GamePhase.GAME_OVER, ended.snapshot().phase());
        assertTrue(ended.gameEnd().isPresent());
        assertFalse(repeated.gameEnd().isPresent());
        assertSame(preview, fixture.engine.state.next);
        assertNull(fixture.engine.state.current);
        assertEquals(0, fixture.source.normalCalls);
        assertEquals(0, ended.snapshot().score());
    }

    @Test
    void everyRotatedPreviewEndsOnlyForOccupiedSpawnCells() {
        for (Block preview : classicBlocks()) {
            for (int rotation = 0; rotation < 4; rotation++) {
                int[] occupied = null;
                int[] hole = null;
                for (int row = 0; row < preview.height(); row++) {
                    for (int column = 0; column < preview.width(); column++) {
                        if (preview.getShape(column, row) == 0 && hole == null) {
                            hole = new int[] {column, row};
                        } else if (preview.getShape(column, row) != 0) {
                            occupied = new int[] {column, row};
                        }
                    }
                }
                assertSpawnProbe(preview, occupied, true);
                if (hole != null) assertSpawnProbe(preview, hole, false);
                preview.rotate();
            }
        }
    }

    @Test
    void twoEnginesOwnIndependentStateAndBlockSupply() {
        GameEngineFixture first = new GameEngineFixture(
            new TBlock(), new OBlock(), new IBlock(), new LBlock());
        GameEngineFixture second = new GameEngineFixture(
            new TBlock(), new OBlock(), new IBlock(), new LBlock());

        GameSnapshot firstStart = first.engine.start().snapshot();
        GameSnapshot secondStart = second.engine.start().snapshot();
        assertEquals(firstStart, secondStart);

        first.engine.handle(GameCommand.LEFT);
        GameUpdate firstLock = first.engine.handle(GameCommand.HARD_DROP);
        first.engine.continueAfterLock(firstLock.lockResolution().orElseThrow().turnId());

        assertEquals(2, second.source.normalCalls);
        assertEquals(secondStart, second.engine.snapshot());
        assertEquals("O", first.engine.snapshot().current().kind());
        assertEquals("I", first.engine.snapshot().next().kind());

        second.engine.handle(GameCommand.RIGHT);
        assertEquals(4, second.engine.snapshot().x());
        assertEquals(3, first.engine.snapshot().x());
        assertEquals(3, first.source.normalCalls);
        assertEquals(2, second.source.normalCalls);
    }

    private static void fillRow(GameEngineFixture fixture, int row) {
        for (int column = 0; column < 10; column++) fixture.fixed(column, row);
    }

    private static void fillRowExcept(GameEngineFixture fixture, int row, int gap) {
        for (int column = 0; column < 10; column++) {
            if (column != gap) fixture.fixed(column, row);
        }
    }

    private static void fixed(
            GameEngineFixture fixture, int x, int y, String text, int argb) {
        fixture.engine.state.fixed[y][x] = new GameState.FixedCell(1, text, argb, 0);
    }

    private static long occupiedCount(GameSnapshot snapshot) {
        return snapshot.fixedCells().stream().flatMap(List::stream)
            .filter(cell -> cell != null).count();
    }

    private static List<Block> classicBlocks() {
        return List.of(new IBlock(), new JBlock(), new LBlock(), new OBlock(),
            new SBlock(), new TBlock(), new ZBlock());
    }

    private static void assertSpawnProbe(Block preview, int[] probe, boolean blocked) {
        GameEngineFixture fixture = new GameEngineFixture(new IBlock());
        fixture.seed(new OBlock(), preview, 0, 18);
        fixture.fixed(3 + probe[0], probe[1]);
        GameUpdate lock = fixture.engine.handle(GameCommand.HARD_DROP);
        GameUpdate result = fixture.engine.continueAfterLock(
            lock.lockResolution().orElseThrow().turnId());
        assertEquals(blocked ? GamePhase.GAME_OVER : GamePhase.ACTIVE,
            result.snapshot().phase(), preview.getClass().getSimpleName() + "/" + shape(preview));
        assertEquals(blocked ? 0 : 1, fixture.source.normalCalls);
    }

    private static List<Integer> asList(int[] values) {
        return java.util.Arrays.stream(values).boxed().toList();
    }

    private static int[][] copy(int[][] grid) {
        return java.util.Arrays.stream(grid).map(int[]::clone).toArray(int[][]::new);
    }

    private static String shape(Block block) {
        int[][] cells = new int[block.height()][block.width()];
        for (int row = 0; row < block.height(); row++) {
            for (int column = 0; column < block.width(); column++) {
                cells[row][column] = block.getShape(column, row);
            }
        }
        return java.util.Arrays.deepToString(cells);
    }
}
