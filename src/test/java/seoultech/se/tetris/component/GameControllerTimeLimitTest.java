package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.IBlock;
import seoultech.se.tetris.blocks.OBlock;
import seoultech.se.tetris.game.BattleEventSink;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GamePhase;

class GameControllerTimeLimitTest {
    @Test
    void samplesElapsedTimeAndDeadlineTogetherButRechecksBeforeInput() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            RecordingEvents events = new RecordingEvents();
            AtomicLong now = new AtomicLong();
            AtomicBoolean advancing = new AtomicBoolean();
            try (ControllerFixture f = new ControllerFixture(GameMode.TIME_LIMIT, events,
                    Duration.ofSeconds(2), () -> advancing.get() ? now.getAndIncrement() : now.get())) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                now.set(Duration.ofSeconds(2).toNanos() - 1);
                advancing.set(true);

                f.controller.dispatch(GameCommand.HARD_DROP);

                assertEquals(1, f.controller.snapshot().score(),
                    "the pre-deadline elapsed sample permits one gravity step, but not the late hard drop");
                assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
                assertEquals(1, events.expirations);
                assertEquals(1, events.expiredScore);
            }
            return null;
        });
    }

    @Test
    void deadlineRejectsInputAndPublishesExpirationExactlyOnce() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            RecordingEvents events = new RecordingEvents();
            try (ControllerFixture f = new ControllerFixture(
                    GameMode.TIME_LIMIT, events, Duration.ofSeconds(1))) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                var before = f.controller.snapshot();
                f.clock.set(Duration.ofSeconds(1).toNanos());

                f.controller.dispatch(GameCommand.HARD_DROP);
                ControllerFixture.fire(f.controller.timer());

                assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
                assertEquals(before.score(), f.controller.snapshot().score());
                assertEquals(1, events.expirations);
                assertEquals(0, events.expiredScore);
                assertEquals(0, events.topOuts);
                assertFalse(f.controller.timer().isRunning());
            }
            return null;
        });
    }

    @Test
    void timerChecksDeadlineBeforeAdvancingGravity() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            RecordingEvents events = new RecordingEvents();
            try (ControllerFixture f = new ControllerFixture(
                    GameMode.TIME_LIMIT, events, Duration.ofSeconds(1))) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                f.clock.set(Duration.ofSeconds(1).toNanos());

                ControllerFixture.fire(f.controller.timer());

                assertEquals(0, f.controller.snapshot().score());
                assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
                assertTrue(f.controller.snapshot().fixedCells().stream()
                    .flatMap(java.util.Collection::stream).allMatch(java.util.Objects::isNull),
                    "deadline must stop before gravity can lock or mutate the board");
                assertEquals(1, events.expirations);
            }
            return null;
        });
    }

    @Test
    void menuExitCancelsTimedMatchWithoutPublishingAResult() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            RecordingEvents events = new RecordingEvents();
            try (ControllerFixture f = new ControllerFixture(
                    GameMode.TIME_LIMIT, events, Duration.ofSeconds(1))) {
                f.prepare(new OBlock(), new OBlock(), new int[20][10], 3, 0);
                f.clock.set(Duration.ofSeconds(2).toNanos());

                f.controller.exitToMenu();

                assertTrue(f.owner.exitCalled);
                assertEquals(GamePhase.STOPPED, f.controller.snapshot().phase());
                assertEquals(0, events.expirations);
                assertEquals(0, events.topOuts);
            }
            return null;
        });
    }

    @Test
    void engineUpdatesPublishScoreLinesAndTopOutOnce() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            RecordingEvents events = new RecordingEvents();
            try (ControllerFixture f = new ControllerFixture(
                    GameMode.CLASSIC, events, Duration.ofSeconds(1))) {
                int[][] grid = new int[20][10];
                for (int column = 0; column < 6; column++) grid[19][column] = 1;
                f.prepare(new IBlock(), new OBlock(), grid, 6, 19);

                f.controller.dispatch(GameCommand.HARD_DROP);

                assertEquals(100, events.score);
                assertEquals(1, events.lines);
                assertEquals(0, events.topOuts);
            }

            RecordingEvents topOutEvents = new RecordingEvents();
            try (ControllerFixture f = new ControllerFixture(
                    GameMode.CLASSIC, topOutEvents, Duration.ofSeconds(1))) {
                int[][] grid = new int[20][10];
                grid[0][3] = 1;
                f.prepare(new OBlock(), new OBlock(), grid, 3, 18);

                f.controller.dispatch(GameCommand.HARD_DROP);
                f.controller.dispatch(GameCommand.HARD_DROP);

                assertEquals(1, topOutEvents.topOuts);
                assertEquals(0, topOutEvents.expirations);
            }
            return null;
        });
    }

    private static final class RecordingEvents implements BattleEventSink {
        int score;
        int lines;
        int expirations;
        int expiredScore = -1;
        int topOuts;

        @Override public void scoreChanged(int score) { this.score = score; }
        @Override public void linesCleared(int completedLines) { lines += completedLines; }
        @Override public void timeExpired(int finalScore) {
            expirations++;
            expiredScore = finalScore;
        }
        @Override public void localTopOut(int finalScore) { topOuts++; }
    }
}
