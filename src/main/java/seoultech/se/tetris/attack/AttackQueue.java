package seoultech.se.tetris.attack;

import java.util.ArrayList;
import java.util.List;

/** 최대 10줄의 대기 공격 */
public final class AttackQueue {
    public static final int MAX_ROWS = 10;

    private final int width;
    private final ArrayList<AttackRow> rows = new ArrayList<>(MAX_ROWS);

    public AttackQueue(int width) {
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        this.width = width;
    }

    public void enqueue(AttackEvent event) {
        for (AttackRow row : event.rows()) {
            if (row.width() != width) throw new IllegalArgumentException("attack width mismatch");
        }
        for (AttackRow row : event.rows()) {
            if (rows.size() == MAX_ROWS) return;
            rows.add(row);
        }
    }

    public List<AttackRow> snapshot() {
        return List.copyOf(rows);
    }

    /** 대기 공격을 한 번만 꺼낸다. */
    public List<AttackRow> drain() {
        List<AttackRow> drained = List.copyOf(rows);
        rows.clear();
        return drained;
    }

    public int size() {
        return rows.size();
    }

    public void clear() {
        rows.clear();
    }
}
