package net.pladum.tablero.tv

import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_DPAD_CENTER
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import android.view.KeyEvent.KEYCODE_ENTER
import android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
import android.view.KeyEvent.KEYCODE_VOLUME_UP
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.Command.ClearPossession
import net.pladum.tablero.shared.Command.NewGame
import net.pladum.tablero.shared.Command.ResetGameClock
import net.pladum.tablero.shared.Command.Score
import net.pladum.tablero.shared.Command.ToggleClock
import net.pladum.tablero.shared.Command.TogglePossession
import net.pladum.tablero.shared.Command.Undo
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Records what the keys asked of quick clock adjust. */
private class FakeClockAdjust : ClockAdjustKeys {
    val calls = mutableListOf<String>()
    override var isOpen = false

    override fun open() {
        isOpen = true
        calls += "open"
    }

    override fun adjust(deltaMs: Long) {
        calls += "adjust $deltaMs"
    }

    override fun done() {
        isOpen = false
        calls += "done"
    }

    override fun cancel() {
        isOpen = false
        calls += "cancel"
    }
}

/** Records what the keys asked of the team foul bar; [rules] says whether period rules let it open. */
private class FakeFouls : FoulKeys {
    val calls = mutableListOf<String>()
    var rules = true
    override var isOpen = false

    override fun open(): Boolean {
        if (!rules) return false
        isOpen = true
        calls += "open"
        return true
    }

    override fun foul(side: Side) {
        isOpen = false
        calls += "foul $side"
    }

    override fun cancel() {
        isOpen = false
        calls += "cancel"
    }
}

/** Records what the keys asked of the menu. */
private class FakeMenu : MenuKeys {
    val calls = mutableListOf<String>()
    override var isOpen = false

    override fun open() {
        isOpen = true
        calls += "open"
    }

    override fun up() {
        calls += "up"
    }

    override fun down() {
        calls += "down"
    }

    override fun left() {
        calls += "left"
    }

    override fun right() {
        calls += "right"
    }

    override fun ok() {
        calls += "ok"
    }

    override fun okReleased() {
        calls += "ok released"
    }

    override fun back() {
        isOpen = false
        calls += "back"
    }
}

/** Each helper replays what the framework delivers to the activity for one physical press. */
class RemoteKeyAdapterTest {
    private val dispatched = mutableListOf<Command>()
    private val clockAdjust = FakeClockAdjust()
    private val fouls = FakeFouls()
    private val timeUp = TimeUpBar({ dispatched += it }, {})
    private val menu = FakeMenu()
    private val keys = RemoteKeyAdapter({ dispatched += it }, clockAdjust, fouls, timeUp, menu)

