package net.pladum.tablero.tv

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.DispatchResult
import net.pladum.tablero.shared.GameEngine
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.TimeSource

/** Quick clock adjust closes on its own, keeping its changes, after this long without a key. */
private const val CLOCK_ADJUST_IDLE_MS = 5_000L

/** Saved instance state: the focused menu row, when the menu was open (a language change recreates the activity). */
private const val STATE_MENU_ROW = "menuRow"

/** The only screen: the scoreboard, rendered from the engine's snapshots. */
class MainActivity : Activity() {
    private lateinit var engine: GameEngine
    private lateinit var timeSource: TimeSource
    private lateinit var scoreboard: ScoreboardView
    private val clockAdjust = ClockAdjustController()
    private val timeUp = TimeUpBar({ dispatch(it) }, { scoreboard.showTimeUpBar(it) })
    private lateinit var menu: Menu
    private lateinit var keys: RemoteKeyAdapter
    private val feedback = FeedbackGrouper()
    private var uiScope: CoroutineScope? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        AppLanguage.overrideConfiguration(newBase)?.let(::applyOverrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TableroApplication
        engine = app.engine
        timeSource = app.timeSource
        logLanguage()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scoreboard = ScoreboardView(this, timeSource)
        setContentView(scoreboard)
        menu = Menu(
            dispatch = { dispatch(it) },
            snapshot = { engine.snapshot.value },
            languages = AppLanguage.available(this),
            language = AppLanguage.inUse(this),
            applyLanguage = { AppLanguage.set(this, it) },
            onChange = { scoreboard.showMenu(it) },
        )
        keys = RemoteKeyAdapter({ dispatch(it) }, clockAdjust, timeUp, menu)
        savedInstanceState?.getString(STATE_MENU_ROW)
            ?.let { saved -> MenuRow.entries.firstOrNull { it.name == saved } }
            ?.let(menu::open)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        menu.focused?.let { outState.putString(STATE_MENU_ROW, it.name) }
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            engine.snapshot.collect { snapshot ->
                timeUp.onTimeUp(snapshot.timeUp)
                scoreboard.render(snapshot)
            }
        }
        scope.launch {
            engine.events.collect { event -> feedback.onEvent(event, timeSource.nowNanos())?.let(scoreboard::show) }
        }
        uiScope = scope
    }

    override fun onStop() {
        if (clockAdjust.isOpen) clockAdjust.done()
        menu.applyGameLength() // The menu may stay open; a pending change must not wait for it.
        uiScope?.cancel()
        uiScope = null
        super.onStop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!keys.onKeyDown(keyCode, event.repeatCount)) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) event.startTracking() // Enables onKeyLongPress for this key.
        return true
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean =
        keys.onKeyLongPress(keyCode) || super.onKeyLongPress(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        keys.onKeyUp(keyCode, event.isCanceled) || super.onKeyUp(keyCode, event)

    private fun logLanguage() {
        val source = if (AppLanguage.chosen(this) != null) "chosen in the app" else "from the device"
        val locale = resources.configuration.locales[0].toLanguageTag()
        Log.i(LOG_TAG, "Language $locale ($source), e.g. ${getString(Side.LEFT.teamNameRes())}")
    }

    private fun dispatch(command: Command): DispatchResult {
        val result = engine.dispatch(command)
        if (result is DispatchResult.Rejected) {
            Log.d(LOG_TAG, "$command rejected: ${result.reason}")
            feedback.onRejected(command, result.reason)?.let(scoreboard::show)
        }
        return result
    }

    /** Runs quick clock adjust (↓) for the remote: one [ClockAdjustSession] per visit, closed after idle time. */
    private inner class ClockAdjustController : ClockAdjustKeys {
        private var session: ClockAdjustSession? = null
        private val closeWhenIdle = Runnable { done() }

        override val isOpen: Boolean get() = session != null

        override fun open() {
            val snapshot = engine.snapshot.value
            val was = ClockReading.of(snapshot.gameClock.remainingAt(timeSource.nowNanos()), snapshot.rules)
            show(ClockAdjustSession(was))
        }

        override fun adjust(deltaMs: Long) {
            val session = session ?: return
            val before = engine.snapshot.value
            val result = dispatch(Command.AdjustGameClock(deltaMs))
            session.onAdjusted(before, result, timeSource.nowNanos())
            show(session)
        }

        override fun done() = close()

        override fun cancel() {
            session?.cancelCommands()?.forEach { dispatch(it) }
            close()
        }

        private fun show(session: ClockAdjustSession) {
            this.session = session
            scoreboard.showClockAdjust(session)
            scoreboard.removeCallbacks(closeWhenIdle)
            scoreboard.postDelayed(closeWhenIdle, CLOCK_ADJUST_IDLE_MS)
        }

        private fun close() {
            session = null
            scoreboard.removeCallbacks(closeWhenIdle)
            scoreboard.showClockAdjust(null)
        }
    }
}
