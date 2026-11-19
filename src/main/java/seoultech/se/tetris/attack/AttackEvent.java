package seoultech.se.tetris.attack;

import java.util.List;

/** 위에서 아래 순서의 공격 데이터 */
public record AttackEvent(List<AttackRow> rows) {
    public AttackEvent {
        rows = List.copyOf(rows);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("an attack requires at least two cleared rows");
        }
        int width = rows.getFirst().width();
        if (rows.stream().anyMatch(row -> row.width() != width)) {
            throw new IllegalArgumentException("attack rows must have the same width");
        }
    }
}
