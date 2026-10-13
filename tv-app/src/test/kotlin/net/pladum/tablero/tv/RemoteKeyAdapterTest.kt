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
import net.pladum.tablero.shared.Command.Score
import net.pladum.tablero.shared.Command.ToggleClock
import net.pladum.tablero.shared.Command.Undo
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Each helper replays what the framework delivers to the activity for one physical press. */
class RemoteKeyAdapterTest {
    private val dispatched = mutableListOf<Command>()
    private val keys = RemoteKeyAdapter { dispatched += it }

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
    fun `back short press undoes, long press is consumed without undoing`() {
        shortPress(KEYCODE_BACK)
        assertEquals(listOf<Command>(Undo), dispatched)
        assertTrue(longPress(KEYCODE_BACK))
        assertEquals(listOf<Command>(Undo), dispatched)
    }

    @ParameterizedTest
    @ValueSource(ints = [KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN])
    fun `up and down are consumed but do nothing yet`(keyCode: Int) {
        shortPress(keyCode)
        assertFalse(longPress(keyCode))
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
