package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.fouls); what moves between layouts is in BoardLayout.
private const val LABEL_SIZE = 56f

/** Fouls stay within 0–9: one digit. */
private val GHOST = ledGhost(1)

/**
 * Each team's fouls under period rules (design 24–26): a `FOULS` label over one LED digit, in the corner beside the
 * clock on the team's side. The digit is lit in the score color, and in the clock color once the team has
 * committed the period's penalty foul; nothing shows under Pickup rules. Placed by [place], colored by [setColors];
 * [draw] only draws. Coordinates are design pixels.
 */
class TeamFouls(context: Context, private val fonts: BoardFonts) {
    private val left = Corner()
    private val right = Corner()
    private var shown = false

    private val label = context.getString(R.string.fouls_label)
    private val labelPaint = textPaint(fonts.barlow(700), LABEL_SIZE, 0, 0.08f, Paint.Align.CENTER)
    private var labelBaseline = 0f

    private var lit = 0
    private var unlit = 0
    private var penalty = 0
    private var penaltyUnlit = 0

    /** Puts the corners where [layout] has them. */
    fun place(layout: BoardLayout) {
        labelPaint.textSize = layout.foulsLabelSize
        labelBaseline = labelPaint.baselineAt(layout.foulsLabelCenterY)
        left.place(layout.foulsCenterX, layout)
        right.place(DESIGN_WIDTH - layout.foulsCenterX, layout)
    }

    /** Colors the label and the digits, as resolved colors: score colors, clock colors from the penalty foul. */
    fun setColors(label: Int, lit: Int, unlit: Int, penalty: Int, penaltyUnlit: Int) {
        labelPaint.color = label
        this.lit = lit
        this.unlit = unlit
        this.penalty = penalty
        this.penaltyUnlit = penaltyUnlit
        left.applyColors()
        right.applyColors()
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
        private var centerX = 0f
        private var fouls = -1
        private var inPenalty = false
        private val digits = LedDigits(fonts)

        fun place(x: Float, layout: BoardLayout) {
            centerX = x
            digits.place(x, layout.foulsCenterY, layout.foulsSize)
        }

        fun applyColors() = if (inPenalty) digits.setColors(penalty, penaltyUnlit) else digits.setColors(lit, unlit)

        fun update(snapshot: GameSnapshot, side: Side): Boolean {
            val nextFouls = snapshot.team(side).fouls
            val nextPenalty = snapshot.inPenalty(side)
            if (nextFouls == fouls && nextPenalty == inPenalty) return false
            fouls = nextFouls
            inPenalty = nextPenalty
            digits.setText(fouls.toString(), GHOST)
            applyColors()
            return true
        }

        fun draw(canvas: Canvas) {
            canvas.drawText(label, centerX, labelBaseline, labelPaint)
            digits.draw(canvas)
        }
    }
}
