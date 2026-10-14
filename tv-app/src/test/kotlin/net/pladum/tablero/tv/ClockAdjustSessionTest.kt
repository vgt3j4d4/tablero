package net.pladum.tablero.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.ClockState
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.Command.AdjustGameClock
import net.pladum.tablero.shared.Command.Undo
import net.pladum.tablero.shared.DispatchResult
import net.pladum.tablero.shared.GameEngine
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.NANOS_PER_MS
import net.pladum.tablero.shared.RejectReason
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.TimeSource
import net.pladum.tablero.shared.UNDO_HISTORY_SIZE
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ClockAdjustSessionTest {
    private var nowNanos = 0L
    private val scope = CoroutineScope(Job())
    private val engine = GameEngine(TimeSource { nowNanos }, scope)
    private val session = ClockAdjustSession(was = ClockReading.MinutesSeconds(10, 0))

    @AfterEach
    fun stopEngine() = scope.cancel()

    /** Dispatches an adjustment the way the overlay does and records it in the session. */
    private fun adjust(deltaMs: Long): DispatchResult {
        val before = engine.snapshot.value
        val result = engine.dispatch(AdjustGameClock(deltaMs))
        session.onAdjusted(before, result, nowNanos)
        return result
    }

    private val remainingMs: Long get() = engine.snapshot.value.gameClock.remainingAt(nowNanos) / NANOS_PER_MS

    @Test
    fun `the net change adds up what was applied`() {
        adjust(ADJUST_MINUTE_MS)
        adjust(-ADJUST_SECOND_MS)
        adjust(-ADJUST_SECOND_MS)
        assertEquals(58, session.netSeconds)
    }

    @Test
    fun `an adjustment clamped at 0 counts what it applied, a rejected one counts nothing`() {
        engine.dispatch(Command.SetGameClock(30_000))
        adjust(-ADJUST_MINUTE_MS)
        assertEquals(DispatchResult.Rejected(RejectReason.NO_CHANGE), adjust(-ADJUST_SECOND_MS))
        assertEquals(-30, session.netSeconds)
        assertEquals(listOf<Command>(Undo), session.cancelCommands())
    }

    @Test
    fun `adjustments of a running clock count the shift, not the time that passed`() {
        engine.dispatch(Command.StartClock)
        nowNanos += 2_000 * NANOS_PER_MS
        adjust(ADJUST_SECOND_MS)
        nowNanos += 3_000 * NANOS_PER_MS
        adjust(ADJUST_SECOND_MS)
        assertEquals(2, session.netSeconds)
    }

    @Test
    fun `cancel undoes each adjustment, and a later undo reaches the command before the overlay`() {
        engine.dispatch(Command.Score(Side.LEFT, 1))
        adjust(ADJUST_MINUTE_MS)
        adjust(ADJUST_SECOND_MS)
        assertEquals(listOf<Command>(Undo, Undo), session.cancelCommands())

        session.cancelCommands().forEach { engine.dispatch(it) }
        assertEquals(10 * ADJUST_MINUTE_MS, remainingMs)
        engine.dispatch(Undo)
        assertEquals(0, engine.snapshot.value.left.score)
    }

    @Test
    fun `adjustments older than the undo history are reverted with one opposite adjustment`() {
        engine.dispatch(Command.SetGameClock(ADJUST_MINUTE_MS))
        repeat(UNDO_HISTORY_SIZE + 5) { adjust(ADJUST_SECOND_MS) }
        val commands = session.cancelCommands()
        assertEquals(List(UNDO_HISTORY_SIZE) { Undo } + AdjustGameClock(-5 * ADJUST_SECOND_MS), commands)

        commands.forEach { engine.dispatch(it) }
        assertEquals(ADJUST_MINUTE_MS, remainingMs)
    }

    @Test
    fun `nothing to cancel without adjustments`() {
        assertEquals(emptyList(), session.cancelCommands())
        assertEquals(0, session.netSeconds)
    }

    @Test
    fun `the net change rounds to whole seconds`() {
        val before = GameSnapshot.initial()
        val after = before.copy(gameClock = ClockState(before.gameClock.remainingNanos - 1_600 * NANOS_PER_MS))
        session.onAdjusted(before, DispatchResult.Applied(after), nowNanos)
        assertEquals(-2, session.netSeconds)
    }
}
