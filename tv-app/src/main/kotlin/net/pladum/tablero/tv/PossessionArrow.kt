package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.poss); what moves between layouts is in BoardLayout.
private const val CENTER_X = DESIGN_WIDTH / 2
private const val LABEL_SIZE = 56f

/** An arrow is this wide for its height. */
private const val ARROW_WIDTH_PER_HEIGHT = 0.8f

/** The arrows' outline, drawn with round joins so their tips look like the design's rounded polygons. */
private const val ARROW_STROKE = 10f

/** Periods and overtimes fit one digit. */
private val PERIOD_GHOST = ledGhost(1)

/**
 * The possession arrow between the scores (design 20–23): a `POS` label in line with the team names over a pair of
 * arrows in line with the scores. The one pointing to the team with the next alternating possession is lit in the
 * score color, the other is drawn like an unlit segment; nothing shows while the arrow is hidden.
 *
 * Under period rules (design 24–26) the period sits between the two arrows, one digit in the clock color, and its
 * label (`PERIOD`, `OT` in overtime) replaces `POS`; the arrows then always show, both unlit while hidden. Placed by
 * [place], colored by [setColors]; [draw] only draws. Coordinates are design pixels.
 */
class PossessionArrow(context: Context, fonts: BoardFonts) {
    private var side: Side? = null

    /** The period or overtime shown between the arrows, or 0 under Pickup rules. */
    private var period = 0
    private var overtime = false

    private val possessionLabel = context.getString(R.string.possession_label)
    private val periodLabel = context.getString(R.string.period_label)
    private val overtimeLabel = context.getString(R.string.overtime_label)
    private var label = possessionLabel
    private val labelPaint = textPaint(fonts.barlow(700), LABEL_SIZE, 0, 0.08f, Paint.Align.CENTER)
    private val labelBaselineOffset = labelPaint.centerToBaselinePerPx("H") * LABEL_SIZE
    private var labelBaseline = 0f

    /** The arrows around the gap between the scores, and around the period digit; [update] picks the pair. */
    private val left = Path()
    private val right = Path()
    private val periodLeft = Path()
    private val periodRight = Path()
    private var leftArrow = left
    private var rightArrow = right
    private val litPaint = arrowPaint()
    private val unlitPaint = arrowPaint()
    private val digit = LedDigits(fonts)

    /** Puts the label, the arrows and the period where [layout] has them. */
    fun place(layout: BoardLayout) {
        labelBaseline = layout.nameCenterY + labelBaselineOffset
        left.arrow(-layout.possessionArrowGap / 2, layout.possessionArrowHeight, layout)
        right.arrow(layout.possessionArrowGap / 2, layout.possessionArrowHeight, layout)
        digit.place(CENTER_X, layout.scoreCenterY, layout.periodSize)
        digit.setText(period.toString(), PERIOD_GHOST)
        val periodBase = digit.width / 2 + layout.periodArrowGap
        periodLeft.arrow(-periodBase, layout.periodArrowHeight, layout)
        periodRight.arrow(periodBase, layout.periodArrowHeight, layout)
    }

    /** Colors the label, the lit and unlit arrows and the period's lit and unlit digit, as resolved colors. */
    fun setColors(label: Int, lit: Int, unlit: Int, period: Int, periodUnlit: Int) {
        labelPaint.color = label
        litPaint.color = lit
        unlitPaint.color = unlit
        digit.setColors(period, periodUnlit)
    }

    /** Points the arrow as [snapshot] has it, with its period under period rules. Returns true when that changed. */
    fun update(snapshot: GameSnapshot): Boolean {
        val nextOvertime = snapshot.overtime > 0
        val nextPeriod = when {
            snapshot.rules.periods == null -> 0
            nextOvertime -> snapshot.overtime
            else -> snapshot.period
        }
        if (snapshot.possession == side && nextPeriod == period && nextOvertime == overtime) return false
        side = snapshot.possession
        period = nextPeriod
        overtime = nextOvertime
        digit.setText(period.toString(), PERIOD_GHOST)
        label = when {
            period == 0 -> possessionLabel
            overtime -> overtimeLabel
            else -> periodLabel
        }
        leftArrow = if (period == 0) left else periodLeft
        rightArrow = if (period == 0) right else periodRight
        return true
    }

    fun draw(canvas: Canvas) {
        if (period == 0 && side == null) return
        canvas.drawText(label, CENTER_X, labelBaseline, labelPaint)
        canvas.drawPath(leftArrow, if (side == Side.LEFT) litPaint else unlitPaint)
        canvas.drawPath(rightArrow, if (side == Side.RIGHT) litPaint else unlitPaint)
        if (period != 0) digit.draw(canvas)
    }

    /**
     * A triangle [height] tall, centered on the scores, with its upright base [base] from the center (negative to
     * the left) and its tip pointing away from the center.
     */
    private fun Path.arrow(base: Float, height: Float, layout: BoardLayout) {
        val baseX = CENTER_X + base
        val tipX = baseX + Math.copySign(height * ARROW_WIDTH_PER_HEIGHT, base)
        reset()
        moveTo(baseX, layout.scoreCenterY - height / 2)
        lineTo(baseX, layout.scoreCenterY + height / 2)
        lineTo(tipX, layout.scoreCenterY)
        close()
    }

    private fun arrowPaint() = fillPaint().apply {
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = ARROW_STROKE
        strokeJoin = Paint.Join.ROUND
    }
}
