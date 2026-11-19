package seoultech.se.tetris.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.blocks.IBlock;
import seoultech.se.tetris.blocks.JBlock;
import seoultech.se.tetris.blocks.LBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.blocks.SBlock;
import seoultech.se.tetris.blocks.TBlock;
import seoultech.se.tetris.blocks.ZBlock;

/** Regression coverage for Classic rules that previously lived behind Board. */
class GameEngineParityTest {
    @Test
    void everyClassicShapeAndRotationRespectsWallsAndFixedCells() {
        for (Block block : classicBlocks()) {
            for (int rotation = 0; rotation < 4; rotation++) {
                String context = block.getClass().getSimpleName() + "/" + rotation;
                GameEngineFixture fixture = new GameEngineFixture();

                fixture.seed(block, new OBlock(), 0, 5);
                fixture.engine.handle(GameCommand.LEFT);
                assertEquals(0, fixture.engine.snapshot().x(), context + " left wall");

                int right = 10 - block.width();
                fixture.seed(block, new OBlock(), right, 5);
                fixture.engine.handle(GameCommand.RIGHT);
                assertEquals(right, fixture.engine.snapshot().x(), context + " right wall");

                int[] leadingCell = rightmostOccupiedCell(block);
                fixture.seed(block, new OBlock(), 3, 5);
                fixture.fixed(3 + leadingCell[0] + 1, 5 + leadingCell[1]);
                fixture.engine.handle(GameCommand.RIGHT);
                assertEquals(3, fixture.engine.snapshot().x(), context + " fixed-cell collision");

                block.rotate();
            }
        }
    }

    @Test
    void rotationUsesLegacyWallFloorAndUpwardCandidateOrder() {
        IBlock vertical = new IBlock();
        vertical.rotate();
        List<RotationCase> cases = List.of(
            new RotationCase(vertical, 9, 5, new int[0][], 6, 5),
            new RotationCase(new IBlock(), 3, 19, new int[0][], 3, 16),
            new RotationCase(new IBlock(), 3, 5, new int[][] {{3, 6}}, 3, 2),
            new RotationCase(new IBlock(), 3, 5, new int[][] {{3, 4}, {3, 6}}, null, null),
            new RotationCase(new IBlock(), 3, 0, new int[][] {{3, 1}}, null, null),
            new RotationCase(new TBlock(), 3, 5, new int[][] {{4, 7}}, 3, 5));

        for (RotationCase scenario : cases) {
            GameEngineFixture fixture = new GameEngineFixture();
            fixture.seed(scenario.block(), new OBlock(), scenario.x(), scenario.y());
            for (int[] obstacle : scenario.obstacles()) fixture.fixed(obstacle[0], obstacle[1]);
            GameSnapshot before = fixture.engine.snapshot();

            fixture.engine.handle(GameCommand.ROTATE);
            GameSnapshot after = fixture.engine.snapshot();

            if (scenario.expectedX() == null) {
                assertEquals(before.x(), after.x());
                assertEquals(before.y(), after.y());
                assertEquals(before.current().cells(), after.current().cells());
            } else {
                assertEquals(scenario.expectedX(), after.x());
                assertEquals(scenario.expectedY(), after.y());
                assertFalse(before.current().cells().equals(after.current().cells()),
                    "non-square block must rotate when a candidate is available");
            }
        }
    }

    @Test
    void ghostQueryIsPureAndHardDropLocksAtTheReportedRow() {
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(new OBlock(), new TBlock(), 3, 0);
        fixture.fixed(3, 10);

        GameSnapshot before = fixture.engine.snapshot();
        GameSnapshot repeated = fixture.engine.snapshot();

        assertEquals(8, before.ghostY());
        assertEquals(before, repeated);
        assertEquals(0, fixture.engine.state.score);
        assertEquals(0, fixture.engine.state.y);

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);

