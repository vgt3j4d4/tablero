package net.pladum.tablero.tv

import android.view.KeyEvent
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.Side

/**
 * Maps the stick remote's keys to [Command]s (see CLAUDE.md → Remote control mapping). Takes primitives instead of
 * [KeyEvent]s so it can be unit tested; [MainActivity] forwards its key callbacks here.
 *
 * Each method returns whether the key is ours. A mapped key is consumed at every stage, so the system never acts
 * on it (Back must never finish the activity). Key repeats are ignored; the long press is reported once by the
 * framework, and the key-up that follows it arrives canceled.
 */
class RemoteKeyAdapter(private val dispatch: (Command) -> Unit) {
    /** Returns true when the key is ours; the caller then starts tracking it for a long press. */
    fun onKeyDown(keyCode: Int, repeatCount: Int): Boolean {
        val key = Key.of(keyCode) ?: return false
        if (repeatCount == 0 && key == Key.OK) dispatch(Command.ToggleClock) // On key-down: no delay.
        return true
    }

    fun onKeyLongPress(keyCode: Int): Boolean = when (Key.of(keyCode)) {
        Key.LEFT -> true.also { dispatch(Command.Score(Side.LEFT, -1)) }
        Key.RIGHT -> true.also { dispatch(Command.Score(Side.RIGHT, -1)) }
        Key.BACK -> true // The menu comes with #9.
        Key.OK, Key.UP, Key.DOWN, null -> false
    }

    fun onKeyUp(keyCode: Int, canceled: Boolean): Boolean {
        val key = Key.of(keyCode) ?: return false
        if (canceled) return true
        when (key) {
            Key.LEFT -> dispatch(Command.Score(Side.LEFT, 1))
            Key.RIGHT -> dispatch(Command.Score(Side.RIGHT, 1))
            Key.BACK -> dispatch(Command.Undo)
            Key.OK, Key.UP, Key.DOWN -> Unit // ↑ unused; ↓ opens quick clock adjust in #7.
        }
        return true
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
