package seoultech.se.tetris.component;

import java.awt.event.KeyEvent;
import seoultech.se.tetris.game.GameCommand;

/**
 * Translates configured input and owns the single-player screen lifecycle.
 *
 * <p>RF-02 still delegates game rules to {@link Board}. Later phases replace
 * that delegate with the UI-independent game engine.</p>
 */
public final class GameController {
    private final Gamepanel gamepanel;
    private final Board board;
    private boolean started;
    private boolean stopped;

    GameController(Gamepanel gamepanel, Board board) {
        this.gamepanel = java.util.Objects.requireNonNull(gamepanel, "gamepanel");
        this.board = java.util.Objects.requireNonNull(board, "board");
    }

    void start() {
        if (started || stopped) return;
        started = true;
        board.boardStart();
    }

    void stop() {
        if (stopped) return;
        stopped = true;
        board.stopAnimations();
        if (board.timer != null) {
            board.timer.stop();
            board.timer = null;
        }
    }

    void exitToMenu() {
        if (stopped) return;
        stop();
        gamepanel.returnToStartScreen();
    }

    void handleKey(int keyCode) {
        if (!started || stopped) return;
        if (gamepanel.finishIfTimeExpired()) return;
        if (board.getIsPaused()) {
            if (keyCode == KeyEvent.VK_ENTER) {
                exitToMenu();
            } else if (keyCode == Maincontainer.PAUSE) {
                dispatch(GameCommand.TOGGLE_PAUSE);
            }
            return;
        }

        if (keyCode == KeyEvent.VK_ESCAPE && !isConfiguredGameKey(keyCode)) {
            exitToMenu();
            return;
        }
        if (keyCode == Maincontainer.DOWN) {
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
        if (!started || stopped) return;
        if (gamepanel.finishIfTimeExpired()) return;
        switch (java.util.Objects.requireNonNull(command, "command")) {
            case LEFT -> board.moveLeft();
            case RIGHT -> board.moveRight();
            case ROTATE -> board.rotate();
            case SOFT_DROP -> board.moveDown();
            case HARD_DROP -> board.hardDrop();
            case TOGGLE_PAUSE -> board.togglePause();
        }
        if (command != GameCommand.TOGGLE_PAUSE) board.drawBoard();
    }

    private static boolean isConfiguredGameKey(int keyCode) {
        return keyCode == Maincontainer.DOWN || keyCode == Maincontainer.RIGHT
            || keyCode == Maincontainer.LEFT || keyCode == Maincontainer.ROTATE
            || keyCode == Maincontainer.HARD_DROP || keyCode == Maincontainer.PAUSE;
    }
}
