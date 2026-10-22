package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameSnapshot

/** The time-up bar's buttons. */
enum class TimeUpChoice { NEW_GAME, RESET_CLOCK, BREAK, SKIP_BREAK }

/**
 * The pairs of buttons the bar offers, left to right: when the game is over, a new game or another go at the clock
 * (design 03); at the end of a period under period rules, the break before the next one or straight on to it
 * (design 29).
 */
enum class TimeUpChoices(val first: TimeUpChoice, val second: TimeUpChoice) {
    GAME_OVER(TimeUpChoice.NEW_GAME, TimeUpChoice.RESET_CLOCK),
    PERIOD_END(TimeUpChoice.BREAK, TimeUpChoice.SKIP_BREAK),
    ;

    companion object {
        /** The pair the bar offers at [snapshot]'s time up. */
        fun at(snapshot: GameSnapshot): TimeUpChoices = if (snapshot.nextPeriod != null) PERIOD_END else GAME_OVER
    }
}

/** What the keys drive at time up. */
interface TimeUpKeys {
    /** The bar shows and owns the remote. */
    val isOpen: Boolean

    /** Time is up but Back closed the bar. */
    val isClosed: Boolean

    /** ←: focus the left button. */
    fun left()

    /** →: focus the right button. */
    fun right()

    /** OK: apply the focused choice. */
    fun pick()

    /** Back: close the bar; the game stays at time up. */
    fun close()

    /** OK after Back: open the bar again. */
    fun reopen()
}

/**
 * The time-up bar (design 03, 29): whether it shows and which button is focused. It opens, its left button focused,
 * each time the game enters the time-up state ([onTimeUp]): the clock ran out, or an undo brought a finished game or
 * period back. Which buttons it offers follows the snapshot ([TimeUpChoices.at]), so fixing a score with the bar
 * closed can turn the end of the last period into the way to overtime, or back.
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
    private var choices = TimeUpChoices.GAME_OVER
    private var focused = choices.first

    override val isOpen: Boolean get() = timeUp && !closed

    override val isClosed: Boolean get() = timeUp && closed

    /** Follows the engine's time-up state and the buttons it calls for, from each snapshot. */
    fun onTimeUp(snapshot: GameSnapshot) {
        val nextChoices = TimeUpChoices.at(snapshot)
        if (snapshot.timeUp == timeUp && nextChoices == choices) return
        val opening = snapshot.timeUp && !timeUp
        timeUp = snapshot.timeUp
        choices = nextChoices
        focused = choices.first
        if (opening) closed = false
        notifyChange()
    }

    override fun left() = focus(choices.first)

    override fun right() = focus(choices.second)

    override fun pick() {
        if (!isOpen) return
        dispatch(
            when (focused) {
                TimeUpChoice.NEW_GAME -> Command.NewGame
                TimeUpChoice.RESET_CLOCK -> Command.ResetGameClock
                TimeUpChoice.BREAK -> Command.NextPeriod(withBreak = true)
                TimeUpChoice.SKIP_BREAK -> Command.NextPeriod(withBreak = false)
            },
        )
    }

    override fun close() {
        if (!isOpen) return
        closed = true
        notifyChange()
    }

    override fun reopen() {
        if (!isClosed) return
        closed = false
        focused = choices.first
        notifyChange()
    }

    private fun focus(choice: TimeUpChoice) {
        if (!isOpen || choice == focused) return
        focused = choice
        notifyChange()
    }

    private fun notifyChange() = onChange(if (isOpen) focused else null)
}
