package seoultech.se.tetris.component;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;

class LineAnimationTest {
    @Test void completedLineIsRemovedBeforeAnimationEnds() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                prepareClear(f);
                f.controller.dispatch(GameCommand.HARD_DROP);
                assertEquals(1, f.controller.snapshot().clearedLines());
                assertNotNull(f.controller.snapshot().fixedCells().get(19).get(2),
                    "삭제 직후 윗행의 칸은 즉시 내려온다");
                assertEquals(java.util.List.of(19), f.controller.snapshot().highlightedRows());
                assertTrue(f.controller.timer().isRunning());
                f.elapse(209_000_000L);
                assertFalse(f.controller.snapshot().highlightedRows().isEmpty());
                f.elapse(1_000_000L);
                assertTrue(f.controller.snapshot().highlightedRows().isEmpty());
                assertEquals(0, f.controller.snapshot().y());
                assertEquals(1, f.controller.snapshot().level());
                assertFalse(f.controller.snapshot().paused());
            }
            return null;
        });
    }
    @Test void stopAnimationsClearsOnlyAnimationState() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                prepareClear(f);
                f.controller.dispatch(GameCommand.HARD_DROP);
                var fixed = f.controller.snapshot().fixedCells();
                int score = f.controller.snapshot().score();
                f.game.stopGame();
                assertTrue(f.controller.snapshot().highlightedRows().isEmpty());
                assertEquals(fixed, f.controller.snapshot().fixedCells());
                assertEquals(score, f.controller.snapshot().score());
                assertFalse(f.controller.timer().isRunning());
            }
            return null;
        });
    }
    static void prepareClear(ControllerFixture f) {
        int[][] grid = new int[20][10];
        grid[18][2] = 1;
        java.util.Arrays.fill(grid[19], 1);
        grid[19][3] = grid[19][4] = 0;
        f.prepare(new OBlock(), new OBlock(), grid, 3, 18);
    }
}
