package net.pladum.tablero.tv

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

// Design pixels, from docs/design/boards.html (.keycap, .hint).
private const val KEYCAP_BORDER = 2f
private const val KEYCAP_RADIUS = 8f
private const val KEYCAP_PADDING = 10f
private const val KEYCAP_HEIGHT = 42f
private const val KEYCAP_GAP = 10f

/** Paint for keycap outlines; the keycap takes the color of the hint's text. */
fun keycapPaint(color: Int) = strokePaint(color, KEYCAP_BORDER)

/**
 * A remote key in an outlined keycap followed by what it does: `[BACK] UNDO`. Laid out once with [layoutEndingAt],
 * then drawn as is. Coordinates are design pixels.
 */
class KeyHint(private val key: String, private val label: String) {
    private val keycap = RectF()
    private var keyX = 0f
    private var labelX = 0f

    /** The hint's width with [paint]: keycap, gap and label. */
    fun width(paint: Paint): Float =
        paint.measureText(key) + 2 * (KEYCAP_PADDING + KEYCAP_BORDER) + KEYCAP_GAP + paint.measureText(label)

    /** Places the hint so that it ends at [right], centered on [centerY]; returns its left edge. */
    fun layoutEndingAt(right: Float, centerY: Float, paint: Paint): Float {
        labelX = right - paint.measureText(label)
        val capRight = labelX - KEYCAP_GAP
        val capLeft = capRight - paint.measureText(key) - 2 * (KEYCAP_PADDING + KEYCAP_BORDER)
        keycap.set(capLeft, centerY - KEYCAP_HEIGHT / 2, capRight, centerY + KEYCAP_HEIGHT / 2)
        keycap.inset(KEYCAP_BORDER / 2, KEYCAP_BORDER / 2) // The stroke is centered on the outline.
        keyX = capLeft + KEYCAP_BORDER + KEYCAP_PADDING
        return capLeft
    }

    fun draw(canvas: Canvas, baseline: Float, paint: Paint, keycapPaint: Paint) {
        canvas.drawRoundRect(keycap, KEYCAP_RADIUS, KEYCAP_RADIUS, keycapPaint)
        canvas.drawText(key, keyX, baseline, paint)
        canvas.drawText(label, labelX, baseline, paint)
    }
}
