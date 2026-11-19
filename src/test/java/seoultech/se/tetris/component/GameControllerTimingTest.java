package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.event.ActionEvent;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GamePhase;

class GameControllerTimingTest {
    @Test
    void inputAccountsForElapsedTimeOnceAndDiscardsAnOlderScheduledCallback() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                var timer = f.controller.timer();
                var oldTick = new ActionEvent(timer, ActionEvent.ACTION_PERFORMED, timer.getActionCommand());
                f.clock.set(1_000_000_000L);
                f.controller.dispatch(GameCommand.RIGHT);
                var afterInput = f.controller.snapshot();
                assertEquals(4, afterInput.x());
                assertEquals(1, afterInput.y());
                assertEquals(1, afterInput.score());
                f.clock.set(2_000_000_000L);
                for (var listener : timer.getActionListeners()) listener.actionPerformed(oldTick);
                assertEquals(afterInput, f.controller.snapshot());
                ControllerFixture.fire(timer);
                assertEquals(2, f.controller.snapshot().y());
            }
            return null;
        });
    }

    @Test
    void menuExitDoesNotAdvanceOverdueTimeOrSaveAResult() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            try (ControllerFixture f = new ControllerFixture(GameMode.CLASSIC)) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 18);
                f.clock.set(10_000_000_000L);
                f.controller.exitToMenu();
                assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
                assertEquals(0, f.controller.snapshot().score());
                assertTrue(f.controller.snapshot().fixedCells().stream()
                    .flatMap(java.util.Collection::stream).allMatch(java.util.Objects::isNull));
                assertTrue(f.owner.exitCalled);
                assertFalse(f.controller.timer().isRunning());
            }
            return null;
        });
    }
}
