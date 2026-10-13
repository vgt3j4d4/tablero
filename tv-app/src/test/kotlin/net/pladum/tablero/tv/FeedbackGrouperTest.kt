package net.pladum.tablero.tv

import net.pladum.tablero.shared.Command
import net.pladum.tablero.shared.Command.Score
import net.pladum.tablero.shared.GameEvent
import net.pladum.tablero.shared.GameEvent.CommandApplied
import net.pladum.tablero.shared.GameEvent.CommandUndone
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.NANOS_PER_MS
import net.pladum.tablero.shared.RejectReason
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.Side.LEFT
import net.pladum.tablero.shared.Side.RIGHT
import net.pladum.tablero.shared.TeamColor.BLACK
import net.pladum.tablero.shared.TeamColor.RED
import net.pladum.tablero.shared.TeamColor.YELLOW
import net.pladum.tablero.shared.TeamState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val WINDOW_MS = FEEDBACK_GROUP_WINDOW_NANOS / NANOS_PER_MS

class FeedbackGrouperTest {
    private val grouper = FeedbackGrouper()
    private var state = GameSnapshot.initial().copy(left = TeamState(YELLOW, 48), right = TeamState(BLACK, 45))

    /** Applies a score press to [state] at [atMs] and returns the confirmation for it. */
    private fun press(side: Side, delta: Int, atMs: Long): Feedback? {
        val before = state
        state = when (side) {
            LEFT -> before.copy(left = before.left.copy(score = before.left.score + delta))
            RIGHT -> before.copy(right = before.right.copy(score = before.right.score + delta))
        }
        return grouper.onEvent(CommandApplied(Score(side, delta), before, state), atMs * NANOS_PER_MS)
    }

    @Test
    fun `one press`() {
        assertEquals(Feedback.Score(LEFT, YELLOW, 1, from = 48, to = 49), press(LEFT, 1, atMs = 0))
    }

    @Test
    fun `presses within the window add up, each measured from the previous one`() {
        press(LEFT, 1, atMs = 0)
        press(LEFT, 1, atMs = WINDOW_MS)
        assertEquals(Feedback.Score(LEFT, YELLOW, 3, from = 48, to = 51), press(LEFT, 1, atMs = 2 * WINDOW_MS))
    }

    @Test
    fun `a press after the window starts a new confirmation`() {
        press(LEFT, 1, atMs = 0)
        assertEquals(Feedback.Score(LEFT, YELLOW, 1, from = 49, to = 50), press(LEFT, 1, atMs = WINDOW_MS + 1))
    }

    @Test
    fun `the other side or the other direction starts a new confirmation`() {
        press(LEFT, 1, atMs = 0)
        assertEquals(Feedback.Score(RIGHT, BLACK, 1, from = 45, to = 46), press(RIGHT, 1, atMs = 100))
        assertEquals(Feedback.Score(LEFT, YELLOW, 1, from = 49, to = 50), press(LEFT, 1, atMs = 200))
        assertEquals(Feedback.Score(LEFT, YELLOW, -1, from = 50, to = 49), press(LEFT, -1, atMs = 300))
        assertEquals(Feedback.Score(LEFT, YELLOW, -2, from = 50, to = 48), press(LEFT, -1, atMs = 400))
    }

    @Test
    fun `a score changed in between by something else starts a new confirmation`() {
        press(LEFT, 1, atMs = 0)
        state = state.copy(left = TeamState(RED, 0)) // A new game, for instance.
        assertEquals(Feedback.Score(LEFT, RED, 1, from = 0, to = 1), press(LEFT, 1, atMs = 100))
    }

    @Test
    fun `undoing a score shows the undone press and what it did to the score`() {
        press(LEFT, 1, atMs = 0)
        val before = state
        val after = before.copy(left = before.left.copy(score = 48))
        val undone = grouper.onEvent(CommandUndone(Score(LEFT, 1), before, after), 100 * NANOS_PER_MS)
        assertEquals(Feedback.UndoneScore(YELLOW, 1, from = 49, to = 48), undone)
    }

    @Test
    fun `an undo ends the group`() {
        press(LEFT, 1, atMs = 0)
        grouper.onEvent(CommandUndone(Score(LEFT, 1), state, state), 100 * NANOS_PER_MS)
        assertEquals(Feedback.Score(LEFT, YELLOW, 1, from = 49, to = 50), press(LEFT, 1, atMs = 200))
    }

    @Test
    fun `other undone commands are named`() {
        val undone = { command: Command -> grouper.onEvent(CommandUndone(command, state, state), 0) }
        assertEquals(Feedback.Undone(UndoneAction.START_CLOCK), undone(Command.StartClock))
        assertEquals(Feedback.Undone(UndoneAction.NEW_GAME), undone(Command.NewGame(RED, BLACK)))
        assertEquals(Feedback.Undone(UndoneAction.ADJUST_CLOCK), undone(Command.AdjustGameClock(1_000)))
    }

    @Test
    fun `clock commands and clock events need no confirmation`() {
        assertNull(grouper.onEvent(CommandApplied(Command.StartClock, state, state), 0))
        assertNull(grouper.onEvent(GameEvent.ClockStarted, 0))
        assertNull(grouper.onEvent(GameEvent.GameClockExpired, 0))
    }

    @Test
    fun `nothing to undo is confirmed, other rejections are not`() {
        assertEquals(Feedback.NothingToUndo, grouper.onRejected(Command.Undo, RejectReason.NOTHING_TO_UNDO))
        assertNull(grouper.onRejected(Score(LEFT, -1), RejectReason.SCORE_AT_ZERO))
    }
}
