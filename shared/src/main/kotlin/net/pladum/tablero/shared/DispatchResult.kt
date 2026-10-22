package net.pladum.tablero.shared

sealed interface DispatchResult {
    data class Applied(val snapshot: GameSnapshot) : DispatchResult

    data class Rejected(val reason: RejectReason) : DispatchResult
}

enum class RejectReason {
    /** The clock ran out; give it time (adjust, set, reset) or start a new game first. */
    TIME_UP,
    CLOCK_RUNNING,
    CLOCK_STOPPED,
    CLOCK_AT_ZERO,
    SCORE_AT_ZERO,
    INVALID_ARGUMENT,

    /** A count would leave its range: fouls `0..MAX_FOULS`, the period `1..lastPeriod`. */
    OUT_OF_RANGE,

    /** The command needs period rules, and the game is played under Pickup rules. */
    NOT_IN_RULES,

    /** Not at time up, or the game is over: there is no next period. */
    NO_NEXT_PERIOD,
    NOT_IN_BREAK,

    /** The command would leave the state exactly as it is. */
    NO_CHANGE,
    NOTHING_TO_UNDO,
}
