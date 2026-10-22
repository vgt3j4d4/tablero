package net.pladum.tablero.tv

import android.graphics.Canvas
import android.graphics.Paint

/**
 * LED digits over their unlit segments, as the scores, the fouls and the period show them: the unlit 8s are centered
 * on a point and the lit digits drawn right-aligned on them, so a short number fills the last cells, as on real
 * scoreboards. Placed by [place], colored by [setColors], given its text by [setText]; [draw] only draws. Design
 * pixels.
 */
class LedDigits(private val fonts: BoardFonts) {
    private val litPaint = textPaint(fonts.led, 0f, 0, align = Paint.Align.RIGHT)
    private val unlitPaint = textPaint(fonts.led, 0f, 0)
    private var centerX = 0f
    private var baseline = 0f
    private var text = ""
    private var ghost = ""
    private var ghostX = 0f

    /** How wide the unlit segments are: the cells the digits take. */
    var width = 0f
        private set

    /** Centers the digits on ([centerX], [centerY]) at [size]. */
    fun place(centerX: Float, centerY: Float, size: Float) {
        litPaint.textSize = size
        unlitPaint.textSize = size
        this.centerX = centerX
        baseline = fonts.ledBaseline(centerY, size)
        measure()
    }

    /** Colors the lit digits and the unlit segments, as resolved colors. */
    fun setColors(lit: Int, unlit: Int) {
        litPaint.color = lit
        unlitPaint.color = unlit
    }

    /** Shows [text] over [ghost], its unlit segments ([ledGhost], [scoreGhost]). */
    fun setText(text: String, ghost: String) {
        this.text = text
        if (ghost == this.ghost) return
        this.ghost = ghost
        measure()
    }

    fun draw(canvas: Canvas) {
        canvas.drawText(ghost, ghostX, baseline, unlitPaint)
        canvas.drawText(text, ghostX + width, baseline, litPaint)
    }

    private fun measure() {
        width = unlitPaint.measureText(ghost)
        ghostX = centerX - width / 2
    }
}
