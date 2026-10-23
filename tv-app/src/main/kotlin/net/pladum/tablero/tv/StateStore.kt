package net.pladum.tablero.tv

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.pladum.tablero.shared.EngineState
import net.pladum.tablero.shared.GameEngine
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val STATE_FILE = "engine-state.json"
private const val PREFS = "state-store"
private const val PREF_BOOT = "boot"
private const val RUNNING_SAVE_INTERVAL_MS = 5_000L

/** The kernel's random id for the current boot; the boot count setting only exists from API 24. */
private const val BOOT_ID_FILE = "/proc/sys/kernel/random/boot_id"

/**
 * Persists the engine state (see docs/architecture.md → Persistence) as `EngineState` JSON in an [AtomicFile].
 *
 * Running-clock instants are only valid within one boot, so the boot each save belongs to is kept in preferences,
 * written *after* the file: a crash in between makes the next start assume a reboot (clock restored stopped, at most
 * one save interval lost), never the reverse.
 */
class StateStore(private val context: Context) {
    class Loaded(val state: EngineState, val rebooted: Boolean)

    private val file = AtomicFile(File(context.filesDir, STATE_FILE))
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Identifies the current boot: the boot count from API 24, the kernel's boot id before it. Null when neither can
     * be read; then nothing is recorded and every start counts as a reboot.
     */
    private val boot: String? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
                .takeIf { it >= 0 }
                ?.let { "count:$it" }
        } else {
            try {
                File(BOOT_ID_FILE).readText().trim().takeIf { it.isNotEmpty() }?.let { "id:$it" }
            } catch (e: IOException) {
                Log.w(LOG_TAG, "Could not read the boot id", e)
                null
            }
        }
    }

    /** The saved state, or null on first launch or when the file can't be used (the next save replaces it). */
    fun load(): Loaded? {
        val json = try {
            // No exists() check: reading also recovers the backup left by an interrupted write.
            file.readFully().decodeToString()
        } catch (_: FileNotFoundException) {
            return null
        }
        val state = try {
            EngineState.fromJson(json)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Ignoring unreadable saved state", e)
            return null
        }
        val savedBoot = prefs.getString(PREF_BOOT, null)
        return Loaded(state, rebooted = savedBoot == null || savedBoot != boot)
    }

    /**
     * Saves after every state change and every [RUNNING_SAVE_INTERVAL_MS] while the clock runs. The first save
     * happens at once, which also records the current boot. Saves run one at a time on a single IO thread.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watch(engine: GameEngine, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO.limitedParallelism(1)) {
            engine.snapshot.collectLatest { snapshot ->
                save(engine.exportState())
                while (snapshot.gameClock.running) {
                    delay(RUNNING_SAVE_INTERVAL_MS)
                    save(engine.exportState())
                }
            }
        }
    }

    @SuppressLint("ApplySharedPref") // commit(): the boot must be stored after the file, synchronously.
    private fun save(state: EngineState) {
        val out = try {
            file.startWrite()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not save state", e)
            return
        }
        try {
            out.write(state.toJson().encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e(LOG_TAG, "Could not save state", e)
            return
        }
        val current = boot // A lazy property doesn't smart-cast.
        if (current != null && prefs.getString(PREF_BOOT, null) != current) {
            prefs.edit().putString(PREF_BOOT, current).commit()
        }
        Log.d(LOG_TAG, "Saved state v${state.snapshot.version}")
    }
}
