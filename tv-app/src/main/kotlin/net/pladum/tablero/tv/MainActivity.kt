package net.pladum.tablero.tv

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.pladum.tablero.shared.GameEngine

/** The only screen: the scoreboard, rendered from the engine's snapshots. */
class MainActivity : Activity() {
    private lateinit var engine: GameEngine
    private lateinit var scoreboard: ScoreboardView
    private var uiScope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TableroApplication
        engine = app.engine
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        scoreboard = ScoreboardView(this, app.timeSource)
        setContentView(scoreboard)
    }

    override fun onStart() {
        super.onStart()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch { engine.snapshot.collect(scoreboard::render) }
        uiScope = scope
    }

    override fun onStop() {
        uiScope?.cancel()
        uiScope = null
        super.onStop()
    }
}
