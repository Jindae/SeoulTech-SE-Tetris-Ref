package seoultech.se.tetris;

import java.util.Locale;
import java.util.Optional;

public enum GameMode {
    CLASSIC("Classic"),
    ITEM("Item"),
    TIME_LIMIT("Time Limit");

    private final String displayName;

    GameMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<GameMode> parse(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (GameMode mode : values()) {
            if (mode.displayName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return Optional.of(mode);
            }
        }
        return Optional.empty();
    }
}
