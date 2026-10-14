package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading.MinutesSeconds
import net.pladum.tablero.shared.ClockReading.SecondsTenths
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LedTextTest {
    @Test
    fun `minutes and seconds have no leading zero and two-digit seconds`() {
        assertEquals("10:00", MinutesSeconds(10, 0).format())
        assertEquals("9:42", MinutesSeconds(9, 42).format())
        assertEquals("1:05", MinutesSeconds(1, 5).format())
    }

    @Test
    fun `seconds and tenths`() {
        assertEquals("38.4", SecondsTenths(38, 4).format())
        assertEquals("5.0", SecondsTenths(5, 0).format())
        assertEquals("0.0", SecondsTenths(0, 0).format())
    }

    @Test
    fun `the clock ghost has a digit for every place the reading can light`() {
        assertEquals("88:88", MinutesSeconds(60, 0).ghost())
        assertEquals("88:88", MinutesSeconds(7, 42).ghost())
        assertEquals("88.8", SecondsTenths(59, 9).ghost())
        assertEquals("88.8", SecondsTenths(5, 0).ghost())
    }

    @Test
    fun `the score ghost has at least two digits`() {
        assertEquals("88", scoreGhost("0"))
        assertEquals("88", scoreGhost("51"))
        assertEquals("888", scoreGhost("102"))
    }

    @Test
    fun `durations`() {
        assertEquals("10:00", durationText(10 * 60_000L))
        assertEquals("60:00", durationText(60 * 60_000L))
        assertEquals("1:00", durationText(60_000L))
        assertEquals("0:05", durationText(5_000L))
    }
}
