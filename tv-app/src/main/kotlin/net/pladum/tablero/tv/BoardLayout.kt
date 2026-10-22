package net.pladum.tablero.tv

private const val PREF_LAYOUT = "layout"

/** A rectangle in design pixels; plain floats so layouts stay usable in JVM unit tests. */
data class DesignBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Where the scoreboard's parts sit, in design pixels (docs/design/boards.html): Classic is design 01–10 and 20,
 * Large is design 11–13 and 21. The menu's Layout row chooses one; [ScoreboardView] and [ClockAdjustOverlay] read these values
 * instead of branching on the layout. Names and scores are placed by their visual centers.
 */
enum class BoardLayout(
    /** The box behind the clock, or null for none. */
    val clockBox: DesignBox?,
    val clockCenterY: Float,
    /** Text size of the clock and the scores, which share one LED size. */
    val ledSize: Float,
    val leftTeamX: Float,
    val rightTeamX: Float,
    val nameCenterY: Float,
    val nameSize: Float,
    val scoreCenterY: Float,
    /** The possession arrows' height, and the gap between the two; they sit centered on [scoreCenterY]. */
    val possessionArrowHeight: Float,
    val possessionArrowGap: Float,
    /** Quick clock adjust's ring around the clock, outer edge. */
    val adjustRing: DesignBox,
) {
    /**
     * The clock in a box; team columns around a 260 px gap. The team block (84 px name row, 28 px gap, 330 px score)
     * is centered between the clock row (476) and the status bar (1016).
     */
    CLASSIC(
        clockBox = DesignBox(340f, 36f, 1580f, 476f),
        clockCenterY = 256f,
        ledSize = 330f,
        leftTeamX = 415f,
        rightTeamX = 1505f,
        nameCenterY = 567f,
        nameSize = 84f,
        scoreCenterY = 802f,
        possessionArrowHeight = 120f,
        possessionArrowGap = 28f,
        adjustRing = DesignBox(326f, 22f, 1594f, 490f),
    ),

    /**
     * Digits as large as the screen allows, at least 36 px from every edge: no clock box, two equal team columns.
     * The team block (56 px name row, 14 px gap, 410 px score) is centered between the clock row (446) and the
     * status bar (1016).
     */
    LARGE(
        clockBox = null,
        clockCenterY = 241f,
        ledSize = 410f,
        leftTeamX = 480f,
        rightTeamX = 1440f,
        nameCenterY = 519f,
        nameSize = 56f,
        scoreCenterY = 766f,
        possessionArrowHeight = 100f, // Clears the bigger scores.
        possessionArrowGap = 24f,
        adjustRing = DesignBox(259f, 12f, 1661f, 470f),
    ),
}

/** The string resource naming the layout in the menu: "Classic" / "Clásico", "Large" / "Grande". */
fun BoardLayout.labelRes(): Int = when (this) {
    BoardLayout.CLASSIC -> R.string.layout_classic
    BoardLayout.LARGE -> R.string.layout_large
}

/** The layout chosen in the menu; [BoardLayout.CLASSIC] until one is chosen. */
object LayoutSetting : EnumSetting<BoardLayout>(PREF_LAYOUT, BoardLayout.entries, BoardLayout.CLASSIC)
