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
import net.pladum.tablero.shared.NANOS_PER_MS
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.TeamState
import net.pladum.tablero.shared.TimeSource

/** Redraw check interval while the clock runs; the view only invalidates when the displayed value changes. */
private const val TICK_MS = 100L

/** How long a confirmation stays up after the last change to it. */
private const val FEEDBACK_VISIBLE_MS = 3_000L

// Design pixels (DESIGN_WIDTH × DESIGN_HEIGHT), from docs/design/boards.html; what moves between layouts is in
// BoardLayout.
private const val CENTER_X = DESIGN_WIDTH / 2
private const val CLOCK_BOX_RADIUS = 32f
private const val CLOCK_BOX_BORDER = 2f

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
 * The scoreboard surface: one Canvas view (design screens 01 to 03, 05 to 33). Renders the latest [GameSnapshot]
 * given to [render] (with the break bar while a break runs), confirmations given to [show], the time-up bar, the
 * quick clock adjust overlay, the team foul bar, the menu, its set game clock dialog, its how to use screen and its
 * exit confirm screen.
 *
 * Everything is laid out in design pixels, where [BoardLayout] puts it, in the colors of [BoardMode], and scaled to
 * the view. Text is built and measured when the state or the layout changes; [onDraw] allocates nothing. While the clock runs the view re-reads [timeSource] every [TICK_MS] and
 * redraws only when the reading changes (1 Hz above the tenths threshold, 10 Hz below).
 *
 * Only the clock digits change at 10 Hz, so everything else (box, unlit segments, teams, possession arrow and period,
 * fouls, status bar, overlays) is drawn into a screen-sized [layer] bitmap when it changes, and a tick draws that
 * bitmap plus one text. On the stick's slow CPU, recording and issuing the full scene on every tick missed about half
 * the frame deadlines.
 * With the menu open, the board is dimmed in the layer and a tick draws the digits in a dimmed color, clipped to the
 * left of the panel, and the menu's Game clock value; the set game clock dialog and the exit confirm screen only
 * open while the clock is stopped, so they go into the layer with the clock digits under them. How to use also only
 * opens while the clock is stopped and covers the whole board, so it is all in the layer and no digits are drawn.
 * Holding OK on EXIT redraws on every frame while it lasts, but only the button's fill and labels over the layer.
 */
