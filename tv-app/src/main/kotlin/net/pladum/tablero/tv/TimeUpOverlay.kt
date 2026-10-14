package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

// Design pixels, from docs/design/boards.html (.time-up-frame, .time-up-bar, .btn).
private const val FRAME_WIDTH = 14f
private const val BAR_TOP = 976f
private const val BAR_CENTER_Y = 1028f
private const val PADDING = 48f
private const val TITLE_SIZE = 48f
private const val RESULT_SIZE = 30f
private const val TEXT_GAP = 20f
private const val BUTTON_HEIGHT = 68f
private const val BUTTON_RADIUS = 12f
private const val BUTTON_PADDING = 24f
private const val BUTTON_GAP = 16f
private const val FOCUSED_BORDER = 4f
private const val FOCUSED_SIZE = 32f
private const val UNFOCUSED_BORDER = 3f
private const val UNFOCUSED_SIZE = 28f

/** Minimum space between the title group and the buttons. */
private const val GROUP_GAP = 24f

/**
 * Time up (design 03): a red frame around the board while the game is at time up, and while the bar is open a red
 * bar over the status bar with `TIME UP  Home wins 62–58` and the NEW GAME / RESET CLOCK · KEEP SCORE buttons.
 * Text is built in [update]; [draw] only draws. Design pixels.
 */
class TimeUpOverlay(private val context: Context, fonts: BoardFonts) {
    var barVisible = false
        private set

    private var frameVisible = false
    private var left = -1
    private var right = -1
    private var focused: TimeUpChoice? = null

    private val frame = RectF(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT).apply { inset(FRAME_WIDTH / 2, FRAME_WIDTH / 2) }
    private val framePaint = strokePaint(context.getColor(R.color.time_up_frame), FRAME_WIDTH)
    private val barPaint = fillPaint(context.getColor(R.color.time_up_bar))
    private val white = context.getColor(R.color.time_up_text)

    private val title = context.getString(R.string.time_up_title)
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, white, 0.06f)
    private val resultPaint = textPaint(fonts.barlow(600), RESULT_SIZE, white)
    private val titleCenter = titlePaint.centerToBaselinePerPx("H")
    private var result = ""
    private var resultX = 0f
    private var baseline = 0f

    private val focusedFill = fillPaint(white)
    private val unfocusedOutline = strokePaint(context.getColor(R.color.time_up_outline), UNFOCUSED_BORDER)
    private val focusedText =
        textPaint(fonts.barlow(800), FOCUSED_SIZE, context.getColor(R.color.time_up_ink), 0.04f, Paint.Align.CENTER)
    private val unfocusedText = textPaint(fonts.barlow(700), UNFOCUSED_SIZE, white, 0.04f, Paint.Align.CENTER)
    private val focusedBaseline = BAR_CENTER_Y + focusedText.centerToBaselinePerPx("H") * FOCUSED_SIZE
    private val unfocusedBaseline = BAR_CENTER_Y + unfocusedText.centerToBaselinePerPx("H") * UNFOCUSED_SIZE

    /** Laid out right to left; each is as wide as its focused look, so focus moves without anything shifting. */
    private val buttons = TimeUpChoice.entries.map { Button(it) }
    private val buttonsLeft = buttons.foldRight(DESIGN_WIDTH - PADDING + BUTTON_GAP) { button, right ->
        button.layoutEndingAt(right - BUTTON_GAP)
    }

    /** Takes the latest state; returns true when what the overlay shows changed. */
    fun update(timeUp: Boolean, left: Int, right: Int, focused: TimeUpChoice?): Boolean {
        val bar = timeUp && focused != null
        if (timeUp == frameVisible && bar == barVisible && focused == this.focused &&
            (!bar || (left == this.left && right == this.right))
        ) {
            return false
        }
        frameVisible = timeUp
        barVisible = bar
        this.focused = focused
        this.left = left
        this.right = right
        if (bar) layout()
        return true
    }

    fun draw(canvas: Canvas) {
        if (frameVisible) canvas.drawRect(frame, framePaint)
        if (!barVisible) return
        canvas.drawRect(0f, BAR_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, barPaint)
        canvas.drawText(title, PADDING, baseline, titlePaint)
        canvas.drawText(result, resultX, baseline, resultPaint)
        for (button in buttons) button.draw(canvas, button.choice == focused)
    }

    /** Builds the result and places it after the title; both shrink if a translation would reach the buttons. */
    private fun layout() {
        result = when {
            left > right -> context.getString(R.string.time_up_wins, context.getString(R.string.team_home), left, right)
            right > left -> context.getString(R.string.time_up_wins, context.getString(R.string.team_away), right, left)
            else -> context.getString(R.string.time_up_tie, left, right)
        }
        titlePaint.textSize = TITLE_SIZE
        resultPaint.textSize = RESULT_SIZE
        val width = titlePaint.measureText(title) + TEXT_GAP + resultPaint.measureText(result)
        val available = buttonsLeft - GROUP_GAP - PADDING
        if (width > available) {
            titlePaint.textSize = TITLE_SIZE * available / width
            resultPaint.textSize = RESULT_SIZE * available / width
        }
        resultX = PADDING + titlePaint.measureText(title) + TEXT_GAP * titlePaint.textSize / TITLE_SIZE
        baseline = BAR_CENTER_Y + titleCenter * titlePaint.textSize
    }

    private inner class Button(val choice: TimeUpChoice) {
        private val label = context.getString(
            when (choice) {
                TimeUpChoice.NEW_GAME -> R.string.time_up_new_game
                TimeUpChoice.RESET_CLOCK -> R.string.time_up_reset_clock
            },
        )
        private val box = RectF()
        private val outline = RectF()

        /** Places the button so that it ends at [right]; returns its left edge. */
        fun layoutEndingAt(right: Float): Float {
            val width = focusedText.measureText(label) + 2 * (BUTTON_PADDING + FOCUSED_BORDER)
            box.set(right - width, BAR_CENTER_Y - BUTTON_HEIGHT / 2, right, BAR_CENTER_Y + BUTTON_HEIGHT / 2)
            outline.set(box)
            outline.inset(UNFOCUSED_BORDER / 2, UNFOCUSED_BORDER / 2) // The stroke is centered on the outline.
            return box.left
        }

        fun draw(canvas: Canvas, focused: Boolean) {
            if (focused) {
                canvas.drawRoundRect(box, BUTTON_RADIUS, BUTTON_RADIUS, focusedFill)
                canvas.drawText(label, box.centerX(), focusedBaseline, focusedText)
            } else {
                val radius = BUTTON_RADIUS - UNFOCUSED_BORDER / 2
                canvas.drawRoundRect(outline, radius, radius, unfocusedOutline)
                canvas.drawText(label, box.centerX(), unfocusedBaseline, unfocusedText)
            }
        }
    }
}
