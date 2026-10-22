package net.pladum.tablero.tv

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * The design's reference canvas (docs/design/boards.html). Everything on the scoreboard is laid out in these
 * "design pixels"; [ScoreboardView] scales them to the screen (the stick renders at 1280×720).
 */
const val DESIGN_WIDTH = 1920f
const val DESIGN_HEIGHT = 1080f

private const val LED_FONT = "fonts/DSEG7Classic-Bold.ttf"

/** Glyph bounds come in whole pixels, so the LED digits' center is measured at a size larger than any on screen. */
private const val LED_MEASURE_SIZE = 1000f
private val BARLOW_FONTS = mapOf(
    500 to "fonts/BarlowCondensed-Medium.ttf",
    600 to "fonts/BarlowCondensed-SemiBold.ttf",
    700 to "fonts/BarlowCondensed-Bold.ttf",
    800 to "fonts/BarlowCondensed-ExtraBold.ttf",
)

/**
 * The bundled fonts, unmodified TTFs in `assets/fonts/` (licenses in `assets/licenses/`): DSEG7 Classic Bold for
 * digits, Barlow Condensed 1.422 (weights 500–800) for text. They are assets rather than font resources because
 * `Resources.getFont` and weighted `Typeface.create` need API 26 and 28; one file per weight works on every API.
 */
class BoardFonts(context: Context) {
    val led: Typeface = Typeface.createFromAsset(context.assets, LED_FONT)
    private val barlow = BARLOW_FONTS.mapValues { (_, path) -> Typeface.createFromAsset(context.assets, path) }

    /** From an LED digit's visual center down to its baseline, per pixel of text size (see [centerToBaselinePerPx]). */
    val ledCenterPerPx: Float = textPaint(led, LED_MEASURE_SIZE, 0).centerToBaselinePerPx("8")

    /** Barlow Condensed in [weight] 500, 600, 700 or 800. */
    fun barlow(weight: Int): Typeface = barlow.getValue(weight)
}

fun textPaint(
    typeface: Typeface,
    size: Float,
    color: Int,
    letterSpacingEm: Float = 0f,
    align: Paint.Align = Paint.Align.LEFT,
) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.typeface = typeface
    textSize = size
    this.color = color
    letterSpacing = letterSpacingEm
    textAlign = align
}

fun fillPaint(color: Int = 0) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    style = Paint.Style.STROKE
    strokeWidth = width
}

/** A [strokePaint] with round caps and joins, for line icons (chevrons, arrows). */
fun roundStrokePaint(color: Int, width: Float) = strokePaint(color, width).apply {
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/**
 * Text is placed by its visual center line, like the design's flex centering. This is the distance from that
 * center down to the baseline, per pixel of text size, measured on [sample]: capitals for text, an 8 for digits.
 */
fun Paint.centerToBaselinePerPx(sample: String): Float {
    val bounds = Rect()
    getTextBounds(sample, 0, sample.length, bounds)
    return -(bounds.top + bounds.bottom) / 2f / textSize
}

/** Sets [Paint.textSize] to [size], or smaller so that [text] fits [maxWidth]. Returns the text's width. */
fun Paint.fitText(text: String, size: Float, maxWidth: Float): Float {
    textSize = size
    val width = measureText(text)
    if (width <= maxWidth) return width
    textSize = size * maxWidth / width
    return measureText(text)
}
