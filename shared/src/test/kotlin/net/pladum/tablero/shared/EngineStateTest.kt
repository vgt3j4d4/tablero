package net.pladum.tablero.shared

import kotlinx.serialization.SerializationException
import net.pladum.tablero.shared.Command.AdjustGameClock
import net.pladum.tablero.shared.Command.NewGame
import net.pladum.tablero.shared.Command.ResetGameClock
import net.pladum.tablero.shared.Command.Score
import net.pladum.tablero.shared.Command.SetGameClock
import net.pladum.tablero.shared.Command.SetGameLength
import net.pladum.tablero.shared.Command.StartClock
import net.pladum.tablero.shared.Command.StopClock
import net.pladum.tablero.shared.Command.Undo
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EngineStateTest {
    @Test
    fun `JSON round trip keeps the snapshot and every kind of history entry`() = engineTest {
        apply(Score(RIGHT, 1))
        apply(SetGameLength(8 * MINUTE_MS))
        apply(NewGame)
        apply(Score(LEFT, 1))
        apply(SetGameClock(MINUTE_MS))
        apply(AdjustGameClock(-SECOND_MS))
        apply(ResetGameClock)
        apply(StartClock)
        advance(SECOND_MS)
        apply(StopClock)
        apply(StartClock)
        val state = engine.exportState()
        assertEquals(10, state.history.size)
        assertEquals(state, EngineState.fromJson(state.toJson()))
    }

    @Test
    fun `after the app is killed a running clock keeps counting and undo still works`() = engineTest {
        apply(StartClock)
        apply(Score(LEFT, 1))
        val saved = engine.exportState().toJson()
        advance(5 * SECOND_MS)
        start(restored = EngineState.fromJson(saved))
        assertClock(GAME_MS - 5 * SECOND_MS, running = true)
        apply(Undo)
        assertEquals(0, state.left.score)
    }

    @Test
    fun `a clock that ran out while the app was dead is time up on restart`() = engineTest {
        apply(StartClock)
        val saved = engine.exportState()
        advance(GAME_MS + MINUTE_MS)
        start(restored = saved)
        assertTrue(state.timeUp)
        assertClock(0, running = false)
    }

    @Test
    fun `after a reboot the clock is stopped at its last saved value and history is dropped`() = engineTest {
        apply(Score(LEFT, 1))
        apply(StartClock)
        advance(MINUTE_MS)
        val saved = engine.exportState()
        start(restored = saved, rebooted = true, timeSource = { 42 })
        assertEquals(GAME_MS - MINUTE_MS, state.gameClock.remainingNanos / NANOS_PER_MS)
        assertEquals(false, state.gameClock.running)
        assertEquals(1, state.left.score)
        assertEquals(DispatchResult.Rejected(RejectReason.NOTHING_TO_UNDO), engine.dispatch(Undo))
    }

    @Test
    fun `a reboot after the clock ran out restores time up`() = engineTest {
        apply(StartClock)
        // Saved while running; the clock ran out before the next save, then the device rebooted.
        val saved = engine.exportState().copy(savedAtNanos = (GAME_MS + SECOND_MS) * NANOS_PER_MS)
        start(restored = saved, rebooted = true, timeSource = { 42 })
        assertTrue(state.timeUp)
        assertClock(0, running = false)
    }

    @Test
    fun `unknown keys are ignored so older apps can read newer optional fields`() = engineTest {
        val json = engine.exportState().toJson().replaceFirst("{", """{"someFutureField":1,""")
        assertEquals(engine.exportState().snapshot, EngineState.fromJson(json).snapshot)
    }

    @Test
    fun `a game saved before gameStarted existed reads as started`() = engineTest {
        val json = engine.exportState().toJson()
        val old = json.replace(""","gameStarted":false""", "")
        assertNotEquals(json, old)
        assertTrue(EngineState.fromJson(old).snapshot.gameStarted)
    }

    @Test
    fun `state from a newer schema or malformed JSON is refused`() = engineTest {
        val newer = engine.exportState().copy(schemaVersion = EngineState.SCHEMA_VERSION + 1).toJson()
        assertFailsWith<IllegalArgumentException> { EngineState.fromJson(newer) }
        assertFailsWith<SerializationException> { EngineState.fromJson("{") }
    }
}
