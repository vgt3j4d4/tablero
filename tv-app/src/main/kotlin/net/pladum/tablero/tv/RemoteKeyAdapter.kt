package net.pladum.tablero.tv

import android.view.KeyEvent
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.Side

/** What the keys drive while quick clock adjust (↓) is open; [MainActivity] runs the [ClockAdjustSession]. */
interface ClockAdjustKeys {
    val isOpen: Boolean

    fun open()

    fun adjust(deltaMs: Long)

    /** OK: keep the adjustments and close. */
    fun done()

    /** Back: revert the session's adjustments and close. */
    fun cancel()
}

/**
 * Maps the stick remote's keys to [Command]s (see CLAUDE.md → Remote control mapping). Takes primitives instead of
 * [KeyEvent]s so it can be unit tested; [MainActivity] forwards its key callbacks here.
 *
 * Each method returns whether the key is ours. A mapped key is consumed at every stage, so the system never acts
 * on it (Back must never finish the activity). Key repeats are ignored; the long press is reported once by the
 * framework, and the key-up that follows it arrives canceled.
 *
 * Quick clock adjust and the time-up bar act on key-down, so a key pressed there (or ↓, which opens quick adjust)
 * is swallowed until it is released: its key-up must not act again on the scoreboard once the overlay has closed
 * (Back would undo).
 */
class RemoteKeyAdapter(
    private val dispatch: (Command) -> Unit,
    private val clockAdjust: ClockAdjustKeys,
    private val timeUp: TimeUpKeys,
) {
    private val swallowed = mutableSetOf<Key>()

    /** Returns true when the key is ours; the caller then starts tracking it for a long press. */
    fun onKeyDown(keyCode: Int, repeatCount: Int): Boolean {
        val key = Key.of(keyCode) ?: return false
        if (repeatCount > 0 || key in swallowed) return true
        when {
            clockAdjust.isOpen -> {
                swallowed += key
                adjustKey(key)
            }
            timeUp.isOpen -> {
                swallowed += key
                timeUpKey(key)
            }
            key == Key.OK && timeUp.isClosed -> {
                swallowed += key
                timeUp.reopen() // The clock can't start at time up anyway.
            }
            key == Key.OK -> dispatch(Command.ToggleClock) // On key-down: no delay.
            key == Key.DOWN -> {
                swallowed += key
                clockAdjust.open()
            }
        }
        return true
    }

    fun onKeyLongPress(keyCode: Int): Boolean {
        val key = Key.of(keyCode) ?: return false
        if (key in swallowed || clockAdjust.isOpen || timeUp.isOpen) return true
        return when (key) {
            Key.LEFT -> true.also { dispatch(Command.Score(Side.LEFT, -1)) }
            Key.RIGHT -> true.also { dispatch(Command.Score(Side.RIGHT, -1)) }
            Key.BACK -> true // The menu comes with #9.
            Key.OK, Key.UP, Key.DOWN -> false
        }
    }

    fun onKeyUp(keyCode: Int, canceled: Boolean): Boolean {
        val key = Key.of(keyCode) ?: return false
        if (swallowed.remove(key) || canceled || clockAdjust.isOpen || timeUp.isOpen) return true
        when (key) {
            Key.LEFT -> dispatch(Command.Score(Side.LEFT, 1))
            Key.RIGHT -> dispatch(Command.Score(Side.RIGHT, 1))
            Key.BACK -> dispatch(Command.Undo)
            Key.OK, Key.UP, Key.DOWN -> Unit // ↑ unused; OK acted on key-down; ↓ opened quick adjust on key-down.
        }
        return true
    }

    private fun adjustKey(key: Key) = when (key) {
        Key.UP -> clockAdjust.adjust(ADJUST_MINUTE_MS)
        Key.DOWN -> clockAdjust.adjust(-ADJUST_MINUTE_MS)
        Key.RIGHT -> clockAdjust.adjust(ADJUST_SECOND_MS)
        Key.LEFT -> clockAdjust.adjust(-ADJUST_SECOND_MS)
        Key.OK -> clockAdjust.done()
        Key.BACK -> clockAdjust.cancel()
    }

    private fun timeUpKey(key: Key) = when (key) {
        Key.LEFT -> timeUp.focus(TimeUpChoice.NEW_GAME)
        Key.RIGHT -> timeUp.focus(TimeUpChoice.RESET_CLOCK)
        Key.OK -> timeUp.pick()
        Key.BACK -> timeUp.close()
        Key.UP, Key.DOWN -> Unit
    }

    private enum class Key {
        OK, LEFT, RIGHT, UP, DOWN, BACK;

        companion object {
            fun of(keyCode: Int): Key? = when (keyCode) {
                // Some remotes and keyboards send ENTER for OK.
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> OK
                KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> UP
                KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
                KeyEvent.KEYCODE_BACK -> BACK
                else -> null
            }
        }
    }
}
