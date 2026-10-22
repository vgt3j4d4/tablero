package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import net.pladum.tablero.shared.GameSnapshot

// Design pixels, from docs/design/boards.html (.break-bar, .hints); the bar is a tall one (BarTitle).
private const val BORDER = 2f
private const val HINT_SIZE = 28f
private const val HINT_GAP = 40f

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
    private val title = BarTitle(fonts, context.color(R.color.text), context.color(R.color.text_soft))

    private val muted = context.color(R.color.text_muted)
    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, muted, 0.06f)
    private val keycapPaint = keycapPaint(muted)
    private val hintBaseline = TALL_BAR_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hints = listOf(
        KeyHint(context.getString(R.string.key_down), context.getString(R.string.hint_adjust)),
        KeyHint(context.getString(R.string.key_ok), context.getString(R.string.hint_end_break)),
    )
    private val hintsLeft = hints.layoutEndingAt(DESIGN_WIDTH - BAR_PADDING, HINT_GAP, TALL_BAR_CENTER_Y, hintPaint)

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
        canvas.drawRect(0f, TALL_BAR_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, barPaint)
        canvas.drawRect(0f, TALL_BAR_TOP, DESIGN_WIDTH, TALL_BAR_TOP + BORDER, linePaint)
        title.draw(canvas)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    private fun layout(shown: Shown) {
        val detail = if (shown.overtime > 0) {
            context.getString(R.string.break_next_overtime, shown.overtime)
        } else {
            context.getString(R.string.break_next_period, shown.period)
        }
        val titleText = context.getString(if (shown.halftime) R.string.break_halftime else R.string.break_title)
        title.layout(titleText, detail, hintsLeft)
    }

    /** The period coming after the break (or its overtime number), and whether the break is halftime. */
    private data class Shown(val period: Int, val overtime: Int, val halftime: Boolean)
}
