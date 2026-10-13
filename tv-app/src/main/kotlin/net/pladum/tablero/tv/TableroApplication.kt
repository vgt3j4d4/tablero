package net.pladum.tablero.tv

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.pladum.tablero.shared.GameEngine
import net.pladum.tablero.shared.TimeSource

const val LOG_TAG = "tablero"

/** Owns the [GameEngine] for the whole process, so it outlives activity recreation. */
class TableroApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeSource: TimeSource = ElapsedRealtimeTimeSource
    lateinit var engine: GameEngine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = GameEngine(timeSource, scope)
    }
}
