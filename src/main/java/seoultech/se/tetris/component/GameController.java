package seoultech.se.tetris.component;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;
import javax.swing.Timer;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameEngine;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;

/** Serializes input, elapsed time, engine updates, and immutable UI publication on the EDT. */
public final class GameController {
    private final Gamepanel gamepanel;
    private final Board board;
    private final GameEngine engine;
    private final LongSupplier clock;
    private final Timer timer;

    private boolean started;
    private boolean stopped;
    private boolean gameOverPublished;
    private long lastAdvanceNanos;
    private long timerGeneration;

    GameController(Gamepanel gamepanel, Board board, GameEngine engine, LongSupplier clock) {
        this.gamepanel = Objects.requireNonNull(gamepanel, "gamepanel");
        this.board = Objects.requireNonNull(board, "board");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        timer = new Timer(1, this::timerFired);
        timer.setRepeats(false);
        publish(engine.snapshot());
    }

    void start() {
        if (started || stopped) return;
        started = true;
        lastAdvanceNanos = clock.getAsLong();
        consume(engine.start(), true);
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        cancelTimer();
        consume(engine.stop(), false);
    }

    void exitToMenu() {
        if (stopped) return;
        stop();
        gamepanel.returnToStartScreen();
    }

    void handleKey(int keyCode) {
        if (!started || stopped) return;
        GameSnapshot current = snapshot();
        if (current.paused()) {
            if (keyCode == KeyEvent.VK_ENTER) {
                exitToMenu();
            } else if (keyCode == Maincontainer.PAUSE) {
                dispatch(GameCommand.TOGGLE_PAUSE);
            }
            return;
        }

        if (keyCode == KeyEvent.VK_ESCAPE && !isConfiguredGameKey(keyCode)) {
            exitToMenu();
        } else if (keyCode == Maincontainer.DOWN) {
            dispatch(GameCommand.SOFT_DROP);
        } else if (keyCode == Maincontainer.RIGHT) {
            dispatch(GameCommand.RIGHT);
        } else if (keyCode == Maincontainer.LEFT) {
            dispatch(GameCommand.LEFT);
        } else if (keyCode == Maincontainer.ROTATE) {
            dispatch(GameCommand.ROTATE);
        } else if (keyCode == Maincontainer.HARD_DROP) {
            dispatch(GameCommand.HARD_DROP);
        } else if (keyCode == Maincontainer.PAUSE) {
            dispatch(GameCommand.TOGGLE_PAUSE);
        }
    }

    void dispatch(GameCommand command) {
        Objects.requireNonNull(command, "command");
        if (!started || stopped) return;
        if (advanceToNow(false)) return;
        consume(engine.handle(command), true);
    }

    GameEngine engine() {
        return engine;
    }

    GameSnapshot snapshot() {
        return engine.snapshot();
    }

    Timer timer() {
        return timer;
    }

    void refreshSnapshot() {
        publish(engine.snapshot());
    }

    private void timerFired(ActionEvent event) {
        long callbackGeneration;
        try {
            callbackGeneration = Long.parseLong(event.getActionCommand());
        } catch (NumberFormatException ignored) {
            return;
        }
        if (stopped || callbackGeneration != timerGeneration) return;
        advanceToNow(true);
    }

    /** Returns true when advancing ended or stopped the session. */
    private boolean advanceToNow(boolean schedule) {
        long now = clock.getAsLong();
        long elapsed = Math.max(0L, now - lastAdvanceNanos);
        lastAdvanceNanos = now;
        consume(engine.advance(elapsed), schedule);
        return stopped || snapshot().phase() == seoultech.se.tetris.game.GamePhase.GAME_OVER;
    }

    private void consume(GameUpdate update, boolean schedule) {
        GameUpdate displayed = Objects.requireNonNull(update, "update");
        if (displayed.lockResolution().isPresent()) {
            displayed = engine.continueAfterLock(displayed.lockResolution().orElseThrow().turnId());
        }
        publish(displayed.snapshot());
        if (displayed.gameEnd().isPresent() && !gameOverPublished) {
            gameOverPublished = true;
            cancelTimer();
            gamepanel.gameOver();
            return;
        }
        if (schedule && !stopped) schedule(displayed.nextWakeupNanos());
    }

    private void publish(GameSnapshot snapshot) {
        board.showSnapshot(snapshot);
        gamepanel.showSnapshot(snapshot);
    }

    private void schedule(OptionalLong wakeupNanos) {
        timer.stop();
        long generation = ++timerGeneration;
        if (wakeupNanos.isEmpty() || stopped) return;
        long nanos = wakeupNanos.orElseThrow();
        long millis = Math.max(1L, (nanos + 999_999L) / 1_000_000L);
        int delay = millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
        timer.setActionCommand(Long.toString(generation));
        timer.setInitialDelay(delay);
        timer.restart();
    }

    private void cancelTimer() {
        timer.stop();
        timerGeneration++;
        timer.setActionCommand(Long.toString(timerGeneration));
    }

    private static boolean isConfiguredGameKey(int keyCode) {
        return keyCode == Maincontainer.DOWN || keyCode == Maincontainer.RIGHT
            || keyCode == Maincontainer.LEFT || keyCode == Maincontainer.ROTATE
            || keyCode == Maincontainer.HARD_DROP || keyCode == Maincontainer.PAUSE;
    }
}
