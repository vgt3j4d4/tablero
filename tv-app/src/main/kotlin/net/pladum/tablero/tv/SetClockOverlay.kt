package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

// Design pixels, from docs/design/boards.html (set-game-clock board, .modal, .scrim).
private val MODAL = RectF(400f, 150f, 1520f, 930f)
private const val MODAL_RADIUS = 28f
private const val BORDER = 2f
private const val CENTER_X = DESIGN_WIDTH / 2
private const val EYEBROW_CENTER_Y = 215f
private const val EYEBROW_SIZE = 30f
private const val WAS_CENTER_Y = 258f
private const val WAS_SIZE = 30f

private const val FIELD_SIZE = 300f
private const val FIELD_TOP = 390f
private const val FIELD_RADIUS = 20f
private const val FIELD_CENTER_Y = FIELD_TOP + FIELD_SIZE / 2
private const val MINUTES_CENTER_X = 770f
private const val SECONDS_CENTER_X = 1150f
private const val FOCUSED_BORDER = 6f
private const val UNFOCUSED_BORDER = 4f
private const val DIGIT_SIZE = 170f
private const val LABEL_SIZE = 26f
private const val LABEL_CENTER_Y = 715f

/** The ↑ / ↓ marks around the focused field: a 56×40 box (boards.html), drawn with a 5 px stroke. */
private const val ARROW_WIDTH = 56f
private const val ARROW_UP_TOP = 342f
private const val ARROW_DOWN_TOP = 731f
private const val ARROW_STROKE = 5f

private const val HINTS_LINE_LEFT = 456f
private const val HINTS_LINE_RIGHT = 1464f
private const val HINTS_TOP = 844f
private const val HINTS_CENTER_Y = 887f
private const val HINT_SIZE = 28f
private const val HINT_GAP = 44f

/**
 * The set game clock dialog (design 08): a modal over the dimmed board with the minutes and seconds in two fields,
 * the focused one outlined in amber. Text is built in [update]; [draw] only draws, into the scoreboard's cached
 * layer (the dialog only opens while the clock is stopped). Design pixels.
 */
