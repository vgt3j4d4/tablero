package net.pladum.tablero.shared

import net.pladum.tablero.shared.ClockReading.MinutesSeconds
import net.pladum.tablero.shared.ClockReading.SecondsTenths
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class ClockReadingTest {
    private val rules = GameRules()

    @ParameterizedTest(name = "{0} ms reads {1}:{2}")
    @CsvSource(
        "600000, 10, 0",
        "599999, 10, 0",
        "599000, 9, 59",
        "60001, 1, 1",
        "60000, 1, 0",
        // Rounds up to 60.0 tenths, so it stays on MM:SS.
        "59950, 1, 0",
        "59901, 1, 0",
    )
    fun `minutes and seconds round up`(remainingMs: Long, minutes: Int, seconds: Int) {
        assertEquals(MinutesSeconds(minutes, seconds), ClockReading.of(remainingMs * NANOS_PER_MS, rules))
    }

    @ParameterizedTest(name = "{0} ms reads {1}.{2}")
    @CsvSource(
        "59900, 59, 9",
        "59899, 59, 9",
        "10000, 10, 0",
        "1050, 1, 1",
        "100, 0, 1",
        "1, 0, 1",
        "0, 0, 0",
        "-5, 0, 0",
    )
    fun `seconds and tenths round up below a minute`(remainingMs: Long, seconds: Int, tenths: Int) {
        assertEquals(SecondsTenths(seconds, tenths), ClockReading.of(remainingMs * NANOS_PER_MS, rules))
    }

    @ParameterizedTest(name = "{0} ns reads 0.{1}")
    @CsvSource("1, 1", "99999999, 1", "100000000, 1", "100000001, 2")
    fun `sub-millisecond time rounds up to the next tenth`(remainingNanos: Long, tenths: Int) {
        assertEquals(SecondsTenths(0, tenths), ClockReading.of(remainingNanos, rules))
    }

    @ParameterizedTest(name = "threshold {0} ms: 30.5 s is tenths = {1}")
    @CsvSource("0, false", "30000, false", "30600, true", "60000, true")
    fun `the tenths threshold is rules data`(tenthsBelowMs: Long, tenths: Boolean) {
        val reading = ClockReading.of(30_500 * NANOS_PER_MS, GameRules(tenthsBelowMs = tenthsBelowMs))
        assertEquals(if (tenths) SecondsTenths(30, 5) else MinutesSeconds(0, 31), reading)
    }
}
