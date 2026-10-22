package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.RulesPreset
import net.pladum.tablero.shared.TeamState
import net.pladum.tablero.tv.TimeUpChoice.BREAK
import net.pladum.tablero.tv.TimeUpChoice.NEW_GAME
import net.pladum.tablero.tv.TimeUpChoice.RESET_CLOCK
import net.pladum.tablero.tv.TimeUpChoice.SKIP_BREAK
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val PLAYING = GameSnapshot.initial()
private val TIME_UP = PLAYING.copy(timeUp = true)
private val FIBA = TIME_UP.copy(rules = GameRules(preset = RulesPreset.FIBA))

class TimeUpBarTest {
    private val dispatched = mutableListOf<Command>()

    /** What the scoreboard was told after each change: the focused button, or null for hidden. */
    private val shown = mutableListOf<TimeUpChoice?>()
    private val bar = TimeUpBar({ dispatched += it }, { shown += it })

    @Test
    fun `hidden until time is up`() {
        assertFalse(bar.isOpen)
        assertFalse(bar.isClosed)
        bar.onTimeUp(PLAYING)
        bar.pick()
        assertEquals(emptyList(), shown)
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `opens with new game focused when time is up, and hides when it no longer is`() {
        bar.onTimeUp(TIME_UP)
        assertTrue(bar.isOpen)
        bar.onTimeUp(TIME_UP) // Every later snapshot at time up.
        bar.onTimeUp(PLAYING)
        assertFalse(bar.isOpen)
        assertEquals(listOf(NEW_GAME, null), shown)
    }

    @Test
    fun `picks dispatch the focused choice`() {
        bar.onTimeUp(TIME_UP)
        bar.pick()
        bar.right()
        bar.pick()
        assertEquals(listOf<Command>(Command.NewGame, Command.ResetGameClock), dispatched)
        assertEquals(listOf<TimeUpChoice?>(NEW_GAME, RESET_CLOCK), shown)
    }

    @Test
    fun `at the end of a period it offers the break, focused, or skipping it`() {
        bar.onTimeUp(FIBA.copy(period = 2))
        bar.pick()
        bar.right()
        bar.pick()
        val breakOrNot = listOf<Command>(Command.NextPeriod(withBreak = true), Command.NextPeriod(withBreak = false))
        assertEquals(breakOrNot, dispatched)
        assertEquals(listOf<TimeUpChoice?>(BREAK, SKIP_BREAK), shown)
    }

    @Test
    fun `a score fixed with the bar closed can turn the game's end into the way to overtime`() {
        val ended = FIBA.copy(period = 4, left = TeamState(score = 1))
        bar.onTimeUp(ended)
        bar.close()
        bar.onTimeUp(ended.copy(left = TeamState(score = 0))) // Tied: overtime.
        assertTrue(bar.isClosed)
        bar.reopen()
        assertEquals(listOf(NEW_GAME, null, null, BREAK), shown)
    }

    @Test
    fun `focusing the focused choice changes nothing`() {
        bar.onTimeUp(TIME_UP)
        bar.left()
        assertEquals(listOf<TimeUpChoice?>(NEW_GAME), shown)
    }

    @Test
    fun `closing keeps time up, and reopening focuses new game again`() {
        bar.onTimeUp(TIME_UP)
        bar.right()
        bar.close()
        assertTrue(bar.isClosed)
        bar.pick()
        bar.right()
        bar.reopen()
        assertTrue(bar.isOpen)
        assertEquals(listOf(NEW_GAME, RESET_CLOCK, null, NEW_GAME), shown)
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `a closed bar opens again the next time the game reaches time up`() {
        bar.onTimeUp(TIME_UP)
        bar.close()
        bar.onTimeUp(PLAYING) // E.g. a new game, or time added with quick clock adjust.
        assertFalse(bar.isClosed)
        bar.onTimeUp(TIME_UP) // The clock ran out again, or an undo brought the finished game back.
        assertTrue(bar.isOpen)
        assertEquals(listOf(NEW_GAME, null, null, NEW_GAME), shown)
    }

    @Test
    fun `reopening does nothing unless the bar was closed at time up`() {
        bar.reopen()
        bar.onTimeUp(TIME_UP)
        bar.reopen()
        assertEquals(listOf<TimeUpChoice?>(NEW_GAME), shown)
    }
}
