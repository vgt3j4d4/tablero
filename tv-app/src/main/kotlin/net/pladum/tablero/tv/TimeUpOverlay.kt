package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import net.pladum.tablero.shared.GameSnapshot

// Design pixels, from docs/design/boards.html (.time-up-frame, .time-up-bar, .btn); the bar is a tall one (BarTitle).
private const val FRAME_WIDTH = 14f
private const val BUTTON_HEIGHT = 64f
private const val BUTTON_RADIUS = 12f
private const val BUTTON_PADDING = 24f
private const val BUTTON_GAP = 16f
private const val FOCUSED_BORDER = 4f
private const val FOCUSED_SIZE = 32f
private const val UNFOCUSED_BORDER = 3f
private const val UNFOCUSED_SIZE = 28f

/**
 * Time up (design 03, 29): a red frame around the board while the game is at time up, and while the bar is open a
 * red bar over the status bar with the title, the score and the two buttons [TimeUpChoices.at] the snapshot calls
 * for: `TIME UP  Home wins 62–58` with NEW GAME / RESET CLOCK · KEEP SCORE when the game is over, `END OF PERIOD 2
 * Home leads 48–45` with HALFTIME · 15:00 (or BREAK · 2:00) / SKIP BREAK at the end of a period. Text is built in
 * [update]; [draw] only draws. Design pixels.
 */
class TimeUpOverlay(private val context: Context, fonts: BoardFonts) {
    var barVisible = false
        private set

    private var frameVisible = false
    private var shown: Shown? = null
    private var focused: TimeUpChoice? = null

    private val frame = RectF(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT).apply { inset(FRAME_WIDTH / 2, FRAME_WIDTH / 2) }
    private val framePaint = strokePaint(context.color(R.color.time_up_frame), FRAME_WIDTH)
    private val barPaint = fillPaint(context.color(R.color.time_up_bar))
    private val white = context.color(R.color.time_up_text)

    private val title = BarTitle(fonts, white, white)

    private val focusedFill = fillPaint(white)
    private val unfocusedOutline = strokePaint(context.color(R.color.time_up_outline), UNFOCUSED_BORDER)
    private val focusedText =
        textPaint(fonts.barlow(800), FOCUSED_SIZE, context.color(R.color.time_up_ink), 0.04f, Paint.Align.CENTER)
    private val unfocusedText = textPaint(fonts.barlow(700), UNFOCUSED_SIZE, white, 0.04f, Paint.Align.CENTER)
    private val focusedBaseline = focusedText.baselineAt(TALL_BAR_CENTER_Y)
    private val unfocusedBaseline = unfocusedText.baselineAt(TALL_BAR_CENTER_Y)

    private val first = Button()
    private val second = Button()

    /**
     * Takes the latest state; returns true when what the overlay shows changed. A [focused] choice from just before
     * the snapshot changed the pair highlights neither button until the bar catches up.
     */
    fun update(s: GameSnapshot, focused: TimeUpChoice?): Boolean {
        val bar = s.timeUp && focused != null
        val shown = if (bar) Shown(s.left.score, s.right.score, s.period, s.nextPeriod) else null
        if (s.timeUp == frameVisible && bar == barVisible && focused == this.focused && shown == this.shown) {
            return false
        }
        frameVisible = s.timeUp
        barVisible = bar
        this.focused = focused
        if (shown != null && shown != this.shown) layout(s)
        this.shown = shown
        return true
    }

    fun draw(canvas: Canvas) {
        if (frameVisible) canvas.drawRect(frame, framePaint)
        if (!barVisible) return
        canvas.drawRect(0f, TALL_BAR_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, barPaint)
        title.draw(canvas)
        first.draw(canvas, first.choice == focused)
        second.draw(canvas, second.choice == focused)
    }

    /** Labels and places the buttons, right to left, then the title and the result before them. */
    private fun layout(s: GameSnapshot) {
        val choices = TimeUpChoices.at(s)
        first.set(choices.first, buttonLabel(choices.first, s))
        second.set(choices.second, buttonLabel(choices.second, s))
        val buttonsLeft = first.layoutEndingAt(second.layoutEndingAt(DESIGN_WIDTH - BAR_PADDING) - BUTTON_GAP)

        val periodEnd = choices == TimeUpChoices.PERIOD_END
        val titleText = when {
            !periodEnd -> context.getString(R.string.time_up_title)
            s.overtime > 0 -> context.getString(R.string.time_up_overtime_end, s.overtime)
            else -> context.getString(R.string.time_up_period_end, s.period)
        }
        val left = s.left.score
        val right = s.right.score
        val ahead = if (periodEnd) R.string.time_up_leads else R.string.time_up_wins
        val result = when {
            left > right -> context.getString(ahead, context.getString(R.string.team_home), left, right)
            right > left -> context.getString(ahead, context.getString(R.string.team_away), right, left)
            else -> context.getString(R.string.time_up_tie, left, right)
        }
        title.layout(titleText, result, buttonsLeft)
    }

    /** A button's label; the break's says how long it lasts. */
    private fun buttonLabel(choice: TimeUpChoice, s: GameSnapshot): String = when (choice) {
        TimeUpChoice.NEW_GAME -> context.getString(R.string.time_up_new_game)
        TimeUpChoice.RESET_CLOCK -> context.getString(R.string.time_up_reset_clock)
        TimeUpChoice.BREAK -> {
            val next = checkNotNull(s.nextPeriod)
            val length = durationText(s.breakBeforeMs(next))
            context.getString(if (s.halftimeBefore(next)) R.string.time_up_halftime else R.string.time_up_break, length)
        }
        TimeUpChoice.SKIP_BREAK -> context.getString(R.string.time_up_skip_break)
    }

    /** What the bar's text depends on in the snapshot, to tell when it needs laying out again. */
    private data class Shown(val left: Int, val right: Int, val period: Int, val nextPeriod: Int?)

    /** One of the two buttons; [set] gives it its choice and label. Each is as wide as its focused look. */
    private inner class Button {
        var choice = TimeUpChoice.NEW_GAME
            private set
        private var label = ""
        private val box = RectF()
        private val outline = RectF()

        fun set(choice: TimeUpChoice, label: String) {
            this.choice = choice
            this.label = label
        }

        /** Places the button so that it ends at [right]; returns its left edge. */
        fun layoutEndingAt(right: Float): Float {
            val width = focusedText.measureText(label) + 2 * (BUTTON_PADDING + FOCUSED_BORDER)
            box.set(right - width, TALL_BAR_CENTER_Y - BUTTON_HEIGHT / 2, right, TALL_BAR_CENTER_Y + BUTTON_HEIGHT / 2)
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
