package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

// Design pixels, from docs/design/boards.html (exit-confirm board, .modal, .btn, .eyebrow).
private val MODAL = RectF(460f, 230f, 1460f, 850f)
private const val MODAL_RADIUS = 28f
private const val BORDER = 2f
private const val CONTENT_LEFT = 526f
private const val CONTENT_WIDTH = 868f

/** Centers of the text lines: eyebrow, title, then the body's lines [BODY_LINE_HEIGHT] apart. */
private const val EYEBROW_CENTER_Y = 306f
private const val EYEBROW_SIZE = 30f
private const val TITLE_CENTER_Y = 380f
private const val TITLE_SIZE = 72f
private const val BODY_FIRST_CENTER_Y = 458f
private const val BODY_SIZE = 34f
private const val BODY_LINE_HEIGHT = 44.2f

private const val BUTTON_TOP = 688f
private const val BUTTON_HEIGHT = 104f
private const val BUTTON_GAP = 28f
private const val BUTTON_WIDTH = (CONTENT_WIDTH - BUTTON_GAP) / 2
private const val BUTTON_RADIUS = 12f
private const val FOCUSED_BORDER = 6f
private const val UNFOCUSED_BORDER = 3f
private const val LABEL_SIZE = 40f
private const val CENTER_Y = BUTTON_TOP + BUTTON_HEIGHT / 2

/** EXIT over HOLD OK · 2 S, as one block centered in the button (line-height 1.05). */
private const val EXIT_CENTER_Y = 727.4f
private const val HOLD_CENTER_Y = 761f
private const val HOLD_SIZE = 24f

/**
 * The exit confirm screen (design 09): a modal over the dimmed board asking to leave for Android TV, with CANCEL and
 * EXIT. Holding OK on EXIT fills it in red from the left over [EXIT_HOLD_MS].
 *
 * Text is built in [update]; [draw] goes into the scoreboard's cached layer (the screen only opens while the clock
 * is stopped), and while OK is held [drawHold] draws the fill and EXIT's labels over it on every frame. Design pixels.
 */
