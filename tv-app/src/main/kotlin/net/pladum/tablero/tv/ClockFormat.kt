package net.pladum.tablero.tv

import net.pladum.tablero.shared.ClockReading

/**
 * Digits for the clock display: `9:42`, `10:00`, `38.4`. No leading zero, because the LED look (#6) draws the
 * empty leading digit as an unlit ghost segment.
 */
fun ClockReading.format(): String = when (this) {
    is ClockReading.MinutesSeconds -> "$minutes:${seconds.toString().padStart(2, '0')}"
    is ClockReading.SecondsTenths -> "$seconds.$tenths"
}
