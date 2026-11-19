package seoultech.se.tetris.game;

/** Why a battle ended; kept transport-neutral for later partial implementations. */
public enum MatchEndReason {
    TIME_EXPIRED,
    LOCAL_TOP_OUT,
    REMOTE_TOP_OUT,
    SIMULTANEOUS_TOP_OUT
}
