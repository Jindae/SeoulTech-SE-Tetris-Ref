package seoultech.se.tetris.component;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.event.ActionEvent;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.game.GamePhase;

class GameControllerLifecycleTest {
    @Test void pauseStopsSchedulingAndResumeStartsANewGravityPeriod() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                f.prepare(new seoultech.se.tetris.blocks.OBlock(), new seoultech.se.tetris.blocks.OBlock(),
                    new int[20][10], 3, 0);
                f.elapse(400_000_000L);
                f.controller.handleKey(Maincontainer.PAUSE);
                assertTrue(f.controller.snapshot().paused());
                assertFalse(f.controller.timer().isRunning());
                f.clock.addAndGet(10_000_000_000L);
                f.controller.handleKey(Maincontainer.DOWN);
                assertEquals(0, f.controller.snapshot().y());
                f.controller.handleKey(Maincontainer.PAUSE);
                assertFalse(f.controller.snapshot().paused());
                assertTrue(f.controller.timer().isRunning());
                f.elapse(999_000_000L);
                assertEquals(0, f.controller.snapshot().y());
                f.elapse(1_000_000L);
                assertEquals(1, f.controller.snapshot().y());
            }
            return null;
        });
    }

    @Test void naturalGameOverStopsTheTimerAndOpensNameEntryOnlyOnce(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary) throws Exception {
        String previous = System.getProperty("tetris.dataDir");
        System.setProperty("tetris.dataDir", temporary.toString());
        try {
            ExistingFeatureFixture.onEdt(() -> {
                try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                    int[][] grid = new int[20][10];
                    grid[0][3] = 1;
                    f.prepare(new seoultech.se.tetris.blocks.OBlock(), new seoultech.se.tetris.blocks.OBlock(),
                        grid, 3, 18);
                    var timer = f.controller.timer();
                    var stale = new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, timer.getActionCommand());
                    f.controller.dispatch(seoultech.se.tetris.game.GameCommand.HARD_DROP);
                    assertEquals(GamePhase.GAME_OVER, f.controller.snapshot().phase());
                    assertFalse(timer.isRunning());
                    var panel = ExistingFeatureFixture.get(f.game, "gameOverPanel", javax.swing.JPanel.class);
                    assertNotNull(panel);
                    assertEquals(1, f.game.getKeyListeners().length);
                    assertTrue(seoultech.se.tetris.score.ScoreStore.readRanked().isEmpty());
                    f.clock.addAndGet(10_000_000_000L);
                    for (var listener : timer.getActionListeners()) listener.actionPerformed(stale);
                    f.controller.dispatch(seoultech.se.tetris.game.GameCommand.HARD_DROP);
                    assertSame(panel, ExistingFeatureFixture.get(f.game, "gameOverPanel", javax.swing.JPanel.class));
                    assertEquals(1, f.game.getKeyListeners().length);
                    var enter = new java.awt.event.KeyEvent(f.game, java.awt.event.KeyEvent.KEY_PRESSED,
                        0, 0, java.awt.event.KeyEvent.VK_ENTER, java.awt.event.KeyEvent.CHAR_UNDEFINED);
                    for (var listener : f.game.getKeyListeners()) listener.keyPressed(enter);
                    assertEquals(1, f.owner.scoreboardTransitions);
                    assertEquals(1, seoultech.se.tetris.score.ScoreStore.readRanked().size());
                }
                return null;
            });
        } finally {
            if (previous == null) System.clearProperty("tetris.dataDir");
            else System.setProperty("tetris.dataDir", previous);
        }
    }

    @Test void constructionDoesNotStartAndStartIsIdempotent() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                assertEquals(GamePhase.NEW, f.controller.snapshot().phase());
                assertNull(f.controller.snapshot().current());
                assertFalse(f.controller.timer().isRunning());
                f.game.startGame();
                var started = f.controller.snapshot();
                assertTrue(f.controller.timer().isRunning());
                f.game.startGame();
                assertEquals(started, f.controller.snapshot());
            }
            return null;
        });
    }
    @Test void stopIsIdempotentAndRejectsLateInputOrRestart() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                f.game.startGame();
                var timer = f.controller.timer();
                var stale = new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, timer.getActionCommand());
                f.game.stopGame();
                var stopped = f.controller.snapshot();
                f.game.stopGame();
                f.clock.addAndGet(5_000_000_000L);
                for (var listener : timer.getActionListeners()) listener.actionPerformed(stale);
                f.controller.handleKey(Maincontainer.RIGHT);
                f.game.startGame();
                assertEquals(GamePhase.STOPPED, stopped.phase());
                assertEquals(stopped, f.controller.snapshot());
                assertFalse(timer.isRunning());
                assertFalse(f.owner.exitCalled);
            }
            return null;
        });
    }
}
