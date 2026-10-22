package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.fouls); what moves between layouts is in BoardLayout.
private const val LABEL_SIZE = 56f

/** One digit's unlit segments; fouls stay within 0–9. */
private const val GHOST = "8"

/**
 * Each team's fouls under period rules (design 24–26): a `FOULS` label over one LED digit, in the corner beside the
 * clock on the team's side. The digit is lit in the score color, and in the clock color once the team has
 * committed the period's penalty foul; nothing shows under Pickup rules. Placed by [place], colored by [setColors];
 * [draw] only draws. Coordinates are design pixels.
 */
class TeamFouls(context: Context, fonts: BoardFonts) {
    private val left = Corner()
    private val right = Corner()
    private var shown = false

    private val label = context.getString(R.string.fouls_label)
    private val labelPaint = textPaint(fonts.barlow(700), LABEL_SIZE, 0, 0.08f, Paint.Align.CENTER)
    private val labelCenterPerPx = labelPaint.centerToBaselinePerPx("H")
    private var labelBaseline = 0f

    private val litPaint = textPaint(fonts.led, 0f, 0, align = Paint.Align.RIGHT)
    private val penaltyPaint = textPaint(fonts.led, 0f, 0, align = Paint.Align.RIGHT)
    private val unlitPaint = textPaint(fonts.led, 0f, 0)
    private val penaltyUnlitPaint = textPaint(fonts.led, 0f, 0)
    private val ledCenterPerPx = textPaint(fonts.led, LABEL_SIZE, 0).centerToBaselinePerPx(GHOST)
    private var ledBaseline = 0f
    private var ghostWidth = 0f

    /** Puts the corners where [layout] has them. */
    fun place(layout: BoardLayout) {
        labelPaint.textSize = layout.foulsLabelSize
        labelBaseline = layout.foulsLabelCenterY + labelCenterPerPx * layout.foulsLabelSize
        for (paint in arrayOf(litPaint, penaltyPaint, unlitPaint, penaltyUnlitPaint)) paint.textSize = layout.foulsSize
        ledBaseline = layout.foulsCenterY + ledCenterPerPx * layout.foulsSize
        ghostWidth = unlitPaint.measureText(GHOST)
        left.centerX = layout.foulsCenterX
        right.centerX = DESIGN_WIDTH - layout.foulsCenterX
    }

    /** Colors the label and the digits, as resolved colors: score colors, clock colors from the penalty foul. */
    fun setColors(label: Int, lit: Int, unlit: Int, penalty: Int, penaltyUnlit: Int) {
        labelPaint.color = label
        litPaint.color = lit
        unlitPaint.color = unlit
        penaltyPaint.color = penalty
        penaltyUnlitPaint.color = penaltyUnlit
    }

    /** Shows [snapshot]'s fouls, or nothing under Pickup rules. Returns true when that changed. */
    fun update(snapshot: GameSnapshot): Boolean {
        val next = snapshot.rules.periods != null
        var changed = next != shown
        shown = next
        if (next) {
            changed = left.update(snapshot, Side.LEFT) || changed
            changed = right.update(snapshot, Side.RIGHT) || changed
        }
        return changed
    }

    fun draw(canvas: Canvas) {
        if (!shown) return
        left.draw(canvas)
        right.draw(canvas)
    }

    private inner class Corner {
        var centerX = 0f
        private var fouls = -1
        private var penalty = false
        private var text = ""

        fun update(snapshot: GameSnapshot, side: Side): Boolean {
            val nextFouls = snapshot.team(side).fouls
            val nextPenalty = snapshot.inPenalty(side)
            if (nextFouls == fouls && nextPenalty == penalty) return false
            fouls = nextFouls
            penalty = nextPenalty
            text = fouls.toString()
            return true
        }

        fun draw(canvas: Canvas) {
            canvas.drawText(label, centerX, labelBaseline, labelPaint)
            val ghostX = centerX - ghostWidth / 2
            canvas.drawText(GHOST, ghostX, ledBaseline, if (penalty) penaltyUnlitPaint else unlitPaint)
            canvas.drawText(text, ghostX + ghostWidth, ledBaseline, if (penalty) penaltyPaint else litPaint)
        }
    }
}
