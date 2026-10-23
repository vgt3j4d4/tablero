package net.pladum.tablero.shared

import kotlinx.serialization.Serializable

/**
 * A countdown clock. While running, [remainingNanos] is the remaining time at [startedAtNanos] and the current value
 * is computed on read, so the clock never drifts.
 */
@Serializable
data class ClockState(
    val remainingNanos: Long,
    /** [TimeSource] instant at which the clock was started; `null` while stopped. */
    val startedAtNanos: Long? = null,
) {
    val running: Boolean get() = startedAtNanos != null

    fun remainingAt(nowNanos: Long): Long {
        val startedAt = startedAtNanos ?: return remainingNanos
        return (remainingNanos - (nowNanos - startedAt)).coerceAtLeast(0)
    }

    internal fun startAt(nowNanos: Long): ClockState = ClockState(remainingNanos, nowNanos)

    internal fun stopAt(nowNanos: Long): ClockState = ClockState(remainingAt(nowNanos))

    internal fun shiftedBy(deltaNanos: Long): ClockState = copy(remainingNanos = remainingNanos + deltaNanos)

    companion object {
        fun stopped(remainingMs: Long): ClockState = ClockState(remainingMs * NANOS_PER_MS)
    }
}