class SetClockOverlay(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private var editor: SetClockEditor? = null

    private val text = context.getColor(R.color.text)
    private val muted = context.getColor(R.color.text_muted)
    private val adjust = context.getColor(R.color.adjust)

    private val scrimPaint = fillPaint(context.getColor(R.color.modal_scrim))
    private val modalPaint = fillPaint(context.getColor(R.color.panel))
    private val modalBorder = RectF(MODAL).apply { inset(BORDER / 2, BORDER / 2) }
    private val modalBorderPaint = strokePaint(context.getColor(R.color.panel_line), BORDER)
    private val linePaint = fillPaint(context.getColor(R.color.panel_line))

    private val eyebrow = context.getString(R.string.set_clock_title)
    private val eyebrowPaint = textPaint(fonts.barlow(600), EYEBROW_SIZE, muted, 0.24f, Paint.Align.CENTER)
    private val eyebrowBaseline = EYEBROW_CENTER_Y + eyebrowPaint.centerToBaselinePerPx("H") * EYEBROW_SIZE
    private var was = ""
    private val wasPaint = textPaint(fonts.barlow(500), WAS_SIZE, context.getColor(R.color.text_soft), align = Paint.Align.CENTER)
    private val wasBaseline = WAS_CENTER_Y + wasPaint.centerToBaselinePerPx("H") * WAS_SIZE

    private val digitPaint = textPaint(fonts.led, DIGIT_SIZE, text, align = Paint.Align.CENTER)
    private val digitBaseline = FIELD_CENTER_Y + digitPaint.centerToBaselinePerPx("8") * DIGIT_SIZE
    private val labelPaint = textPaint(fonts.barlow(600), LABEL_SIZE, muted, 0.2f, Paint.Align.CENTER)
    private val labelBaseline = LABEL_CENTER_Y + labelPaint.centerToBaselinePerPx("H") * LABEL_SIZE
    private val focusedFill = fillPaint(context.getColor(R.color.panel_raised))
    private val focusedOutline = strokePaint(adjust, FOCUSED_BORDER)
    private val unfocusedOutline = strokePaint(context.getColor(R.color.field_outline), UNFOCUSED_BORDER)
    private val arrowPaint = strokePaint(adjust, ARROW_STROKE).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val arrowUp = Path().apply {
        moveTo(8f, 32f)
        lineTo(28f, 10f)
        lineTo(48f, 32f)
    }
    private val arrowDown = Path().apply {
        moveTo(8f, 8f)
        lineTo(28f, 30f)
        lineTo(48f, 8f)
    }
    private val minutes = Field(MINUTES_CENTER_X, context.getString(R.string.set_clock_minutes))
    private val seconds = Field(SECONDS_CENTER_X, context.getString(R.string.set_clock_seconds))

    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, muted, 0.06f)
    private val keycapPaint = keycapPaint(muted)
    private val hintBaseline = HINTS_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hints = listOf(
        KeyHint(context.getString(R.string.key_up_down), context.getString(R.string.hint_change)),
        KeyHint(context.getString(R.string.key_left_right), context.getString(R.string.hint_field)),
        KeyHint(context.getString(R.string.key_ok), context.getString(R.string.hint_save)),
        KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_cancel)),
    )

    init {
        // Centered as a group.
        val widths = hints.map { it.width(hintPaint) }
        var right = CENTER_X + (widths.sum() + HINT_GAP * (hints.size - 1)) / 2
        for (i in hints.indices.reversed()) {
            hints[i].layoutEndingAt(right, HINTS_CENTER_Y, hintPaint)
            right -= widths[i] + HINT_GAP
        }
    }

    /** Takes the dialog's state (null = closed); returns true when what it shows changed. */
    fun update(editor: SetClockEditor?): Boolean {
        if (editor == this.editor) return false
        this.editor = editor
        visible = editor != null
        if (editor != null) {
            was = context.getString(R.string.set_clock_was, editor.was.format())
            minutes.update(editor.minutes, editor.focused == ClockField.MINUTES)
            seconds.update(editor.seconds, editor.focused == ClockField.SECONDS)
        }
        return true
    }

    /** Over everything else on the board, the lit clock digits included. */
    fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, scrimPaint)
        canvas.drawRoundRect(MODAL, MODAL_RADIUS, MODAL_RADIUS, modalPaint)
        canvas.drawRoundRect(modalBorder, MODAL_RADIUS, MODAL_RADIUS, modalBorderPaint)
        canvas.drawText(eyebrow, CENTER_X, eyebrowBaseline, eyebrowPaint)
        canvas.drawText(was, CENTER_X, wasBaseline, wasPaint)
        minutes.draw(canvas)
        seconds.draw(canvas)
        canvas.drawText(":", CENTER_X, digitBaseline, digitPaint)
        canvas.drawRect(HINTS_LINE_LEFT, HINTS_TOP, HINTS_LINE_RIGHT, HINTS_TOP + BORDER, linePaint)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    private inner class Field(private val centerX: Float, private val label: String) {
        private val box = RectF(centerX - FIELD_SIZE / 2, FIELD_TOP, centerX + FIELD_SIZE / 2, FIELD_TOP + FIELD_SIZE)
        private val focusedBox = RectF(box).apply { inset(FOCUSED_BORDER / 2, FOCUSED_BORDER / 2) }
        private val unfocusedBox = RectF(box).apply { inset(UNFOCUSED_BORDER / 2, UNFOCUSED_BORDER / 2) }
        private var digits = ""
        private var focused = false

        fun update(value: Int, focused: Boolean) {
            digits = value.toString().padStart(2, '0')
            this.focused = focused
        }

        fun draw(canvas: Canvas) {
            if (focused) {
                canvas.drawRoundRect(box, FIELD_RADIUS, FIELD_RADIUS, focusedFill)
                val radius = FIELD_RADIUS - FOCUSED_BORDER / 2
                canvas.drawRoundRect(focusedBox, radius, radius, focusedOutline)
                drawArrow(canvas, arrowUp, ARROW_UP_TOP)
                drawArrow(canvas, arrowDown, ARROW_DOWN_TOP)
            } else {
                val radius = FIELD_RADIUS - UNFOCUSED_BORDER / 2
                canvas.drawRoundRect(unfocusedBox, radius, radius, unfocusedOutline)
            }
            canvas.drawText(digits, centerX, digitBaseline, digitPaint)
            labelPaint.color = if (focused) adjust else muted
            canvas.drawText(label, centerX, labelBaseline, labelPaint)
        }

        private fun drawArrow(canvas: Canvas, path: Path, top: Float) {
            val saved = canvas.save()
            canvas.translate(centerX - ARROW_WIDTH / 2, top)
            canvas.drawPath(path, arrowPaint)
            canvas.restoreToCount(saved)
        }
    }
}
