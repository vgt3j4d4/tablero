package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading

/** Unlit segments behind a `MM:SS` reading; the clock never exceeds 60:00. */
private const val CLOCK_GHOST_MINUTES = "88:88"

/** Unlit segments behind a `SS.t` reading; tenths only show below 1:00. */
private const val CLOCK_GHOST_TENTHS = "88.8"

private const val MIN_SCORE_DIGITS = 2

/**
 * Digits for the clock display: `9:42`, `10:00`, `38.4`. No leading zero: the empty leading digit shows as unlit
 * segments of the [ghost], as on real scoreboards.
 */
fun ClockReading.format(): String = when (this) {
    is ClockReading.MinutesSeconds -> "$minutes:${seconds.toString().padStart(2, '0')}"
    is ClockReading.SecondsTenths -> "$seconds.$tenths"
}

/**
 * The unlit "8" segments drawn behind this reading. Digits are drawn right-aligned over it; in DSEG7 every digit
 * has the same width and `.` takes none, so they line up.
 */
fun ClockReading.ghost(): String = when (this) {
    is ClockReading.MinutesSeconds -> CLOCK_GHOST_MINUTES
    is ClockReading.SecondsTenths -> CLOCK_GHOST_TENTHS
}

/** The unlit segments behind a score: two digits, more from 100 up. */
fun scoreGhost(score: String): String = "8".repeat(maxOf(MIN_SCORE_DIGITS, score.length))

/** A duration in whole seconds as `M:SS` (`10:00`, `0:05`): the game length, a clock adjustment. */
fun durationText(durationMs: Long): String {
    val seconds = durationMs / 1_000
    return ClockReading.MinutesSeconds((seconds / 60).toInt(), (seconds % 60).toInt()).format()
}
