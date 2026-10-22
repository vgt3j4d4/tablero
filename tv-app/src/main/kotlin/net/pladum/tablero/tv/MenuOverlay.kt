package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import net.pladum.tablero.shared.GameRules
import net.pladum.tablero.shared.GameSnapshot
import net.pladum.tablero.shared.RulesPreset
import net.pladum.tablero.shared.Side
import net.pladum.tablero.shared.TeamState

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

/** Where the rows are cut when they outgrow the panel (design 32), and how far they fade out there. */
private const val LIST_TOP = FIRST_ROW_TOP
private const val LIST_BOTTOM = FOOTER_TOP
private const val FADE_BOTTOM = 120f
private const val FADE_TOP = 60f
private const val FOOTER_CENTER_Y = 1041f
private const val HINT_SIZE = 24f

/** The chevrons are drawn in a 22-unit box (boards.html CHEVRON_L / CHEVRON_R) scaled to [CHEVRON_SIZE]. */
private const val CHEVRON_UNIT = CHEVRON_SIZE / 22f

/**
 * The menu (design 06, 32): a panel on the right over the dimmed board, with the clock state, the rows of [MenuRow]
 * the rules show in groups, and key hints. Text is built in [update]; the dim over the board and the panel are drawn
 * into the scoreboard's cached layer by [draw], while [drawLive] draws the Game clock value, which changes with the
 * running clock. The lit clock digits are drawn on every frame too, in a color already [dimmed] (a blended rectangle
 * over the board on every frame cost the stick's GPU too much). Design pixels.
 *
 * Under period rules the rows outgrow the panel: the list scrolls so that the focused row and its neighbours show,
 * and fades out where it is cut.
 */
class MenuOverlay(private val context: Context, fonts: BoardFonts) {
    var visible = false
        private set

    private var state: MenuState? = null
    private var running: Boolean? = null
    private var values: Values? = null

    private val text = context.color(R.color.text)
    private val ink = context.color(R.color.board_background)
    private val soft = context.color(R.color.text_soft)
    private val muted = context.color(R.color.text_muted)

    private val scrim = context.color(R.color.menu_scrim)
    private val scrimPaint = fillPaint(scrim)
    private val panelPaint = fillPaint(context.color(R.color.panel))
    private val linePaint = fillPaint(context.color(R.color.panel_line))
    private val groupLinePaint = fillPaint(context.color(R.color.menu_group_line))
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
    private val chevronPaint = roundStrokePaint(soft, 3f * CHEVRON_UNIT)
    private val chevronLeft = chevron(14f, 7f)
    private val chevronRight = chevron(8f, 15f)

    private val rows = MenuRow.entries.map { Row(it) }
    private val groupLines = mutableListOf<Float>()

