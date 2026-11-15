package seoultech.se.tetris.attack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import seoultech.se.tetris.blocks.LBlock;

class AttackRulesTest {
    @Test
    void zeroOrOneClearDoesNotAttack() {
        int[][] board = fullBoard(4, 10);
        BitSet none = new BitSet();
        BitSet one = new BitSet();
        one.set(3);

        assertTrue(AttackRules.fromNormalClear(board, none, new LBlock(), 0, 0).isEmpty());
        assertTrue(AttackRules.fromNormalClear(board, one, new LBlock(), 0, 0).isEmpty());
    }

    @Test
    void threeLineAttackPreservesLastLBlockHolesAndRowOrder() {
        int[][] board = fullBoard(4, 10);
        BitSet cleared = new BitSet();
        cleared.set(1, 4);
        LBlock block = new LBlock();
        block.rotate();

        AttackEvent event = AttackRules.fromNormalClear(board, cleared, block, 4, 1).orElseThrow();

        assertEquals(3, event.rows().size());
        for (int row = 0; row < event.rows().size(); row++) {
            boolean[] expected = new boolean[10];
            java.util.Arrays.fill(expected, true);
            for (int column = 0; column < block.width(); column++) {
                if (block.getShape(column, row) > 0) expected[4 + column] = false;
            }
            assertArrayEquals(expected, event.rows().get(row).cells());
        }
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

    private static AttackRow row(int hole) {
        boolean[] cells = {true, true, true};
        cells[hole] = false;
        return new AttackRow(cells);
    }

    private static int[][] fullBoard(int height, int width) {
        int[][] board = new int[height][width];
        for (int[] row : board) java.util.Arrays.fill(row, 1);
        return board;
    }
}
