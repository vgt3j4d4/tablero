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
import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.DispatchResult
import net.pladum.tablero.shared.GameEngine
import net.pladum.tablero.shared.TeamColor
import net.pladum.tablero.shared.TimeSource

/** The only screen: the scoreboard, rendered from the engine's snapshots. */
class MainActivity : Activity() {
    private lateinit var engine: GameEngine
    private lateinit var timeSource: TimeSource
    private lateinit var scoreboard: ScoreboardView
    private val keys = RemoteKeyAdapter(::dispatch)
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
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch { engine.snapshot.collect(scoreboard::render) }
        scope.launch {
            engine.events.collect { event -> feedback.onEvent(event, timeSource.nowNanos())?.let(scoreboard::show) }
        }
        uiScope = scope
    }

    override fun onStop() {
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
        Log.i(LOG_TAG, "Language $locale ($source), e.g. ${getString(TeamColor.YELLOW.nameRes())}")
    }

    private fun dispatch(command: Command) {
        val result = engine.dispatch(command)
        if (result !is DispatchResult.Rejected) return
        Log.d(LOG_TAG, "$command rejected: ${result.reason}")
        feedback.onRejected(command, result.reason)?.let(scoreboard::show)
    }
}
