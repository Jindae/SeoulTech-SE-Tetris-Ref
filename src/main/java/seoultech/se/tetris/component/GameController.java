package seoultech.se.tetris.component;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.swing.Timer;
import seoultech.se.tetris.attack.AttackEvent;
import seoultech.se.tetris.game.BattleEventSink;
import seoultech.se.tetris.game.ClearBatch;
import seoultech.se.tetris.game.GameCommand;
import seoultech.se.tetris.game.GameEngine;
import seoultech.se.tetris.game.GamePhase;
import seoultech.se.tetris.game.GameSnapshot;
import seoultech.se.tetris.game.GameUpdate;
import seoultech.se.tetris.game.MatchClock;

/** Serializes input, elapsed time, engine updates, and immutable UI publication on the EDT. */
public final class GameController {
    private final Gamepanel gamepanel;
    private final Board board;
    private final GameEngine engine;
    private final LongSupplier clock;
    private final MatchClock matchClock;
    private final BattleEventSink battleEvents;
    private final Timer timer;

    private boolean started;
    private boolean stopped;
    private boolean gameOverPublished;
    private long lastAdvanceNanos;
    private long timerGeneration;
    private int publishedScore;
    private Consumer<AttackEvent> attackSink = ignored -> {};

    GameController(Gamepanel gamepanel, Board board, GameEngine engine, LongSupplier clock,
            MatchClock matchClock, BattleEventSink battleEvents) {
        this.gamepanel = Objects.requireNonNull(gamepanel, "gamepanel");
        this.board = Objects.requireNonNull(board, "board");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.matchClock = matchClock;
        this.battleEvents = Objects.requireNonNull(battleEvents, "battleEvents");
        publishedScore = engine.snapshot().score();
        timer = new Timer(1, this::timerFired);
        timer.setRepeats(false);
        publish(engine.snapshot());
    }

    void start() {
        if (started || stopped) return;
        started = true;
        if (matchClock != null) matchClock.start();
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
        if (expireIfNeeded()) return;
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

    /** Connects locally generated attacks to a match or transport coordinator. */
    public void setAttackSink(Consumer<AttackEvent> sink) {
        attackSink = Objects.requireNonNull(sink, "sink");
    }

    /** Routes an opponent attack through the same serialized controller boundary as input. */
    public void receiveAttack(AttackEvent attack) {
        Objects.requireNonNull(attack, "attack");
        if (!started || stopped) return;
        if (advanceToNow(false)) return;
        consume(engine.receiveAttack(attack), true);
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
        if (expireIfNeeded()) return true;
        long elapsed = Math.max(0L, now - lastAdvanceNanos);
        lastAdvanceNanos = now;
        consume(engine.advance(elapsed), schedule);
        return stopped || snapshot().phase() == GamePhase.GAME_OVER;
    }

    private void consume(GameUpdate update, boolean schedule) {
        GameUpdate displayed = Objects.requireNonNull(update, "update");
        if (displayed.lockResolution().isPresent()) {
            var resolution = displayed.lockResolution().orElseThrow();
            publishCompletedLines(resolution.clearBatches());
            resolution.attack().ifPresent(attackSink);
            displayed = engine.continueAfterLock(resolution.turnId());
        }
        publish(displayed.snapshot());
        if (displayed.gameEnd().isPresent() && !gameOverPublished) {
            gameOverPublished = true;
            cancelTimer();
            battleEvents.localTopOut(displayed.snapshot().score());
            gamepanel.gameOver();
            return;
        }
        if (schedule && !stopped) schedule(displayed.nextWakeupNanos());
    }

    private void publish(GameSnapshot snapshot) {
        board.showSnapshot(snapshot);
        gamepanel.showSnapshot(snapshot);
        if (snapshot.score() != publishedScore) {
            publishedScore = snapshot.score();
            battleEvents.scoreChanged(publishedScore);
        }
        if (matchClock != null) gamepanel.showRemainingTime(matchClock.remainingSeconds());
    }

    private void schedule(OptionalLong wakeupNanos) {
        timer.stop();
        long generation = ++timerGeneration;
        if (stopped) return;
        long nanos = wakeupNanos.orElse(Long.MAX_VALUE);
        if (matchClock != null) {
            nanos = Math.min(nanos, matchClock.remainingNanos());
            nanos = Math.min(nanos, 1_000_000_000L);
        }
        if (nanos == Long.MAX_VALUE) return;
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

    private boolean expireIfNeeded() {
        if (matchClock == null || !matchClock.isExpired()) return false;
        if (gameOverPublished || stopped) return true;
        gameOverPublished = true;
        stopped = true;
        cancelTimer();
        GameUpdate stoppedUpdate = engine.stop();
        publish(stoppedUpdate.snapshot());
        battleEvents.timeExpired(stoppedUpdate.snapshot().score());
        gamepanel.gameOver("Time Up");
        return true;
    }

    private void publishCompletedLines(List<ClearBatch> batches) {
        int completedLines = batches.stream()
            .filter(batch -> batch.cause() == ClearBatch.Cause.COMPLETED_ROWS)
            .mapToInt(batch -> batch.rows().size())
            .sum();
        if (completedLines > 0) battleEvents.linesCleared(completedLines);
    }

    private static boolean isConfiguredGameKey(int keyCode) {
        return keyCode == Maincontainer.DOWN || keyCode == Maincontainer.RIGHT
            || keyCode == Maincontainer.LEFT || keyCode == Maincontainer.ROTATE
            || keyCode == Maincontainer.HARD_DROP || keyCode == Maincontainer.PAUSE;
    }
}
