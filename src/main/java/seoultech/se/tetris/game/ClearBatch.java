package seoultech.se.tetris.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Facts captured immediately before one logical row-removal operation. */
public record ClearBatch(
        Cause cause,
        List<Integer> rows,
        List<List<GameSnapshot.Cell>> rowsBefore,
        List<List<Boolean>> lockedPieceOriginMasks) {

    public ClearBatch {
        Objects.requireNonNull(cause, "cause");
        rows = List.copyOf(rows);
        rowsBefore = immutableMatrix(rowsBefore);
        lockedPieceOriginMasks = immutableMatrix(lockedPieceOriginMasks);
    }

    private static <T> List<List<T>> immutableMatrix(List<List<T>> source) {
        ArrayList<List<T>> result = new ArrayList<>(source.size());
        for (List<T> row : source) result.add(Collections.unmodifiableList(new ArrayList<>(row)));
        return Collections.unmodifiableList(result);
    }

    public enum Cause {
        COMPLETED_ROWS, ROW_ITEM, COLUMN_ITEM, CROSS_ITEM, BOMB_ITEM, ANCHOR_ITEM
    }
}
