package net.pladum.tablero.tv

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.pladum.tablero.shared.GameEngine
import net.pladum.tablero.shared.TimeSource

const val LOG_TAG = "tablero"

/**
 * Owns the [GameEngine] for the whole process, so it outlives activity recreation. The engine is restored from
 * the last saved state on start and saved on every change.
 */
class TableroApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val timeSource: TimeSource = ElapsedRealtimeTimeSource
    lateinit var engine: GameEngine
        private set

    override fun onCreate() {
        super.onCreate()
        val store = StateStore(this)
        val saved = store.load()
        when {
            saved == null -> Log.i(LOG_TAG, "Starting a fresh game")
            saved.rebooted -> Log.i(LOG_TAG, "Restoring the game after a reboot: clock stopped, undo history dropped")
            else -> Log.i(LOG_TAG, "Restoring the game")
        }
        engine = GameEngine(timeSource, scope, restored = saved?.state, rebooted = saved?.rebooted ?: false)
        store.watch(engine, scope)
    }
}
