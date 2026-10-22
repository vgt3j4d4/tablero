package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.poss); what moves between layouts is in BoardLayout.
private const val CENTER_X = DESIGN_WIDTH / 2
private const val LABEL_SIZE = 56f

/** An arrow is this wide for its height. */
private const val ARROW_WIDTH_PER_HEIGHT = 0.8f

/** The arrows' outline, drawn with round joins so their tips look like the design's rounded polygons. */
private const val ARROW_STROKE = 10f

/**
 * The possession arrow between the scores (design 20–23): a `POS` label in line with the team names over a pair of
 * arrows in line with the scores. The one pointing to the team with the next alternating possession is lit in the
 * score color, the other is drawn like an unlit segment; nothing shows while the arrow is hidden. Placed by [place],
 * colored by [setColors]; [draw] only draws. Coordinates are design pixels.
 */
class PossessionArrow(context: Context, fonts: BoardFonts) {
    private var side: Side? = null

    private val label = context.getString(R.string.possession_label)
    private val labelPaint = textPaint(fonts.barlow(700), LABEL_SIZE, 0, 0.08f, Paint.Align.CENTER)
    private val labelBaselineOffset = labelPaint.centerToBaselinePerPx("H") * LABEL_SIZE
    private var labelBaseline = 0f

    private val left = Path()
    private val right = Path()
    private val litPaint = arrowPaint()
    private val unlitPaint = arrowPaint()

    /** Puts the label and the arrows where [layout] has them. */
    fun place(layout: BoardLayout) {
        labelBaseline = layout.nameCenterY + labelBaselineOffset
        val width = layout.possessionArrowHeight * ARROW_WIDTH_PER_HEIGHT
        val baseX = layout.possessionArrowGap / 2
        left.arrow(CENTER_X - baseX, CENTER_X - baseX - width, layout)
        right.arrow(CENTER_X + baseX, CENTER_X + baseX + width, layout)
    }

    /** Colors the label, the lit arrow and the unlit one, as resolved colors. */
    fun setColors(label: Int, lit: Int, unlit: Int) {
        labelPaint.color = label
        litPaint.color = lit
        unlitPaint.color = unlit
    }

    /** Points the arrow to [next], or hides it when null. Returns true when that changed. */
    fun update(next: Side?): Boolean {
        if (next == side) return false
        side = next
        return true
    }

    fun draw(canvas: Canvas) {
        val side = side ?: return
        canvas.drawText(label, CENTER_X, labelBaseline, labelPaint)
        canvas.drawPath(left, if (side == Side.LEFT) litPaint else unlitPaint)
        canvas.drawPath(right, if (side == Side.RIGHT) litPaint else unlitPaint)
    }

    /** A triangle with its upright base at [baseX] and its tip at [tipX], centered on the scores. */
    private fun Path.arrow(baseX: Float, tipX: Float, layout: BoardLayout) {
        val halfHeight = layout.possessionArrowHeight / 2
        reset()
        moveTo(baseX, layout.scoreCenterY - halfHeight)
        lineTo(baseX, layout.scoreCenterY + halfHeight)
        lineTo(tipX, layout.scoreCenterY)
        close()
    }

    private fun arrowPaint() = fillPaint().apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = ARROW_STROKE
        strokeJoin = Paint.Join.ROUND
    }
}
