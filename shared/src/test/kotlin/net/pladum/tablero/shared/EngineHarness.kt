package net.pladum.tablero.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.fail

const val SECOND_MS = 1_000L
const val MINUTE_MS = 60 * SECOND_MS
const val GAME_MS = GameRules.DEFAULT_GAME_LENGTH_MS

/** Runs an engine on virtual time: the [TimeSource] is the test scheduler's clock. */
fun engineTest(block: EngineHarness.() -> Unit) = runTest { EngineHarness(this).block() }

class EngineHarness(private val test: TestScope) {
    val time = TimeSource { test.testScheduler.currentTime * NANOS_PER_MS }
    lateinit var engine: GameEngine
        private set
    val events = mutableListOf<GameEvent>()

    init {
        start()
    }

    val state: GameSnapshot get() = engine.snapshot.value

    val remainingMs: Long get() = state.gameClock.remainingAt(time.nowNanos()) / NANOS_PER_MS

    /** Replaces the engine, as when the app process starts again. Events are recorded from the new engine only. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(restored: EngineState? = null, rebooted: Boolean = false, timeSource: TimeSource = time) {
        engine = GameEngine(timeSource, test.backgroundScope, restored, rebooted)
        events.clear()
        val engine = engine
        test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) {
            engine.events.collect { events += it }
        }
    }

    /** Moves virtual time forward and runs whatever became due, including timers due exactly now. */
    fun advance(ms: Long) {
        test.testScheduler.advanceTimeBy(ms)
        test.testScheduler.runCurrent()
    }

    fun apply(command: Command): GameSnapshot = when (val result = engine.dispatch(command)) {
        is DispatchResult.Applied -> result.snapshot
        is DispatchResult.Rejected -> fail("$command was rejected: ${result.reason}")
    }

    fun assertRejected(command: Command, reason: RejectReason) {
        val before = state
        assertEquals(DispatchResult.Rejected(reason), engine.dispatch(command))
        assertEquals(before, state, "a rejected command must not change the state")
    }

    fun assertClock(remainingMs: Long, running: Boolean) {
        assertEquals(remainingMs, this.remainingMs, "remaining")
        assertEquals(running, state.gameClock.running, "running")
    }
}
