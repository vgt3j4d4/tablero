package net.pladum.tablero.shared

import kotlinx.serialization.Serializable

/** Team colors; display names are localized in tv-app. */
@Serializable
enum class TeamColor { YELLOW, BLACK, WHITE, BLUE, RED, GREEN, ORANGE, PURPLE, LIGHT_BLUE }

@Serializable
enum class Side { LEFT, RIGHT }

@Serializable
data class TeamState(val color: TeamColor, val score: Int = 0)

/** The complete game state. Immutable; the engine publishes a new snapshot on every change. */
@Serializable
data class GameSnapshot(
    /** Increments on every state change. */
    val version: Long,
    val rules: GameRules,
    val gameClock: ClockState,
    val left: TeamState,
    val right: TeamState,
    /** The clock reached 0; the UI shows the time-up bar until the clock gets time again. */
    val timeUp: Boolean,
    /** The session's team colors, offered in the next-game picker. */
    val teams: List<TeamColor>,
) {
    fun team(side: Side): TeamState = when (side) {
        Side.LEFT -> left
        Side.RIGHT -> right
    }

    internal fun withTeam(side: Side, team: TeamState): GameSnapshot = when (side) {
        Side.LEFT -> copy(left = team)
        Side.RIGHT -> copy(right = team)
    }

    companion object {
        /** First launch: every color in the roster, Yellow vs Black, clock stopped at the game length. */
        fun initial(rules: GameRules = GameRules()): GameSnapshot = GameSnapshot(
            version = 0,
            rules = rules,
            gameClock = ClockState.stopped(rules.gameLengthMs),
            left = TeamState(TeamColor.YELLOW),
            right = TeamState(TeamColor.BLACK),
            timeUp = false,
            teams = TeamColor.entries,
        )
    }
}
