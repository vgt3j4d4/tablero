package net.pladum.tablero.shared

import kotlinx.serialization.Serializable

/** The home team plays on the left, the away team on the right; tv-app shows them as HOME / AWAY. */
@Serializable
enum class Side { LEFT, RIGHT }

/** [fouls] counts team fouls in the current period, under period rules only. */
@Serializable
data class TeamState(val score: Int = 0, val fouls: Int = 0)

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
    /**
     * The team the possession arrow points to, the one with the next alternating possession; null while the arrow is
     * hidden, as it is after a new game and in states saved before this field existed.
     */
    val possession: Side? = null,
    /** Under period rules, the period being played, or coming after a break; past the last one it is overtime. */
    val period: Int = 1,
    /** The game clock counts down a break before [period]; when it runs out the clock shows the period's length. */
    val inBreak: Boolean = false,
) {
    fun team(side: Side): TeamState = when (side) {
        Side.LEFT -> left
        Side.RIGHT -> right
    }

    internal fun withTeam(side: Side, team: TeamState): GameSnapshot = when (side) {
        Side.LEFT -> copy(left = team)
        Side.RIGHT -> copy(right = team)
    }

    /** Under period rules, the overtime being played (1 for the first); 0 in regular time and under Pickup. */
    val overtime: Int get() = rules.periods?.let { (period - it.periods).coerceAtLeast(0) } ?: 0

    /**
     * Where the game goes at time up under period rules: the next period, or an overtime while the score is tied
     * after the last period; null when the game is over, the clock hasn't run out, or under Pickup.
     */
    val nextPeriod: Int?
        get() {
            val rules = rules.periods ?: return null
            if (!timeUp) return null
            val next = period + 1
            val tied = left.score == right.score
            return next.takeIf { (period < rules.periods || tied) && it <= rules.lastPeriod }
        }

    /** The break that comes before [next] under period rules: halftime after the middle period. */
    fun breakBeforeMs(next: Int): Long {
        val rules = checkNotNull(rules.periods) { "Breaks need period rules" }
        return if (next - 1 == rules.periods / 2) rules.halftimeMs else rules.breakMs
    }

    /** The clock [period] starts from: the game length, or the overtime length past the last period. */
    fun lengthMs(period: Int): Long {
        val periods = rules.periods
        return if (periods != null && period > periods.periods) periods.overtimeLengthMs else rules.gameLengthMs
    }

    /** [side] has committed the period's penalty foul, so every foul it commits from now gives free throws. */
    fun inPenalty(side: Side): Boolean {
        val rules = rules.periods ?: return false
        return team(side).fouls >= rules.penaltyFoul
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
