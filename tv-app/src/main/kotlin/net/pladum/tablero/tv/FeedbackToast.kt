package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.toast); the bar's place is in BoardPaint.kt.
private const val LEFT = 500f
private const val RIGHT = 1420f
private const val RADIUS = 16f
private const val PADDING = 28f
private const val GAP = 16f
private const val TITLE_SIZE = 40f
private const val DETAIL_SIZE = 32f
private const val HINT_SIZE = 28f

/** Minimum space between the confirmation text and the Undo hint. */
private const val HINT_GAP = 24f

/**
 * The confirmation over the status bar (design 05, 17, 23: `HOME +3`, `48 → 51`, then the `BACK UNDO` hint), in the
 * colors of the board's [BoardMode]: [mode] until [setMode] changes it. Text is built and measured in [show]; [draw]
 * only draws. Coordinates are design pixels.
 */
class FeedbackToast(private val context: Context, fonts: BoardFonts, mode: BoardMode) {
    var visible = false
        private set

    /** Square bottom: its rounded corners are off screen. */
    private val panel = RectF(LEFT, STATUS_BAR_TOP, RIGHT, DESIGN_HEIGHT + RADIUS)
    private val panelPaint = fillPaint()

    private var title = ""
    private var titleX = 0f
    private var titleBaseline = 0f
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, 0, 0.04f)

    private var detail = ""
    private var detailX = 0f
    private val detailPaint = textPaint(fonts.barlow(600), DETAIL_SIZE, 0)
    private val detailBaseline = detailPaint.baselineAt(STATUS_BAR_CENTER_Y, "0")

    private var hasHint = false
    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, 0, 0.06f)
    private val keycapPaint = keycapPaint(0)
    private val hint = KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_undo))
    private val hintLeft = hint.layoutEndingAt(RIGHT - PADDING, STATUS_BAR_CENTER_Y, hintPaint)

    init {
        setMode(mode)
    }

    /** Takes [mode]'s colors. */
    fun setMode(mode: BoardMode) {
        panelPaint.color = context.color(mode.toastPanel)
        titlePaint.color = context.color(mode.toastTitle)
        val detail = context.color(mode.toastDetail)
        for (paint in arrayOf(detailPaint, hintPaint, keycapPaint)) paint.color = detail
    }

    fun show(feedback: Feedback) {
        detail = ""
        hasHint = true
        title = when (feedback) {
            is Feedback.Score -> {
                detail = context.getString(R.string.feedback_score_change, feedback.from, feedback.to)
                scoreTitle(feedback.side, feedback.delta)
            }
            is Feedback.UndoneScore -> {
                detail = context.getString(R.string.feedback_score_change, feedback.from, feedback.to)
                context.getString(R.string.feedback_undone, scoreTitle(feedback.side, feedback.delta))
            }
            is Feedback.Foul -> {
                detail = context.getString(R.string.feedback_score_change, feedback.from, feedback.to)
                val foul = context.getString(R.string.feedback_foul, context.teamTitle(feedback.side))
                if (feedback.undone) context.getString(R.string.feedback_undone, foul) else foul
            }
            is Feedback.Undone ->
                context.getString(R.string.feedback_undone, context.getString(feedback.action.labelRes()))
            is Feedback.Possession -> feedback.side?.let {
                context.getString(R.string.feedback_possession, context.teamTitle(it))
            } ?: context.getString(R.string.feedback_possession_hidden)
            Feedback.NothingToUndo -> {
                hasHint = false
                context.getString(R.string.feedback_nothing_to_undo)
            }
        }
        layout()
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawRoundRect(panel, RADIUS, RADIUS, panelPaint)
        canvas.drawText(title, titleX, titleBaseline, titlePaint)
        if (detail.isNotEmpty()) canvas.drawText(detail, detailX, detailBaseline, detailPaint)
        if (hasHint) hint.draw(canvas, hintPaint, keycapPaint)
    }

    private fun layout() {
        val x = LEFT + PADDING
        val right = if (hasHint) hintLeft - HINT_GAP else RIGHT - PADDING
        val detailWidth = if (detail.isEmpty()) 0f else GAP + detailPaint.measureText(detail)
        titleX = x
        val titleWidth = titlePaint.fitText(title, TITLE_SIZE, right - x - detailWidth)
        titleBaseline = titlePaint.baselineAt(STATUS_BAR_CENTER_Y)
        detailX = x + titleWidth + GAP
    }

    /** `HOME +3`, `LOCAL −1`. */
    private fun scoreTitle(side: Side, delta: Int): String {
        val change = if (delta >= 0) {
            context.getString(R.string.feedback_plus, delta)
        } else {
            context.getString(R.string.feedback_minus, -delta)
        }
        return context.getString(R.string.feedback_score, context.teamTitle(side), change)
    }

    private fun UndoneAction.labelRes(): Int = when (this) {
        UndoneAction.START_CLOCK -> R.string.undone_start_clock
        UndoneAction.STOP_CLOCK -> R.string.undone_stop_clock
        UndoneAction.ADJUST_CLOCK -> R.string.undone_adjust_clock
        UndoneAction.SET_CLOCK -> R.string.undone_set_clock
        UndoneAction.RESET_CLOCK -> R.string.undone_reset_clock
        UndoneAction.NEW_GAME -> R.string.undone_new_game
        UndoneAction.GAME_LENGTH -> R.string.undone_game_length
        UndoneAction.POSSESSION -> R.string.undone_possession
        UndoneAction.RULES -> R.string.undone_rules
        UndoneAction.NEXT_PERIOD -> R.string.undone_next_period
        UndoneAction.END_BREAK -> R.string.undone_end_break
        UndoneAction.PERIOD -> R.string.undone_period
    }
}
