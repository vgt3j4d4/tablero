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

/** The period's unlit segments: periods and overtimes fit one digit. */
private const val PERIOD_GHOST = "8"

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

    /** The period or overtime number shown between the arrows, or 0 under Pickup rules. */
    private var period = 0
    private var overtime = false
    private var periodText = ""

    private val possessionLabel = context.getString(R.string.possession_label)
    private val periodLabel = context.getString(R.string.period_label)
    private val overtimeLabel = context.getString(R.string.overtime_label)
    private var label = possessionLabel
    private val labelPaint = textPaint(fonts.barlow(700), LABEL_SIZE, 0, 0.08f, Paint.Align.CENTER)
    private val labelBaselineOffset = labelPaint.centerToBaselinePerPx("H") * LABEL_SIZE
    private var labelBaseline = 0f

    private val left = Path()
    private val right = Path()
    private val periodLeftArrow = Path()
    private val periodRightArrow = Path()
    private val litPaint = arrowPaint()
    private val unlitPaint = arrowPaint()

    private val periodPaint = textPaint(fonts.led, 0f, 0, align = Paint.Align.RIGHT)
    private val periodUnlitPaint = textPaint(fonts.led, 0f, 0)
    private val ledCenterPerPx = textPaint(fonts.led, LABEL_SIZE, 0).centerToBaselinePerPx(PERIOD_GHOST)
    private var periodBaseline = 0f
    private var periodGhostX = 0f
    private var periodDigitRight = 0f

    /** Puts the label, the arrows and the period where [layout] has them. */
    fun place(layout: BoardLayout) {
        labelBaseline = layout.nameCenterY + labelBaselineOffset
        val width = layout.possessionArrowHeight * ARROW_WIDTH_PER_HEIGHT
        val baseX = layout.possessionArrowGap / 2
        left.arrow(CENTER_X - baseX, CENTER_X - baseX - width, layout.possessionArrowHeight, layout)
        right.arrow(CENTER_X + baseX, CENTER_X + baseX + width, layout.possessionArrowHeight, layout)

        periodPaint.textSize = layout.periodSize
        periodUnlitPaint.textSize = layout.periodSize
        periodBaseline = layout.scoreCenterY + ledCenterPerPx * layout.periodSize
        val digitWidth = periodUnlitPaint.measureText(PERIOD_GHOST)
        periodGhostX = CENTER_X - digitWidth / 2
        periodDigitRight = periodGhostX + digitWidth
        val periodHeight = layout.periodArrowHeight
        val periodWidth = periodHeight * ARROW_WIDTH_PER_HEIGHT
        val periodBaseX = digitWidth / 2 + layout.periodArrowGap
        periodLeftArrow.arrow(CENTER_X - periodBaseX, CENTER_X - periodBaseX - periodWidth, periodHeight, layout)
        periodRightArrow.arrow(CENTER_X + periodBaseX, CENTER_X + periodBaseX + periodWidth, periodHeight, layout)
    }

    /** Colors the label, the lit and unlit arrows and the period's lit and unlit digit, as resolved colors. */
    fun setColors(label: Int, lit: Int, unlit: Int, period: Int, periodUnlit: Int) {
        labelPaint.color = label
        litPaint.color = lit
        unlitPaint.color = unlit
        periodPaint.color = period
        periodUnlitPaint.color = periodUnlit
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
        periodText = period.toString()
        label = when {
            period == 0 -> possessionLabel
            overtime -> overtimeLabel
            else -> periodLabel
        }
        return true
    }

    fun draw(canvas: Canvas) {
        val side = side
        if (period == 0 && side == null) return
        canvas.drawText(label, CENTER_X, labelBaseline, labelPaint)
        canvas.drawPath(if (period == 0) left else periodLeftArrow, if (side == Side.LEFT) litPaint else unlitPaint)
        canvas.drawPath(if (period == 0) right else periodRightArrow, if (side == Side.RIGHT) litPaint else unlitPaint)
        if (period == 0) return
        canvas.drawText(PERIOD_GHOST, periodGhostX, periodBaseline, periodUnlitPaint)
        canvas.drawText(periodText, periodDigitRight, periodBaseline, periodPaint)
    }

    /** A triangle [height] tall with its upright base at [baseX] and its tip at [tipX], centered on the scores. */
    private fun Path.arrow(baseX: Float, tipX: Float, height: Float, layout: BoardLayout) {
        val halfHeight = height / 2
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
