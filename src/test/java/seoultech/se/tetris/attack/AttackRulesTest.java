package seoultech.se.tetris.attack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.game.ClearBatch;
import seoultech.se.tetris.game.GameSnapshot;

class AttackRulesTest {
    @Test
    void zeroOrOneClearDoesNotAttack() {
        assertTrue(AttackRules.fromClearBatches(List.of()).isEmpty());
        assertTrue(AttackRules.fromClearBatches(List.of(batch(
            ClearBatch.Cause.COMPLETED_ROWS, new boolean[][] {{true, false, true}}))).isEmpty());
    }

    @Test
    void threeLineAttackPreservesLockedPieceHolesAndRowOrder() {
        boolean[][] priorCells = {
            {true, true, true},
            {true, true, true},
            {true, true, true}
        };
        boolean[][] lockedOrigins = {
            {false, true, false},
            {true, false, false},
            {false, false, true}
        };
        AttackEvent event = AttackRules.fromClearBatches(List.of(batch(
            ClearBatch.Cause.COMPLETED_ROWS, priorCells, lockedOrigins))).orElseThrow();

        assertEquals(3, event.rows().size());
        assertArrayEquals(new boolean[] {true, false, true}, event.rows().get(0).cells());
        assertArrayEquals(new boolean[] {false, true, true}, event.rows().get(1).cells());
        assertArrayEquals(new boolean[] {true, true, false}, event.rows().get(2).cells());
    }

    @Test
    void itemCausedRowsDoNotGenerateAnAttack() {
        ClearBatch item = batch(ClearBatch.Cause.ROW_ITEM,
            new boolean[][] {{true, true, true}, {true, true, true}});

        assertTrue(AttackRules.fromClearBatches(List.of(item)).isEmpty());
    }

    @Test
    void queueAppendsBelowExistingRowsAndDropsBottomOverflow() {
        AttackQueue queue = new AttackQueue(3);
        List<AttackRow> first = java.util.stream.IntStream.range(0, 8)
            .mapToObj(index -> row(index % 3)).toList();
        List<AttackRow> second = List.of(row(0), row(1), row(2), row(0));

        queue.enqueue(new AttackEvent(first));
        queue.enqueue(new AttackEvent(second));

        assertEquals(10, queue.size());
        assertEquals(first, queue.snapshot().subList(0, 8));
        assertEquals(second.subList(0, 2), queue.snapshot().subList(8, 10));
        assertEquals(10, queue.drain().size());
        assertEquals(0, queue.size());
    }

    @Test
    void fullQueueIgnoresNewAttack() {
        AttackQueue queue = new AttackQueue(3);
        List<AttackRow> ten = java.util.stream.IntStream.range(0, 10)
            .mapToObj(index -> row(index % 3)).toList();
        queue.enqueue(new AttackEvent(ten));

        queue.enqueue(new AttackEvent(List.of(row(1), row(2))));

        assertEquals(ten, queue.snapshot());
    }

    @Test
    void queueSnapshotIsImmutableAndWidthMismatchDoesNotPartiallyMutate() {
        AttackQueue queue = new AttackQueue(3);
        queue.enqueue(new AttackEvent(List.of(row(0), row(1))));

        assertThrows(UnsupportedOperationException.class, () -> queue.snapshot().clear());
        AttackEvent wrongWidth = new AttackEvent(List.of(
            new AttackRow(new boolean[] {true, false, true, true}),
            new AttackRow(new boolean[] {true, true, false, true})));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(wrongWidth));
        assertEquals(2, queue.size());
    }

    private static AttackRow row(int hole) {
        boolean[] cells = {true, true, true};
        cells[hole] = false;
        return new AttackRow(cells);
    }

    private static ClearBatch batch(ClearBatch.Cause cause, boolean[][] cells) {
        boolean[][] origins = new boolean[cells.length][cells[0].length];
        return batch(cause, cells, origins);
    }

    private static ClearBatch batch(ClearBatch.Cause cause, boolean[][] cells,
            boolean[][] origins) {
        List<Integer> rows = java.util.stream.IntStream.range(0, cells.length).boxed().toList();
        List<List<GameSnapshot.Cell>> rowsBefore = java.util.Arrays.stream(cells)
            .map(row -> java.util.stream.IntStream.range(0, row.length)
                .mapToObj(column -> row[column]
                    ? new GameSnapshot.Cell(1, "O", 0xff808080) : null)
                .toList())
            .toList();
        List<List<Boolean>> masks = java.util.Arrays.stream(origins)
            .map(row -> java.util.stream.IntStream.range(0, row.length)
                .mapToObj(column -> row[column]).toList())
            .toList();
        return new ClearBatch(cause, rows, rowsBefore, masks);
    }
}
