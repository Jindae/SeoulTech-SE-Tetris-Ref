package seoultech.se.tetris.component;

import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.Timer;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.game.GameConfig;
import seoultech.se.tetris.game.GameEngine;
import seoultech.se.tetris.game.GameEngineFixture;
import seoultech.se.tetris.game.GeneratorBlockSource;
import sun.misc.Unsafe;

/** Uses a real Swing controller with a deterministic monotonic clock. Run on the EDT. */
final class ControllerFixture implements AutoCloseable {
    final TestOwner owner;
    final AtomicLong clock = new AtomicLong();
    final GameEngine engine;
    final GameEngineFixture state;
    final Gamepanel game;
    final GameController controller;

    ControllerFixture(GameMode mode) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        owner = (TestOwner) ((Unsafe) field.get(null)).allocateInstance(TestOwner.class);
        owner.setting = owner.new Setting();
        engine = new GameEngine(new GameConfig(mode, Difficulty.NORMAL), new GeneratorBlockSource());
        state = new GameEngineFixture(engine);
        game = new Gamepanel(owner, mode, engine, clock::get);
        controller = game.controller();
    }

    void prepare(Block current, Block next, int[][] grid, int x, int y) {
        game.startGame();
        state.seed(current, next, x, y);
        state.seedGrid(grid);
        controller.refreshSnapshot();
    }

    void elapse(long nanos) {
        Timer timer = controller.timer();
        clock.addAndGet(nanos);
        fire(timer);
    }

    static void fire(Timer timer) {
        if (timer == null) throw new AssertionError("Expected a scheduled controller tick");
        for (var listener : timer.getActionListeners()) {
            listener.actionPerformed(new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, timer.getActionCommand()));
        }
    }

    @Override public void close() { game.stopGame(); }

    static final class TestOwner extends Maincontainer {
        private static final long serialVersionUID = 1L;
        boolean exitCalled;
        int scoreboardTransitions;
        @Override protected void exitGameEnterStart() { exitCalled = true; }
        @Override protected void exitGameEnterScoreboard(String name, int score,
                Difficulty difficulty, GameMode mode) { scoreboardTransitions++; }
    }
}
