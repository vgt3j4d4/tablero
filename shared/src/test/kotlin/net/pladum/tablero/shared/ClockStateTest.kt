package net.pladum.tablero.shared

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClockStateTest {
    @Test
    fun `a stopped clock does not change with time`() {
        val clock = ClockState.stopped(5_000)
        assertFalse(clock.running)
        assertEquals(5_000 * NANOS_PER_MS, clock.remainingAt(Long.MAX_VALUE / 2))
    }

    @Test
    fun `a running clock counts down from its start instant and never goes below zero`() {
        val clock = ClockState.stopped(5_000).startAt(nowNanos = 1_000)
        assertTrue(clock.running)
        assertEquals(5_000 * NANOS_PER_MS, clock.remainingAt(1_000))
        assertEquals(3_000 * NANOS_PER_MS, clock.remainingAt(1_000 + 2_000 * NANOS_PER_MS))
        assertEquals(0, clock.remainingAt(1_000 + 9_000 * NANOS_PER_MS))
    }

    @Test
    fun `stopping keeps the remaining time at that instant`() {
        val clock = ClockState.stopped(5_000).startAt(0).stopAt(1_500 * NANOS_PER_MS)
        assertEquals(ClockState(3_500 * NANOS_PER_MS), clock)
    }
}
