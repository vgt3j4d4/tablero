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

    /** The command would leave the state exactly as it is. */
    NO_CHANGE,
    NOTHING_TO_UNDO,
}
