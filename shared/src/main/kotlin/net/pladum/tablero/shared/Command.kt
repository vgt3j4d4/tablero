package net.pladum.tablero.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything that can change the game. Input adapters map keys or messages to commands; they never set state
 * directly. Serial names are stable because commands are persisted in the undo history.
 */
@Serializable
sealed interface Command {
    @Serializable
    @SerialName("toggle_clock")
    data object ToggleClock : Command

    @Serializable
    @SerialName("start_clock")
    data object StartClock : Command

    @Serializable
    @SerialName("stop_clock")
    data object StopClock : Command

    /** [delta] is +1 or −1; a score never goes below 0. */
    @Serializable
    @SerialName("score")
    data class Score(val side: Side, val delta: Int) : Command

    /** Relative; allowed while running or stopped. The result is clamped to `0..MAX_CLOCK_MS`. */
    @Serializable
    @SerialName("adjust_game_clock")
    data class AdjustGameClock(val deltaMs: Long) : Command

    /** Absolute correction; only while stopped. */
    @Serializable
    @SerialName("set_game_clock")
    data class SetGameClock(val remainingMs: Long) : Command

    /** Clock back to the game length, stopped; scores kept. */
    @Serializable
    @SerialName("reset_game_clock")
    data object ResetGameClock : Command

    /** Scores 0–0, clock at the game length, stopped, possession arrow hidden. */
    @Serializable
    @SerialName("new_game")
    data object NewGame : Command

    /** Points the possession arrow the other way; a hidden arrow shows, pointing to [Side.LEFT]. */
    @Serializable
    @SerialName("toggle_possession")
    data object TogglePossession : Command

    /** Hides the possession arrow. */
    @Serializable
    @SerialName("clear_possession")
    data object ClearPossession : Command

    /** Takes effect from the next reset or new game. */
    @Serializable
    @SerialName("set_game_length")
    data class SetGameLength(val gameLengthMs: Long) : Command

    /** Reverts the effect of the last applied command. */
    @Serializable
    @SerialName("undo")
    data object Undo : Command
}
