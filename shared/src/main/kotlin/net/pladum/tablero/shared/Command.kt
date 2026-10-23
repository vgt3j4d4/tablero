package net.pladum.tablero.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything that can change the game. Input adapters map keys or messages to commands; they never set state
 * directly. Serial names are stable because commands are persisted in the undo history.
 */
@Serializable
sealed interface Command {
    /** Starts or stops the clock; during a break it ends the break. Recorded as what it resolved to. */
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

    /** Clock back to the period's length (the game length under Pickup), stopped, ending a break; scores kept. */
    @Serializable
    @SerialName("reset_game_clock")
    data object ResetGameClock : Command

    /** Scores 0–0, clock at the game length, stopped, possession arrow hidden; period 1 with no fouls. */
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

    /** Switches the rules while the clock is stopped; scores and clock stay, the game goes back to period 1. */
    @Serializable
    @SerialName("set_rules")
    data class SetRules(val preset: RulesPreset) : Command

    /** Period rules only: [delta] is +1 or −1; fouls stay within `0..MAX_FOULS`. */
    @Serializable
    @SerialName("foul")
    data class Foul(val side: Side, val delta: Int) : Command

    /**
     * Period rules only, at time up: on to [GameSnapshot.nextPeriod], with fouls back to 0 unless it is an overtime
     * that keeps them. [withBreak] runs the break on the game clock first; otherwise the clock shows the period's
     * length, stopped.
     */
    @Serializable
    @SerialName("next_period")
    data class NextPeriod(val withBreak: Boolean) : Command

    /** Ends a break before its time: the clock shows the period's length, stopped. */
    @Serializable
    @SerialName("end_break")
    data object EndBreak : Command

    /** Period rules only: corrects the period number by [delta] (+1 or −1), leaving fouls and the clock alone. */
    @Serializable
    @SerialName("change_period")
    data class ChangePeriod(val delta: Int) : Command

    /** Reverts the effect of the last applied command. */
    @Serializable
    @SerialName("undo")
    data object Undo : Command
}
