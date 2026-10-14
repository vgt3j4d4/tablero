package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command

/** The time-up bar's buttons, left to right. */
enum class TimeUpChoice { NEW_GAME, RESET_CLOCK }

/** What the keys drive at time up. */
interface TimeUpKeys {
    /** The bar shows and owns the remote. */
    val isOpen: Boolean

    /** Time is up but Back closed the bar. */
    val isClosed: Boolean

    /** ←/→ */
    fun focus(choice: TimeUpChoice)

    /** OK: apply the focused choice. */
    fun pick()

    /** Back: close the bar; the game stays at time up. */
    fun close()

    /** OK after Back: open the bar again. */
    fun reopen()
}

/**
 * The time-up bar (design 03): whether it shows and which button is focused. It opens, NEW GAME focused, each time
 * the game enters the time-up state ([onTimeUp]): the clock ran out, or an undo brought a finished game back.
 *
 * Back closes it without leaving that state, so the scorer can still fix a score (Back would otherwise undo, and
 * undoing the start restores the full clock); OK opens it again. Picking a choice dispatches its command, which ends
 * the time-up state, and the bar goes with it.
 *
 * @param onChange gets the focused button while the bar shows, null when it hides.
 */
class TimeUpBar(
    private val dispatch: (Command) -> Unit,
    private val onChange: (TimeUpChoice?) -> Unit,
) : TimeUpKeys {
    private var timeUp = false
    private var closed = false
    private var focused = TimeUpChoice.NEW_GAME

    override val isOpen: Boolean get() = timeUp && !closed

    override val isClosed: Boolean get() = timeUp && closed

    /** Follows the engine's time-up state, from each snapshot. */
    fun onTimeUp(timeUp: Boolean) {
        if (timeUp == this.timeUp) return
        this.timeUp = timeUp
        if (timeUp) open() else notifyChange()
    }

    override fun focus(choice: TimeUpChoice) {
        if (!isOpen || choice == focused) return
        focused = choice
        notifyChange()
    }

    override fun pick() {
        if (!isOpen) return
        dispatch(
            when (focused) {
                TimeUpChoice.NEW_GAME -> Command.NewGame
                TimeUpChoice.RESET_CLOCK -> Command.ResetGameClock
            },
        )
    }

    override fun close() {
        if (!isOpen) return
        closed = true
        notifyChange()
    }

    override fun reopen() {
        if (isClosed) open()
    }

    private fun open() {
        closed = false
        focused = TimeUpChoice.NEW_GAME
        notifyChange()
    }

    private fun notifyChange() = onChange(if (isOpen) focused else null)
}
