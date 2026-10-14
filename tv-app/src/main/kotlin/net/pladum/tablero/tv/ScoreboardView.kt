package net.pladum.tablero.tv

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import net.pladum.tablero.shared.ClockReading
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.TeamState
import net.pladum.tablero.shared.TimeSource

/** Redraw check interval while the clock runs; the view only invalidates when the displayed value changes. */
private const val TICK_MS = 100L

/** How long a confirmation stays up after the last change to it. */
private const val FEEDBACK_VISIBLE_MS = 3_000L

// Layout in design pixels (DESIGN_WIDTH × DESIGN_HEIGHT), from docs/design/boards.html.
private const val CENTER_X = DESIGN_WIDTH / 2
private val CLOCK_BOX = RectF(340f, 36f, 1580f, 476f)
private const val CLOCK_BOX_RADIUS = 32f
private const val CLOCK_BOX_BORDER = 2f
private const val LED_SIZE = 330f

/** Team columns: two halves around a 260 px gap. */
private const val LEFT_TEAM_X = 415f
private const val RIGHT_TEAM_X = 1505f

/**
 * The team block (84 px name row, 28 px gap, 330 px score) is centered between the clock row (476) and the status
 * bar (1016): the name row centers at 567 and the score at 802.
 */
private const val NAME_CENTER_Y = 567f
private const val SCORE_CENTER_Y = 802f
private const val NAME_SIZE = 84f

/** Longer team names shrink to fit. */
private const val NAME_MAX_WIDTH = 800f

private const val STATUS_TOP = 1016f
private const val STATUS_CENTER_Y = 1048f
private const val STATUS_LINE = 2f
private const val STATUS_PADDING = 48f
private const val STATUS_SIZE = 28f
private const val STATUS_ICON_SIZE = 18f
private const val STATUS_ICON_GAP = 12f

/**
 * The scoreboard surface: one Canvas view, no layout (design screens 01, 02, 03, 05 and 07). Renders the latest
 * [GameSnapshot] given to [render], confirmations given to [show], the time-up bar and the quick clock adjust
 * overlay.
 *
 * Everything is laid out in design pixels and scaled to the view. Text is built and measured when the state
 * changes; [onDraw] allocates nothing. While the clock runs the view re-reads [timeSource] every [TICK_MS] and
 * redraws only when the reading changes (1 Hz above the tenths threshold, 10 Hz below).
 *
 * Only the clock digits change at 10 Hz, so everything else (box, unlit segments, teams, status bar, overlays)
 * is drawn into a screen-sized [layer] bitmap when it changes, and a tick draws that bitmap plus one text. On the
 * stick's slow CPU, recording and issuing the full scene on every tick missed about half the frame deadlines.
 */
@SuppressLint("ViewConstructor") // Built in code by MainActivity, never inflated from XML.
class ScoreboardView(context: Context, private val timeSource: TimeSource) : View(context) {
    private val fonts = BoardFonts(context)
    private val textColor = context.getColor(R.color.text)

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    /** Everything but the lit clock digits; redrawn in [onDraw] when [layerDirty]. */
    private var layer: Bitmap? = null
    private val layerCanvas = Canvas()
    private var layerDirty = true
    private val backgroundColor = context.getColor(R.color.board_background)

    private var snapshot: GameSnapshot? = null

    private val clockBoxPaint = fillPaint(context.getColor(R.color.clock_box))
    private val clockBoxBorder = RectF(CLOCK_BOX).apply { inset(CLOCK_BOX_BORDER / 2, CLOCK_BOX_BORDER / 2) }
    private val clockBoxBorderPaint = strokePaint(context.getColor(R.color.clock_box_border), CLOCK_BOX_BORDER)
    private val clockPaint = ledPaint(R.color.led_red, Paint.Align.RIGHT)
    private val clockGhostPaint = ledPaint(R.color.led_red_unlit, Paint.Align.LEFT)
    private val ledBaselineOffset = clockPaint.centerToBaselinePerPx("8") * LED_SIZE
    private val clockBaseline = CLOCK_BOX.centerY() + ledBaselineOffset
    private var reading: ClockReading? = null
    private var clockText = ""
    private var clockGhost = ""
    private var clockGhostX = 0f
    private var clockRight = 0f

    private val left = TeamSlot(Side.LEFT, LEFT_TEAM_X)
    private val right = TeamSlot(Side.RIGHT, RIGHT_TEAM_X)

