package net.pladum.tablero.shared

private const val NANOS_PER_TENTH = 100 * NANOS_PER_MS
private const val NANOS_PER_SECOND = 1_000 * NANOS_PER_MS

/**
 * What the clock display shows: numbers only, tv-app turns them into digits. Values are rounded **up** to the
 * displayed unit, so the display reads 0 only once the clock has expired.
 */
sealed interface ClockReading {
    /** `MM:SS`, used at or above the rules' tenths threshold. */
    data class MinutesSeconds(val minutes: Int, val seconds: Int) : ClockReading

    /** `SS.t`, used below the rules' tenths threshold. */
    data class SecondsTenths(val seconds: Int, val tenths: Int) : ClockReading

    companion object {
        fun of(remainingNanos: Long, rules: GameRules): ClockReading {
            val remaining = remainingNanos.coerceAtLeast(0)
            // Decide on the rounded value, so 59.95 s reads 1:00 rather than 60.0.
            val tenths = ceilDiv(remaining, NANOS_PER_TENTH)
            if (tenths * NANOS_PER_TENTH < rules.tenthsBelowMs * NANOS_PER_MS) {
                return SecondsTenths(seconds = (tenths / 10).toInt(), tenths = (tenths % 10).toInt())
            }
            val seconds = ceilDiv(remaining, NANOS_PER_SECOND)
            return MinutesSeconds(minutes = (seconds / 60).toInt(), seconds = (seconds % 60).toInt())
        }
    }
}

private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1) / divisor
