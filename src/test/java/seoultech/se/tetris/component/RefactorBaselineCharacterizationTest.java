package seoultech.se.tetris.component;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.*;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GamePhase;

/** RF-01 behavior boundaries exercised through the final controller and immutable snapshots. */
class RefactorBaselineCharacterizationTest {
    @TempDir Path dataDir;
    private String previousDataDir;
    private int previousRight;
    @BeforeEach void isolateGlobalState() {
        previousDataDir = System.getProperty("tetris.dataDir");
        previousRight = Maincontainer.RIGHT;
        System.setProperty("tetris.dataDir", dataDir.toString());
    }
    @AfterEach void restoreGlobalState() {
        Maincontainer.RIGHT = previousRight;
        if (previousDataDir == null) System.clearProperty("tetris.dataDir");
        else System.setProperty("tetris.dataDir", previousDataDir);
    }
    @Test void normalClearHighlightExpiresWhilePausedWithoutMovingActiveBlock() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                LineAnimationTest.prepareClear(f);
                f.controller.dispatch(GameCommand.HARD_DROP);
                assertTrue(f.controller.timer().isRunning());
                f.controller.dispatch(GameCommand.TOGGLE_PAUSE);
                var paused = f.controller.snapshot();
                assertFalse(paused.highlightedRows().isEmpty());
                f.elapse(211_000_000L);
                assertTrue(f.controller.snapshot().paused());
                assertEquals(paused.x(), f.controller.snapshot().x());
                assertEquals(paused.y(), f.controller.snapshot().y());
                assertTrue(f.controller.snapshot().highlightedRows().isEmpty());
                assertFalse(f.controller.timer().isRunning());
            }
            return null;
        });
    }
    @Test void escapeKeyUsesConfiguredActionBeforeMenuExit() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                Maincontainer.RIGHT = KeyEvent.VK_ESCAPE;
                press(f.game, KeyEvent.VK_ESCAPE);
                assertEquals(4, f.controller.snapshot().x());
                assertFalse(f.owner.exitCalled);
                Maincontainer.RIGHT = KeyEvent.VK_RIGHT;
                press(f.game, KeyEvent.VK_ESCAPE);
                assertTrue(f.owner.exitCalled);
                assertFalse(f.controller.timer().isRunning());
                assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
            }
            return null;
        });
    }
    @ParameterizedTest @EnumSource(AnimatedItem.class)
    void animatedItemCommitsOnlyAfterCompletionTick(AnimatedItem item) throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.ITEM)) {
                Block block = item.block();
                int y = 20 - block.height();
                int row = item == AnimatedItem.COLUMN ? 8
                    : item == AnimatedItem.ROW ? y + ((RowBlock) block).getItemY() : y;
                int col = item == AnimatedItem.COLUMN ? 3 + ((ColumnBlock) block).getItemX() : 0;
                int[][] grid = new int[20][10];
                grid[row][col] = 1;
                f.prepare(block, new OBlock(), grid, 3, y);
                var queued = f.controller.snapshot().next();
                f.controller.dispatch(GameCommand.HARD_DROP);
                assertEquals(GamePhase.RESOLVING, f.controller.snapshot().phase());
                var lockedGrid = f.controller.snapshot().fixedCells();
                for (int frame = 0; frame < item.frames; frame++) {
                    f.elapse(50_000_000L);
                    assertEquals(GamePhase.RESOLVING, f.controller.snapshot().phase());
                    assertEquals(lockedGrid, f.controller.snapshot().fixedCells());
                    assertEquals(0, f.controller.snapshot().score());
                    assertEquals(queued, f.controller.snapshot().next());
                }
                f.elapse(50_000_000L);
                var completed = f.controller.snapshot();
                assertEquals(GamePhase.ACTIVE, completed.phase());
                assertEquals(queued, completed.current());
                assertEquals(item.score, completed.score());
                assertEquals(item.lines, completed.clearedLines());
                assertNull(completed.fixedCells().get(row).get(col));
                f.elapse(0);
                assertEquals(completed, f.controller.snapshot(), "동일 시점 tick은 중복 확정하지 않는다");
            }
            return null;
        });
    }
    private static void press(Gamepanel game, int key) {
        var event = new KeyEvent(game, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
        for (var listener : game.getKeyListeners()) listener.keyPressed(event);
    }
    enum AnimatedItem {
        ROW(5, 100, 1), COLUMN(10, 0, 0), CROSS(10, 300, 2);
        final int frames, score, lines;
        AnimatedItem(int frames, int score, int lines) {
            this.frames = frames; this.score = score; this.lines = lines;
        }
        Block block() {
            return switch (this) {
                case ROW -> new RowBlock();
                case COLUMN -> new ColumnBlock();
                case CROSS -> new CrossBlock();
            };
        }
    }
}