    private val statusLinePaint = fillPaint(context.getColor(R.color.status_line))
    private val clockStatePaint = textPaint(fonts.barlow(600), STATUS_SIZE, textColor, 0.08f, Paint.Align.RIGHT)
    private val statusBaseline = STATUS_CENTER_Y + clockStatePaint.centerToBaselinePerPx("H") * STATUS_SIZE
    private val statusIconPaint = fillPaint(textColor)
    private val pauseBarLeft = RectF()
    private val pauseBarRight = RectF()
    private var running: Boolean? = null
    private var clockStateText = ""
    private var statusIconX = 0f

    private val toast = FeedbackToast(context, fonts)
    private val hideToast = Runnable {
        toast.hide()
        invalidateLayer()
    }
    private val clockAdjust = ClockAdjustOverlay(context, fonts)
    private val timeUp = TimeUpOverlay(context, fonts)
    private var timeUpFocus: TimeUpChoice? = null

    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            refreshClock()
            updateTicker()
        }
    }

    fun render(next: GameSnapshot) {
        snapshot = next
        var changed = left.update(next.left)
        changed = right.update(next.right) || changed
        changed = updateStatus(next) || changed
        changed = updateTimeUp() || changed
        if (changed) invalidateLayer()
        refreshClock()
        updateTicker()
    }

    /** Shows a confirmation over the status bar; it hides [FEEDBACK_VISIBLE_MS] after the last one. */
    fun show(feedback: Feedback) {
        toast.show(feedback)
        removeCallbacks(hideToast)
        postDelayed(hideToast, FEEDBACK_VISIBLE_MS)
        invalidateLayer()
    }

    /**
     * Shows quick clock adjust for [session], or hides it when null. Its bar takes the status bar's place, so a
     * confirmation showing when it opens is dropped, and one arriving while it is open waits for it to close.
     */
    fun showClockAdjust(session: ClockAdjustSession?) {
        if (session == null) {
            clockAdjust.hide()
        } else {
            if (!clockAdjust.visible) {
                removeCallbacks(hideToast)
                toast.hide()
            }
            clockAdjust.show(session)
        }
        invalidateLayer()
    }

    /**
     * Shows the time-up bar with [focused] highlighted, or hides it when null; the red frame follows the snapshot.
     * Like quick clock adjust, the bar covers the status bar: a confirmation showing when it opens is dropped.
     */
    fun showTimeUpBar(focused: TimeUpChoice?) {
        if (focused != null && timeUpFocus == null) {
            removeCallbacks(hideToast)
            toast.hide()
        }
        timeUpFocus = focused
        if (updateTimeUp()) invalidateLayer()
    }

    private fun refreshClock() {
        val s = snapshot ?: return
        val now = ClockReading.of(s.gameClock.remainingAt(timeSource.nowNanos()), s.rules)
        if (now == reading) return
        reading = now
        clockText = now.format()
        val ghost = now.ghost()
        if (ghost != clockGhost) {
            clockGhost = ghost
            val width = clockGhostPaint.measureText(ghost)
            clockGhostX = CENTER_X - width / 2
            clockRight = clockGhostX + width
            layerDirty = true
        }
        invalidate()
    }

    private fun invalidateLayer() {
        layerDirty = true
        invalidate()
    }

    /** Returns true when the status bar text changed. */
    private fun updateStatus(s: GameSnapshot): Boolean {
        if (s.gameClock.running == running) return false
        running = s.gameClock.running
        clockStateText = context.getString(
            if (s.gameClock.running) R.string.status_clock_running else R.string.status_clock_stopped,
        )
        val textLeft = DESIGN_WIDTH - STATUS_PADDING - clockStatePaint.measureText(clockStateText)
        statusIconX = textLeft - STATUS_ICON_GAP - STATUS_ICON_SIZE
        // The pause icon: two 5×16 bars in an 18×18 box (boards.html PAUSE_ICON).
        val top = STATUS_CENTER_Y - STATUS_ICON_SIZE / 2
        pauseBarLeft.set(statusIconX + 2, top + 1, statusIconX + 7, top + 17)
        pauseBarRight.set(statusIconX + 11, top + 1, statusIconX + 16, top + 17)
        return true
    }

    private fun updateTimeUp(): Boolean {
        val s = snapshot ?: return false
        return timeUp.update(s.timeUp, s.left.score, s.right.score, timeUpFocus)
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
        removeCallbacks(hideToast)
        toast.hide()
        updateTicker()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) refreshClock()
        updateTicker()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = minOf(w / DESIGN_WIDTH, h / DESIGN_HEIGHT)
        offsetX = (w - DESIGN_WIDTH * scale) / 2
        offsetY = (h - DESIGN_HEIGHT * scale) / 2
        layer?.recycle()
        layer = if (w > 0 && h > 0) Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) else null
        layerCanvas.setBitmap(layer)
        layerDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        val layer = layer ?: return
        if (layerDirty) {
            drawLayer(layerCanvas)
            layerDirty = false
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        val saved = canvas.save()
        toDesignPixels(canvas)
        canvas.drawText(clockText, clockRight, clockBaseline, clockPaint)
        canvas.restoreToCount(saved)
    }

    private fun drawLayer(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        val saved = canvas.save()
        toDesignPixels(canvas)

        canvas.drawRoundRect(CLOCK_BOX, CLOCK_BOX_RADIUS, CLOCK_BOX_RADIUS, clockBoxPaint)
        canvas.drawRoundRect(clockBoxBorder, CLOCK_BOX_RADIUS, CLOCK_BOX_RADIUS, clockBoxBorderPaint)
        canvas.drawText(clockGhost, clockGhostX, clockBaseline, clockGhostPaint)

        left.draw(canvas)
        right.draw(canvas)

        canvas.drawRect(0f, STATUS_TOP, DESIGN_WIDTH, STATUS_TOP + STATUS_LINE, statusLinePaint)
        canvas.drawText(clockStateText, DESIGN_WIDTH - STATUS_PADDING, statusBaseline, clockStatePaint)
        if (running == true) {
            canvas.drawCircle(statusIconX + STATUS_ICON_SIZE / 2, STATUS_CENTER_Y, 8f, statusIconPaint)
        } else {
            canvas.drawRoundRect(pauseBarLeft, 1f, 1f, statusIconPaint)
            canvas.drawRoundRect(pauseBarRight, 1f, 1f, statusIconPaint)
        }

        timeUp.draw(canvas)
        when {
            clockAdjust.visible -> clockAdjust.draw(canvas)
            !timeUp.barVisible -> toast.draw(canvas)
        }
        canvas.restoreToCount(saved)
    }

    private fun toDesignPixels(canvas: Canvas) {
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
    }

    private fun ledPaint(colorRes: Int, align: Paint.Align) =
        textPaint(fonts.led, LED_SIZE, context.getColor(colorRes), align = align)

    /** One team column: its name (HOME / AWAY), score over its unlit segments. */
    private inner class TeamSlot(side: Side, private val centerX: Float) {
        private var score = -1

        private val name = context.getString(side.teamNameRes()).uppercase(resources.configuration.locales[0])
        private val namePaint = textPaint(fonts.barlow(700), NAME_SIZE, textColor, 0.03f, Paint.Align.CENTER).apply {
            fitText(name, NAME_SIZE, NAME_MAX_WIDTH)
        }
        private val nameBaseline = NAME_CENTER_Y + namePaint.centerToBaselinePerPx("H") * namePaint.textSize

        private var scoreText = ""
        private var scoreGhost = ""
        private var scoreGhostX = 0f
        private var scoreRight = 0f
        private val scorePaint = ledPaint(R.color.led_amber, Paint.Align.RIGHT)
        private val scoreGhostPaint = ledPaint(R.color.led_amber_unlit, Paint.Align.LEFT)
        private val scoreBaseline = SCORE_CENTER_Y + ledBaselineOffset

        /** Returns true when anything this column shows changed. */
        fun update(team: TeamState): Boolean {
            if (team.score == score) return false
            score = team.score
            scoreText = score.toString()
            val ghost = scoreGhost(scoreText)
            if (ghost != scoreGhost) {
                scoreGhost = ghost
                val width = scoreGhostPaint.measureText(ghost)
                scoreGhostX = centerX - width / 2
                scoreRight = scoreGhostX + width
            }
            return true
        }

        fun draw(canvas: Canvas) {
            canvas.drawText(name, centerX, nameBaseline, namePaint)
            canvas.drawText(scoreGhost, scoreGhostX, scoreBaseline, scoreGhostPaint)
            canvas.drawText(scoreText, scoreRight, scoreBaseline, scorePaint)
        }
    }
}
