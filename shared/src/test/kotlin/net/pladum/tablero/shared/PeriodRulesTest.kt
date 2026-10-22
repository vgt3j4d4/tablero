package net.pladum.tablero.shared

import net.pladum.tablero.shared.Command.AdjustGameClock
import net.pladum.tablero.shared.Command.ChangePeriod
import net.pladum.tablero.shared.Command.EndBreak
import net.pladum.tablero.shared.Command.Foul
import net.pladum.tablero.shared.Command.NewGame
import net.pladum.tablero.shared.Command.NextPeriod
import net.pladum.tablero.shared.Command.ResetGameClock
import net.pladum.tablero.shared.Command.Score
import net.pladum.tablero.shared.Command.SetGameClock
import net.pladum.tablero.shared.Command.SetGameLength
import net.pladum.tablero.shared.Command.SetRules
import net.pladum.tablero.shared.Command.StartClock
import net.pladum.tablero.shared.Command.StopClock
import net.pladum.tablero.shared.Command.ToggleClock
import net.pladum.tablero.shared.Command.TogglePossession
import net.pladum.tablero.shared.Command.Undo
import net.pladum.tablero.shared.GameEvent.BreakEnded
import net.pladum.tablero.shared.GameEvent.ClockStarted
import net.pladum.tablero.shared.GameEvent.ClockStopped
import net.pladum.tablero.shared.GameEvent.GameClockExpired
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val FIBA = PeriodRules.FIBA
private val BREAK_MS = FIBA.breakMs
private val HALFTIME_MS = FIBA.halftimeMs
private val OVERTIME_MS = FIBA.overtimeLengthMs

/** Plays periods out until [period] is on the board, skipping the breaks. */
private fun EngineHarness.goTo(period: Int) {
    while (state.period < period) {
        playOut()
        apply(NextPeriod(withBreak = false))
    }
}

private fun EngineHarness.fouls(side: Side, count: Int) = repeat(count) { apply(Foul(side, 1)) }

class PeriodRulesTest {
    @Nested
    inner class SwitchingRules {
        @Test
        fun `the game starts under Pickup rules`() = engineTest {
            assertNull(state.rules.periods)
            assertEquals(RulesPreset.PICKUP, state.rules.preset)
        }

        @Test
        fun `FIBA keeps scores and clock, and starts at period 1 with no fouls`() = engineTest {
            apply(Score(LEFT, 1))
            apply(SetGameClock(7 * MINUTE_MS))
            fiba()
            assertEquals(FIBA, state.rules.periods)
            assertEquals(RulesPreset.FIBA, state.rules.preset)
            assertEquals(1, state.left.score)
            assertClock(7 * MINUTE_MS, running = false)
            assertEquals(1, state.period)
        }

        @Test
        fun `switching back to Pickup clears the period and the fouls`() = engineTest {
            fiba()
            goTo(3)
            fouls(LEFT, 2)
            apply(SetRules(RulesPreset.PICKUP))
            assertEquals(1, state.period)
            assertEquals(0, state.left.fouls)
        }

        @Test
        fun `rules change only while the clock is stopped, and only to other rules`() = engineTest {
            assertRejected(SetRules(RulesPreset.PICKUP), RejectReason.NO_CHANGE)
            apply(StartClock)
            assertRejected(SetRules(RulesPreset.FIBA), RejectReason.CLOCK_RUNNING)
        }

        @Test
        fun `period commands are rejected under Pickup rules`() = engineTest {
            assertRejected(Foul(LEFT, 1), RejectReason.NOT_IN_RULES)
            assertRejected(ChangePeriod(1), RejectReason.NOT_IN_RULES)
            playOut()
            assertRejected(NextPeriod(withBreak = true), RejectReason.NOT_IN_RULES)
            assertNull(state.nextPeriod)
        }
    }

    @Nested
    inner class Fouls {
        @Test
        fun `plus and minus one per side, while the clock runs too`() = engineTest {
            fiba()
            apply(StartClock)
            fouls(LEFT, 2)
            apply(Foul(RIGHT, 1))
            apply(Foul(LEFT, -1))
            assertEquals(1, state.left.fouls)
            assertEquals(1, state.right.fouls)
            assertTrue(state.gameClock.running)
        }

        @Test
        fun `fouls stay within one digit and steps of one`() = engineTest {
            fiba()
            assertRejected(Foul(LEFT, -1), RejectReason.OUT_OF_RANGE)
            fouls(LEFT, GameRules.MAX_FOULS)
            assertRejected(Foul(LEFT, 1), RejectReason.OUT_OF_RANGE)
            assertRejected(Foul(RIGHT, 2), RejectReason.INVALID_ARGUMENT)
        }

        @Test
        fun `the 5th foul of a period puts the team in penalty`() = engineTest {
            fiba()
            fouls(RIGHT, 4)
            assertFalse(state.inPenalty(RIGHT))
            apply(Foul(RIGHT, 1))
            assertTrue(state.inPenalty(RIGHT))
            assertFalse(state.inPenalty(LEFT))
        }
    }

