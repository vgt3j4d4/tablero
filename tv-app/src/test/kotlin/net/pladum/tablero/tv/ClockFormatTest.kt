package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading.MinutesSeconds
import net.pladum.tablero.shared.ClockReading.SecondsTenths
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClockFormatTest {
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
}
