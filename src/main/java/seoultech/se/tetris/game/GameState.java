package seoultech.se.tetris.game;

import java.util.BitSet;
import seoultech.se.tetris.blocks.Block;

/** Package-owned mutable state. The public engine API never exposes this object. */
final class GameState {
    static final int WIDTH = 10;
    static final int HEIGHT = 20;

    final FixedCell[][] fixed = new FixedCell[HEIGHT][WIDTH];
    final BitSet highlightedRows = new BitSet(HEIGHT);
    Block current;
    Block next;
    int x = 3;
    int y;
    int score;
    int clearedLines;
    int level = 1;
    boolean paused;
    GamePhase phase = GamePhase.NEW;
    long gravityElapsedNanos;
    long highlightElapsedNanos;
    long nextTurnId = 1;
    LockResolution pendingLock;
    ItemEffectState itemEffect;
    int pendingItems;
    boolean anchorTouched;

    record FixedCell(int value, String text, int argb, long originTurnId) {}
}
