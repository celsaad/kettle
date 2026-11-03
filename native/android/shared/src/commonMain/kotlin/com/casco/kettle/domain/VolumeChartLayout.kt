package com.casco.kettle.domain

// Where the exercise editor's Recent bars go (#241), as plain numbers, ported from RN's
// `volume-chart.tsx`. Split from the drawing so a test reaches the arithmetic without a Canvas, as
// `ProgressChartLayout.kt` is.

/** The Recent chart's height, value labels and dates included. */
const val VOLUME_CHART_HEIGHT = 96f

/** Room under the bars for the dates. */
const val VOLUME_DATE_HEIGHT = 18f

private const val BAR_AREA_HEIGHT = 56f
private const val MIN_BAR_WIDTH = 16f
private const val MAX_BAR_WIDTH = 40f
private const val BAR_GAP = 8f

/** A zero still draws, so a session that moved nothing reads as one that happened. */
private const val MIN_BAR_HEIGHT = 2f

/** One bar, in the chart's own units (dp on Android): its centre, top edge, width and height. */
data class VolumeBar(
    val centerX: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * Where each of [values]' bars goes across [width], in the order given: an equal share each, from zero
 * so a small change isn't drawn as a cliff, and standing on the line above the dates.
 */
fun volumeChartLayout(
    values: List<Double>,
    width: Float,
): List<VolumeBar> {
    if (values.isEmpty()) return emptyList()
    val max = maxOf(1.0, values.max())
    val step = width / values.size
    val barWidth = (step - BAR_GAP).coerceIn(MIN_BAR_WIDTH, MAX_BAR_WIDTH)
    val bottom = VOLUME_CHART_HEIGHT - VOLUME_DATE_HEIGHT
    return values.mapIndexed { index, value ->
        val height = maxOf(MIN_BAR_HEIGHT, (value / max).toFloat() * BAR_AREA_HEIGHT)
        VolumeBar(index * step + step / 2, bottom - height, barWidth, height)
    }
}
