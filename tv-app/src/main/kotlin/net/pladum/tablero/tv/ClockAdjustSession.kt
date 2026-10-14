package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.DispatchResult
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.NANOS_PER_MS
import net.pladum.tablero.shared.UNDO_HISTORY_SIZE
import kotlin.math.abs

/** ↑/↓ in quick clock adjust. */
const val ADJUST_MINUTE_MS = 60_000L

/** ←/→ in quick clock adjust. */
const val ADJUST_SECOND_MS = 1_000L

private const val NANOS_PER_SECOND = 1_000 * NANOS_PER_MS

/**
 * One visit to quick clock adjust (↓). Every key is its own [Command.AdjustGameClock], applied at once (the clock
 * may keep running); the session remembers what each one actually changed so that Cancel can revert them all.
 *
 * Cancel undoes the session's adjustments through the engine's undo history, so they leave no trace and a later
 * Back undoes whatever came before the overlay. Only the last [UNDO_HISTORY_SIZE] commands are kept, so older
 * adjustments of a long session are reverted with one opposite adjustment instead. This relies on the session's
 * adjustments being the newest commands, which holds while the overlay owns the remote and no other input exists.
 */
class ClockAdjustSession(
    /** The clock reading when the overlay opened (`was 7:42`). */
    val was: ClockReading,
) {
    /** What each applied adjustment changed, oldest first; less than asked for when the clock hit a limit. */
    private val applied = ArrayDeque<Long>()

    /** The net change so far, rounded to whole seconds. */
    val netSeconds: Long
        get() {
            val net = applied.sum()
            val seconds = (abs(net) + NANOS_PER_SECOND / 2) / NANOS_PER_SECOND
            return if (net < 0) -seconds else seconds
        }

    /** Records the result of dispatching an adjustment; [before] is the snapshot just before it. */
    fun onAdjusted(before: GameSnapshot, result: DispatchResult, nowNanos: Long) {
        if (result !is DispatchResult.Applied) return // A rejected adjustment (clock already at a limit) did nothing.
        applied += result.snapshot.gameClock.remainingAt(nowNanos) - before.gameClock.remainingAt(nowNanos)
    }

    /** The commands that revert every adjustment of this session, to dispatch in order. */
    fun cancelCommands(): List<Command> {
        val undoable = minOf(applied.size, UNDO_HISTORY_SIZE)
        val forgotten = applied.take(applied.size - undoable).sum()
        return buildList {
            repeat(undoable) { add(Command.Undo) }
            if (forgotten != 0L) add(Command.AdjustGameClock(-Math.round(forgotten.toDouble() / NANOS_PER_MS)))
        }
    }
}