        assertEquals(GamePhase.AWAITING_SPAWN, locked.snapshot().phase());
        assertEquals(8, locked.snapshot().score());
        assertNull(locked.snapshot().current());
        for (int row = 8; row <= 9; row++) {
            for (int column = 3; column <= 4; column++) {
                assertNotNull(locked.snapshot().fixedCells().get(row).get(column));
            }
        }
    }

    @Test
    void automaticSoftAndHardDropUseTheSameLevelPointsPerCell() {
        for (int level : new int[] {1, 2, 19}) {
            GameEngineFixture automatic = oneCellDropFixture(level);
            automatic.engine.advance(automatic.engine.snapshot().dropIntervalNanos());
            assertEquals(level, automatic.engine.snapshot().score(), "automatic level " + level);
            assertEquals(18, automatic.engine.snapshot().y());

            GameEngineFixture soft = oneCellDropFixture(level);
            soft.engine.handle(GameCommand.SOFT_DROP);
            assertEquals(level, soft.engine.snapshot().score(), "soft level " + level);
            assertEquals(18, soft.engine.snapshot().y());

            GameEngineFixture hard = oneCellDropFixture(level);
            GameUpdate locked = hard.engine.handle(GameCommand.HARD_DROP);
            assertEquals(level, locked.snapshot().score(), "hard level " + level);
            assertEquals(GamePhase.AWAITING_SPAWN, locked.snapshot().phase());
        }
    }

    @Test
    void fourLineClearUsesComboScoreAndCrossesOnlyOneLevelBoundary() {
        IBlock vertical = new IBlock();
        vertical.rotate();
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(vertical, new OBlock(), 3, 16);
        fixture.engine.state.clearedLines = 6;
        fixture.fixed(0, 15);
        for (int row = 16; row < 20; row++) {
            for (int column = 0; column < 10; column++) {
                if (column != 3) fixture.fixed(column, row);
            }
        }

        GameUpdate locked = fixture.engine.handle(GameCommand.HARD_DROP);
        LockResolution resolution = locked.lockResolution().orElseThrow();

        assertEquals(1_000, locked.snapshot().score());
        assertEquals(10, locked.snapshot().clearedLines());
        assertEquals(2, locked.snapshot().level());
        assertEquals(List.of(16, 17, 18, 19), resolution.clearBatches().get(0).rows());
        assertNotNull(locked.snapshot().fixedCells().get(19).get(0),
            "row above the clear must fall by four cells");
        assertEquals(1, locked.snapshot().fixedCells().stream()
            .flatMap(List::stream).filter(cell -> cell != null).count());
    }

    @Test
    void snapshotDropIntervalUsesConfiguredDifficultyAndCurrentLevel() {
        int[][] expectedMillis = {
            {1_000, 827, 669},
            {1_000, 793, 618},
            {1_000, 761, 574}
        };
        for (Difficulty difficulty : Difficulty.values()) {
            GameEngine engine = engine(difficulty, new OBlock(), new TBlock());
            engine.start();
            for (int level = 1; level <= 3; level++) {
                engine.state.level = level;
                assertEquals(expectedMillis[difficulty.ordinal()][level - 1] * 1_000_000L,
                    engine.snapshot().dropIntervalNanos(), difficulty + " level " + level);
            }
            engine.state.level = 19;
            long maximumLevelInterval = engine.snapshot().dropIntervalNanos();
            engine.state.level = 20;
            assertEquals(maximumLevelInterval, engine.snapshot().dropIntervalNanos());
        }
    }

    @Test
    void queuedSpawnChecksOccupiedShapeCellsButIgnoresShapeHoles() {
        GameEngineFixture throughHole = new GameEngineFixture(new IBlock());
        TBlock nextThroughHole = new TBlock();
        throughHole.seed(new OBlock(), nextThroughHole, 0, 18);
        throughHole.fixed(3, 0);
        GameUpdate holeLock = throughHole.engine.handle(GameCommand.HARD_DROP);

        GameUpdate continued = throughHole.engine.continueAfterLock(
            holeLock.lockResolution().orElseThrow().turnId());

        assertEquals(GamePhase.ACTIVE, continued.snapshot().phase());
        assertSame(nextThroughHole, throughHole.engine.state.current);
        assertEquals(1, throughHole.source.normalCalls);

        GameEngineFixture occupied = new GameEngineFixture(new IBlock());
        TBlock blockedNext = new TBlock();
        occupied.seed(new OBlock(), blockedNext, 0, 18);
        occupied.fixed(4, 0);
        GameUpdate blockedLock = occupied.engine.handle(GameCommand.HARD_DROP);

        GameUpdate ended = occupied.engine.continueAfterLock(
            blockedLock.lockResolution().orElseThrow().turnId());

        assertEquals(GamePhase.GAME_OVER, ended.snapshot().phase());
        assertTrue(ended.gameEnd().isPresent());
        assertSame(blockedNext, occupied.engine.state.next);
        assertEquals(0, occupied.source.normalCalls);
    }

    private static GameEngineFixture oneCellDropFixture(int level) {
        GameEngineFixture fixture = new GameEngineFixture();
        fixture.seed(new OBlock(), new TBlock(), 3, 17);
        fixture.engine.state.level = level;
        return fixture;
    }

    private static GameEngine engine(Difficulty difficulty, Block... blocks) {
        return new GameEngine(new GameConfig(GameMode.CLASSIC, difficulty),
            new GameEngineFixture.QueueBlockSource(blocks));
    }

    private static int[] rightmostOccupiedCell(Block block) {
        for (int column = block.width() - 1; column >= 0; column--) {
            for (int row = 0; row < block.height(); row++) {
                if (block.getShape(column, row) != 0) return new int[] {column, row};
            }
        }
        throw new AssertionError("Classic block has no occupied cells");
    }

    private static List<Block> classicBlocks() {
        return List.of(new IBlock(), new JBlock(), new LBlock(), new OBlock(),
            new SBlock(), new TBlock(), new ZBlock());
    }

    private record RotationCase(
        Block block, int x, int y, int[][] obstacles, Integer expectedX, Integer expectedY) {}
}
