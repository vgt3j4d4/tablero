package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.NANOS_PER_MS
import net.pladum.tablero.tv.ClockField.MINUTES
import net.pladum.tablero.tv.ClockField.SECONDS
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SetClockEditorTest {
    private fun editor(remainingMs: Long) = SetClockEditor.of(remainingMs * NANOS_PER_MS, GameRules())

    @Test
    fun `starts from the clock rounded up to whole seconds, minutes focused`() {
        val editor = editor(7 * 60_000L + 42_300)
        assertEquals(7, editor.minutes)
        assertEquals(43, editor.seconds)
        assertEquals(MINUTES, editor.focused)
        assertEquals(ClockReading.MinutesSeconds(7, 43), editor.was)
    }

    @Test
    fun `remembers a reading with tenths as it was shown`() {
        val editor = editor(38_400)
        assertEquals(ClockReading.SecondsTenths(38, 4), editor.was)
        assertEquals(39_000, editor.remainingMs)
    }

    @Test
    fun `minutes stop at 0 and 60`() {
        assertEquals(0, editor(30_000).change(-1).minutes)
        val atMax = editor(59 * 60_000L).change(1).change(1)
        assertEquals(60, atMax.minutes)
    }

    @Test
    fun `seconds wrap around without carrying into the minutes`() {
        val up = editor(7 * 60_000L + 59_000).focus(SECONDS).change(1)
        assertEquals(7, up.minutes)
        assertEquals(0, up.seconds)
        val down = up.change(-1)
        assertEquals(59, down.seconds)
    }

    @Test
    fun `60 minutes has no seconds`() {
        val atMax = editor(59 * 60_000L + 30_000).change(1)
        assertEquals(60 * 60_000L, atMax.remainingMs)
        assertEquals(atMax, atMax.focus(SECONDS).change(1).focus(MINUTES))
    }

    @Test
    fun `edited only while the fields differ from the start`() {
        val start = editor(5 * 60_000L)
        assertFalse(start.edited)
        assertTrue(start.change(1).edited)
        assertFalse(start.change(1).change(-1).edited)
        assertFalse(start.focus(SECONDS).edited)
    }
}
