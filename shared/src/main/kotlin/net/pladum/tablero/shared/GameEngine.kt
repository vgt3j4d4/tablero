package net.pladum.tablero.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Commands kept for undo. */
const val UNDO_HISTORY_SIZE = 20

private const val EVENT_BUFFER_SIZE = 64

/**
 * The single source of truth for a game. Applies [Command]s, publishes [snapshot]s and emits [events].
 * Thread-safe: commands may arrive from any thread.
 *
 * @param scope runs the expiry timer; cancel it to stop the engine.
 * @param restored state saved by a previous process, if any.
 * @param rebooted the device rebooted since [restored] was saved, so its [TimeSource] instants are meaningless:
 *   the clock is restored stopped at its last saved value and the undo history is dropped.
 */
class GameEngine(
    private val timeSource: TimeSource,
    private val scope: CoroutineScope,
    restored: EngineState? = null,
    rebooted: Boolean = false,
) {
    private val lock = Any()
    private val history = ArrayDeque<HistoryEntry>()
    private val _snapshot: MutableStateFlow<GameSnapshot>
    private val _events = MutableSharedFlow<GameEvent>(
        extraBufferCapacity = EVENT_BUFFER_SIZE,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var expiryJob: Job? = null

    val snapshot: StateFlow<GameSnapshot>
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    init {
        val initial = when {
            restored == null -> GameSnapshot.initial()
            rebooted -> restored.snapshot.let {
                val clock = it.gameClock.stopAt(restored.savedAtNanos)
                it.copy(gameClock = clock, timeUp = it.timeUp || (it.gameClock.running && clock.remainingNanos == 0L))
            }
            else -> restored.snapshot.also { history.addAll(restored.history) }
        }
        _snapshot = MutableStateFlow(initial)
        snapshot = _snapshot.asStateFlow()
        synchronized(lock) { scheduleExpiry(timeSource.nowNanos()) }
    }

    fun dispatch(command: Command): DispatchResult = synchronized(lock) {
        val now = timeSource.nowNanos()
        expireIfDue(now)
        val current = _snapshot.value
        when (val step = if (command == Command.Undo) undo(current, now) else apply(current, command, now)) {
            is Step.Reject -> DispatchResult.Rejected(step.reason)
            is Step.Change -> {
                if (history.size == UNDO_HISTORY_SIZE) history.removeFirst()
                history.addLast(step.entry)
                commit(step.next, now) { GameEvent.CommandApplied(step.entry.command, before = current, after = it) }
                DispatchResult.Applied(_snapshot.value)
            }
            is Step.Revert -> {
                commit(step.next, now) { GameEvent.CommandUndone(step.undone, before = current, after = it) }
                DispatchResult.Applied(_snapshot.value)
            }
        }
    }

    /** The state to persist; see [EngineState]. */
    fun exportState(): EngineState = synchronized(lock) {
        EngineState(savedAtNanos = timeSource.nowNanos(), snapshot = _snapshot.value, history = history.toList())
    }

    private fun apply(s: GameSnapshot, command: Command, now: Long): Step {
        val next = when (command) {
            Command.ToggleClock -> {
                val resolved = if (s.gameClock.running) Command.StopClock else Command.StartClock
                return apply(s, resolved, now)
            }
            Command.StartClock -> when {
                s.timeUp -> return Step.Reject(RejectReason.TIME_UP)
                s.gameClock.running -> return Step.Reject(RejectReason.CLOCK_RUNNING)
                s.gameClock.remainingNanos == 0L -> return Step.Reject(RejectReason.CLOCK_AT_ZERO)
                else -> s.copy(gameClock = s.gameClock.startAt(now), rules = s.rulesAtStart(), gameStarted = true)
            }
            Command.StopClock -> {
                if (!s.gameClock.running) return Step.Reject(RejectReason.CLOCK_STOPPED)
                s.copy(gameClock = s.gameClock.stopAt(now))
            }
            is Command.Score -> {
                if (command.delta != 1 && command.delta != -1) return Step.Reject(RejectReason.INVALID_ARGUMENT)
                val team = s.team(command.side)
                val score = team.score + command.delta
                if (score < 0) return Step.Reject(RejectReason.SCORE_AT_ZERO)
                s.withTeam(command.side, team.copy(score = score))
            }
            is Command.AdjustGameClock -> {
                val (clock, applied) = adjust(s.gameClock, command.deltaMs * NANOS_PER_MS, now)
                if (applied == 0L) return Step.Reject(RejectReason.NO_CHANGE)
                return change(command, s, clock.withTimeUp(s), now, appliedDeltaNanos = applied)
            }
            is Command.SetGameClock -> {
                if (s.gameClock.running) return Step.Reject(RejectReason.CLOCK_RUNNING)
                if (command.remainingMs !in 0..GameRules.MAX_CLOCK_MS) return Step.Reject(RejectReason.INVALID_ARGUMENT)
                ClockState.stopped(command.remainingMs).withTimeUp(s)
            }
            Command.ResetGameClock -> s.copy(gameClock = ClockState.stopped(s.rules.gameLengthMs), timeUp = false)
            Command.NewGame -> s.copy(
                gameClock = ClockState.stopped(s.rules.gameLengthMs),
                left = TeamState(),
                right = TeamState(),
                timeUp = false,
                gameStarted = false,
                possession = null,
            )
            Command.TogglePossession -> s.copy(
                possession = when (s.possession) {
                    null, Side.RIGHT -> Side.LEFT
                    Side.LEFT -> Side.RIGHT
                },
            )
            Command.ClearPossession -> s.copy(possession = null)
            is Command.SetGameLength -> {
                val range = GameRules.MIN_GAME_LENGTH_MS..GameRules.MAX_GAME_LENGTH_MS
                if (command.gameLengthMs !in range) return Step.Reject(RejectReason.INVALID_ARGUMENT)
                s.copy(rules = s.rules.copy(gameLengthMs = command.gameLengthMs))
            }
            Command.Undo -> error("Undo is handled by undo()")
        }
        return change(command, s, next, now)
    }

    /** Reverts the effect of the last command, not the time that has passed since (see docs/architecture.md). */
    private fun undo(s: GameSnapshot, now: Long): Step {
        val entry = history.removeLastOrNull() ?: return Step.Reject(RejectReason.NOTHING_TO_UNDO)
        val before = entry.before
        val command = entry.command
        val next = when (command) {
            Command.StartClock -> s.copy(
                gameClock = before.gameClock,
                rules = before.rules,
                timeUp = before.timeUp,
                gameStarted = before.gameStarted,
            )
            Command.StopClock,
            is Command.SetGameClock,
            Command.ResetGameClock,
            -> s.copy(gameClock = before.gameClock, timeUp = before.timeUp)
            is Command.Score -> {
                val score = before.team(command.side).score
                s.withTeam(command.side, s.team(command.side).copy(score = score))
            }
            is Command.AdjustGameClock -> adjust(s.gameClock, -entry.appliedDeltaNanos, now).first.withTimeUp(s)
            Command.NewGame -> s.copy(
                gameClock = before.gameClock.stopAt(entry.appliedAtNanos),
                left = before.left,
                right = before.right,
                timeUp = before.timeUp,
                gameStarted = before.gameStarted,
                possession = before.possession,
            )
            Command.TogglePossession, Command.ClearPossession -> s.copy(possession = before.possession)
            is Command.SetGameLength -> s.copy(rules = before.rules)
            Command.ToggleClock, Command.Undo -> error("$command is never recorded")
        }
        return Step.Revert(next, undone = command)
    }

    private fun change(
        command: Command,
        s: GameSnapshot,
        next: GameSnapshot,
        now: Long,
        appliedDeltaNanos: Long = 0,
    ): Step {
        if (next == s) return Step.Reject(RejectReason.NO_CHANGE)
        val entry = HistoryEntry(command, before = s, appliedAtNanos = now, appliedDeltaNanos = appliedDeltaNanos)
        return Step.Change(next, entry)
    }

    /** A game's first start makes the time on the clock its length, when that is a valid length. */
    private fun GameSnapshot.rulesAtStart(): GameRules {
        if (gameStarted) return rules
        val clockMs = gameClock.remainingNanos / NANOS_PER_MS
        val valid = clockMs in GameRules.MIN_GAME_LENGTH_MS..GameRules.MAX_GAME_LENGTH_MS
        return if (valid) rules.copy(gameLengthMs = clockMs) else rules
    }

    /** Shifts the clock by [deltaNanos], clamped to `0..MAX_CLOCK_MS`; returns the clock and the delta applied. */
    private fun adjust(clock: ClockState, deltaNanos: Long, now: Long): Pair<ClockState, Long> {
        val remaining = clock.remainingAt(now)
        val target = (remaining + deltaNanos).coerceIn(0, GameRules.MAX_CLOCK_MS * NANOS_PER_MS)
        val applied = target - remaining
        return clock.shiftedBy(applied) to applied
    }

    /** Puts [this] clock into [s]; any time on the clock clears the time-up state. */
    private fun ClockState.withTimeUp(s: GameSnapshot): GameSnapshot =
        s.copy(gameClock = this, timeUp = s.timeUp && remainingNanos == 0L)

    /**
     * Publishes [next] and emits the event [describe] builds from it as published, ahead of the clock events and of
     * an expiry the change may cause at once.
     */
    private fun commit(next: GameSnapshot, now: Long, describe: (GameSnapshot) -> GameEvent) {
        val previous = _snapshot.value
        val committed = next.copy(version = previous.version + 1)
        _snapshot.value = committed
        _events.tryEmit(describe(committed))
        if (!previous.gameClock.running && next.gameClock.running) _events.tryEmit(GameEvent.ClockStarted)
        if (previous.gameClock.running && !next.gameClock.running) _events.tryEmit(GameEvent.ClockStopped)
        scheduleExpiry(now)
    }

    private fun expireIfDue(now: Long): Boolean {
        val current = _snapshot.value
        if (!current.gameClock.running || current.gameClock.remainingAt(now) > 0) return false
        _snapshot.value = current.copy(version = current.version + 1, gameClock = ClockState(0), timeUp = true)
        _events.tryEmit(GameEvent.GameClockExpired)
        return true
    }

    /** Arms a timer for the running clock's expiry instant; it re-checks against [timeSource] when it fires. */
    private fun scheduleExpiry(now: Long) {
        expiryJob?.cancel()
        expiryJob = null
        if (expireIfDue(now)) return
        val clock = _snapshot.value.gameClock
        if (!clock.running) return
        val remainingNanos = clock.remainingAt(now)
        expiryJob = scope.launch {
            delay((remainingNanos + NANOS_PER_MS - 1) / NANOS_PER_MS)
            synchronized(lock) {
                if (isActive) scheduleExpiry(timeSource.nowNanos())
            }
        }
    }

    private sealed interface Step {
        data class Reject(val reason: RejectReason) : Step

        /** A command applied; [entry] goes to the undo history. */
        data class Change(val next: GameSnapshot, val entry: HistoryEntry) : Step

        /** The last command in the history was undone. */
        data class Revert(val next: GameSnapshot, val undone: Command) : Step
    }
}