    @Nested
    inner class EndOfPeriod {
        @Test
        fun `a period running out is time up with the buzzer, as under Pickup`() = engineTest {
            fiba()
            playOut()
            assertTrue(state.timeUp)
            assertEquals(GameClockExpired, clockEvents.last())
            assertEquals(2, state.nextPeriod)
        }

        @Test
        fun `skipping the break puts the next period's length on the clock, fouls back to 0`() = engineTest {
            fiba()
            fouls(LEFT, 3)
            apply(TogglePossession)
            playOut()
            apply(NextPeriod(withBreak = false))
            assertEquals(2, state.period)
            assertClock(GAME_MS, running = false)
            assertFalse(state.timeUp)
            assertFalse(state.inBreak)
            assertEquals(0, state.left.fouls)
            assertEquals(LEFT, state.possession, "the arrow carries across periods")
        }

        @Test
        fun `the next period is only offered at time up`() = engineTest {
            fiba()
            assertNull(state.nextPeriod)
            assertRejected(NextPeriod(withBreak = false), RejectReason.NO_NEXT_PERIOD)
        }

        @Test
        fun `periods use the learned game length`() = engineTest {
            fiba()
            apply(SetGameClock(8 * MINUTE_MS))
            playOut()
            apply(NextPeriod(withBreak = false))
            assertClock(8 * MINUTE_MS, running = false)
        }

        @Test
        fun `reset puts the period's length back, overtime included`() = engineTest {
            fiba()
            goTo(5)
            apply(StartClock)
            advance(MINUTE_MS)
            apply(ResetGameClock)
            assertClock(OVERTIME_MS, running = false)
        }
    }

    @Nested
    inner class Breaks {
        @ParameterizedTest(name = "after period {0} the break lasts {1} min")
        @CsvSource("1, 2", "2, 15", "3, 2")
        fun `breaks are 2 minutes, 15 at halftime`(period: Int, minutes: Long) = engineTest {
            fiba()
            goTo(period)
            playOut()
            assertEquals(minutes * MINUTE_MS, state.breakBeforeMs(period + 1))
        }

        @Test
        fun `the break counts down on the game clock, already in the next period`() = engineTest {
            fiba()
            fouls(RIGHT, 4)
            goTo(2)
            playOut()
            apply(NextPeriod(withBreak = true))
            assertTrue(state.inBreak)
            assertEquals(3, state.period)
            assertEquals(0, state.right.fouls)
            advance(MINUTE_MS)
            assertClock(HALFTIME_MS - MINUTE_MS, running = true)
        }

        @Test
        fun `a break running out sounds the buzzer and leaves the period's length, stopped`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            advance(BREAK_MS)
            assertFalse(state.inBreak)
            assertClock(GAME_MS, running = false)
            assertEquals(listOf(ClockStarted, GameClockExpired, ClockStarted, BreakEnded), clockEvents)
        }

