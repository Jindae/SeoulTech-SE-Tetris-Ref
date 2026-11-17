package seoultech.se.tetris.game;

/** Natural terminal result. User-requested stop is not a game end. */
public record GameEnd(int score, int clearedLines, int level) {}