    private fun shortPress(keyCode: Int) {
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 0))
        assertTrue(keys.onKeyUp(keyCode, canceled = false))
    }

    /** Down, repeats while held, one long-press callback, then a canceled up. */
    private fun longPress(keyCode: Int): Boolean {
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 0))
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 1))
        val consumed = keys.onKeyLongPress(keyCode)
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 2))
        assertTrue(keys.onKeyUp(keyCode, canceled = consumed))
        return consumed
    }

    @Test
    fun `left and right short presses score +1`() {
        shortPress(KEYCODE_DPAD_LEFT)
        shortPress(KEYCODE_DPAD_RIGHT)
        shortPress(KEYCODE_DPAD_LEFT)
        assertEquals(listOf<Command>(Score(LEFT, 1), Score(RIGHT, 1), Score(LEFT, 1)), dispatched)
    }

    @Test
    fun `left and right long presses score -1 once, with no +1 on release`() {
        assertTrue(longPress(KEYCODE_DPAD_LEFT))
        assertTrue(longPress(KEYCODE_DPAD_RIGHT))
        assertEquals(listOf<Command>(Score(LEFT, -1), Score(RIGHT, -1)), dispatched)
    }

    @ParameterizedTest
    @ValueSource(ints = [KEYCODE_DPAD_CENTER, KEYCODE_ENTER, KEYCODE_NUMPAD_ENTER])
    fun `OK toggles the clock on key-down, once however long it is held`(keyCode: Int) {
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 0))
        assertEquals(listOf<Command>(ToggleClock), dispatched)
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 1))
        assertFalse(keys.onKeyLongPress(keyCode))
        assertTrue(keys.onKeyDown(keyCode, repeatCount = 2))
        assertTrue(keys.onKeyUp(keyCode, canceled = false))
        assertEquals(listOf<Command>(ToggleClock), dispatched)
    }

    @Test
    fun `back short press undoes, long press opens the menu without undoing`() {
        shortPress(KEYCODE_BACK)
        assertEquals(listOf<Command>(Undo), dispatched)
        assertTrue(longPress(KEYCODE_BACK))
        assertEquals(listOf<Command>(Undo), dispatched)
        assertEquals(listOf("open"), menu.calls)
    }

    @Test
    fun `a key-up without its key-down is ignored`() {
        // The activity was recreated (language change) while the key was held.
        assertTrue(keys.onKeyUp(KEYCODE_BACK, canceled = false))
        assertTrue(keys.onKeyUp(KEYCODE_DPAD_LEFT, canceled = false))
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `up short press flips the possession arrow, long press hides it with no flip on release`() {
        shortPress(KEYCODE_DPAD_UP)
        assertTrue(longPress(KEYCODE_DPAD_UP))
        assertEquals(listOf<Command>(TogglePossession, ClearPossession), dispatched)
        assertEquals(emptyList(), clockAdjust.calls)
    }

    @Nested
    inner class QuickClockAdjust {
        @Test
        fun `down opens it on release, since down also has a long press`() {
            assertTrue(keys.onKeyDown(KEYCODE_DPAD_DOWN, repeatCount = 0))
            assertEquals(emptyList(), clockAdjust.calls)
            assertTrue(keys.onKeyUp(KEYCODE_DPAD_DOWN, canceled = false))
            assertEquals(listOf("open"), clockAdjust.calls)
        }

        @Test
        fun `arrows adjust by a minute or a second and OK closes, all on key-down`() {
            shortPress(KEYCODE_DPAD_DOWN)
            listOf(KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_CENTER)
                .forEach(::shortPress)
            val expected = listOf("open", "adjust 60000", "adjust -60000", "adjust 1000", "adjust -1000", "done")
            assertEquals(expected, clockAdjust.calls)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `back cancels, and its release does not undo on the scoreboard`() {
            shortPress(KEYCODE_DPAD_DOWN)
            shortPress(KEYCODE_BACK)
            assertEquals(listOf("open", "cancel"), clockAdjust.calls)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `held keys neither repeat nor long-press`() {
            shortPress(KEYCODE_DPAD_DOWN)
            assertTrue(longPress(KEYCODE_DPAD_LEFT))
            assertTrue(longPress(KEYCODE_BACK))
            assertEquals(listOf("open", "adjust -1000", "cancel"), clockAdjust.calls)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `a key released after the overlay closed on its own does not act`() {
            shortPress(KEYCODE_DPAD_DOWN)
            assertTrue(keys.onKeyDown(KEYCODE_DPAD_LEFT, repeatCount = 0))
            clockAdjust.isOpen = false // Auto-close after a few idle seconds.
            assertTrue(keys.onKeyUp(KEYCODE_DPAD_LEFT, canceled = false))
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `a key held since before the overlay opened does not act on release`() {
            assertTrue(keys.onKeyDown(KEYCODE_DPAD_LEFT, repeatCount = 0))
            shortPress(KEYCODE_DPAD_DOWN)
            assertTrue(keys.onKeyUp(KEYCODE_DPAD_LEFT, canceled = false))
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `after closing, keys work on the scoreboard again`() {
            shortPress(KEYCODE_DPAD_DOWN)
            shortPress(KEYCODE_DPAD_CENTER)
            shortPress(KEYCODE_DPAD_LEFT)
            shortPress(KEYCODE_BACK)
            assertEquals(listOf<Command>(Score(LEFT, 1), Undo), dispatched)
        }
    }

    @Nested
    inner class TeamFouls {
        @Test
        fun `down held opens the foul bar, and its release opens nothing else`() {
            assertTrue(longPress(KEYCODE_DPAD_DOWN))
            assertEquals(listOf("open"), fouls.calls)
            assertEquals(emptyList(), clockAdjust.calls)
        }

        @Test
        fun `left or right gives that team the foul, on key-down, and their release does not score`() {
            longPress(KEYCODE_DPAD_DOWN)
            shortPress(KEYCODE_DPAD_RIGHT)
            longPress(KEYCODE_DPAD_DOWN)
            shortPress(KEYCODE_DPAD_LEFT)
            assertEquals(listOf("open", "foul RIGHT", "open", "foul LEFT"), fouls.calls)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `back cancels without undoing, the other keys do nothing`() {
            longPress(KEYCODE_DPAD_DOWN)
            listOf(KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_CENTER, KEYCODE_BACK).forEach(::shortPress)
            assertEquals(listOf("open", "cancel"), fouls.calls)
            assertEquals(emptyList(), dispatched)
            assertEquals(emptyList(), clockAdjust.calls)
        }

        @Test
        fun `under Pickup rules a held down opens quick clock adjust on release`() {
            fouls.rules = false
            assertFalse(longPress(KEYCODE_DPAD_DOWN))
            assertEquals(emptyList(), fouls.calls)
            assertEquals(listOf("open"), clockAdjust.calls)
        }

        @Test
        fun `down held does nothing over another overlay`() {
            shortPress(KEYCODE_DPAD_DOWN)
            assertTrue(longPress(KEYCODE_DPAD_DOWN))
            assertEquals(emptyList(), fouls.calls)
            assertEquals(listOf("open", "adjust -60000"), clockAdjust.calls)
        }
    }

    @Nested
    inner class TimeUp {
        @BeforeEach
        fun clockRanOut() = timeUp.onTimeUp(true)

        @Test
        fun `OK starts a new game, the focused choice when the bar opens`() {
            shortPress(KEYCODE_DPAD_CENTER)
            assertEquals(listOf<Command>(NewGame), dispatched)
        }

        @Test
        fun `right then OK resets the clock, left goes back to new game`() {
            shortPress(KEYCODE_DPAD_RIGHT)
            shortPress(KEYCODE_DPAD_CENTER)
            shortPress(KEYCODE_DPAD_LEFT)
            shortPress(KEYCODE_DPAD_CENTER)
            assertEquals(listOf<Command>(ResetGameClock, NewGame), dispatched)
        }

        @Test
        fun `back closes the bar without undoing, then keys work on the scoreboard`() {
            shortPress(KEYCODE_BACK)
            assertFalse(timeUp.isOpen)
            shortPress(KEYCODE_DPAD_LEFT)
            shortPress(KEYCODE_BACK)
            assertEquals(listOf<Command>(Score(LEFT, 1), Undo), dispatched)
        }

        @Test
        fun `OK opens a closed bar again instead of toggling the clock`() {
            shortPress(KEYCODE_BACK)
            shortPress(KEYCODE_DPAD_CENTER)
            assertTrue(timeUp.isOpen)
            shortPress(KEYCODE_DPAD_CENTER)
            assertEquals(listOf<Command>(NewGame), dispatched)
        }

        @Test
        fun `held keys neither repeat nor long-press`() {
            assertTrue(longPress(KEYCODE_DPAD_RIGHT))
            assertTrue(longPress(KEYCODE_BACK))
            assertEquals(emptyList(), dispatched)
            assertFalse(timeUp.isOpen)
        }

        @Test
        fun `up and down do nothing while the bar is open`() {
            shortPress(KEYCODE_DPAD_UP)
            shortPress(KEYCODE_DPAD_DOWN)
            assertEquals(emptyList(), clockAdjust.calls)
            assertEquals(emptyList(), dispatched)
            assertTrue(timeUp.isOpen)
        }

        @Test
        fun `quick clock adjust keeps the remote while open`() {
            timeUp.onTimeUp(false)
            shortPress(KEYCODE_DPAD_DOWN)
            timeUp.onTimeUp(true) // The clock ran out with the overlay open.
            shortPress(KEYCODE_DPAD_CENTER)
            assertEquals(listOf("open", "done"), clockAdjust.calls)
            shortPress(KEYCODE_DPAD_CENTER)
            assertEquals(listOf<Command>(NewGame), dispatched)
        }
    }

    @Nested
    inner class MenuOpen {
        @BeforeEach
        fun openMenu() {
            longPress(KEYCODE_BACK)
            menu.calls.clear()
        }

        @Test
        fun `keys drive the menu on key-down and never reach the scoreboard`() {
            listOf(KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_LEFT, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_CENTER)
                .forEach(::shortPress)
            assertEquals(listOf("up", "down", "left", "right", "ok", "ok released"), menu.calls)
            assertEquals(emptyList(), dispatched)
            assertEquals(emptyList(), clockAdjust.calls)
        }

        @Test
        fun `back closes it, and its release does not undo`() {
            shortPress(KEYCODE_BACK)
            assertEquals(listOf("back"), menu.calls)
            assertEquals(emptyList(), dispatched)
            shortPress(KEYCODE_BACK)
            assertEquals(listOf<Command>(Undo), dispatched)
        }

        @Test
        fun `held keys neither repeat nor long-press`() {
            assertTrue(longPress(KEYCODE_DPAD_LEFT))
            assertTrue(longPress(KEYCODE_BACK))
            assertEquals(listOf("left", "back"), menu.calls)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `OK held is one press, and its release reaches the menu even after a long press`() {
            assertTrue(longPress(KEYCODE_DPAD_CENTER))
            assertEquals(listOf("ok", "ok released"), menu.calls)
            assertEquals(emptyList(), dispatched)
        }

        @Test
        fun `only OK's release reaches the menu`() {
            shortPress(KEYCODE_DPAD_LEFT)
            shortPress(KEYCODE_BACK)
            assertEquals(listOf("left", "back"), menu.calls)
        }

        @Test
        fun `it keeps the remote when time runs out`() {
            timeUp.onTimeUp(true)
            shortPress(KEYCODE_DPAD_CENTER)
            assertEquals(listOf("ok", "ok released"), menu.calls)
            assertEquals(emptyList(), dispatched)
        }
    }

    @Test
    fun `back long press does not open the menu over quick clock adjust`() {
        shortPress(KEYCODE_DPAD_DOWN)
        assertTrue(longPress(KEYCODE_BACK))
        assertEquals(listOf("open", "cancel"), clockAdjust.calls)
        assertEquals(emptyList(), menu.calls)
    }

    @Test
    fun `at time up, back closes the bar and a second long press opens the menu`() {
        timeUp.onTimeUp(true)
        assertTrue(longPress(KEYCODE_BACK))
        assertFalse(timeUp.isOpen)
        assertEquals(emptyList(), menu.calls)
        assertTrue(longPress(KEYCODE_BACK))
        assertEquals(listOf("open"), menu.calls)
        assertEquals(emptyList(), dispatched)
    }

    @Test
    fun `other keys are left to the system`() {
        assertFalse(keys.onKeyDown(KEYCODE_VOLUME_UP, repeatCount = 0))
        assertFalse(keys.onKeyLongPress(KEYCODE_VOLUME_UP))
        assertFalse(keys.onKeyUp(KEYCODE_VOLUME_UP, canceled = false))
        assertEquals(emptyList(), dispatched)
    }
}
