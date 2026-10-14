package net.pladum.tablero.shared

import kotlinx.serialization.Serializable

/** The home team plays on the left, the away team on the right; tv-app shows them as HOME / AWAY. */
@Serializable
enum class Side { LEFT, RIGHT }

@Serializable
data class TeamState(val score: Int = 0)

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
    /**
     * The clock has been started since the last new game. That first start makes the time on the clock the game
     * length, so a game set to 7:00 before it starts makes 7:00 the length for resets and new games, while a later
     * start (a short overtime after a reset) leaves it alone. States saved before this field existed read as
     * started, so a restored game never takes its length from a paused clock.
     */
    val gameStarted: Boolean = true,
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
        /** First launch: 0–0, clock stopped at the game length. */
        fun initial(rules: GameRules = GameRules()): GameSnapshot = GameSnapshot(
            version = 0,
            rules = rules,
            gameClock = ClockState.stopped(rules.gameLengthMs),
            left = TeamState(),
            right = TeamState(),
            timeUp = false,
            gameStarted = false,
        )
    }
}
