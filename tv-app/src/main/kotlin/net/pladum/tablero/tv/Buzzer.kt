package net.pladum.tablero.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import net.pladum.tablero.shared.GameEngine
import net.pladum.tablero.shared.GameEvent

/**
 * The time-up horn (`res/raw/buzzer.wav`, made by `scripts/make-buzzer.py`), preloaded in a [SoundPool] so it
 * plays without delay. It reacts to [GameEvent.GameClockExpired] and [GameEvent.BreakEnded] only, which the engine
 * emits once per expiry: an undo that brings a finished game back shows the time-up bar again but doesn't sound the
 * horn.
 *
 * It plays as media, the stream that goes out over HDMI and follows the TV's volume.
 */
class Buzzer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    @Volatile
    private var loaded = false
    private val soundId: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status ->
            loaded = status == 0
            if (loaded) Log.i(LOG_TAG, "Buzzer loaded") else Log.e(LOG_TAG, "Could not load the buzzer ($status)")
        }
        soundId = pool.load(context, R.raw.buzzer, 1)
    }

    /**
     * Sounds the horn each time [engine]'s clock runs out, at the end of a period or of a break. Subscribes before
     * returning, so no later expiry is missed; one that happened while the app wasn't running (found on restore)
     * doesn't sound.
     */
    fun watch(engine: GameEngine, scope: CoroutineScope) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            engine.events.collect { if (it == GameEvent.GameClockExpired || it == GameEvent.BreakEnded) play() }
        }
    }

    private fun play() {
        if (!loaded) {
            Log.w(LOG_TAG, "Time up before the buzzer finished loading")
            return
        }
        Log.i(LOG_TAG, "Buzzer")
        pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