    /** How far the rows are scrolled up, and where the last one ends before scrolling. */
    private var scroll = 0f
    private var rowsBottom = 0f
    private val panel = context.color(R.color.panel)
    private val fadeTopPaint = fadePaint(LIST_TOP, LIST_TOP + FADE_TOP, panel, transparent(panel))
    private val fadeBottomPaint = fadePaint(LIST_BOTTOM - FADE_BOTTOM, LIST_BOTTOM, transparent(panel), panel)

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
        val values = Values(
            rules = s.rules,
            gameLengthMs = state?.pendingGameLengthMs ?: s.rules.gameLengthMs,
            left = s.left,
            right = s.right,
            period = s.period,
            overtime = s.overtime,
        )
        val running = s.gameClock.running
        if (state == this.state && running == this.running && values == this.values) return false
        this.state = state
        this.running = running
        this.values = values
        visible = state?.showsRows == true
        if (state != null) layout(state, values)
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
        val saved = canvas.save()
        canvas.clipRect(PANEL_LEFT, LIST_TOP, DESIGN_WIDTH, LIST_BOTTOM)
        canvas.translate(0f, -scroll)
        for (y in groupLines) canvas.drawRect(PANEL_LEFT, y, DESIGN_WIDTH, y + GROUP_LINE, groupLinePaint)
        for (row in rows) row.draw(canvas)
        canvas.restoreToCount(saved)
        if (scroll > 0) canvas.drawRect(PANEL_LEFT, LIST_TOP, DESIGN_WIDTH, LIST_TOP + FADE_TOP, fadeTopPaint)
        if (rowsBottom - scroll > LIST_BOTTOM) {
            canvas.drawRect(PANEL_LEFT, LIST_BOTTOM - FADE_BOTTOM, DESIGN_WIDTH, LIST_BOTTOM, fadeBottomPaint)
        }
        canvas.drawRect(PANEL_LEFT, FOOTER_TOP, DESIGN_WIDTH, FOOTER_TOP + BORDER, linePaint)
        for (hint in hints) hint.draw(canvas, hintBaseline, hintPaint, keycapPaint)
    }

    /** Drawn on every frame: the Game clock row's value, [clock] as the scoreboard shows it, unless scrolled away. */
    fun drawLive(canvas: Canvas, clock: String) {
        if (!visible) return
        val row = rows[MenuRow.GAME_CLOCK.ordinal]
        val top = row.top - scroll
        if (top < LIST_TOP) return
        row.styleValue()
        canvas.drawText(clock, row.valueCenter, top + rowBaseline, valuePaint)
    }

    private fun layout(state: MenuState, values: Values) {
        chip = context.getString(if (running == true) R.string.status_clock_running else R.string.status_clock_stopped)
        val shown = MenuRow.shownUnder(values.rules)
        groupLines.clear()
        var top = FIRST_ROW_TOP
        var previous: MenuRow? = null
        for (row in rows) {
            row.shown = row.row in shown
            if (!row.shown) continue
            if (previous != null && row.row.group != previous.group) {
                groupLines += top + GROUP_PADDING
                top += GROUP_PADDING + GROUP_LINE + GROUP_GAP
            }
            row.top = top
            row.layout(state, values)
            top += ROW_HEIGHT
            previous = row.row
        }
        rowsBottom = top
        scrollTo(shown, state.focused)
    }

    /** Scrolls as little as needed for the focused row and the rows next to it to show. */
    private fun scrollTo(shown: List<MenuRow>, focused: MenuRow) {
        val index = shown.indexOf(focused)
        val above = rows[shown[maxOf(index - 1, 0)].ordinal].top
        val below = rows[shown[minOf(index + 1, shown.lastIndex)].ordinal].top + ROW_HEIGHT
        if (above - scroll < LIST_TOP) scroll = above - LIST_TOP
        if (below - scroll > LIST_BOTTOM) scroll = below - LIST_BOTTOM
        scroll = scroll.coerceIn(0f, maxOf(0f, rowsBottom - LIST_BOTTOM))
    }

    /** Opaque, since a shader's colors are drawn through the paint's alpha. */
    private fun fadePaint(top: Float, bottom: Float, from: Int, to: Int) = fillPaint(Color.BLACK).apply {
        shader = LinearGradient(0f, top, 0f, bottom, from, to, Shader.TileMode.CLAMP)
    }

    /** A chevron from (x1, 4) through (x2, 11) to (x1, 18) in the 22-unit box, scaled. */
    private fun chevron(x1: Float, x2: Float) = Path().apply {
        moveTo(x1 * CHEVRON_UNIT, 4f * CHEVRON_UNIT)
        lineTo(x2 * CHEVRON_UNIT, 11f * CHEVRON_UNIT)
        lineTo(x1 * CHEVRON_UNIT, 18f * CHEVRON_UNIT)
    }

    private inner class Row(val row: MenuRow) {
        /** The rules show this row; [draw] skips it otherwise. */
        var shown = false
        var top = 0f
        var valueCenter = VALUE_RIGHT - VALUE_MIN_WIDTH / 2
            private set

        private var label = ""
        private val fixedLabel = when (row) {
            MenuRow.GAME_CLOCK -> context.getString(R.string.menu_game_clock)
            MenuRow.PERIOD -> context.getString(R.string.menu_period)
            MenuRow.GAME_LENGTH -> context.getString(R.string.menu_game_length)
            MenuRow.HOME_SCORE -> context.getString(R.string.menu_score, context.getString(Side.LEFT.teamNameRes()))
            MenuRow.AWAY_SCORE -> context.getString(R.string.menu_score, context.getString(Side.RIGHT.teamNameRes()))
            MenuRow.HOME_FOULS -> context.getString(R.string.menu_fouls, context.getString(Side.LEFT.teamNameRes()))
            MenuRow.AWAY_FOULS -> context.getString(R.string.menu_fouls, context.getString(Side.RIGHT.teamNameRes()))
            MenuRow.NEW_GAME -> context.getString(R.string.menu_new_game)
            MenuRow.RULES -> context.getString(R.string.menu_rules)
            MenuRow.LAYOUT -> context.getString(R.string.menu_layout)
            MenuRow.MODE -> context.getString(R.string.menu_mode)
            MenuRow.LANGUAGE -> context.getString(R.string.menu_language)
            MenuRow.HELP -> context.getString(R.string.menu_help)
            MenuRow.EXIT -> context.getString(R.string.menu_exit)
        }

        /** Empty for rows without a value, and for Game clock, whose value [drawLive] draws. */
        private var value = ""
        private var focused = false
        private var enabled = true
        private var hasLeftChevron = false
        private var hasRightChevron = false
        private var leftChevronX = 0f

        fun layout(state: MenuState, values: Values) {
            focused = state.focused == row
            enabled = !row.stoppedOnly || running != true
            label = if (row == MenuRow.GAME_LENGTH && values.rules.periods != null) {
                context.getString(R.string.menu_period_length)
            } else {
                fixedLabel
            }
            value = when (row) {
                MenuRow.PERIOD -> if (values.overtime > 0) {
                    context.getString(R.string.menu_period_overtime, values.overtime)
                } else {
                    values.period.toString()
                }
                MenuRow.GAME_LENGTH -> durationText(values.gameLengthMs)
                MenuRow.HOME_SCORE -> values.left.score.toString()
                MenuRow.AWAY_SCORE -> values.right.score.toString()
                MenuRow.HOME_FOULS -> values.left.fouls.toString()
                MenuRow.AWAY_FOULS -> values.right.fouls.toString()
                MenuRow.RULES -> {
                    val rules = state.pendingRules ?: RulesPreset.of(values.rules) ?: RulesPreset.PICKUP
                    context.getString(rules.labelRes())
                }
                MenuRow.LAYOUT -> context.getString(state.layout.labelRes())
                MenuRow.MODE -> context.getString(state.mode.labelRes())
                MenuRow.LANGUAGE -> state.language.nativeName()
                MenuRow.GAME_CLOCK, MenuRow.NEW_GAME, MenuRow.HELP, MenuRow.EXIT -> ""
            }
            hasLeftChevron = row in CHANGEABLE && enabled
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
            if (!shown) return
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

    /** The snapshot's values the rows show, with a pending game length. */
    private data class Values(
        val rules: GameRules,
        val gameLengthMs: Long,
        val left: TeamState,
        val right: TeamState,
        val period: Int,
        val overtime: Int,
    )

    private companion object {
        /** Rows whose value ←/→ change; they show both chevrons. */
        val CHANGEABLE = setOf(
            MenuRow.PERIOD,
            MenuRow.GAME_LENGTH,
            MenuRow.HOME_SCORE,
            MenuRow.AWAY_SCORE,
            MenuRow.HOME_FOULS,
            MenuRow.AWAY_FOULS,
            MenuRow.RULES,
            MenuRow.LAYOUT,
            MenuRow.MODE,
            MenuRow.LANGUAGE,
        )
    }
}

/** [color] fully transparent, for a fade into it. */
private fun transparent(color: Int): Int = color and 0x00FFFFFF

/** The string resource naming the rules in the menu: "Pickup" / "Recreativo", "FIBA". */
private fun RulesPreset.labelRes(): Int = when (this) {
    RulesPreset.PICKUP -> R.string.rules_pickup
    RulesPreset.FIBA -> R.string.rules_fiba
}
