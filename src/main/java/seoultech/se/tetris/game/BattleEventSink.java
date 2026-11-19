package seoultech.se.tetris.game;

/**
 * Boundary for the later P2P implementation. Game logic emits facts only;
 * transport code decides how and when to deliver them to the peer.
 */
public interface BattleEventSink {
    BattleEventSink NONE = new BattleEventSink() {};

    default void scoreChanged(int score) {}

    /** Raw completed-line fact; R3-02 maps it to the agreed garbage attack. */
    default void linesCleared(int completedLines) {}

    default void timeExpired(int finalScore) {}

    default void localTopOut(int finalScore) {}

    default void matchDecided(MatchDecision decision) {}
}
