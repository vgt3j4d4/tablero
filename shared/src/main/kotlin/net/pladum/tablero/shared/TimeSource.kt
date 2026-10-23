package net.pladum.tablero.shared

/**
 * Monotonic time in nanoseconds. Production uses `SystemClock.elapsedRealtimeNanos()`; tests use a fake.
 * Never back this with wall-clock time.
 */
fun interface TimeSource {
    fun nowNanos(): Long
}

const val NANOS_PER_MS = 1_000_000L
