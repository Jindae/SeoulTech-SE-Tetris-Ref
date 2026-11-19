package seoultech.se.tetris.attack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import seoultech.se.tetris.game.ClearBatch;

/* 줄 삭제 결과를 공격으로 변환하는 규칙 */
public final class AttackRules {
    private AttackRules() {}

    /** Converts ordinary completed-row facts into the exact rows sent to an opponent. */
    public static Optional<AttackEvent> fromClearBatches(List<ClearBatch> batches) {
        Objects.requireNonNull(batches, "batches");
        List<ClearBatch> ordinary = batches.stream()
            .filter(batch -> batch.cause() == ClearBatch.Cause.COMPLETED_ROWS)
            .toList();
        int clearedRows = ordinary.stream().mapToInt(batch -> batch.rows().size()).sum();
        if (clearedRows < 2) return Optional.empty();

        ArrayList<AttackRow> attackRows = new ArrayList<>(clearedRows);
        for (ClearBatch batch : ordinary) {
            for (int row = 0; row < batch.rowsBefore().size(); row++) {
                List<?> cells = batch.rowsBefore().get(row);
                List<Boolean> origins = batch.lockedPieceOriginMasks().get(row);
                boolean[] occupied = new boolean[cells.size()];
                for (int column = 0; column < cells.size(); column++) {
                    occupied[column] = cells.get(column) != null && !origins.get(column);
                }
                attackRows.add(new AttackRow(occupied));
            }
        }
        return Optional.of(new AttackEvent(attackRows));
    }
}
