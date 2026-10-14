package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockState
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.NANOS_PER_MS
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE_MS = 60_000L
private val ENGLISH = Locale.forLanguageTag("en")
private val SPANISH = Locale.forLanguageTag("es")

class MenuTest {
    private var snapshot = GameSnapshot.initial()
    private val dispatched = mutableListOf<Command>()
    private val applied = mutableListOf<Locale>()

    /** What the scoreboard was last told to show; null = closed. */
    private var shown: MenuState? = null
    private val menu = Menu(
        dispatch = { dispatched += it },
        snapshot = { snapshot },
        languages = listOf(ENGLISH, SPANISH),
        language = ENGLISH,
        applyLanguage = { applied += it },
        onChange = { shown = it },
    )

    private fun focus(row: MenuRow) = repeat(row.ordinal) { menu.down() }

    @Test
    fun `opens on the first row and closes with back`() {
        menu.open()
        assertTrue(menu.isOpen)
        assertEquals(MenuRow.GAME_CLOCK, shown?.focused)
        menu.back()
        assertFalse(menu.isOpen)
        assertNull(shown)
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `up and down stop at the first and last rows`() {
        menu.open()
        menu.up()
        assertEquals(MenuRow.GAME_CLOCK, menu.focused)
        repeat(MenuRow.entries.size + 2) { menu.down() }
        assertEquals(MenuRow.entries.last(), menu.focused)
    }

    @Test
    fun `reopens on a given row`() {
        menu.open(MenuRow.LANGUAGE)
        assertEquals(MenuRow.LANGUAGE, shown?.focused)
    }

    @Test
    fun `score rows change the score with every press`() {
        menu.open()
        focus(MenuRow.HOME_SCORE)
        menu.right()
        menu.right()
        menu.down()
        menu.left()
        assertEquals(listOf<Command>(Command.Score(LEFT, 1), Command.Score(LEFT, 1), Command.Score(RIGHT, -1)), dispatched)
    }

    @Test
    fun `new game starts a new game and closes the menu`() {
        menu.open()
        focus(MenuRow.NEW_GAME)
        menu.ok()
        assertEquals(listOf<Command>(Command.NewGame), dispatched)
        assertFalse(menu.isOpen)
    }

    @Test
    fun `OK does nothing on rows that only change values`() {
        menu.open()
        focus(MenuRow.HOME_SCORE)
        menu.ok()
        assertEquals(emptyList(), dispatched)
        assertTrue(menu.isOpen)
    }

    @Nested
    inner class GameLength {
        @Test
        fun `changes stay pending until focus leaves the row, then apply as one command`() {
            menu.open()
            focus(MenuRow.GAME_LENGTH)
            menu.left()
            menu.left()
            menu.left()
            assertEquals(7 * MINUTE_MS, shown?.pendingGameLengthMs)
            assertEquals(emptyList(), dispatched)
            menu.down()
            assertEquals(listOf<Command>(Command.SetGameLength(7 * MINUTE_MS)), dispatched)
            assertNull(shown?.pendingGameLengthMs)
        }

        @Test
        fun `closing the menu applies a pending change`() {
            menu.open()
            focus(MenuRow.GAME_LENGTH)
            menu.right()
            menu.back()
            assertEquals(listOf<Command>(Command.SetGameLength(11 * MINUTE_MS)), dispatched)
        }

        @Test
        fun `stepping back to the current length applies nothing`() {
            menu.open()
            focus(MenuRow.GAME_LENGTH)
            menu.right()
            menu.left()
            menu.up()
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `applyGameLength applies it and keeps the menu open`() {
            menu.open()
            focus(MenuRow.GAME_LENGTH)
            menu.left()
            menu.applyGameLength()
            assertEquals(listOf<Command>(Command.SetGameLength(9 * MINUTE_MS)), dispatched)
            assertTrue(menu.isOpen)
            assertNull(shown?.pendingGameLengthMs)
        }

        @Test
        fun `steps by whole minutes within 1 to 60`() {
            assertEquals(2 * MINUTE_MS, stepGameLength(MINUTE_MS, 1))
            assertEquals(MINUTE_MS, stepGameLength(MINUTE_MS, -1))
            assertEquals(60 * MINUTE_MS, stepGameLength(60 * MINUTE_MS, 1))
            assertEquals(59 * MINUTE_MS, stepGameLength(60 * MINUTE_MS, -1))
        }

        @Test
        fun `a length that isn't whole minutes steps to the nearest one`() {
            val length = 7 * MINUTE_MS + 42_000
            assertEquals(8 * MINUTE_MS, stepGameLength(length, 1))
            assertEquals(7 * MINUTE_MS, stepGameLength(length, -1))
        }
    }

    @Nested
    inner class Language {
        @Test
        fun `a change stays pending until focus leaves the row`() {
            menu.open()
            focus(MenuRow.LANGUAGE)
            menu.right()
            assertEquals(SPANISH, shown?.language)
            assertEquals(emptyList(), applied)
            menu.up()
            assertEquals(listOf(SPANISH), applied)
        }

        @Test
        fun `choices wrap around, and coming back to the language in use applies nothing`() {
            menu.open()
            focus(MenuRow.LANGUAGE)
            menu.left()
            assertEquals(SPANISH, shown?.language)
            menu.left()
            assertEquals(ENGLISH, shown?.language)
            menu.back()
            assertEquals(emptyList(), applied)
        }

        @Test
        fun `closing the menu applies a pending change`() {
            menu.open()
            focus(MenuRow.LANGUAGE)
            menu.right()
            menu.back()
            assertEquals(listOf(SPANISH), applied)
            assertFalse(menu.isOpen)
        }
    }

    @Nested
    inner class SetGameClock {
        @Test
        fun `OK on game clock opens the dialog at the current time`() {
            snapshot = snapshot.copy(gameClock = ClockState.stopped(7 * MINUTE_MS + 42_000))
            menu.open()
            menu.ok()
            val editor = assertNotNull(shown?.setClock)
            assertEquals(7, editor.minutes)
            assertEquals(42, editor.seconds)
        }

        @Test
        fun `the dialog does not open while the clock runs`() {
            snapshot = snapshot.copy(gameClock = ClockState(10 * MINUTE_MS * NANOS_PER_MS, startedAtNanos = 0))
            menu.open()
            menu.ok()
            assertNull(shown?.setClock)
        }

        @Test
        fun `arrows edit the fields and OK saves the time, back in the menu`() {
            menu.open()
            menu.ok()
            menu.up() // 11:00
            menu.right()
            menu.down() // 11:59
            menu.ok()
            assertEquals(listOf<Command>(Command.SetGameClock(11 * MINUTE_MS + 59_000)), dispatched)
            assertNull(shown?.setClock)
            assertEquals(MenuRow.GAME_CLOCK, shown?.focused)
        }

        @Test
        fun `OK without a change leaves the clock alone`() {
            menu.open()
            menu.ok()
            menu.ok()
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `back cancels the dialog and keeps the menu open`() {
            menu.open()
            menu.ok()
            menu.up()
            menu.back()
            assertEquals(emptyList(), dispatched)
            assertTrue(menu.isOpen)
            assertNull(shown?.setClock)
        }
    }

    @Test
    fun `game length steps from the engine's length`() {
        snapshot = snapshot.copy(rules = GameRules(gameLengthMs = 7 * MINUTE_MS))
        menu.open()
        focus(MenuRow.GAME_LENGTH)
        menu.left()
        assertEquals(6 * MINUTE_MS, shown?.pendingGameLengthMs)
    }
}
