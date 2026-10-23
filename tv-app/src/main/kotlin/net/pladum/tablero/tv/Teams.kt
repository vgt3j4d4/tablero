package net.pladum.tablero.tv

import android.content.Context
import net.pladum.tablero.shared.Side

/** The string resource naming the team on this side: "Home" / "Local" on the left, "Away" / "Visita" on the right. */
fun Side.teamNameRes(): Int = when (this) {
    Side.LEFT -> R.string.team_home
    Side.RIGHT -> R.string.team_away
}

/** The team on [side] named as the board shows it: `HOME`, `LOCAL`. */
fun Context.teamTitle(side: Side): String = getString(side.teamNameRes()).uppercase(resources.configuration.primaryLocale)
