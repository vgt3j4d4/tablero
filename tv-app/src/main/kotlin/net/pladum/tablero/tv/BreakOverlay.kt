package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import net.pladum.tablero.shared.GameSnapshot

// Design pixels, from docs/design/boards.html (.break-bar, .hints).
private const val BAR_TOP = 992f
private const val BAR_CENTER_Y = 1036f
private const val BORDER = 2f
private const val PADDING = 48f
private const val TITLE_SIZE = 48f
private const val DETAIL_SIZE = 30f
private const val TEXT_GAP = 20f
private const val HINT_SIZE = 28f
private const val HINT_GAP = 40f

/** Minimum space between the title group and the key hints. */
private const val GROUP_GAP = 24f

/**
 * The break between periods under period rules (design 30): while the game clock counts it down, a bar over the
 * status bar with `HALFTIME` (or `BREAK`), which period starts when it ends, and the key hints (`↓ ADJUST`,
 * `OK END BREAK`), in the menu's panel colors in both modes. Text is built in [update]; [draw] only draws. Design
 * pixels.
 */
class BreakOverlay(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private var shown: Shown? = null

    private val barPaint = fillPaint(context.color(R.color.panel))
    private val linePaint = fillPaint(context.color(R.color.panel_line))

    private var title = ""
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, context.color(R.color.text), 0.06f)
    private val titleCenter = titlePaint.centerToBaselinePerPx("H")
    private var detail = ""
    private val detailPaint = textPaint(fonts.barlow(600), DETAIL_SIZE, context.color(R.color.text_soft))
    private var detailX = 0f
    private var baseline = 0f

    private val muted = context.color(R.color.text_muted)
    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, muted, 0.06f)
    private val keycapPaint = keycapPaint(muted)
    private val hintBaseline = BAR_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hints = listOf(
        KeyHint(context.getString(R.string.key_down), context.getString(R.string.hint_adjust)),
        KeyHint(context.getString(R.string.key_ok), context.getString(R.string.hint_end_break)),
    )
    private val hintsLeft = hints.foldRight(DESIGN_WIDTH - PADDING + HINT_GAP) { hint, right ->
        hint.layoutEndingAt(right - HINT_GAP, BAR_CENTER_Y, hintPaint)
    }

    /** Shows the bar while [s] is in a break; returns true when what it shows changed. */
    fun update(s: GameSnapshot): Boolean {
        val next = if (s.inBreak) Shown(s.period, s.overtime, s.halftimeBefore(s.period)) else null
        if (next == shown) return false
        shown = next
        visible = next != null
        if (next != null) layout(next)
        return true
    }

    fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawRect(0f, BAR_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, barPaint)
        canvas.drawRect(0f, BAR_TOP, DESIGN_WIDTH, BAR_TOP + BORDER, linePaint)
        canvas.drawText(title, PADDING, baseline, titlePaint)
        canvas.drawText(detail, detailX, baseline, detailPaint)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    /** Builds the title and the detail on one baseline; both shrink if a translation would reach the hints. */
    private fun layout(shown: Shown) {
        title = context.getString(if (shown.halftime) R.string.break_halftime else R.string.break_title)
        detail = if (shown.overtime > 0) {
            context.getString(R.string.break_next_overtime, shown.overtime)
        } else {
            context.getString(R.string.break_next_period, shown.period)
        }
        titlePaint.textSize = TITLE_SIZE
        detailPaint.textSize = DETAIL_SIZE
        val width = titlePaint.measureText(title) + TEXT_GAP + detailPaint.measureText(detail)
        val available = hintsLeft - GROUP_GAP - PADDING
        if (width > available) {
            titlePaint.textSize = TITLE_SIZE * available / width
            detailPaint.textSize = DETAIL_SIZE * available / width
        }
        detailX = PADDING + titlePaint.measureText(title) + TEXT_GAP * titlePaint.textSize / TITLE_SIZE
        baseline = BAR_CENTER_Y + titleCenter * titlePaint.textSize
    }

    /** The period coming after the break (or its overtime number), and whether the break is halftime. */
    private data class Shown(val period: Int, val overtime: Int, val halftime: Boolean)
}
