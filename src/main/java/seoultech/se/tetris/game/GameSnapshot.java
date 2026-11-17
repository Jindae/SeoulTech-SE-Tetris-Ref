package seoultech.se.tetris.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import seoultech.se.tetris.Difficulty;
import seoultech.se.tetris.GameMode;

/** Deeply immutable display state. Null cells represent empty rectangular slots. */
public record GameSnapshot(
        int width,
        int height,
        GameMode mode,
        Difficulty difficulty,
        GamePhase phase,
        boolean paused,
        List<List<Cell>> fixedCells,
        Piece current,
        Piece next,
        Integer x,
        Integer y,
        Integer ghostY,
        int score,
        int clearedLines,
        int level,
        long dropIntervalNanos,
        List<Integer> highlightedRows,
        Effect effect) {

    public GameSnapshot {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(phase, "phase");
        fixedCells = immutableMatrix(fixedCells);
        highlightedRows = List.copyOf(highlightedRows);
    }

    private static <T> List<List<T>> immutableMatrix(List<List<T>> source) {
        ArrayList<List<T>> rows = new ArrayList<>(source.size());
        for (List<T> row : source) {
            rows.add(Collections.unmodifiableList(new ArrayList<>(row)));
        }
        return Collections.unmodifiableList(rows);
    }

    public record Cell(int value, String text, int argb) {
        public Cell {
            Objects.requireNonNull(text, "text");
        }
    }

    public record Piece(String kind, List<List<Cell>> cells) {
        public Piece {
            Objects.requireNonNull(kind, "kind");
            cells = immutableMatrix(cells);
        }
    }

    public record OverlayCell(int x, int y, String text, int argb) {
        public OverlayCell {
            Objects.requireNonNull(text, "text");
        }
    }

    public record Effect(String kind, int frame, List<OverlayCell> overlayCells) {
        public Effect {
            Objects.requireNonNull(kind, "kind");
            overlayCells = List.copyOf(overlayCells);
        }
    }
}
