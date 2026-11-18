package seoultech.se.tetris.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import seoultech.se.tetris.GameMode;
import seoultech.se.tetris.blocks.Block;
import seoultech.se.tetris.game.BattleEventSink;
import seoultech.se.tetris.game.MatchClock;
import sun.misc.Unsafe;

class GameControllerLifecycleTest {
    @Test
    @Timeout(3)
    void constructionDoesNotStartAndStartIsIdempotent() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            GameFixture fixture = new GameFixture();
            try (fixture) {
                assertNull(field(fixture.board, "curr", Block.class));
                assertNull(field(fixture.board, "next", Block.class));
                assertFalse(fixture.initialTimer.isRunning());

                fixture.game.startGame();
                Block current = field(fixture.board, "curr", Block.class);
                Block next = field(fixture.board, "next", Block.class);
                assertTrue(fixture.initialTimer.isRunning());

                fixture.game.startGame();
                assertSame(current, field(fixture.board, "curr", Block.class));
                assertSame(next, field(fixture.board, "next", Block.class));
                assertSame(fixture.initialTimer, fixture.board.timer);
            }
            return null;
        });
    }

    @Test
    @Timeout(3)
    void stopIsIdempotentAndRejectsLateInputOrRestart() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            GameFixture fixture = new GameFixture();
            try (fixture) {
                fixture.game.startGame();
                int x = fixture.board.x;
                Block current = field(fixture.board, "curr", Block.class);
                fixture.game.stopGame();
                fixture.game.stopGame();

                assertNull(fixture.board.timer);
                assertFalse(fixture.initialTimer.isRunning());
                fixture.controller.handleKey(Maincontainer.RIGHT);
                fixture.game.startGame();

                assertSame(current, field(fixture.board, "curr", Block.class));
                assertEquals(x, fixture.board.x);
                assertNull(fixture.board.timer);
                assertFalse(fixture.owner.exitCalled);
            }
            return null;
        });
    }

    @Test
    @Timeout(3)
    void expiredDeadlineRejectsHardDropBeforeDisplayTimerTick() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            AtomicLong now = new AtomicLong();
            RecordingBattleEvents events = new RecordingBattleEvents();
            GameFixture fixture = new GameFixture(GameMode.TIME_LIMIT, events, now);
            try (fixture) {
                fixture.game.startGame();
                now.set(Duration.ofSeconds(1).toNanos());

                fixture.controller.handleKey(Maincontainer.HARD_DROP);

                assertEquals(0, fixture.board.getScore(),
                    "the score at the deadline must be final even before the display timer fires");
                assertEquals(0, events.expiredScore);
                assertEquals(1, events.expirationCount);
                assertNull(fixture.board.timer);
            }
            return null;
        });
    }

    @Test
    @Timeout(3)
    void expiredDeadlineRejectsAutomaticDropBeforeDisplayTimerTick() throws Exception {
        ExistingFeatureFixture.onEdt(() -> {
            AtomicLong now = new AtomicLong();
            RecordingBattleEvents events = new RecordingBattleEvents();
            GameFixture fixture = new GameFixture(GameMode.TIME_LIMIT, events, now);
            try (fixture) {
                fixture.game.startGame();
                now.set(Duration.ofSeconds(1).toNanos());

                fixture.initialTimer.getActionListeners()[0].actionPerformed(null);

                assertEquals(0, fixture.board.getScore());
                assertEquals(0, events.expiredScore);
                assertEquals(1, events.expirationCount);
                assertNull(fixture.board.timer);
            }
            return null;
        });
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(target));
    }

    private static final class GameFixture implements AutoCloseable {
        final TestMaincontainer owner;
        final Gamepanel game;
        final Board board;
        final GameController controller;
        final Timer initialTimer;

        GameFixture() throws Exception {
            this(GameMode.CLASSIC, BattleEventSink.NONE, null);
        }

        GameFixture(GameMode mode, BattleEventSink events, AtomicLong now) throws Exception {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            owner = (TestMaincontainer) ((Unsafe) unsafeField.get(null))
                .allocateInstance(TestMaincontainer.class);
            owner.setting = owner.new Setting();
            setField(owner, "timeLimitSeconds", 1);
            game = new Gamepanel(owner, mode, events);
            if (now != null) setField(game, "matchClock", clock(now));
            board = field(game, "board", Board.class);
            controller = field(game, "controller", GameController.class);
            initialTimer = board.timer;
        }

        @Override
        public void close() {
            game.stopGame();
            initialTimer.stop();
        }
    }

    private static MatchClock clock(AtomicLong now) throws Exception {
        Constructor<MatchClock> constructor = MatchClock.class
            .getDeclaredConstructor(Duration.class, LongSupplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance(Duration.ofSeconds(1), (LongSupplier) now::get);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> declaringClass = target instanceof Gamepanel
            ? target.getClass() : target.getClass().getSuperclass();
        Field field = declaringClass.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class RecordingBattleEvents implements BattleEventSink {
        int expirationCount;
        int expiredScore = -1;

        @Override
        public void timeExpired(int finalScore) {
            expirationCount++;
            expiredScore = finalScore;
        }
    }

    private static final class TestMaincontainer extends Maincontainer {
        private static final long serialVersionUID = 1L;
        boolean exitCalled;

        @Override
        protected void exitGameEnterStart() {
            exitCalled = true;
        }
    }
}
