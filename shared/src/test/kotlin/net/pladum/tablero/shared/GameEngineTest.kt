package net.pladum.tablero.shared

import net.pladum.tablero.shared.Command.AdjustGameClock
import net.pladum.tablero.shared.Command.NewGame
import net.pladum.tablero.shared.Command.ResetGameClock
import net.pladum.tablero.shared.Command.Score
import net.pladum.tablero.shared.Command.SetGameClock
import net.pladum.tablero.shared.Command.SetGameLength
import net.pladum.tablero.shared.Command.SetTeams
import net.pladum.tablero.shared.Command.StartClock
import net.pladum.tablero.shared.Command.StopClock
import net.pladum.tablero.shared.Command.ToggleClock
import net.pladum.tablero.shared.Command.Undo
import net.pladum.tablero.shared.GameEvent.ClockStarted
import net.pladum.tablero.shared.GameEvent.ClockStopped
import net.pladum.tablero.shared.GameEvent.GameClockExpired
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import net.pladum.tablero.shared.TeamColor.BLACK
import net.pladum.tablero.shared.TeamColor.BLUE
import net.pladum.tablero.shared.TeamColor.RED
import net.pladum.tablero.shared.TeamColor.WHITE
import net.pladum.tablero.shared.TeamColor.YELLOW
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameEngineTest {
    @Test
    fun `first launch is yellow vs black, 0-0, full clock stopped, every color in the roster`() = engineTest {
        assertEquals(TeamState(YELLOW, 0), state.left)
        assertEquals(TeamState(BLACK, 0), state.right)
        assertClock(GAME_MS, running = false)
        assertFalse(state.timeUp)
        assertEquals(TeamColor.entries, state.teams)
        assertEquals(0, state.version)
    }

    @Test
    fun `every applied change increments the version, rejections do not`() = engineTest {
        apply(Score(LEFT, 1))
        assertEquals(1, state.version)
        assertRejected(Score(RIGHT, -1), RejectReason.SCORE_AT_ZERO)
        assertEquals(1, state.version)
        apply(Undo)
        assertEquals(2, state.version)
    }

    @Nested
    inner class Clock {
        @Test
        fun `toggle starts and stops the clock and emits events`() = engineTest {
            apply(ToggleClock)
            advance(3 * SECOND_MS)
            assertClock(GAME_MS - 3 * SECOND_MS, running = true)
            apply(ToggleClock)
            advance(5 * SECOND_MS)
            assertClock(GAME_MS - 3 * SECOND_MS, running = false)
            assertEquals(listOf(ClockStarted, ClockStopped), events)
        }

        @Test
        fun `start while running and stop while stopped are rejected`() = engineTest {
            assertRejected(StopClock, RejectReason.CLOCK_STOPPED)
            apply(StartClock)
            assertRejected(StartClock, RejectReason.CLOCK_RUNNING)
        }

        @Test
        fun `expires exactly once at zero and stops there`() = engineTest {
            apply(StartClock)
            advance(GAME_MS - 1)
            assertFalse(state.timeUp)
            advance(1)
            assertTrue(state.timeUp)
            assertClock(0, running = false)
            advance(MINUTE_MS)
            assertEquals(listOf(ClockStarted, GameClockExpired), events)
        }

        @Test
        fun `starting is rejected while time is up or with no time on the clock`() = engineTest {
            apply(SetGameClock(SECOND_MS))
            apply(StartClock)
            advance(SECOND_MS)
            assertRejected(ToggleClock, RejectReason.TIME_UP)
            apply(ResetGameClock)
            apply(SetGameClock(0))
            assertRejected(StartClock, RejectReason.CLOCK_AT_ZERO)
        }

        @Test
        fun `scoring while running leaves the clock alone`() = engineTest {
            apply(StartClock)
            advance(SECOND_MS)
            apply(Score(LEFT, 1))
            advance(SECOND_MS)
            assertClock(GAME_MS - 2 * SECOND_MS, running = true)
        }
    }

    @Nested
    inner class Scores {
        @Test
        fun `plus and minus one per side`() = engineTest {
            apply(Score(LEFT, 1))
            apply(Score(LEFT, 1))
            apply(Score(RIGHT, 1))
            apply(Score(LEFT, -1))
            assertEquals(1, state.left.score)
            assertEquals(1, state.right.score)
        }

        @Test
        fun `never below zero`() = engineTest {
            assertRejected(Score(LEFT, -1), RejectReason.SCORE_AT_ZERO)
        }

        @Test
        fun `only steps of one`() = engineTest {
            assertRejected(Score(LEFT, 2), RejectReason.INVALID_ARGUMENT)
            assertRejected(Score(LEFT, 0), RejectReason.INVALID_ARGUMENT)
        }
    }

    @Nested
    inner class AdjustClock {
        @Test
        fun `minutes and seconds while stopped`() = engineTest {
            apply(AdjustGameClock(-MINUTE_MS))
            apply(AdjustGameClock(-SECOND_MS))
            assertClock(GAME_MS - MINUTE_MS - SECOND_MS, running = false)
        }

        @Test
        fun `while running the clock keeps running and no time is lost`() = engineTest {
            apply(StartClock)
            advance(10 * SECOND_MS)
            apply(AdjustGameClock(MINUTE_MS))
            assertClock(GAME_MS + 50 * SECOND_MS, running = true)
            advance(SECOND_MS)
            assertClock(GAME_MS + 49 * SECOND_MS, running = true)
        }

        @Test
        fun `clamped to zero and to the clock maximum`() = engineTest {
            apply(SetGameClock(30 * SECOND_MS))
            apply(AdjustGameClock(-MINUTE_MS))
            assertClock(0, running = false)
            apply(SetGameClock(GameRules.MAX_CLOCK_MS - SECOND_MS))
            apply(AdjustGameClock(MINUTE_MS))
            assertClock(GameRules.MAX_CLOCK_MS, running = false)
        }

        @Test
        fun `nothing to adjust is rejected`() = engineTest {
            apply(SetGameClock(0))
            assertRejected(AdjustGameClock(-SECOND_MS), RejectReason.NO_CHANGE)
            assertRejected(AdjustGameClock(0), RejectReason.NO_CHANGE)
        }

        @Test
        fun `taking a running clock to zero expires it`() = engineTest {
            apply(StartClock)
            apply(AdjustGameClock(-GAME_MS))
            assertTrue(state.timeUp)
            assertEquals(listOf(ClockStarted, GameClockExpired), events)
        }

        @Test
        fun `adding time after time up clears it so the clock can restart`() = engineTest {
            apply(StartClock)
            advance(GAME_MS)
            apply(AdjustGameClock(2 * SECOND_MS))
            assertFalse(state.timeUp)
            apply(StartClock)
            assertClock(2 * SECOND_MS, running = true)
        }
    }

    @Nested
    inner class SetClock {
        @Test
        fun `sets an exact value while stopped`() = engineTest {
            apply(SetGameClock(42_300))
            assertClock(42_300, running = false)
        }

        @Test
        fun `rejected while running or out of range`() = engineTest {
            assertRejected(SetGameClock(-1), RejectReason.INVALID_ARGUMENT)
            assertRejected(SetGameClock(GameRules.MAX_CLOCK_MS + 1), RejectReason.INVALID_ARGUMENT)
            apply(StartClock)
            assertRejected(SetGameClock(MINUTE_MS), RejectReason.CLOCK_RUNNING)
        }

        @Test
        fun `setting time after time up clears it`() = engineTest {
            apply(StartClock)
            advance(GAME_MS)
            apply(SetGameClock(SECOND_MS))
            assertFalse(state.timeUp)
        }
    }

    @Nested
    inner class ResetAndNewGame {
        @Test
        fun `reset brings the clock back to the game length, stopped, scores kept`() = engineTest {
            apply(Score(LEFT, 1))
            apply(StartClock)
            advance(GAME_MS)
            apply(ResetGameClock)
            assertClock(GAME_MS, running = false)
            assertFalse(state.timeUp)
            assertEquals(1, state.left.score)
        }

        @Test
        fun `reset stops a running clock`() = engineTest {
            apply(StartClock)
            advance(SECOND_MS)
            apply(ResetGameClock)
            assertClock(GAME_MS, running = false)
            assertEquals(listOf(ClockStarted, ClockStopped), events)
        }

        @Test
        fun `a new game sets the teams, 0-0 and a full stopped clock`() = engineTest {
            apply(Score(LEFT, 1))
            apply(StartClock)
            advance(GAME_MS)
            apply(NewGame(left = BLUE, right = YELLOW))
            assertEquals(TeamState(BLUE, 0), state.left)
            assertEquals(TeamState(YELLOW, 0), state.right)
            assertClock(GAME_MS, running = false)
            assertFalse(state.timeUp)
        }

        @Test
        fun `a team cannot play itself`() = engineTest {
            assertRejected(NewGame(RED, RED), RejectReason.INVALID_ARGUMENT)
        }
    }

    @Nested
    inner class RulesAndRoster {
        @Test
        fun `game length applies from the next reset or new game`() = engineTest {
            apply(SetGameLength(8 * MINUTE_MS))
            assertClock(GAME_MS, running = false)
            apply(ResetGameClock)
            assertClock(8 * MINUTE_MS, running = false)
            apply(SetGameLength(12 * MINUTE_MS))
            apply(NewGame(RED, BLUE))
            assertClock(12 * MINUTE_MS, running = false)
        }

        @Test
        fun `game length is between one and sixty minutes`() = engineTest {
            apply(SetGameLength(GameRules.MIN_GAME_LENGTH_MS))
            apply(SetGameLength(GameRules.MAX_GAME_LENGTH_MS))
            assertRejected(SetGameLength(GameRules.MIN_GAME_LENGTH_MS - 1), RejectReason.INVALID_ARGUMENT)
            assertRejected(SetGameLength(GameRules.MAX_GAME_LENGTH_MS + 1), RejectReason.INVALID_ARGUMENT)
        }

        @Test
        fun `the roster needs at least two distinct colors`() = engineTest {
            apply(SetTeams(listOf(YELLOW, BLACK, WHITE)))
            assertEquals(listOf(YELLOW, BLACK, WHITE), state.teams)
            assertRejected(SetTeams(listOf(YELLOW)), RejectReason.INVALID_ARGUMENT)
            assertRejected(SetTeams(listOf(YELLOW, BLACK, YELLOW)), RejectReason.INVALID_ARGUMENT)
            assertRejected(SetTeams(listOf(YELLOW, BLACK, WHITE)), RejectReason.NO_CHANGE)
        }
    }

    @Nested
    inner class Undoing {
        @Test
        fun `nothing to undo`() = engineTest {
            assertRejected(Undo, RejectReason.NOTHING_TO_UNDO)
        }

        @Test
        fun `a score goes back`() = engineTest {
            apply(Score(RIGHT, 1))
            apply(Undo)
            assertEquals(0, state.right.score)
        }

        @Test
        fun `undoing a score while running leaves the clock running`() = engineTest {
            apply(StartClock)
            apply(Score(LEFT, 1))
            advance(SECOND_MS)
            apply(Undo)
            assertClock(GAME_MS - SECOND_MS, running = true)
        }

        @Test
        fun `an accidental start costs no time`() = engineTest {
            apply(ToggleClock)
            advance(5 * SECOND_MS)
            apply(Undo)
            assertClock(GAME_MS, running = false)
            assertEquals(listOf(ClockStarted, ClockStopped), events)
        }

        @Test
        fun `an accidental stop resumes as if it never stopped`() = engineTest {
            apply(StartClock)
            advance(10 * SECOND_MS)
            apply(ToggleClock)
            advance(5 * SECOND_MS)
            apply(Undo)
            assertClock(GAME_MS - 15 * SECOND_MS, running = true)
        }

        @Test
        fun `resuming a stop whose time already ran out expires the clock`() = engineTest {
            apply(StartClock)
            advance(GAME_MS - SECOND_MS)
            apply(StopClock)
            advance(10 * SECOND_MS)
            apply(Undo)
            assertTrue(state.timeUp)
            assertEquals(GameClockExpired, events.last())
        }

        @Test
        fun `an adjustment reverts the delta actually applied`() = engineTest {
            apply(SetGameClock(30 * SECOND_MS))
            apply(AdjustGameClock(-MINUTE_MS))
            apply(Undo)
            assertClock(30 * SECOND_MS, running = false)
        }

        @Test
        fun `undoing an adjustment keeps a running clock running`() = engineTest {
            apply(StartClock)
            apply(AdjustGameClock(MINUTE_MS))
            advance(SECOND_MS)
            apply(Undo)
            assertClock(GAME_MS - SECOND_MS, running = true)
        }

        @Test
        fun `set clock and reset go back to the previous clock`() = engineTest {
            apply(SetGameClock(MINUTE_MS))
            apply(Undo)
            assertClock(GAME_MS, running = false)

            apply(StartClock)
            advance(GAME_MS)
            apply(ResetGameClock)
            apply(Undo)
            assertClock(0, running = false)
            assertTrue(state.timeUp)
        }

        @Test
        fun `an accidental new game restores the previous game, clock stopped`() = engineTest {
            apply(Score(LEFT, 1))
            apply(Score(RIGHT, 1))
            apply(StartClock)
            advance(MINUTE_MS)
            apply(NewGame(RED, BLUE))
            advance(MINUTE_MS)
            apply(Undo)
            assertEquals(TeamState(YELLOW, 1), state.left)
            assertEquals(TeamState(BLACK, 1), state.right)
            assertClock(GAME_MS - MINUTE_MS, running = false)
        }

        @Test
        fun `game length and roster go back`() = engineTest {
            apply(SetGameLength(5 * MINUTE_MS))
            apply(SetTeams(listOf(RED, BLUE)))
            apply(Undo)
            apply(Undo)
            assertEquals(GameRules(), state.rules)
            assertEquals(TeamColor.entries, state.teams)
        }

        @Test
        fun `rejected commands are not recorded`() = engineTest {
            apply(Score(LEFT, 1))
            assertRejected(StopClock, RejectReason.CLOCK_STOPPED)
            apply(Undo)
            assertEquals(0, state.left.score)
        }

        @Test
        fun `history keeps the last commands only`() = engineTest {
            repeat(UNDO_HISTORY_SIZE + 5) { apply(Score(LEFT, 1)) }
            repeat(UNDO_HISTORY_SIZE) { apply(Undo) }
            assertRejected(Undo, RejectReason.NOTHING_TO_UNDO)
            assertEquals(5, state.left.score)
        }
    }
}
