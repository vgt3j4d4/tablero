package net.pladum.tablero.tv

import android.graphics.Canvas

// Design pixels, from docs/design/boards.html (.bar.tall); the bar's place is in BoardPaint.kt.
private const val TITLE_SIZE = 48f
private const val DETAIL_SIZE = 30f
private const val TEXT_GAP = 20f

/** Minimum space between the title group and what the bar shows on its right. */
private const val GROUP_GAP = 24f

/**
 * A tall bar's title and its detail on one baseline (`TIME UP  Home wins 62–58`, `HALFTIME  Period 3 starts at
 * 0:00`); both shrink together if a translation would reach what the bar shows on its right. Laid out by [layout];
 * [draw] only draws. Design pixels.
 */
class BarTitle(fonts: BoardFonts, titleColor: Int, detailColor: Int) {
    private val titlePaint = textPaint(fonts.barlow(800), TITLE_SIZE, titleColor, 0.06f)
    private val detailPaint = textPaint(fonts.barlow(600), DETAIL_SIZE, detailColor)
    private var title = ""
    private var detail = ""
    private var detailX = 0f
    private var baseline = 0f

    /** Shows [title] and [detail], leaving room for what starts at [right] (the bar's buttons or key hints). */
    fun layout(title: String, detail: String, right: Float) {
        this.title = title
        this.detail = detail
        titlePaint.textSize = TITLE_SIZE
        detailPaint.textSize = DETAIL_SIZE
        val width = titlePaint.measureText(title) + TEXT_GAP + detailPaint.measureText(detail)
        val available = right - GROUP_GAP - BAR_PADDING
        if (width > available) {
            titlePaint.textSize = TITLE_SIZE * available / width
            detailPaint.textSize = DETAIL_SIZE * available / width
        }
        detailX = BAR_PADDING + titlePaint.measureText(title) + TEXT_GAP * titlePaint.textSize / TITLE_SIZE
        baseline = titlePaint.baselineAt(TALL_BAR_CENTER_Y)
    }

    fun draw(canvas: Canvas) {
        canvas.drawText(title, BAR_PADDING, baseline, titlePaint)
        canvas.drawText(detail, detailX, baseline, detailPaint)
    }
}
