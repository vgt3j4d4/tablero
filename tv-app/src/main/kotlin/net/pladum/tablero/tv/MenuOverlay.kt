package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.Side

// Design pixels, from docs/design/boards.html (.menu, .menu-group, .menu-row).
/** The panel's left edge; the board to its left stays visible, dimmed. */
const val MENU_LEFT = 1100f
private const val BORDER = 2f
private const val PANEL_LEFT = MENU_LEFT + BORDER
private const val PADDING = 48f
private const val TEXT_LEFT = PANEL_LEFT + PADDING
private const val TEXT_RIGHT = DESIGN_WIDTH - PADDING
private const val TITLE_SIZE = 60f
private const val TITLE_BASELINE = 105f
private const val CHIP_SIZE = 28f
private const val FIRST_ROW_TOP = 142f
private const val ROW_HEIGHT = 70f
private const val ROW_SIZE = 38f

/** Space below a group's last row, its dividing line, and the space before the next group. */
private const val GROUP_PADDING = 14f
private const val GROUP_LINE = 2f
private const val GROUP_GAP = 14f

private const val VALUE_MIN_WIDTH = 120f
private const val CHEVRON_SIZE = 24f
private const val CHEVRON_GAP = 14f
private const val VALUE_RIGHT = TEXT_RIGHT - CHEVRON_SIZE - CHEVRON_GAP
private const val FOOTER_TOP = 1000f
private const val FOOTER_CENTER_Y = 1041f
private const val HINT_SIZE = 24f

/** The chevrons are drawn in a 22-unit box (boards.html CHEVRON_L / CHEVRON_R) scaled to [CHEVRON_SIZE]. */
private const val CHEVRON_UNIT = CHEVRON_SIZE / 22f

/**
 * The menu (design 06): a panel on the right over the dimmed board, with the clock state, the rows of [MenuRow] in
 * groups, and key hints. Text is built in [update]; the dim over the board and the panel are drawn into the
 * scoreboard's cached layer by [draw], while [drawLive] draws the Game clock value, which changes with the running
 * clock. The lit clock digits are drawn on every frame too, in a color already [dimmed] (a blended rectangle over
 * the board on every frame cost the stick's GPU too much). Design pixels.
 */
