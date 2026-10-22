package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (remote-mapping board, .help, .remote, .map).
private const val CONTENT_LEFT = 548f
private const val CONTENT_RIGHT = 1824f
private const val EYEBROW_CENTER_Y = 90f
private const val EYEBROW_SIZE = 30f
private const val TITLE_CENTER_Y = 152f
private const val TITLE_SIZE = 72f

/** The key table: a line, the column names, a line, then one row per key, each above its own line. */
private const val TABLE_TOP = 224f
private const val LINE = 2f
private const val HEADER_SIZE = 26f
private const val FIRST_ROW_TOP = 291f
private const val HEADER_CENTER_Y = (TABLE_TOP + FIRST_ROW_TOP) / 2
private const val ROW_HEIGHT = 108f
private const val PRESS_LEFT = 728f
private const val HOLD_LEFT = 1367.3f
private const val CELL_PADDING = 24f
private const val PRESS_WIDTH = HOLD_LEFT - PRESS_LEFT - CELL_PADDING
private const val HOLD_WIDTH = CONTENT_RIGHT - HOLD_LEFT - CELL_PADDING

/** Centers from a row's top: the keycap and the HOLD text in the middle, PRESS's text above its note. */
private const val ROW_CENTER = 53f
private const val PRESS_CENTER = 35.5f
private const val NOTE_CENTER = 76.9f
private const val MAIN_SIZE = 38f
private const val NOTE_SIZE = 26f
private const val KEYCAP_SIZE = 34f
private const val KEYCAP_HEIGHT = 51f
private const val KEYCAP_TOP = ROW_CENTER - KEYCAP_HEIGHT / 2
private const val KEYCAP_MIN_WIDTH = 76f

/** The ↓ row's place in the table: OK, ←, →, ↑, ↓, BACK. */
private const val DOWN_ROW = 4
private const val KEYCAP_PADDING = 14f
private const val KEYCAP_BORDER = 3f
private const val KEYCAP_RADIUS = 12f

/** The legend (a grey swatch, then what grey keys mean) and the close hint, on one line at the bottom. */
private const val FOOTER_CENTER_Y = 1003f
private const val SWATCH_SIZE = 18f
private const val SWATCH_BORDER = 3f
private const val SWATCH_RADIUS = 4f
private const val LEGEND_LEFT = CONTENT_LEFT + SWATCH_SIZE + 14f
private const val LEGEND_SIZE = 28f
private const val LEGEND_GAP = 48f
private const val HINT_SIZE = 28f

/** The remote, in a column of [SIDE_WIDTH] with its caption: the ring around OK, BACK and HOME, then unused keys. */
private const val SIDE_WIDTH = 340f
private val REMOTE = RectF(116f, 198.5f, 416f, 838.5f)
private const val REMOTE_TOP_RADIUS = 150f
private const val REMOTE_BOTTOM_RADIUS = 100f
private const val REMOTE_BORDER = 2f
private const val REMOTE_CENTER_X = 266f
private const val RING_CENTER_Y = 348.5f
private const val RING_RADIUS = 118f
private const val RING_ARROW_SIZE = 30f
private const val RING_UP_CENTER_Y = 262.5f
private const val RING_DOWN_CENTER_Y = 434.5f
private const val RING_LEFT_CENTER_X = 183f
private const val RING_RIGHT_CENTER_X = 349f
private const val OK_RADIUS = 56f
private const val OK_SIZE = 36f
private const val ROUND_KEY_CENTER_Y = 548.5f
private const val ROUND_KEY_RADIUS = 38f
private const val BACK_CENTER_X = 192f
private const val HOME_CENTER_X = 340f
private const val KEY_LABEL_CENTER_Y = 607.5f
private const val KEY_LABEL_SIZE = 22f
private const val SMALL_KEY_CENTER_Y = 688.5f
private const val SMALL_KEY_RADIUS = 24f
private const val SMALL_KEY_SPACING = 76f
private val PILLS = listOf(RectF(160f, 756.5f, 256f, 796.5f), RectF(276f, 756.5f, 372f, 796.5f))
private const val PILL_RADIUS = 20f
private const val CAPTION_CENTER_Y = 882f
private const val CAPTION_SIZE = 26f

