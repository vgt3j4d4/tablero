package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.GameEvent
import net.pladum.tablero.shared.RejectReason
import net.pladum.tablero.shared.Side
import kotlin.math.sign

/** Score presses closer together than this, on one side and in one direction, show as one confirmation. */
const val FEEDBACK_GROUP_WINDOW_NANOS = 1_500_000_000L

/** What the confirmation toast shows after an action; values only, [FeedbackToast] turns them into text. */
sealed interface Feedback {
    /** One score change, or several grouped presses: [delta] in total, [from] → [to]. */
    data class Score(val side: Side, val delta: Int, val from: Int, val to: Int) : Feedback

    /** A score command was undone: [delta] is that command's, [from] → [to] what undoing it did to the score. */
    data class UndoneScore(val side: Side, val delta: Int, val from: Int, val to: Int) : Feedback

    data class Undone(val action: UndoneAction) : Feedback

    /** The possession arrow now points to [side], or is hidden when null. */
    data class Possession(val side: Side?) : Feedback

    data object NothingToUndo : Feedback
}

/** Every recorded command other than a score, as the undo confirmation names it. */
enum class UndoneAction {
    START_CLOCK, STOP_CLOCK, ADJUST_CLOCK, SET_CLOCK, RESET_CLOCK, NEW_GAME, GAME_LENGTH, POSSESSION
}

/**
 * Turns the engine's events into confirmations, whichever input sent the command. Consecutive score presses on the
 * same side and in the same direction, each within [groupWindowNanos] of the previous one, add up into one
 * confirmation (`HOME +3 · 48 → 51`); they stay separate commands in the engine.
 */
class FeedbackGrouper(private val groupWindowNanos: Long = FEEDBACK_GROUP_WINDOW_NANOS) {
    private var lastScore: Feedback.Score? = null
    private var lastScoreAtNanos = 0L

    /**
     * The confirmation for [event] at [nowNanos], or null when it has none (clock start/stop, expiry, and the
     * commands the menu applies: it shows the values it changes).
     */
    fun onEvent(event: GameEvent, nowNanos: Long): Feedback? = when (event) {
        is GameEvent.CommandApplied -> when (val command = event.command) {
            is Command.Score -> score(command, event, nowNanos)
            Command.TogglePossession, Command.ClearPossession ->
                Feedback.Possession(event.after.possession).also { lastScore = null }
            else -> null
        }
        is GameEvent.CommandUndone -> undone(event).also { lastScore = null }
        GameEvent.ClockStarted, GameEvent.ClockStopped, GameEvent.GameClockExpired -> null
    }

    /** The confirmation for a command the engine rejected, or null when the rejection needs none. */
    fun onRejected(command: Command, reason: RejectReason): Feedback? {
        if (command != Command.Undo || reason != RejectReason.NOTHING_TO_UNDO) return null
        lastScore = null
        return Feedback.NothingToUndo
    }

    private fun score(command: Command.Score, event: GameEvent.CommandApplied, nowNanos: Long): Feedback.Score {
        val before = event.before.team(command.side)
        val after = event.after.team(command.side)
        val previous = lastScore
        val next = if (
            previous != null &&
            previous.side == command.side &&
            previous.delta.sign == command.delta.sign &&
            previous.to == before.score &&
            nowNanos - lastScoreAtNanos <= groupWindowNanos
        ) {
            previous.copy(delta = previous.delta + command.delta, to = after.score)
        } else {
            Feedback.Score(command.side, command.delta, from = before.score, to = after.score)
        }
        lastScore = next
        lastScoreAtNanos = nowNanos
        return next
    }

    private fun undone(event: GameEvent.CommandUndone): Feedback? = when (val command = event.command) {
        is Command.Score -> Feedback.UndoneScore(
            side = command.side,
            delta = command.delta,
            from = event.before.team(command.side).score,
            to = event.after.team(command.side).score,
        )
        Command.StartClock -> Feedback.Undone(UndoneAction.START_CLOCK)
        Command.StopClock -> Feedback.Undone(UndoneAction.STOP_CLOCK)
        is Command.AdjustGameClock -> Feedback.Undone(UndoneAction.ADJUST_CLOCK)
        is Command.SetGameClock -> Feedback.Undone(UndoneAction.SET_CLOCK)
        Command.ResetGameClock -> Feedback.Undone(UndoneAction.RESET_CLOCK)
        Command.NewGame -> Feedback.Undone(UndoneAction.NEW_GAME)
        is Command.SetGameLength -> Feedback.Undone(UndoneAction.GAME_LENGTH)
        Command.TogglePossession, Command.ClearPossession -> Feedback.Undone(UndoneAction.POSSESSION)
        Command.ToggleClock, Command.Undo -> null // Never recorded: a toggle is recorded as what it resolved to.
    }
}
