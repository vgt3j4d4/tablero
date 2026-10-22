package net.pladum.tablero.tv

private const val PREF_LAYOUT = "layout"

/** A rectangle in design pixels; plain floats so layouts stay usable in JVM unit tests. */
data class DesignBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * Where the scoreboard's parts sit, in design pixels (docs/design/boards.html): Classic is design 01–10, 20 and 24,
 * Large is design 11–13, 21 and 25. The menu's Layout row chooses one; [ScoreboardView] and [ClockAdjustOverlay] read these values
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
    /**
     * Under period rules, each team's fouls in the corner beside the clock: the home corner's center (the away one
     * mirrors it), its `FOULS` label and its digit, centered one above the other.
     */
    val foulsCenterX: Float,
    val foulsLabelCenterY: Float,
    val foulsLabelSize: Float,
    val foulsCenterY: Float,
    val foulsSize: Float,
    /** Under period rules, the period digit between the scores, and the possession arrows on either side of it. */
    val periodSize: Float,
    val periodArrowHeight: Float,
    val periodArrowGap: Float,
    /** The team foul bar's ring around the home team's fouls, outer edge; the away one mirrors it. */
    val foulRing: DesignBox,
) {
    /**
     * The clock in a box; team columns around a 260 px gap. The team block (84 px name row, 28 px gap, 330 px score)
     * is centered between the clock row (476) and the status bar (1016). The fouls (56 px label, 24 px gap, 250 px
     * digit) are centered in the 268 × 440 corner left of the clock box.
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
        foulsCenterX = 170f,
        foulsLabelCenterY = 119f,
        foulsLabelSize = 56f,
        foulsCenterY = 296f,
        foulsSize = 250f,
        periodSize = 250f,
        periodArrowHeight = 120f,
        periodArrowGap = 28f,
        foulRing = DesignBox(22f, 22f, 318f, 490f),
    ),

    /**
     * Digits as large as the screen allows, at least 36 px from every edge: no clock box, two equal team columns.
     * The team block (56 px name row, 14 px gap, 410 px score) is centered between the clock row (446) and the
     * status bar (1016). The fouls (48 px label, 24 px gap, 230 px digit) are centered in the 218 × 410 corner left
     * of the clock; the period and its arrows shrink to clear the bigger scores.
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
        foulsCenterX = 145f,
        foulsLabelCenterY = 114f,
        foulsLabelSize = 48f,
        foulsCenterY = 277f,
        foulsSize = 230f,
        periodSize = 180f,
        periodArrowHeight = 84f,
        periodArrowGap = 14f,
        foulRing = DesignBox(22f, 22f, 268f, 460f), // 14 px around the corner, as in Classic.
    ),
}

/** The string resource naming the layout in the menu: "Classic" / "Clásico", "Large" / "Grande". */
fun BoardLayout.labelRes(): Int = when (this) {
    BoardLayout.CLASSIC -> R.string.layout_classic
    BoardLayout.LARGE -> R.string.layout_large
}

/** The layout chosen in the menu; [BoardLayout.CLASSIC] until one is chosen. */
object LayoutSetting : EnumSetting<BoardLayout>(PREF_LAYOUT, BoardLayout.entries, BoardLayout.CLASSIC)
