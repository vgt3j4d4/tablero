package net.pladum.tablero.tv

import android.view.WindowManager

private const val PREF_MODE = "mode"

/** The clock box's colors, both color resources. */
data class ClockBoxColors(val fill: Int, val border: Int)

/**
 * The board's colors (docs/design/boards.html): Indoor is design 01–13, Outdoor is design 14–19. The menu's Mode row
 * chooses one; [ScoreboardView] and [FeedbackToast] read these values instead of branching on the mode. A null clock
 * box color leaves the box out. The menu, its dialogs and the time-up and adjust bars look the same in both modes.
 */
enum class BoardMode(
    val background: Int,
    /** The clock box's colors where the layout has one, or null for none. */
    val clockBox: ClockBoxColors?,
    val clock: Int,
    val clockUnlit: Int,
    val score: Int,
    val scoreUnlit: Int,
    /** Team names and the status bar's text and icon. */
    val text: Int,
    val statusLine: Int,
    val toastPanel: Int,
    val toastTitle: Int,
    /** The confirmation's detail (`48 → 51`) and Undo hint. */
    val toastDetail: Int,
    /** The window's brightness: [WindowManager.LayoutParams.screenBrightness]. */
    val screenBrightness: Float,
) {
    /** LEDs on a black board. */
    INDOOR(
        background = R.color.board_background,
        clockBox = ClockBoxColors(fill = R.color.clock_box, border = R.color.clock_box_border),
        clock = R.color.led_red,
        clockUnlit = R.color.led_red_unlit,
        score = R.color.led_amber,
        scoreUnlit = R.color.led_amber_unlit,
        text = R.color.text,
        statusLine = R.color.status_line,
        toastPanel = R.color.text,
        toastTitle = R.color.board_background,
        toastDetail = R.color.text_off,
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE,
    ),

    /**
     * For daylight, where reflections wash out a dark board and dim reds: black digits and deep red scores on white,
     * no clock box (its grey would only take contrast), faint unlit segments (without them a lit 1 drifts right and a
     * short reading looks off-centre), a dark confirmation, and the window at full brightness (which only reaches the
     * backlight when tablero runs on the TV itself, not on a stick over HDMI).
     */
    OUTDOOR(
        background = R.color.outdoor_background,
        clockBox = null,
        clock = R.color.outdoor_ink,
        clockUnlit = R.color.outdoor_ink_unlit,
        score = R.color.outdoor_score,
        scoreUnlit = R.color.outdoor_score_unlit,
        text = R.color.outdoor_ink,
        statusLine = R.color.outdoor_status_line,
        toastPanel = R.color.panel,
        toastTitle = R.color.text,
        toastDetail = R.color.text_muted,
        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL,
    ),
}

/** The string resource naming the mode in the menu: "Indoor" / "Interior", "Outdoor" / "Exterior". */
fun BoardMode.labelRes(): Int = when (this) {
    BoardMode.INDOOR -> R.string.mode_indoor
    BoardMode.OUTDOOR -> R.string.mode_outdoor
}

/** The mode chosen in the menu; [BoardMode.INDOOR] until one is chosen. */
object ModeSetting : EnumSetting<BoardMode>(PREF_MODE, BoardMode.entries, BoardMode.INDOOR)
