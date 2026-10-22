package net.pladum.tablero.shared

import kotlinx.serialization.Serializable

/** Rules are data: everything that varies between game formats lives here, not in `if` branches. */
@Serializable
data class GameRules(
    /**
     * Clock value for a reset or a new game (under [periods], each period's length): set from the menu, or by a
     * game's first start ([GameSnapshot.gameStarted]).
     */
    val gameLengthMs: Long = DEFAULT_GAME_LENGTH_MS,
    /** Below this remaining time the clock is read with tenths (`SS.t`) instead of `MM:SS`. */
    val tenthsBelowMs: Long = DEFAULT_TENTHS_BELOW_MS,
    /** The rule set the menu chose; saves store its name, and its data comes with the app ([periods]). */
    val preset: RulesPreset = RulesPreset.PICKUP,
) {
    /** How a game splits into periods; null plays one clock per game (Pickup). */
    val periods: PeriodRules? get() = preset.periods

    companion object {
        const val DEFAULT_GAME_LENGTH_MS = 10 * 60_000L
        const val DEFAULT_TENTHS_BELOW_MS = 60_000L
        const val MIN_GAME_LENGTH_MS = 60_000L
        const val MAX_GAME_LENGTH_MS = 60 * 60_000L

        /** Upper bound for any clock value, including quick adjustments beyond the game length. */
        const val MAX_CLOCK_MS = MAX_GAME_LENGTH_MS

        /** A team's fouls fit one digit on the board. */
        const val MAX_FOULS = 9
    }
}

/**
 * A game played in periods: a break after each one, overtime while the score is tied after the last, and team
 * fouls counted per period.
 */
@Serializable
data class PeriodRules(
    val periods: Int,
    val overtimeLengthMs: Long,
    /** The break after each period but the middle one, and before each overtime. */
    val breakMs: Long,
    /** The break after the middle period (half of [periods]). */
    val halftimeMs: Long,
    /** The first team foul of a period that gives the other team free throws. */
    val penaltyFoul: Int,
    /** Fouls in overtime add to those of the last period instead of starting again from 0. */
    val overtimeKeepsFouls: Boolean,
) {
    /** The highest period number: overtimes beyond [MAX_OVERTIMES] don't fit the board's digit. */
    val lastPeriod: Int get() = periods + MAX_OVERTIMES

    companion object {
        const val MAX_OVERTIMES = 9

        /** FIBA: 4 × 10:00 (the period length is the game length), 2:00 breaks, 15:00 halftime, 5:00 overtimes. */
        val FIBA = PeriodRules(
            periods = 4,
            overtimeLengthMs = 5 * 60_000L,
            breakMs = 2 * 60_000L,
            halftimeMs = 15 * 60_000L,
            penaltyFoul = 5,
            overtimeKeepsFouls = true,
        )
    }
}

/** The rule sets the menu offers; each one names its data. */
@Serializable
enum class RulesPreset(val periods: PeriodRules?) {
    PICKUP(null),
    FIBA(PeriodRules.FIBA),
}
