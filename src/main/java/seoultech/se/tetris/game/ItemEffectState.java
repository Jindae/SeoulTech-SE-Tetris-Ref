package seoultech.se.tetris.game;

import java.util.BitSet;

/** Package-owned progress for an animated item effect. */
final class ItemEffectState {
    static final long FRAME_NANOS = 50_000_000L;

    final Kind kind;
    final int firstRow;
    final int firstColumn;
    final int frames;
    final long turnId;
    final int clearedLinesBeforeLock;
    final BitSet rowsToRemove;
    int frame;
    long elapsedNanos;

    ItemEffectState(Kind kind, int firstRow, int firstColumn, int frames, long turnId,
            int clearedLinesBeforeLock, BitSet rowsToRemove) {
        this.kind = kind;
        this.firstRow = firstRow;
        this.firstColumn = firstColumn;
        this.frames = frames;
        this.turnId = turnId;
        this.clearedLinesBeforeLock = clearedLinesBeforeLock;
        this.rowsToRemove = (BitSet) rowsToRemove.clone();
    }

    enum Kind {
        ROW, COLUMN, CROSS
    }
}