        @Test
        fun `the clock toggle ends a break, whichever input sends it`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            apply(ToggleClock)
            assertFalse(state.inBreak)
            assertClock(GAME_MS, running = false)
            assertEquals(EndBreak, (events.last { it is GameEvent.CommandApplied } as GameEvent.CommandApplied).command)
        }

        @Test
        fun `ending a break early leaves the period's length, stopped`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            advance(SECOND_MS)
            apply(EndBreak)
            assertFalse(state.inBreak)
            assertClock(GAME_MS, running = false)
            assertEquals(ClockStopped, clockEvents.last())
            assertRejected(EndBreak, RejectReason.NOT_IN_BREAK)
        }

        @Test
        fun `a break adjusts like any clock, and taken to 0 it ends`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            apply(AdjustGameClock(MINUTE_MS))
            assertClock(BREAK_MS + MINUTE_MS, running = true)
            apply(AdjustGameClock(-BREAK_MS - MINUTE_MS))
            assertFalse(state.inBreak)
            assertClock(GAME_MS, running = false)
        }

        @Test
        fun `a break that runs out doesn't count as the game's first start`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            advance(BREAK_MS)
            assertEquals(GAME_MS, state.rules.gameLengthMs)
        }
    }

    @Nested
    inner class Overtime {
        @Test
        fun `a winner after period 4 ends the game`() = engineTest {
            fiba()
            goTo(4)
            apply(Score(LEFT, 1))
            playOut()
            assertNull(state.nextPeriod)
            assertRejected(NextPeriod(withBreak = true), RejectReason.NO_NEXT_PERIOD)
        }

        @Test
        fun `a tie after period 4 goes to overtime, 5 minutes, fouls kept`() = engineTest {
            fiba()
            goTo(4)
            fouls(LEFT, 5)
            playOut()
            assertEquals(5, state.nextPeriod)
            apply(NextPeriod(withBreak = true))
            assertEquals(BREAK_MS, remainingMs)
            advance(BREAK_MS)
            assertEquals(1, state.overtime)
            assertClock(OVERTIME_MS, running = false)
            assertEquals(5, state.left.fouls)
            assertTrue(state.inPenalty(LEFT))
        }

        @Test
        fun `overtimes follow one another while the score stays tied`() = engineTest {
            fiba()
            goTo(5)
            playOut()
            assertEquals(6, state.nextPeriod)
            apply(Score(RIGHT, 1))
            assertNull(state.nextPeriod)
        }

        @Test
        fun `no overtime beyond what the board can show`() = engineTest {
            fiba()
            goTo(FIBA.lastPeriod)
            assertEquals(PeriodRules.MAX_OVERTIMES, state.overtime)
            playOut()
            assertNull(state.nextPeriod)
        }
    }

    @Nested
    inner class Corrections {
        @Test
        fun `the period changes by one, leaving fouls and the clock alone`() = engineTest {
            fiba()
            fouls(LEFT, 2)
            apply(ChangePeriod(1))
            assertEquals(2, state.period)
            assertEquals(2, state.left.fouls)
            assertClock(GAME_MS, running = false)
            assertRejected(ChangePeriod(2), RejectReason.INVALID_ARGUMENT)
        }

        @Test
        fun `the period stays within 1 and the last overtime`() = engineTest {
            fiba()
            assertRejected(ChangePeriod(-1), RejectReason.OUT_OF_RANGE)
            repeat(FIBA.lastPeriod - 1) { apply(ChangePeriod(1)) }
            assertRejected(ChangePeriod(1), RejectReason.OUT_OF_RANGE)
        }

        @Test
        fun `a new game is period 1 with no fouls and no break`() = engineTest {
            fiba()
            fouls(LEFT, 3)
            playOut()
            apply(NextPeriod(withBreak = true))
            apply(NewGame)
            assertEquals(1, state.period)
            assertFalse(state.inBreak)
            assertEquals(TeamState(), state.left)
            assertClock(GAME_MS, running = false)
            assertEquals(FIBA, state.rules.periods, "a new game keeps the rules")
        }

        @Test
        fun `the period length is the game length setting`() = engineTest {
            fiba()
            apply(SetGameLength(8 * MINUTE_MS))
            apply(NewGame)
            assertClock(8 * MINUTE_MS, running = false)
        }
    }

    @Nested
    inner class Undoing {
        @Test
        fun `a foul goes back`() = engineTest {
            fiba()
            apply(Foul(RIGHT, 1))
            apply(Undo)
            assertEquals(0, state.right.fouls)
        }

        @Test
        fun `moving on to the next period brings the period's end back, silently`() = engineTest {
            fiba()
            fouls(LEFT, 4)
            playOut()
            apply(NextPeriod(withBreak = true))
            advance(SECOND_MS)
            apply(Undo)
            assertEquals(1, state.period)
            assertTrue(state.timeUp)
            assertFalse(state.inBreak)
            assertClock(0, running = false)
            assertEquals(4, state.left.fouls)
            assertEquals(1, clockEvents.count { it == GameClockExpired })
        }

        @Test
        fun `a period whose break already ran out goes back too`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            advance(BREAK_MS)
            apply(Undo)
            assertEquals(1, state.period)
            assertTrue(state.timeUp)
        }

        @Test
        fun `ending a break by mistake resumes it as if it had never ended`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            advance(10 * SECOND_MS)
            apply(EndBreak)
            advance(5 * SECOND_MS)
            apply(Undo)
            assertTrue(state.inBreak)
            assertClock(BREAK_MS - 15 * SECOND_MS, running = true)
        }

        @Test
        fun `a break that would have run out by now stays over, without a second buzzer`() = engineTest {
            fiba()
            playOut()
            apply(NextPeriod(withBreak = true))
            apply(EndBreak)
            advance(BREAK_MS)
            apply(Undo)
            assertFalse(state.inBreak)
            assertClock(GAME_MS, running = false)
            assertFalse(BreakEnded in clockEvents)
        }

        @Test
        fun `rules, period corrections and a reset during a break go back`() = engineTest {
            fiba()
            fouls(LEFT, 2)
            apply(ChangePeriod(1))
            apply(SetRules(RulesPreset.PICKUP))
            apply(Undo)
            assertEquals(FIBA, state.rules.periods)
            assertEquals(2, state.period)
            assertEquals(2, state.left.fouls)
            apply(Undo)
            assertEquals(1, state.period)

            playOut()
            apply(NextPeriod(withBreak = true))
            apply(ResetGameClock)
            assertFalse(state.inBreak)
            apply(Undo)
            assertTrue(state.inBreak)
            assertClock(BREAK_MS, running = true)
        }

        @Test
        fun `undoing a new game brings back the period and the fouls`() = engineTest {
            fiba()
            fouls(RIGHT, 3)
            goTo(3)
            fouls(RIGHT, 1)
            apply(NewGame)
            apply(Undo)
            assertEquals(3, state.period)
            assertEquals(1, state.right.fouls)
        }
    }

    @Test
    fun `stopping a break pauses it, and starting it again resumes it`() = engineTest {
        fiba()
        playOut()
        apply(NextPeriod(withBreak = true))
        advance(SECOND_MS)
        apply(StopClock)
        advance(MINUTE_MS)
        assertClock(BREAK_MS - SECOND_MS, running = false)
        assertTrue(state.inBreak)
        apply(StartClock)
        advance(BREAK_MS)
        assertFalse(state.inBreak)
    }
}