/** Outlines of the keys tablero uses, and of the others. */
private const val USED_BORDER = 4f
private const val UNUSED_BORDER = 3f

/** The icons on BACK and HOME are drawn in a 32-unit box (boards.html), one unit per pixel. */
private const val ICON_SIZE = 32f
private const val ICON_STROKE = 3f

/**
 * How to use (design 10, 33): a generic remote, since every Android TV remote has the D-pad, its center button and
 * BACK, next to a table of what each key does when pressed or held; under period rules holding ↓ gives a team foul.
 * It covers the whole board and only opens while the clock is stopped, so everything is laid out once and [draw] goes
 * into the scoreboard's cached layer. Design pixels.
 */
class HelpOverlay(context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private val text = context.color(R.color.text)
    private val muted = context.color(R.color.text_muted)
    private val faint = context.color(R.color.text_faint)
    private val off = context.color(R.color.text_off)

    private val backgroundPaint = fillPaint(context.color(R.color.panel))
    private val tableLinePaint = fillPaint(context.color(R.color.field_outline))
    private val rowLinePaint = fillPaint(context.color(R.color.help_row_line))

    private val eyebrow = Label(
        context.getString(R.string.help_eyebrow), CONTENT_LEFT, EYEBROW_CENTER_Y,
        textPaint(fonts.barlow(600), EYEBROW_SIZE, muted, 0.24f),
    )
    private val title = Label(
        context.getString(R.string.help_title), CONTENT_LEFT, TITLE_CENTER_Y,
        textPaint(fonts.barlow(700), TITLE_SIZE, text), CONTENT_RIGHT - CONTENT_LEFT,
    )
    private val headerPaint = textPaint(fonts.barlow(600), HEADER_SIZE, muted, 0.2f)
    private val headers = listOf(
        Label(context.getString(R.string.help_column_key), CONTENT_LEFT, HEADER_CENTER_Y, headerPaint),
        Label(context.getString(R.string.help_column_press), PRESS_LEFT, HEADER_CENTER_Y, headerPaint),
        Label(context.getString(R.string.help_column_hold), HOLD_LEFT, HEADER_CENTER_Y, headerPaint),
    )

    private val keycapPaint = textPaint(fonts.barlow(700), KEYCAP_SIZE, text, align = Paint.Align.CENTER)
    private val keycapOutline = strokePaint(text, KEYCAP_BORDER)
    private val mainPaint = textPaint(fonts.barlow(600), MAIN_SIZE, text)
    private val nonePaint = textPaint(fonts.barlow(600), MAIN_SIZE, faint)
    private val notePaint = textPaint(fonts.barlow(500), NOTE_SIZE, muted)
    private val none = context.getString(R.string.help_none)
    private val home = context.getString(Side.LEFT.teamNameRes())
    private val away = context.getString(Side.RIGHT.teamNameRes())
    private val pickupRows = run {
        val locale = context.resources.configuration.primaryLocale
        listOf(
            Row(context.getString(R.string.key_ok), context.getString(R.string.help_clock),
                context.getString(R.string.help_clock_note), null),
            Row(context.getString(R.string.key_left), context.getString(R.string.help_score_up, home),
                context.getString(R.string.help_score_note), context.getString(R.string.help_score_down, home)),
            Row(context.getString(R.string.key_right), context.getString(R.string.help_score_up, away),
                context.getString(R.string.help_score_note), context.getString(R.string.help_score_down, away)),
            Row(context.getString(R.string.key_up), context.getString(R.string.help_possession),
                context.getString(R.string.help_possession_note, home.uppercase(locale)),
                context.getString(R.string.help_possession_hide)),
            Row(context.getString(R.string.key_down), context.getString(R.string.help_adjust),
                context.getString(R.string.help_adjust_note), null),
            Row(context.getString(R.string.key_back), context.getString(R.string.help_undo),
                context.getString(R.string.help_undo_note), context.getString(R.string.help_menu)),
        )
    }

    /** Under period rules ↓ also counts down breaks, and holding it gives a team foul. */
    private val periodRows = pickupRows.toMutableList().apply {
        this[DOWN_ROW] = Row(context.getString(R.string.key_down), context.getString(R.string.help_adjust),
            context.getString(R.string.help_adjust_note_breaks), context.getString(R.string.help_foul),
            context.getString(R.string.help_foul_note, home, away))
    }
    private var rows: List<Row> = pickupRows

    private val swatch = RectF(
        CONTENT_LEFT, FOOTER_CENTER_Y - SWATCH_SIZE / 2, CONTENT_LEFT + SWATCH_SIZE, FOOTER_CENTER_Y + SWATCH_SIZE / 2,
    ).apply { inset(SWATCH_BORDER / 2, SWATCH_BORDER / 2) }
    private val swatchPaint = strokePaint(off, SWATCH_BORDER)
    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, muted, 0.06f)
    private val hintKeycapPaint = keycapPaint(muted)
    private val hintBaseline = FOOTER_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hint = KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_close))
    private val hintLeft = hint.layoutEndingAt(CONTENT_RIGHT, FOOTER_CENTER_Y, hintPaint)
    private val legend = Label(
        context.getString(
            R.string.help_legend, context.getString(R.string.key_back), context.getString(R.string.menu_help),
        ),
        LEGEND_LEFT, FOOTER_CENTER_Y, textPaint(fonts.barlow(500), LEGEND_SIZE, context.color(R.color.text_soft)),
        hintLeft - LEGEND_GAP - LEGEND_LEFT,
    )

    private val remote = remotePath(REMOTE)
    private val remoteBorder = remotePath(RectF(REMOTE).apply { inset(REMOTE_BORDER / 2, REMOTE_BORDER / 2) })
    private val remotePaint = fillPaint(context.color(R.color.panel_raised))
    private val remoteBorderPaint = strokePaint(context.color(R.color.field_outline), REMOTE_BORDER)
    private val usedPaint = strokePaint(text, USED_BORDER)
    private val unusedPaint = strokePaint(off, UNUSED_BORDER)
    private val okPaint = fillPaint(text)
    private val ok = Label(
        context.getString(R.string.key_ok), REMOTE_CENTER_X, RING_CENTER_Y,
        textPaint(fonts.barlow(800), OK_SIZE, context.color(R.color.board_background), align = Paint.Align.CENTER),
    )
    private val ringArrowPaint = textPaint(fonts.barlow(700), RING_ARROW_SIZE, text, align = Paint.Align.CENTER)
    private val ringArrows = listOf(
        Label(context.getString(R.string.key_up), REMOTE_CENTER_X, RING_UP_CENTER_Y, ringArrowPaint),
        Label(context.getString(R.string.key_down), REMOTE_CENTER_X, RING_DOWN_CENTER_Y, ringArrowPaint),
        Label(context.getString(R.string.key_left), RING_LEFT_CENTER_X, RING_CENTER_Y, ringArrowPaint),
        Label(context.getString(R.string.key_right), RING_RIGHT_CENTER_X, RING_CENTER_Y, ringArrowPaint),
    )
    private val backIcon = icon(BACK_CENTER_X) {
        moveTo(12f, 8f)
        lineTo(5f, 15f)
        lineTo(12f, 22f)
        moveTo(5f, 15f)
        lineTo(20f, 15f)
        arcTo(RectF(13f, 15f, 27f, 29f), -90f, 180f)
        lineTo(14f, 29f)
    }
    private val homeIcon = icon(HOME_CENTER_X) {
        moveTo(5f, 16f)
        lineTo(16f, 6f)
        lineTo(27f, 16f)
        moveTo(9f, 13f)
        lineTo(9f, 26f)
        lineTo(23f, 26f)
        lineTo(23f, 13f)
    }
    private val usedIconPaint = roundStrokePaint(text, ICON_STROKE)
    private val unusedIconPaint = roundStrokePaint(off, ICON_STROKE)
    private val backLabel = Label(
        context.getString(R.string.key_back), BACK_CENTER_X, KEY_LABEL_CENTER_Y,
        textPaint(fonts.barlow(700), KEY_LABEL_SIZE, text, 0.1f, Paint.Align.CENTER),
    )
    private val homeLabel = Label(
        context.getString(R.string.key_home), HOME_CENTER_X, KEY_LABEL_CENTER_Y,
        textPaint(fonts.barlow(600), KEY_LABEL_SIZE, faint, 0.1f, Paint.Align.CENTER),
    )
    private val pills = PILLS.map { RectF(it).apply { inset(UNUSED_BORDER / 2, UNUSED_BORDER / 2) } }
    private val caption = Label(
        context.getString(R.string.help_remote), REMOTE_CENTER_X, CAPTION_CENTER_Y,
        textPaint(fonts.barlow(500), CAPTION_SIZE, muted, align = Paint.Align.CENTER), SIDE_WIDTH,
    )

    /** Shows or hides the screen, with the ↓ row [periodRules] call for; returns true when that changed. */
    fun update(open: Boolean, periodRules: Boolean): Boolean {
        val nextRows = if (periodRules) periodRows else pickupRows
        if (open == visible && nextRows === rows) return false
        visible = open
        rows = nextRows
        return true
    }

    fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawRect(0f, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, backgroundPaint)
        drawRemote(canvas)
        eyebrow.draw(canvas)
        title.draw(canvas)
        canvas.drawRect(CONTENT_LEFT, TABLE_TOP, CONTENT_RIGHT, TABLE_TOP + LINE, tableLinePaint)
        for (header in headers) header.draw(canvas)
        canvas.drawRect(CONTENT_LEFT, FIRST_ROW_TOP - LINE, CONTENT_RIGHT, FIRST_ROW_TOP, tableLinePaint)
        rows.forEachIndexed { i, row ->
            val saved = canvas.save()
            canvas.translate(0f, FIRST_ROW_TOP + i * ROW_HEIGHT)
            row.draw(canvas)
            canvas.restoreToCount(saved)
        }
        canvas.drawRoundRect(swatch, SWATCH_RADIUS, SWATCH_RADIUS, swatchPaint)
        legend.draw(canvas)
        hint.draw(canvas, hintBaseline, hintPaint, hintKeycapPaint)
    }

    private fun drawRemote(canvas: Canvas) {
        canvas.drawPath(remote, remotePaint)
        canvas.drawPath(remoteBorder, remoteBorderPaint)
        canvas.drawCircle(REMOTE_CENTER_X, RING_CENTER_Y, RING_RADIUS - USED_BORDER / 2, usedPaint)
        for (arrow in ringArrows) arrow.draw(canvas)
        canvas.drawCircle(REMOTE_CENTER_X, RING_CENTER_Y, OK_RADIUS, okPaint)
        ok.draw(canvas)
        canvas.drawCircle(BACK_CENTER_X, ROUND_KEY_CENTER_Y, ROUND_KEY_RADIUS - USED_BORDER / 2, usedPaint)
        canvas.drawPath(backIcon, usedIconPaint)
        backLabel.draw(canvas)
        canvas.drawCircle(HOME_CENTER_X, ROUND_KEY_CENTER_Y, ROUND_KEY_RADIUS - UNUSED_BORDER / 2, unusedPaint)
        canvas.drawPath(homeIcon, unusedIconPaint)
        homeLabel.draw(canvas)
        for (i in -1..1) {
            val x = REMOTE_CENTER_X + i * SMALL_KEY_SPACING
            canvas.drawCircle(x, SMALL_KEY_CENTER_Y, SMALL_KEY_RADIUS - UNUSED_BORDER / 2, unusedPaint)
        }
        val pillRadius = PILL_RADIUS - UNUSED_BORDER / 2
        for (pill in pills) canvas.drawRoundRect(pill, pillRadius, pillRadius, unusedPaint)
        caption.draw(canvas)
    }

    /** The remote's outline: rounder at the top than at the bottom, like the design. */
    private fun remotePath(bounds: RectF): Path {
        val inset = (REMOTE.width() - bounds.width()) / 2
        val top = REMOTE_TOP_RADIUS - inset
        val bottom = REMOTE_BOTTOM_RADIUS - inset
        return Path().apply {
            addRoundRect(bounds, floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom), Path.Direction.CW)
        }
    }

    /** An icon drawn by [draw] in its 32-unit box, moved to the round key centered at [centerX]. */
    private fun icon(centerX: Float, draw: Path.() -> Unit) = Path().apply {
        draw()
        offset(centerX - ICON_SIZE / 2, ROUND_KEY_CENTER_Y - ICON_SIZE / 2)
    }

    /**
     * One line of text placed by its center line at [x] (where [paint]'s alignment puts it), shrunk to [maxWidth]
     * when given; [draw] sets [paint] to that size, so labels can share a paint.
     */
    private class Label(
        private val text: String,
        private val x: Float,
        centerY: Float,
        private val paint: Paint,
        maxWidth: Float = Float.MAX_VALUE,
    ) {
        private val size: Float
        private val baseline: Float

        init {
            val defaultSize = paint.textSize
            paint.fitText(text, defaultSize, maxWidth)
            size = paint.textSize
            baseline = centerY + paint.centerToBaselinePerPx("H") * size
            paint.textSize = defaultSize
        }

        fun draw(canvas: Canvas) {
            paint.textSize = size
            canvas.drawText(text, x, baseline, paint)
        }
    }

    /**
     * One key's row, laid out from its own top: the keycap, what a press does with a note below, and what holding
     * the key does ([hold] null: nothing), with a note below when [holdNote] is given.
     */
    private inner class Row(key: String, press: String, note: String, hold: String?, holdNote: String? = null) {
        private val keycap: RectF
        private val keyLabel: Label
        private val pressLabel = Label(press, PRESS_LEFT, PRESS_CENTER, mainPaint, PRESS_WIDTH)
        private val noteLabel = Label(note, PRESS_LEFT, NOTE_CENTER, notePaint, PRESS_WIDTH)
        private val holdLabel = when {
            hold == null -> Label(none, HOLD_LEFT, ROW_CENTER, nonePaint)
            holdNote == null -> Label(hold, HOLD_LEFT, ROW_CENTER, mainPaint, HOLD_WIDTH)
            else -> Label(hold, HOLD_LEFT, PRESS_CENTER, mainPaint, HOLD_WIDTH)
        }
        private val holdNoteLabel = holdNote?.let { Label(it, HOLD_LEFT, NOTE_CENTER, notePaint, HOLD_WIDTH) }

        init {
            val width = maxOf(KEYCAP_MIN_WIDTH, keycapPaint.measureText(key) + 2 * (KEYCAP_PADDING + KEYCAP_BORDER))
            keycap = RectF(CONTENT_LEFT, KEYCAP_TOP, CONTENT_LEFT + width, KEYCAP_TOP + KEYCAP_HEIGHT)
            keyLabel = Label(key, keycap.centerX(), ROW_CENTER, keycapPaint)
            keycap.inset(KEYCAP_BORDER / 2, KEYCAP_BORDER / 2) // The stroke is centered on the outline.
        }

        fun draw(canvas: Canvas) {
            val radius = KEYCAP_RADIUS - KEYCAP_BORDER / 2
            canvas.drawRoundRect(keycap, radius, radius, keycapOutline)
            keyLabel.draw(canvas)
            pressLabel.draw(canvas)
            noteLabel.draw(canvas)
            holdLabel.draw(canvas)
            holdNoteLabel?.draw(canvas)
            canvas.drawRect(CONTENT_LEFT, ROW_HEIGHT - LINE, CONTENT_RIGHT, ROW_HEIGHT, rowLinePaint)
        }
    }
}
