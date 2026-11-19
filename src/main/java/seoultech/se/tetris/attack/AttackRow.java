package seoultech.se.tetris.attack;

import java.util.Arrays;

/* 공격 한 행 */
public final class AttackRow {
    private final boolean[] occupied;

    public AttackRow(boolean[] occupied) {
        if (occupied == null || occupied.length == 0) {
            throw new IllegalArgumentException("an attack row must have a width");
        }
        this.occupied = occupied.clone();
    }

    public int width() {
        return occupied.length;
    }

    public boolean occupiedAt(int column) {
        return occupied[column];
    }

    public boolean[] cells() {
        return occupied.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AttackRow row && Arrays.equals(occupied, row.occupied);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(occupied);
    }
}