@SuppressLint("ViewConstructor") // Built in code by MainActivity, never inflated from XML.
class ScoreboardView(
    context: Context,
    private val timeSource: TimeSource,
    private var layout: BoardLayout,
    private var mode: BoardMode,
) : View(context) {
    private val fonts = BoardFonts(context)

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    /** Everything but the lit clock digits; redrawn in [onDraw] when [layerDirty]. */
    private var layer: Bitmap? = null
    private val layerCanvas = Canvas()
    private var layerDirty = true
    private var backgroundColor = 0

    private var snapshot: GameSnapshot? = null

    /** Shows when the layout places a clock box and the mode colors one. */
    private val clockBox = RectF()
    private val clockBoxPaint = fillPaint()
    private val clockBoxBorder = RectF()
    private val clockBoxBorderPaint = strokePaint(0, CLOCK_BOX_BORDER)
    private val clockPaint = ledPaint(Paint.Align.RIGHT)
    private val clockGhostPaint = ledPaint(Paint.Align.LEFT)
    private var ledBaselineOffset = 0f
    private var clockBaseline = 0f
    private var reading: ClockReading? = null
    private var clockText = ""
    private var clockGhost = ""
    private var clockGhostX = 0f
    private var clockRight = 0f

    private val left = TeamSlot(Side.LEFT)
    private val right = TeamSlot(Side.RIGHT)
    private val possession = PossessionArrow(context, fonts)
    private val fouls = TeamFouls(context, fonts)

    private val statusLinePaint = fillPaint()
    private val clockStatePaint = textPaint(fonts.barlow(600), STATUS_SIZE, 0, 0.08f, Paint.Align.RIGHT)
    private val statusBaseline = STATUS_CENTER_Y + clockStatePaint.centerToBaselinePerPx("H") * STATUS_SIZE
    private val statusIconPaint = fillPaint()
    private val pauseBarLeft = RectF()
    private val pauseBarRight = RectF()
    private var running: Boolean? = null
    private var clockStateText = ""
    private var statusIconX = 0f

    private val toast = FeedbackToast(context, fonts, mode)
    private val hideToast = Runnable {
        toast.hide()
        invalidateLayer()
    }
    private val clockAdjust = ClockAdjustOverlay(context, fonts)
    private val foulBar = FoulBarOverlay(context, fonts)
    private val breakBar = BreakOverlay(context, fonts)
    private val timeUp = TimeUpOverlay(context, fonts)
    private var timeUpFocus: TimeUpChoice? = null
    private val menu = MenuOverlay(context, fonts)
    private val clockDimPaint = ledPaint(Paint.Align.RIGHT)
    private val setClock = SetClockOverlay(context, fonts)
    private val exitConfirm = ExitConfirmOverlay(context, fonts)
    private val help = HelpOverlay(context, fonts)
    private var menuState: MenuState? = null

    init {
        applyLayout()
        applyMode()
    }

    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            refreshClock()
            updateTicker()
        }
    }

    /** Switches to [next] (menu → Layout); everything is placed again and redrawn. */
    fun setLayout(next: BoardLayout) {
        if (next == layout) return
        layout = next
        applyLayout()
        refreshClock()
        invalidateLayer()
    }

    /** Switches to [next] (menu → Mode); everything is redrawn in its colors. */
    fun setMode(next: BoardMode) {
        if (next == mode) return
        mode = next
        applyMode()
        invalidateLayer()
    }

    fun render(next: GameSnapshot) {
        snapshot = next
        var changed = left.update(next.left)
        changed = right.update(next.right) || changed
        changed = possession.update(next) || changed
        changed = fouls.update(next) || changed
        changed = updateStatus(next) || changed
        changed = updateTimeUp() || changed
        changed = breakBar.update(next) || changed
        changed = updateMenu() || changed
        if (changed) invalidateLayer()
        refreshClock()
        updateTicker()
    }

    /**
     * Shows a confirmation over the status bar; it hides [FEEDBACK_VISIBLE_MS] after the last one. None shows while
     * the menu is open: the menu shows the values it changes.
     */
    fun show(feedback: Feedback) {
        if (menuState != null) return
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
            if (!clockAdjust.visible) dropToast()
            clockAdjust.show(session)
        }
        invalidateLayer()
    }

    /**
     * Shows the team foul bar, or hides it. Like quick clock adjust, its bar takes the status bar's place: a
     * confirmation showing when it opens is dropped, and one arriving while it is open waits for it to close.
     */
    fun showFoulBar(open: Boolean) {
        if (open == foulBar.visible) return
        if (open) {
            dropToast()
        }
        foulBar.visible = open
        invalidateLayer()
    }

    /**
     * Shows the time-up bar with [focused] highlighted, or hides it when null; the red frame follows the snapshot.
     * Like quick clock adjust, the bar covers the status bar: a confirmation showing when it opens is dropped.
     */
    fun showTimeUpBar(focused: TimeUpChoice?) {
        if (focused != null && timeUpFocus == null) {
            dropToast()
        }
        timeUpFocus = focused
        if (updateTimeUp()) invalidateLayer()
    }

    /** Shows the menu, or one of its dialogs, for [state]; hides it when null. */
    fun showMenu(state: MenuState?) {
        if (state != null && menuState == null) {
            dropToast()
        }
        menuState = state
        if (updateMenu()) invalidateLayer() else invalidate() // A hold on EXIT starting or ending.
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
        if (setClock.visible || exitConfirm.visible) layerDirty = true // The digits are in the layer, under the dialog.
        invalidate()
    }

    /** Places the clock, its box, the team columns and the adjust ring where [layout] has them. */
    private fun applyLayout() {
        for (paint in arrayOf(clockPaint, clockGhostPaint, clockDimPaint)) paint.textSize = layout.ledSize
        ledBaselineOffset = fonts.ledCenterPerPx * layout.ledSize
        clockBaseline = layout.clockCenterY + ledBaselineOffset
        val box = layout.clockBox
        if (box != null) {
            clockBox.set(box.left, box.top, box.right, box.bottom)
            clockBoxBorder.set(clockBox)
            clockBoxBorder.inset(CLOCK_BOX_BORDER / 2, CLOCK_BOX_BORDER / 2)
        }
        left.place(layout.leftTeamX)
        right.place(layout.rightTeamX)
        possession.place(layout)
        fouls.place(layout)
        clockAdjust.place(layout)
        foulBar.place(layout)
        // Measured again at the new size on the next refresh.
        reading = null
        clockGhost = ""
    }

    /** Colors the board as [mode] has it. */
    private fun applyMode() {
        backgroundColor = context.color(mode.background)
        mode.clockBox?.let { box ->
            clockBoxPaint.color = context.color(box.fill)
            clockBoxBorderPaint.color = context.color(box.border)
        }
        val clock = context.color(mode.clock)
        val clockUnlit = context.color(mode.clockUnlit)
        clockPaint.color = clock
        clockDimPaint.color = menu.dimmed(clock)
        clockGhostPaint.color = clockUnlit
        val text = context.color(mode.text)
        clockStatePaint.color = text
        statusIconPaint.color = text
        statusLinePaint.color = context.color(mode.statusLine)
        val score = context.color(mode.score)
        val scoreUnlit = context.color(mode.scoreUnlit)
        left.setColors(text, score, scoreUnlit)
        right.setColors(text, score, scoreUnlit)
        possession.setColors(text, score, scoreUnlit, clock, clockUnlit)
        fouls.setColors(text, score, scoreUnlit, clock, clockUnlit)
        toast.setMode(mode)
        foulBar.setMode(context, mode)
    }

    /** Drops the confirmation showing over the status bar, e.g. when a bar or the menu takes its place. */
    private fun dropToast() {
        removeCallbacks(hideToast)
        toast.hide()
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
        return timeUp.update(s, timeUpFocus)
    }

    private fun updateMenu(): Boolean {
        val s = snapshot ?: return false
        val panel = menu.update(menuState, s)
        val exit = exitConfirm.update(menuState?.exitConfirm)
        val helpChanged = help.update(menuState?.help == true, s.rules.periods != null)
        return setClock.update(menuState?.setClock) || exit || helpChanged || panel
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
        dropToast()
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
        if (exitConfirm.visible) drawExitHold(canvas)
        if (setClock.visible || exitConfirm.visible || help.visible) return
        val saved = canvas.save()
        toDesignPixels(canvas)
        if (menu.visible) {
            val board = canvas.save()
            canvas.clipRect(0f, 0f, MENU_LEFT, DESIGN_HEIGHT)
            canvas.drawText(clockText, clockRight, clockBaseline, clockDimPaint)
            canvas.restoreToCount(board)
            menu.drawLive(canvas, clockText)
        } else {
            canvas.drawText(clockText, clockRight, clockBaseline, clockPaint)
        }
        canvas.restoreToCount(saved)
    }

    private fun drawLayer(canvas: Canvas) {
        canvas.drawColor(backgroundColor)
        val saved = canvas.save()
        toDesignPixels(canvas)
        if (help.visible) { // It covers the whole board.
            help.draw(canvas)
            canvas.restoreToCount(saved)
            return
        }

        if (layout.clockBox != null && mode.clockBox != null) {
            canvas.drawRoundRect(clockBox, CLOCK_BOX_RADIUS, CLOCK_BOX_RADIUS, clockBoxPaint)
            canvas.drawRoundRect(clockBoxBorder, CLOCK_BOX_RADIUS, CLOCK_BOX_RADIUS, clockBoxBorderPaint)
        }
        canvas.drawText(clockGhost, clockGhostX, clockBaseline, clockGhostPaint)

        left.draw(canvas)
        right.draw(canvas)
        possession.draw(canvas)
        fouls.draw(canvas)

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
            foulBar.visible -> foulBar.draw(canvas)
            else -> {
                breakBar.draw(canvas) // Never at time up, which only a period's end reaches.
                if (!timeUp.barVisible) toast.draw(canvas)
            }
        }
        menu.draw(canvas)
        if (setClock.visible || exitConfirm.visible) {
            canvas.drawText(clockText, clockRight, clockBaseline, clockPaint)
            setClock.draw(canvas)
            exitConfirm.draw(canvas)
        }
        canvas.restoreToCount(saved)
    }

    /** While OK is held on EXIT: its fill, by the time held, and the next frame until it is full. */
    private fun drawExitHold(canvas: Canvas) {
        val started = exitConfirm.holdStartedNanos ?: return
        val held = (timeSource.nowNanos() - started).toFloat() / (EXIT_HOLD_MS * NANOS_PER_MS)
        val progress = held.coerceIn(0f, 1f)
        val saved = canvas.save()
        toDesignPixels(canvas)
        exitConfirm.drawHold(canvas, progress)
        canvas.restoreToCount(saved)
        if (progress < 1f) postInvalidateOnAnimation()
    }

    private fun toDesignPixels(canvas: Canvas) {
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
    }

    /** Sized by [applyLayout], colored by [applyMode]. */
    private fun ledPaint(align: Paint.Align) = textPaint(fonts.led, layout.ledSize, 0, align = align)

    /**
     * One team column: its name (HOME / AWAY), score over its unlit segments. Placed by [place], colored by [setColors].
     */
    private inner class TeamSlot(side: Side) {
        private var score = -1
        private var centerX = 0f

        private val name = context.teamTitle(side)
        private val namePaint = textPaint(fonts.barlow(700), layout.nameSize, 0, 0.03f, Paint.Align.CENTER)
        private val nameCenterPerPx = namePaint.centerToBaselinePerPx("H")
        private var nameBaseline = 0f

        private val digits = LedDigits(fonts)

        /** Puts the column at [x] and sizes it as [layout] has it. */
        fun place(x: Float) {
            centerX = x
            namePaint.fitText(name, layout.nameSize, NAME_MAX_WIDTH)
            nameBaseline = layout.nameCenterY + nameCenterPerPx * namePaint.textSize
            digits.place(x, layout.scoreCenterY, layout.ledSize)
        }

        /** Colors the name, the score and its unlit segments, as resolved colors. */
        fun setColors(name: Int, score: Int, scoreUnlit: Int) {
            namePaint.color = name
            digits.setColors(score, scoreUnlit)
        }

        /** Returns true when anything this column shows changed. */
        fun update(team: TeamState): Boolean {
            if (team.score == score) return false
            score = team.score
            val text = score.toString()
            digits.setText(text, scoreGhost(text))
            return true
        }

        fun draw(canvas: Canvas) {
            canvas.drawText(name, centerX, nameBaseline, namePaint)
            digits.draw(canvas)
        }
    }
}
