package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.NANOS_PER_MS

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60
private const val MAX_MINUTES = (GameRules.MAX_CLOCK_MS / (SECONDS_PER_MINUTE * MS_PER_SECOND)).toInt()

/** The set game clock dialog's fields, left to right. */
enum class ClockField { MINUTES, SECONDS }

/**
 * The set game clock dialog (design 08): an exact `MM:SS` for the stopped clock. ↑/↓ change the focused field:
 * minutes stop at 0 and 60, seconds wrap around 0–59 without carrying, and 60:00 (the longest clock) has no seconds.
 * Immutable; each key gives a new editor.
 */
data class SetClockEditor(
    /** The clock when the dialog opened (`Was 7:42`). */
    val was: ClockReading,
    val minutes: Int,
    val seconds: Int,
    val focused: ClockField = ClockField.MINUTES,
    /** The time the dialog started from; saving it unchanged leaves the clock alone. */
    private val startMs: Long = (minutes * SECONDS_PER_MINUTE + seconds) * MS_PER_SECOND,
) {
    val remainingMs: Long get() = (minutes * SECONDS_PER_MINUTE + seconds) * MS_PER_SECOND

    /** Whether the fields differ from where the dialog started. */
    val edited: Boolean get() = remainingMs != startMs

    fun focus(field: ClockField): SetClockEditor = copy(focused = field)

    /** ↑ (+1) or ↓ (−1) on the focused field. */
    fun change(delta: Int): SetClockEditor = when (focused) {
        ClockField.MINUTES -> {
            val minutes = (minutes + delta).coerceIn(0, MAX_MINUTES)
            copy(minutes = minutes, seconds = if (minutes == MAX_MINUTES) 0 else seconds)
        }
        ClockField.SECONDS ->
            if (minutes == MAX_MINUTES) this else copy(seconds = (seconds + delta).mod(SECONDS_PER_MINUTE))
    }

    companion object {
        /** Starts from a stopped clock with [remainingNanos] left, rounded up to whole seconds like the display. */
        fun of(remainingNanos: Long, rules: GameRules): SetClockEditor {
            val nanosPerSecond = MS_PER_SECOND * NANOS_PER_MS
            val seconds = (remainingNanos.coerceAtLeast(0) + nanosPerSecond - 1) / nanosPerSecond
            return SetClockEditor(
                was = ClockReading.of(remainingNanos, rules),
                minutes = (seconds / SECONDS_PER_MINUTE).toInt(),
                seconds = (seconds % SECONDS_PER_MINUTE).toInt(),
            )
        }
    }
}
