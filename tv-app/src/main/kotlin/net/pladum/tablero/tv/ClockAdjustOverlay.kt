package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs

// Design pixels, from docs/design/boards.html (.adjust-ring, .adjust-bar); the ring's place comes from the layout.
private const val RING_WIDTH = 8f
private const val RING_RADIUS = 40f
private const val BAR_TOP = 1016f
private const val BAR_CENTER_Y = 1048f
private const val PADDING = 48f
private const val TITLE_SIZE = 36f
private const val WAS_SIZE = 30f
private const val TEXT_GAP = 18f
private const val HINT_SIZE = 26f
private const val HINT_GAP = 28f

/** Minimum space between the title group and the key hints. */
private const val GROUP_GAP = 40f

/**
 * Quick clock adjust (design 07, 13): an amber ring around the clock and an amber bar over the status bar with
 * `ADJUST CLOCK  +1:00  was 7:42` and the key hints. Text is built in [show]; [draw] only draws. Design pixels.
 */
class ClockAdjustOverlay(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private val adjustColor = context.color(R.color.adjust)
    private val ink = context.color(R.color.adjust_ink)
    private val ring = RectF()
    private val ringPaint = strokePaint(adjustColor, RING_WIDTH)
    private val barPaint = fillPaint(adjustColor)

    private val title = context.getString(R.string.adjust_title)
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, context.color(R.color.board_background), 0.06f)
    private val netPaint = textPaint(fonts.barlow(800), TITLE_SIZE, context.color(R.color.board_background))
    private val wasPaint = textPaint(fonts.barlow(600), WAS_SIZE, ink)
    private val titleCenter = titlePaint.centerToBaselinePerPx("H")
    private var net = ""
    private var was = ""
    private var netX = 0f
    private var wasX = 0f
    private var baseline = 0f

    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, ink, 0.06f)
    private val keycapPaint = keycapPaint(ink)
    private val hintBaseline = BAR_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hints = listOf(
        KeyHint(context.getString(R.string.key_up_down), context.getString(R.string.hint_minute)),
        KeyHint(context.getString(R.string.key_left_right), context.getString(R.string.hint_second)),
        KeyHint(context.getString(R.string.key_ok), context.getString(R.string.hint_done)),
        KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_cancel)),
    )

    /** Left edge of the hints, laid out from the right. */
    private val hintsLeft = hints.layoutEndingAt(DESIGN_WIDTH - PADDING, HINT_GAP, BAR_CENTER_Y, hintPaint)

    /** Puts the ring where [layout] has it. */
    fun place(layout: BoardLayout) {
        val box = layout.adjustRing
        ring.set(box.left, box.top, box.right, box.bottom)
        ring.inset(RING_WIDTH / 2, RING_WIDTH / 2)
    }

    fun show(session: ClockAdjustSession) {
        val seconds = session.netSeconds
        val amount = durationText(abs(seconds) * 1_000)
        net = when {
            seconds > 0 -> context.getString(R.string.adjust_plus, amount)
            seconds < 0 -> context.getString(R.string.adjust_minus, amount)
            else -> amount
        }
        was = context.getString(R.string.adjust_was, session.was.format())
        layout()
        visible = true
    }

    fun hide() {
        visible = false
    }

    fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawRoundRect(ring, RING_RADIUS - RING_WIDTH / 2, RING_RADIUS - RING_WIDTH / 2, ringPaint)
        canvas.drawRect(0f, BAR_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, barPaint)
        canvas.drawText(title, PADDING, baseline, titlePaint)
        canvas.drawText(net, netX, baseline, netPaint)
        canvas.drawText(was, wasX, baseline, wasPaint)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    /** Places title, change and `was` on one baseline; the group shrinks if a translation would reach the hints. */
    private fun layout() {
        val sizes = floatArrayOf(TITLE_SIZE, TITLE_SIZE, WAS_SIZE)
        val paints = arrayOf(titlePaint, netPaint, wasPaint)
        paints.forEachIndexed { i, paint -> paint.textSize = sizes[i] }
        val width = titlePaint.measureText(title) + netPaint.measureText(net) + wasPaint.measureText(was) + 2 * TEXT_GAP
        val available = hintsLeft - GROUP_GAP - PADDING
        if (width > available) paints.forEachIndexed { i, paint -> paint.textSize = sizes[i] * available / width }
        val gap = TEXT_GAP * titlePaint.textSize / TITLE_SIZE
        netX = PADDING + titlePaint.measureText(title) + gap
        wasX = netX + netPaint.measureText(net) + gap
        baseline = BAR_CENTER_Y + titleCenter * titlePaint.textSize
    }
}