class ExitConfirmOverlay(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    /** When OK went down on EXIT, or null while it isn't held. */
    var holdStartedNanos: Long? = null
        private set

    private var confirm: ExitConfirm? = null

    private val text = context.color(R.color.text)
    private val off = context.color(R.color.text_off)

    private val scrimPaint = fillPaint(context.color(R.color.modal_scrim))
    private val modalPaint = fillPaint(context.color(R.color.panel))
    private val modalBorder = RectF(MODAL).apply { inset(BORDER / 2, BORDER / 2) }
    private val modalBorderPaint = strokePaint(context.color(R.color.panel_line), BORDER)

    private val eyebrow = context.getString(R.string.exit_eyebrow)
    private val eyebrowPaint = textPaint(fonts.barlow(600), EYEBROW_SIZE, context.color(R.color.text_muted), 0.24f)
    private val eyebrowBaseline = EYEBROW_CENTER_Y + eyebrowPaint.centerToBaselinePerPx("H") * EYEBROW_SIZE
    private val title = context.getString(R.string.exit_title)
    private val titlePaint = textPaint(fonts.barlow(700), TITLE_SIZE, text).apply {
        fitText(title, TITLE_SIZE, CONTENT_WIDTH)
    }
    private val titleBaseline = TITLE_CENTER_Y + titlePaint.centerToBaselinePerPx("H") * titlePaint.textSize
    private val bodyPaint = textPaint(fonts.barlow(500), BODY_SIZE, context.color(R.color.text_soft))
    private val bodyBaseline = BODY_FIRST_CENTER_Y + bodyPaint.centerToBaselinePerPx("H") * BODY_SIZE
    private var bodyLines = emptyList<String>()

    private val cancel = Button(CONTENT_LEFT, context.getString(R.string.exit_cancel))
    private val exit = Button(CONTENT_LEFT + BUTTON_WIDTH + BUTTON_GAP, context.getString(R.string.exit_confirm))
    private val cancelPaint = textPaint(fonts.barlow(700), LABEL_SIZE, text, 0.04f, Paint.Align.CENTER)
    private val cancelBaseline = CENTER_Y + cancelPaint.centerToBaselinePerPx("H") * LABEL_SIZE
    private val exitPaint = textPaint(fonts.barlow(700), LABEL_SIZE, text, 0.06f, Paint.Align.CENTER)
    private val exitBaseline = EXIT_CENTER_Y + exitPaint.centerToBaselinePerPx("H") * LABEL_SIZE
    private val hold = context.getString(R.string.exit_hold, EXIT_HOLD_MS / 1_000)
    private val holdPaint = textPaint(fonts.barlow(600), HOLD_SIZE, text, 0.12f, Paint.Align.CENTER)
    private val holdBaseline = HOLD_CENTER_Y + holdPaint.centerToBaselinePerPx("H") * HOLD_SIZE
    private val focusedFill = fillPaint(context.color(R.color.panel_raised))
    private val focusedOutline = strokePaint(text, FOCUSED_BORDER)
    private val unfocusedOutline = strokePaint(off, UNFOCUSED_BORDER)
    private val holdFill = fillPaint(context.color(R.color.destructive))

    /**
     * Takes the screen's state (null = closed); returns true when what the cached layer shows changed. A hold
     * starting or ending doesn't change the layer: [drawHold] draws it.
     */
    fun update(confirm: ExitConfirm?): Boolean {
        val opened = confirm != null && this.confirm == null
        val changed = confirm?.focused != this.confirm?.focused
        this.confirm = confirm
        holdStartedNanos = confirm?.holdStartedNanos
        visible = confirm != null
        // Whether HOME comes back here can change while the app runs (adb set-home-activity), so ask on each open.
        if (opened) layoutBody()
        return changed
    }

    fun draw(canvas: Canvas) {
        val confirm = confirm ?: return
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, scrimPaint)
        canvas.drawRoundRect(MODAL, MODAL_RADIUS, MODAL_RADIUS, modalPaint)
        canvas.drawRoundRect(modalBorder, MODAL_RADIUS, MODAL_RADIUS, modalBorderPaint)
        canvas.drawText(eyebrow, CONTENT_LEFT, eyebrowBaseline, eyebrowPaint)
        canvas.drawText(title, CONTENT_LEFT, titleBaseline, titlePaint)
        bodyLines.forEachIndexed { i, line ->
            canvas.drawText(line, CONTENT_LEFT, bodyBaseline + i * BODY_LINE_HEIGHT, bodyPaint)
        }
        cancel.drawFrame(canvas, confirm.focused == ExitChoice.CANCEL)
        canvas.drawText(cancel.label, cancel.centerX, cancelBaseline, cancelPaint)
        exit.drawFrame(canvas, confirm.focused == ExitChoice.EXIT)
        drawExitLabels(canvas)
    }

    /** While OK is held: EXIT filled in red up to [progress] (0–1), its labels on top. */
    fun drawHold(canvas: Canvas, progress: Float) {
        val saved = canvas.save()
        canvas.clipPath(exit.inside)
        val inside = exit.insideBounds
        canvas.drawRect(inside.left, inside.top, inside.left + inside.width() * progress, inside.bottom, holdFill)
        canvas.restoreToCount(saved)
        drawExitLabels(canvas)
    }

    private fun drawExitLabels(canvas: Canvas) {
        canvas.drawText(exit.label, exit.centerX, exitBaseline, exitPaint)
        canvas.drawText(hold, exit.centerX, holdBaseline, holdPaint)
    }

    /** Wraps the body to the content width, word by word. */
    private fun layoutBody() {
        val body = context.getString(
            if (AndroidTv.isTableroHome(context)) R.string.exit_body_home else R.string.exit_body_apps,
        )
        val lines = mutableListOf<String>()
        var line = ""
        for (word in body.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && bodyPaint.measureText(candidate) > CONTENT_WIDTH) {
                lines += line
                line = word
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) lines += line
        bodyLines = lines
    }

    private inner class Button(left: Float, val label: String) {
        private val box = RectF(left, BUTTON_TOP, left + BUTTON_WIDTH, BUTTON_TOP + BUTTON_HEIGHT)
        private val focusedBox = RectF(box).apply { inset(FOCUSED_BORDER / 2, FOCUSED_BORDER / 2) }
        private val unfocusedBox = RectF(box).apply { inset(UNFOCUSED_BORDER / 2, UNFOCUSED_BORDER / 2) }
        val centerX = box.centerX()

        /** Inside the focused border, where the hold fill goes (the button is focused while OK is held on it). */
        val insideBounds = RectF(box).apply { inset(FOCUSED_BORDER, FOCUSED_BORDER) }
        val inside = Path().apply {
            val radius = BUTTON_RADIUS - FOCUSED_BORDER
            addRoundRect(insideBounds, radius, radius, Path.Direction.CW)
        }

        fun drawFrame(canvas: Canvas, focused: Boolean) {
            if (focused) {
                canvas.drawRoundRect(box, BUTTON_RADIUS, BUTTON_RADIUS, focusedFill)
                val radius = BUTTON_RADIUS - FOCUSED_BORDER / 2
                canvas.drawRoundRect(focusedBox, radius, radius, focusedOutline)
            } else {
                val radius = BUTTON_RADIUS - UNFOCUSED_BORDER / 2
                canvas.drawRoundRect(unfocusedBox, radius, radius, unfocusedOutline)
            }
        }
    }
}
