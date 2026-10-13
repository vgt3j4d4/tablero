package net.pladum.tablero.shared

import kotlinx.serialization.Serializable

/** Rules are data: everything that varies between game formats lives here, not in `if` branches. */
@Serializable
data class GameRules(
    /** Clock value for a reset or a new game. */
    val gameLengthMs: Long = DEFAULT_GAME_LENGTH_MS,
    /** Below this remaining time the clock is read with tenths (`SS.t`) instead of `MM:SS`. */
    val tenthsBelowMs: Long = DEFAULT_TENTHS_BELOW_MS,
) {
    companion object {
        const val DEFAULT_GAME_LENGTH_MS = 10 * 60_000L
        const val DEFAULT_TENTHS_BELOW_MS = 60_000L
        const val MIN_GAME_LENGTH_MS = 60_000L
        const val MAX_GAME_LENGTH_MS = 60 * 60_000L

        /** Upper bound for any clock value, including quick adjustments beyond the game length. */
        const val MAX_CLOCK_MS = MAX_GAME_LENGTH_MS
    }
}
