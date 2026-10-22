package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.foul-ring, .foul-bar); the rings' place comes from the layout.
private const val RING_WIDTH = 6f
private const val RING_RADIUS = 32f
private const val TITLE_SIZE = 36f
private const val HINT_SIZE = 26f
private const val HINT_GAP = 28f

/** Minimum space between the title and the key hints. */
private const val GROUP_GAP = 40f

/** What the keys drive while the team foul bar (hold ↓) is open; [MainActivity] runs it. */
interface FoulKeys {
    val isOpen: Boolean

    /** Hold ↓: opens the bar and returns true, or returns false under Pickup rules, which count no fouls. */
    fun open(): Boolean

    /** ← / →: one foul for [side], and the bar closes. */
    fun foul(side: Side)

    /** Back: closes the bar without a foul. */
    fun cancel()
}

/**
 * The team foul bar (design 27): a ring around each team's fouls and a bar over the status bar with `TEAM FOUL` and
 * the key hints (`← HOME  → AWAY  BACK CANCEL`), in the confirmation's colors of the board's [BoardMode]. Text is
 * laid out once; [draw] only draws. Design pixels.
 */
class FoulBarOverlay(context: Context, fonts: BoardFonts) {
    var visible = false

    private val leftRing = RectF()
    private val rightRing = RectF()
    private val ringPaint = strokePaint(0, RING_WIDTH)
    private val barPaint = fillPaint()

    private val title = context.getString(R.string.foul_title)
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, 0, 0.06f)
    private val titleBaseline = STATUS_BAR_CENTER_Y + titlePaint.centerToBaselinePerPx("H") * TITLE_SIZE

    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, 0, 0.06f)
    private val keycapPaint = keycapPaint(0)
    private val hintBaseline = STATUS_BAR_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hints = listOf(
        KeyHint(context.getString(R.string.key_left), context.teamTitle(Side.LEFT)),
        KeyHint(context.getString(R.string.key_right), context.teamTitle(Side.RIGHT)),
        KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_cancel)),
    )

    init {
        val hintsLeft = hints.layoutEndingAt(DESIGN_WIDTH - BAR_PADDING, HINT_GAP, STATUS_BAR_CENTER_Y, hintPaint)
        titlePaint.fitText(title, TITLE_SIZE, hintsLeft - GROUP_GAP - BAR_PADDING)
    }

    /** Puts the rings where [layout] has them: the left one as given, the right one mirrored. */
    fun place(layout: BoardLayout) {
        val box = layout.foulRing
        leftRing.set(box.left, box.top, box.right, box.bottom)
        rightRing.set(DESIGN_WIDTH - box.right, box.top, DESIGN_WIDTH - box.left, box.bottom)
        for (ring in arrayOf(leftRing, rightRing)) ring.inset(RING_WIDTH / 2, RING_WIDTH / 2)
    }

    /** Takes [mode]'s colors: the rings in the board's text color, the bar like the confirmation. */
    fun setMode(context: Context, mode: BoardMode) {
        ringPaint.color = context.color(mode.text)
        barPaint.color = context.color(mode.toastPanel)
        titlePaint.color = context.color(mode.toastTitle)
        hintPaint.color = context.color(mode.toastDetail)
        keycapPaint.color = hintPaint.color
    }

    fun draw(canvas: Canvas) {
        if (!visible) return
        val radius = RING_RADIUS - RING_WIDTH / 2
        canvas.drawRoundRect(leftRing, radius, radius, ringPaint)
        canvas.drawRoundRect(rightRing, radius, radius, ringPaint)
        canvas.drawRect(0f, STATUS_BAR_TOP, DESIGN_WIDTH, DESIGN_HEIGHT, barPaint)
        canvas.drawText(title, BAR_PADDING, titleBaseline, titlePaint)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }
}