class MenuOverlay(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private var state: MenuState? = null
    private var running: Boolean? = null
    private var left = -1
    private var right = -1
    private var gameLengthMs = -1L

    private val text = context.getColor(R.color.text)
    private val ink = context.getColor(R.color.board_background)
    private val soft = context.getColor(R.color.text_soft)
    private val muted = context.getColor(R.color.text_muted)

    private val scrim = context.getColor(R.color.menu_scrim)
    private val scrimPaint = fillPaint(scrim)
    private val panelPaint = fillPaint(context.getColor(R.color.panel))
    private val linePaint = fillPaint(context.getColor(R.color.panel_line))
    private val groupLinePaint = fillPaint(context.getColor(R.color.menu_group_line))
    private val focusPaint = fillPaint(text)

    private val title = context.getString(R.string.menu_title)
    private val titlePaint = textPaint(fonts.barlow(700), TITLE_SIZE, text)
    private var chip = ""
    private val chipPaint = textPaint(fonts.barlow(600), CHIP_SIZE, muted, 0.12f, Paint.Align.RIGHT)

    private val labelPaint = textPaint(fonts.barlow(600), ROW_SIZE, text)
    private val valuePaint = textPaint(fonts.barlow(600), ROW_SIZE, soft, align = Paint.Align.CENTER).apply {
        fontFeatureSettings = "tnum"
    }
    private val rowBaseline = ROW_HEIGHT / 2 + labelPaint.centerToBaselinePerPx("H") * ROW_SIZE
    private val chevronPaint = strokePaint(soft, 3f * CHEVRON_UNIT).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val chevronLeft = chevron(14f, 7f)
    private val chevronRight = chevron(8f, 15f)

    private val rows = MenuRow.entries.map { Row(it) }
    private val groupLines = mutableListOf<Float>()

    private val hintPaint = textPaint(fonts.barlow(600), HINT_SIZE, muted, 0.06f)
    private val keycapPaint = keycapPaint(muted)
    private val hintBaseline = FOOTER_CENTER_Y + hintPaint.centerToBaselinePerPx("H") * HINT_SIZE
    private val hints = listOf(
        KeyHint(context.getString(R.string.key_up_down), context.getString(R.string.hint_move)),
        KeyHint(context.getString(R.string.key_left_right), context.getString(R.string.hint_change)),
        KeyHint(context.getString(R.string.key_ok), context.getString(R.string.hint_open)),
        KeyHint(context.getString(R.string.key_back), context.getString(R.string.hint_close)),
    )

    init {
        var top = FIRST_ROW_TOP
        rows.forEachIndexed { i, row ->
            if (i > 0 && row.row.group != rows[i - 1].row.group) {
                groupLines += top + GROUP_PADDING
                top += GROUP_PADDING + GROUP_LINE + GROUP_GAP
            }
            row.top = top
            top += ROW_HEIGHT
        }
        // Space between, like the design's flex row.
        val widths = hints.map { it.width(hintPaint) }
        val gap = (TEXT_RIGHT - TEXT_LEFT - widths.sum()) / (hints.size - 1)
        var right = TEXT_RIGHT
        for (i in hints.indices.reversed()) {
            hints[i].layoutEndingAt(right, FOOTER_CENTER_Y, hintPaint)
            right -= widths[i] + gap
        }
    }

    /** Takes the latest state ([state] null = closed); returns true when what the panel shows changed. */
    fun update(state: MenuState?, s: GameSnapshot): Boolean {
        val gameLength = state?.pendingGameLengthMs ?: s.rules.gameLengthMs
        val running = s.gameClock.running
        if (state == this.state && running == this.running && s.left.score == left && s.right.score == right &&
            gameLength == gameLengthMs
        ) {
            return false
        }
        this.state = state
        this.running = running
        left = s.left.score
        right = s.right.score
        gameLengthMs = gameLength
        visible = state != null && state.setClock == null
        if (state != null) layout(state)
        return true
    }

    /** [color] as it looks under the dim over the board. */
    fun dimmed(color: Int): Int {
        val a = Color.alpha(scrim) / 255f
        fun mix(c: Int, s: Int) = Math.round(c * (1 - a) + s * a)
        return Color.rgb(
            mix(Color.red(color), Color.red(scrim)),
            mix(Color.green(color), Color.green(scrim)),
            mix(Color.blue(color), Color.blue(scrim)),
        )
    }

    /** The dim over the board and the panel; drawn into the cached layer. */
    fun draw(canvas: Canvas) {
        if (!visible) return
        canvas.drawRect(0f, 0f, MENU_LEFT, DESIGN_HEIGHT, scrimPaint)
        canvas.drawRect(MENU_LEFT, 0f, PANEL_LEFT, DESIGN_HEIGHT, linePaint)
        canvas.drawRect(PANEL_LEFT, 0f, DESIGN_WIDTH, DESIGN_HEIGHT, panelPaint)
        canvas.drawText(title, TEXT_LEFT, TITLE_BASELINE, titlePaint)
        canvas.drawText(chip, TEXT_RIGHT, TITLE_BASELINE, chipPaint)
        for (y in groupLines) canvas.drawRect(PANEL_LEFT, y, DESIGN_WIDTH, y + GROUP_LINE, groupLinePaint)
        for (row in rows) row.draw(canvas)
        canvas.drawRect(PANEL_LEFT, FOOTER_TOP, DESIGN_WIDTH, FOOTER_TOP + BORDER, linePaint)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    /** Drawn on every frame: the Game clock row's value, [clock] as the scoreboard shows it. */
    fun drawLive(canvas: Canvas, clock: String) {
        if (!visible) return
        val row = rows[MenuRow.GAME_CLOCK.ordinal]
        row.styleValue()
        canvas.drawText(clock, row.valueCenter, row.top + rowBaseline, valuePaint)
    }

    private fun layout(state: MenuState) {
        chip = context.getString(if (running == true) R.string.status_clock_running else R.string.status_clock_stopped)
        for (row in rows) row.layout(state)
    }

    /** A chevron from (x1, 4) through (x2, 11) to (x1, 18) in the 22-unit box, scaled. */
    private fun chevron(x1: Float, x2: Float) = Path().apply {
        moveTo(x1 * CHEVRON_UNIT, 4f * CHEVRON_UNIT)
        lineTo(x2 * CHEVRON_UNIT, 11f * CHEVRON_UNIT)
        lineTo(x1 * CHEVRON_UNIT, 18f * CHEVRON_UNIT)
    }

    private inner class Row(val row: MenuRow) {
        var top = 0f
        var valueCenter = VALUE_RIGHT - VALUE_MIN_WIDTH / 2
            private set

        private val label = when (row) {
            MenuRow.GAME_CLOCK -> context.getString(R.string.menu_game_clock)
            MenuRow.GAME_LENGTH -> context.getString(R.string.menu_game_length)
            MenuRow.HOME_SCORE -> context.getString(R.string.menu_score, context.getString(Side.LEFT.teamNameRes()))
            MenuRow.AWAY_SCORE -> context.getString(R.string.menu_score, context.getString(Side.RIGHT.teamNameRes()))
            MenuRow.NEW_GAME -> context.getString(R.string.menu_new_game)
            MenuRow.LANGUAGE -> context.getString(R.string.menu_language)
        }

        /** Empty for rows without a value, and for Game clock, whose value [drawLive] draws. */
        private var value = ""
        private var focused = false
        private var enabled = true
        private var hasLeftChevron = false
        private var hasRightChevron = false
        private var leftChevronX = 0f

        fun layout(state: MenuState) {
            focused = state.focused == row
            enabled = row != MenuRow.GAME_CLOCK || running != true // Set only while the clock is stopped.
            value = when (row) {
                MenuRow.GAME_LENGTH -> durationText(gameLengthMs)
                MenuRow.HOME_SCORE -> left.toString()
                MenuRow.AWAY_SCORE -> right.toString()
                MenuRow.LANGUAGE -> state.language.nativeName()
                MenuRow.GAME_CLOCK, MenuRow.NEW_GAME -> ""
            }
            hasLeftChevron = row in CHANGEABLE
            hasRightChevron = enabled
            val width = maxOf(VALUE_MIN_WIDTH, valuePaint.measureText(value))
            valueCenter = VALUE_RIGHT - width / 2
            leftChevronX = VALUE_RIGHT - width - CHEVRON_GAP - CHEVRON_SIZE
        }

        /** Sets the shared paints to this row's colors. */
        fun styleValue() {
            valuePaint.color = if (focused) ink else soft
        }

        fun draw(canvas: Canvas) {
            if (focused) canvas.drawRect(PANEL_LEFT, top, DESIGN_WIDTH, top + ROW_HEIGHT, focusPaint)
            labelPaint.color = when {
                focused -> ink
                enabled -> text
                else -> muted
            }
            canvas.drawText(label, TEXT_LEFT, top + rowBaseline, labelPaint)
            styleValue()
            if (value.isNotEmpty()) canvas.drawText(value, valueCenter, top + rowBaseline, valuePaint)
            chevronPaint.color = valuePaint.color
            val chevronTop = top + (ROW_HEIGHT - CHEVRON_SIZE) / 2
            if (hasLeftChevron) drawChevron(canvas, chevronLeft, leftChevronX, chevronTop)
            if (hasRightChevron) drawChevron(canvas, chevronRight, TEXT_RIGHT - CHEVRON_SIZE, chevronTop)
        }

        private fun drawChevron(canvas: Canvas, path: Path, x: Float, y: Float) {
            val saved = canvas.save()
            canvas.translate(x, y)
            canvas.drawPath(path, chevronPaint)
            canvas.restoreToCount(saved)
        }
    }

    private companion object {
        /** Rows whose value ←/→ change; they show both chevrons. */
        val CHANGEABLE = setOf(MenuRow.GAME_LENGTH, MenuRow.HOME_SCORE, MenuRow.AWAY_SCORE, MenuRow.LANGUAGE)
    }
}
