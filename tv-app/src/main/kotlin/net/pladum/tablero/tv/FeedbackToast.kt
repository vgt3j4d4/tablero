package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import net.pladum.tablero.shared.Side
import java.util.Locale

// Design pixels, from docs/design/boards.html (.toast).
private const val LEFT = 500f
private const val RIGHT = 1420f
private const val TOP = 1016f
private const val CENTER_Y = 1048f
private const val RADIUS = 16f
private const val PADDING = 28f
private const val GAP = 16f
private const val TITLE_SIZE = 40f
private const val DETAIL_SIZE = 32f
private const val HINT_SIZE = 28f

/** Minimum space between the confirmation text and the Undo hint. */
private const val HINT_GAP = 24f

/**
 * The confirmation over the status bar (design 05: `HOME +3`, `48 → 51`, then the `BACK UNDO` hint).
 * Text is built and measured in [show]; [draw] only draws. Coordinates are design pixels.
 */
class FeedbackToast(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private val textOff = context.color(R.color.text_off)
    private val panel = RectF(LEFT, TOP, RIGHT, DESIGN_HEIGHT + RADIUS) // Square bottom: its corners are off screen.
    private val panelPaint = fillPaint(context.color(R.color.text))

    private var title = ""
    private var titleX = 0f
    private var titleBaseline = 0f
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, context.color(R.color.board_background), 0.04f)
    private val titleCenter = titlePaint.centerToBaselinePerPx("H")

    private var detail = ""
    private var detailX = 0f
    private val detailPaint = textPaint(fonts.barlow(600), DETAIL_SIZE, textOff)
    private val detailBaseline = CENTER_Y + detailPaint.centerToBaselinePerPx("0") * DETAIL_SIZE

    private var hasHint = false
    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, textOff, 0.06f)
    private val hintBaseline = CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val keycapPaint = keycapPaint(textOff)
    private val hint = KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_undo))
    private val hintLeft = hint.layoutEndingAt(RIGHT - PADDING, CENTER_Y, hintPaint)

    fun show(feedback: Feedback) {
        val locale = context.resources.configuration.primaryLocale
        detail = ""
        hasHint = true
        title = when (feedback) {
            is Feedback.Score -> {
                detail = context.getString(R.string.feedback_score_change, feedback.from, feedback.to)
                scoreTitle(feedback.side, feedback.delta, locale)
            }
            is Feedback.UndoneScore -> {
                detail = context.getString(R.string.feedback_score_change, feedback.from, feedback.to)
                context.getString(R.string.feedback_undone, scoreTitle(feedback.side, feedback.delta, locale))
            }
            is Feedback.Undone ->
                context.getString(R.string.feedback_undone, context.getString(feedback.action.labelRes()))
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
        if (hasHint) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    private fun layout() {
        val x = LEFT + PADDING
        val right = if (hasHint) hintLeft - HINT_GAP else RIGHT - PADDING
        val detailWidth = if (detail.isEmpty()) 0f else GAP + detailPaint.measureText(detail)
        titleX = x
        val titleWidth = titlePaint.fitText(title, TITLE_SIZE, right - x - detailWidth)
        titleBaseline = CENTER_Y + titleCenter * titlePaint.textSize
        detailX = x + titleWidth + GAP
    }

    /** `HOME +3`, `LOCAL −1`. */
    private fun scoreTitle(side: Side, delta: Int, locale: Locale): String {
        val change = if (delta >= 0) {
            context.getString(R.string.feedback_plus, delta)
        } else {
            context.getString(R.string.feedback_minus, -delta)
        }
        return context.getString(R.string.feedback_score, context.getString(side.teamNameRes()).uppercase(locale), change)
    }

    private fun UndoneAction.labelRes(): Int = when (this) {
        UndoneAction.START_CLOCK -> R.string.undone_start_clock
        UndoneAction.STOP_CLOCK -> R.string.undone_stop_clock
        UndoneAction.ADJUST_CLOCK -> R.string.undone_adjust_clock
        UndoneAction.SET_CLOCK -> R.string.undone_set_clock
        UndoneAction.RESET_CLOCK -> R.string.undone_reset_clock
        UndoneAction.NEW_GAME -> R.string.undone_new_game
        UndoneAction.GAME_LENGTH -> R.string.undone_game_length
    }
}
