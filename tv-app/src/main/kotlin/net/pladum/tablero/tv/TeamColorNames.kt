package net.pladum.tablero.tv

import net.pladum.tablero.shared.TeamColor

/** The string resource with this color's localized name: "Yellow" / "Amarillo". */
fun TeamColor.nameRes(): Int = when (this) {
    TeamColor.YELLOW -> R.string.team_yellow
    TeamColor.BLACK -> R.string.team_black
    TeamColor.WHITE -> R.string.team_white
    TeamColor.BLUE -> R.string.team_blue
    TeamColor.RED -> R.string.team_red
    TeamColor.GREEN -> R.string.team_green
    TeamColor.ORANGE -> R.string.team_orange
    TeamColor.PURPLE -> R.string.team_purple
    TeamColor.LIGHT_BLUE -> R.string.team_light_blue
}
