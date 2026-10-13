package net.pladum.tablero.tv

import android.os.SystemClock
import net.pladum.tablero.shared.TimeSource

/** Monotonic time since boot, including deep sleep. Valid across process death, not across a reboot. */
object ElapsedRealtimeTimeSource : TimeSource {
    override fun nowNanos(): Long = SystemClock.elapsedRealtimeNanos()
}
