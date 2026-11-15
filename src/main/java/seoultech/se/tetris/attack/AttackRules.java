package seoultech.se.tetris.attack;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import seoultech.se.tetris.blocks.Block;

/* 줄 삭제 결과를 공격으로 변환하는 규칙 */
public final class AttackRules {
    private AttackRules() {}

    public static Optional<AttackEvent> fromNormalClear(
            int[][] boardBeforeClear, BitSet clearedRows, Block lastBlock, int blockX, int blockY) {
        if (boardBeforeClear == null || clearedRows == null || lastBlock == null) {
            throw new NullPointerException("clear snapshot and last block are required");
        }
        if (clearedRows.cardinality() < 2) return Optional.empty();
        if (boardBeforeClear.length == 0 || boardBeforeClear[0].length == 0) {
            throw new IllegalArgumentException("board must not be empty");
        }

        int width = boardBeforeClear[0].length;
        List<AttackRow> attackRows = new ArrayList<>(clearedRows.cardinality());
        for (int row = clearedRows.nextSetBit(0); row >= 0; row = clearedRows.nextSetBit(row + 1)) {
            if (boardBeforeClear[row].length != width) {
                throw new IllegalArgumentException("board must be rectangular");
            }
            boolean[] occupied = new boolean[width];
            for (int column = 0; column < width; column++) {
                occupied[column] = boardBeforeClear[row][column] != 0
                    && !belongsToLastBlock(lastBlock, blockX, blockY, column, row);
            }
            attackRows.add(new AttackRow(occupied));
        }
        return Optional.of(new AttackEvent(attackRows));
    }

    private static boolean belongsToLastBlock(Block block, int x, int y, int column, int row) {
        int localX = column - x;
        int localY = row - y;
        return localX >= 0 && localX < block.width() && localY >= 0 && localY < block.height()
            && block.getShape(localX, localY) > 0;
    }
}
