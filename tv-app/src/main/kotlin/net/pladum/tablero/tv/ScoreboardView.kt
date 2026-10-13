package net.pladum.tablero.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.TimeSource

/** Redraw check interval while the clock runs; the view only invalidates when the displayed value changes. */
private const val TICK_MS = 100L

/** Clock band: the top part of the screen; the score digits sit centered in each half below it. */
private const val CLOCK_BAND = 0.42f
private const val CLOCK_TEXT_SIZE = 0.30f
private const val SCORE_TEXT_SIZE = 0.34f

/**
 * The scoreboard surface: one Canvas view, no layout. Renders the latest [GameSnapshot] given to [render].
 * While the clock runs it re-reads [timeSource] every [TICK_MS] and redraws only when the reading changes
 * (1 Hz above the tenths threshold, 10 Hz below). Nothing is allocated in [onDraw].
 */
@SuppressLint("ViewConstructor") // Built in code by MainActivity, never inflated from XML.
class ScoreboardView(context: Context, private val timeSource: TimeSource) : View(context) {
    private var snapshot: GameSnapshot? = null
    private var reading: ClockReading? = null
    private var clockText = ""
    private var leftText = ""
    private var rightText = ""

    private var clockX = 0f
    private var clockY = 0f
    private var leftX = 0f
    private var rightX = 0f
    private var scoreY = 0f

    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            refreshClock()
            updateTicker()
        }
    }

    /** DSEG7 Classic Bold: seven-segment digits. Loaded once; the paints share it. */
    private val ledTypeface = context.resources.getFont(R.font.dseg7_classic_bold)
    private val clockPaint = ledPaint(context.getColor(R.color.led_red))
    private val scorePaint = ledPaint(context.getColor(R.color.led_amber))

    fun render(next: GameSnapshot) {
        val previous = snapshot
        snapshot = next
        if (previous?.left?.score != next.left.score) leftText = next.left.score.toString()
        if (previous?.right?.score != next.right.score) rightText = next.right.score.toString()
        if (previous?.left?.score != next.left.score || previous?.right?.score != next.right.score) invalidate()
        refreshClock()
        updateTicker()
    }

    private fun refreshClock() {
        val s = snapshot ?: return
        val now = ClockReading.of(s.gameClock.remainingAt(timeSource.nowNanos()), s.rules)
        if (now == reading) return
        reading = now
        clockText = now.format()
        invalidate()
    }

    /** Runs the ticker only while the clock runs and the view is on a visible window. */
    private fun updateTicker() {
        val wanted = snapshot?.gameClock?.running == true && isAttachedToWindow && windowVisibility == VISIBLE
        if (wanted && !ticking) {
            ticking = true
            postDelayed(tick, TICK_MS)
        } else if (!wanted && ticking) {
            ticking = false
            removeCallbacks(tick)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshClock()
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        updateTicker()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) refreshClock()
        updateTicker()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        clockPaint.textSize = h * CLOCK_TEXT_SIZE
        scorePaint.textSize = h * SCORE_TEXT_SIZE
        clockX = w / 2f
        clockY = baselineFor(clockPaint, centerY = h * CLOCK_BAND / 2)
        leftX = w / 4f
        rightX = w * 3 / 4f
        scoreY = baselineFor(scorePaint, centerY = h * (CLOCK_BAND + 1) / 2)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(clockText, clockX, clockY, clockPaint)
        canvas.drawText(leftText, leftX, scoreY, scorePaint)
        canvas.drawText(rightText, rightX, scoreY, scorePaint)
    }

    /** Baseline that puts the digits' visual center at [centerY]. */
    private fun baselineFor(paint: Paint, centerY: Float): Float = centerY - (paint.ascent() + paint.descent()) / 2

    private fun ledPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        typeface = ledTypeface
        textAlign = Paint.Align.CENTER
    }
}
