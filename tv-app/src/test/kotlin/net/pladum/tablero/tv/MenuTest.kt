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
    private val running = ClockState(10 * MINUTE_MS * NANOS_PER_MS, startedAtNanos = 0)
    private val dispatched = mutableListOf<Command>()
    private val applied = mutableListOf<Locale>()
    private val layouts = mutableListOf<BoardLayout>()
    private val modes = mutableListOf<BoardMode>()
    private var nowNanos = 0L

    /** The action the menu posted (the end of a hold on EXIT) and when it is due, until removed. */
    private var posted: Runnable? = null
    private var postedDelayMs = 0L
    private var exits = 0

    /** What the scoreboard was last told to show; null = closed. */
    private var shown: MenuState? = null
    private val menu = Menu(
        dispatch = { dispatched += it },
        snapshot = { snapshot },
        layout = BoardLayout.CLASSIC,
        applyLayout = { layouts += it },
        mode = BoardMode.INDOOR,
        applyMode = { modes += it },
        languages = listOf(ENGLISH, SPANISH),
        language = ENGLISH,
        applyLanguage = { applied += it },
        now = { nowNanos },
        postDelayed = { action, delayMs ->
            posted = action
            postedDelayMs = delayMs
        },
        removeCallbacks = { if (it === posted) posted = null },
        exitToAndroidTv = { exits++ },
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
        assertEquals(
            listOf<Command>(Command.Score(LEFT, 1), Command.Score(LEFT, 1), Command.Score(RIGHT, -1)),
            dispatched,
        )
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
    inner class Layout {
        @Test
        fun `each press applies the next layout at once, wrapping around`() {
            menu.open()
            focus(MenuRow.LAYOUT)
            assertEquals(BoardLayout.CLASSIC, shown?.layout)
            menu.right()
            assertEquals(listOf(BoardLayout.LARGE), layouts)
            assertEquals(BoardLayout.LARGE, shown?.layout)
            menu.right()
            assertEquals(listOf(BoardLayout.LARGE, BoardLayout.CLASSIC), layouts)
            menu.left()
            assertEquals(BoardLayout.LARGE, layouts.last())
            assertTrue(menu.isOpen)
        }

        @Test
        fun `leaving the row or closing the menu applies nothing more`() {
            menu.open()
            focus(MenuRow.LAYOUT)
            menu.right()
            menu.down()
            menu.back()
            assertEquals(listOf(BoardLayout.LARGE), layouts)
        }

        @Test
        fun `the menu shows the layout chosen last when it opens again`() {
            menu.open()
            focus(MenuRow.LAYOUT)
            menu.right()
            menu.back()
            menu.open()
            assertEquals(BoardLayout.LARGE, shown?.layout)
        }

        @Test
        fun `a saved layout is restored, and an unknown or missing one falls back to classic`() {
            assertEquals(BoardLayout.LARGE, LayoutSetting.resolve("LARGE"))
            assertEquals(BoardLayout.CLASSIC, LayoutSetting.resolve("HUGE"))
            assertEquals(BoardLayout.CLASSIC, LayoutSetting.resolve(null))
        }
    }

    @Nested
    inner class Mode {
        @Test
        fun `each press applies the next mode at once, wrapping around`() {
            menu.open()
            focus(MenuRow.MODE)
            assertEquals(BoardMode.INDOOR, shown?.mode)
            menu.right()
            assertEquals(listOf(BoardMode.OUTDOOR), modes)
            assertEquals(BoardMode.OUTDOOR, shown?.mode)
            menu.right()
            assertEquals(listOf(BoardMode.OUTDOOR, BoardMode.INDOOR), modes)
            menu.left()
            assertEquals(BoardMode.OUTDOOR, modes.last())
            assertTrue(menu.isOpen)
        }

        @Test
        fun `leaving the row or closing the menu applies nothing more`() {
            menu.open()
            focus(MenuRow.MODE)
            menu.right()
            menu.down()
            menu.back()
            assertEquals(listOf(BoardMode.OUTDOOR), modes)
        }

        @Test
        fun `the menu shows the mode chosen last when it opens again`() {
            menu.open()
            focus(MenuRow.MODE)
            menu.right()
            menu.back()
            menu.open()
            assertEquals(BoardMode.OUTDOOR, shown?.mode)
        }

        @Test
        fun `a saved mode is restored, and an unknown or missing one falls back to indoor`() {
            assertEquals(BoardMode.OUTDOOR, ModeSetting.resolve("OUTDOOR"))
            assertEquals(BoardMode.INDOOR, ModeSetting.resolve("NIGHT"))
            assertEquals(BoardMode.INDOOR, ModeSetting.resolve(null))
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
            snapshot = snapshot.copy(gameClock = running)
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

    @Nested
    inner class HowToUse {
        private fun openHelp() {
            menu.open()
            focus(MenuRow.HELP)
            menu.ok()
        }

        @Test
        fun `OK on how to use shows its screen`() {
            openHelp()
            assertEquals(true, shown?.help)
            assertEquals(MenuRow.HELP, shown?.focused)
        }

        @Test
        fun `the screen does not open while the clock runs`() {
            snapshot = snapshot.copy(gameClock = running)
            openHelp()
            assertEquals(false, shown?.help)
        }

        @Test
        fun `back or OK closes the screen back to the menu`() {
            openHelp()
            menu.back()
            assertEquals(false, shown?.help)
            assertEquals(MenuRow.HELP, shown?.focused)
            menu.ok()
            menu.ok()
            assertTrue(menu.isOpen)
            assertEquals(false, shown?.help)
        }

        @Test
        fun `the arrows do nothing while the screen shows`() {
            openHelp()
            val before = shown
            menu.up()
            menu.down()
            menu.left()
            menu.right()
            assertEquals(before, shown)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `shown on its own, back or OK closes the menu with it`() {
            assertTrue(menu.openHelp())
            assertEquals(true, shown?.help)
            assertEquals(MenuRow.HELP, shown?.focused)
            menu.back()
            assertFalse(menu.isOpen)
            assertNull(shown)
            menu.openHelp()
            menu.ok()
            assertFalse(menu.isOpen)
        }

        @Test
        fun `opened from the menu after being shown on its own, it closes back to the menu`() {
            menu.openHelp()
            menu.back()
            openHelp()
            menu.back()
            assertTrue(menu.isOpen)
        }

        @Test
        fun `is not shown on its own while the clock runs`() {
            snapshot = snapshot.copy(gameClock = running)
            assertFalse(menu.openHelp())
            assertFalse(menu.isOpen)
        }

        @Test
        fun `is not shown on its own over an open menu`() {
            menu.open()
            assertFalse(menu.openHelp())
            assertEquals(MenuRow.GAME_CLOCK, shown?.focused)
            assertEquals(false, shown?.help)
        }
    }

    @Nested
    inner class ExitToAndroidTv {
        private fun openConfirm() {
            menu.open()
            focus(MenuRow.EXIT)
            menu.ok()
        }

        /** OK held on EXIT for [ms]: the posted end of the hold runs when it is due. */
        private fun holdOk(ms: Long) {
            menu.ok()
            nowNanos += ms * NANOS_PER_MS
            val action = posted
            if (action != null && ms >= postedDelayMs) {
                posted = null
                action.run()
            }
        }

        @Test
        fun `OK on exit opens the confirm screen with cancel focused`() {
            openConfirm()
            assertEquals(ExitConfirm(ExitChoice.CANCEL), shown?.exitConfirm)
            assertEquals(MenuRow.EXIT, shown?.focused)
        }

        @Test
        fun `the confirm screen does not open while the clock runs`() {
            snapshot = snapshot.copy(gameClock = running)
            openConfirm()
            assertNull(shown?.exitConfirm)
            assertTrue(menu.isOpen)
        }

        @Test
        fun `OK on cancel and back both return to the menu`() {
            openConfirm()
            menu.ok()
            assertNull(shown?.exitConfirm)
            assertTrue(menu.isOpen)
            menu.ok()
            menu.right()
            menu.back()
            assertNull(shown?.exitConfirm)
            assertTrue(menu.isOpen)
            assertEquals(0, exits)
        }

        @Test
        fun `left and right move between the buttons, up and down do nothing`() {
            openConfirm()
            menu.right()
            assertEquals(ExitChoice.EXIT, shown?.exitConfirm?.focused)
            menu.up()
            menu.down()
            assertEquals(ExitChoice.EXIT, shown?.exitConfirm?.focused)
            assertEquals(MenuRow.EXIT, shown?.focused)
            menu.left()
            assertEquals(ExitChoice.CANCEL, shown?.exitConfirm?.focused)
        }

        @Test
        fun `holding OK on exit for two seconds closes the menu and exits`() {
            openConfirm()
            menu.right()
            nowNanos = 5_000 * NANOS_PER_MS
            menu.ok()
            assertEquals(5_000 * NANOS_PER_MS, shown?.exitConfirm?.holdStartedNanos)
            assertEquals(EXIT_HOLD_MS, postedDelayMs)
            assertEquals(0, exits)
            nowNanos += EXIT_HOLD_MS * NANOS_PER_MS
            assertNotNull(posted).run()
            assertEquals(1, exits)
            assertFalse(menu.isOpen)
            assertNull(shown)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `releasing OK sooner cancels the hold, and the next one starts over`() {
            openConfirm()
            menu.right()
            holdOk(1_500)
            menu.okReleased()
            assertNull(posted)
            assertEquals(ExitConfirm(ExitChoice.EXIT), shown?.exitConfirm)
            holdOk(1_500)
            assertEquals(0, exits)
            assertNotNull(posted)
            menu.okReleased()
            holdOk(EXIT_HOLD_MS)
            assertEquals(1, exits)
        }

        @Test
        fun `moving the focus, back or a pause cancel the hold`() {
            openConfirm()
            menu.right()
            menu.ok()
            menu.left()
            assertNull(posted)
            menu.right()
            menu.ok()
            menu.cancelExitHold()
            assertNull(posted)
            assertEquals(ExitConfirm(ExitChoice.EXIT), shown?.exitConfirm)
            menu.ok()
            menu.back()
            assertNull(posted)
            assertNull(shown?.exitConfirm)
            assertEquals(0, exits)
        }

        @Test
        fun `a clock started during the hold keeps the scoreboard`() {
            openConfirm()
            menu.right()
            menu.ok()
            snapshot = snapshot.copy(gameClock = running)
            assertNotNull(posted).run()
            assertEquals(0, exits)
            assertTrue(menu.isOpen)
            assertNull(shown?.exitConfirm)
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
