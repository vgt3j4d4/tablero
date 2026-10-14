package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.tv.TimeUpChoice.NEW_GAME
import net.pladum.tablero.tv.TimeUpChoice.RESET_CLOCK
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TimeUpBarTest {
    private val dispatched = mutableListOf<Command>()

    /** What the scoreboard was told after each change: the focused button, or null for hidden. */
    private val shown = mutableListOf<TimeUpChoice?>()
    private val bar = TimeUpBar({ dispatched += it }, { shown += it })

    @Test
    fun `hidden until time is up`() {
        assertFalse(bar.isOpen)
        assertFalse(bar.isClosed)
        bar.onTimeUp(false)
        bar.pick()
        assertEquals(emptyList(), shown)
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `opens with new game focused when time is up, and hides when it no longer is`() {
        bar.onTimeUp(true)
        assertTrue(bar.isOpen)
        bar.onTimeUp(true) // Every later snapshot at time up.
        bar.onTimeUp(false)
        assertFalse(bar.isOpen)
        assertEquals(listOf(NEW_GAME, null), shown)
    }

    @Test
    fun `picks dispatch the focused choice`() {
        bar.onTimeUp(true)
        bar.pick()
        bar.focus(RESET_CLOCK)
        bar.pick()
        assertEquals(listOf<Command>(Command.NewGame, Command.ResetGameClock), dispatched)
        assertEquals(listOf<TimeUpChoice?>(NEW_GAME, RESET_CLOCK), shown)
    }

    @Test
    fun `focusing the focused choice changes nothing`() {
        bar.onTimeUp(true)
        bar.focus(NEW_GAME)
        assertEquals(listOf<TimeUpChoice?>(NEW_GAME), shown)
    }

    @Test
    fun `closing keeps time up, and reopening focuses new game again`() {
        bar.onTimeUp(true)
        bar.focus(RESET_CLOCK)
        bar.close()
        assertTrue(bar.isClosed)
        bar.pick()
        bar.focus(RESET_CLOCK)
        bar.reopen()
        assertTrue(bar.isOpen)
        assertEquals(listOf(NEW_GAME, RESET_CLOCK, null, NEW_GAME), shown)
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `a closed bar opens again the next time the game reaches time up`() {
        bar.onTimeUp(true)
        bar.close()
        bar.onTimeUp(false) // E.g. a new game, or time added with quick clock adjust.
        assertFalse(bar.isClosed)
        bar.onTimeUp(true) // The clock ran out again, or an undo brought the finished game back.
        assertTrue(bar.isOpen)
        assertEquals(listOf(NEW_GAME, null, null, NEW_GAME), shown)
    }

    @Test
    fun `reopening does nothing unless the bar was closed at time up`() {
        bar.reopen()
        bar.onTimeUp(true)
        bar.reopen()
        assertEquals(listOf<TimeUpChoice?>(NEW_GAME), shown)
    }
}
