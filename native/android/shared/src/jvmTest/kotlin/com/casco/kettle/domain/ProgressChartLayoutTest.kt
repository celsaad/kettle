package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * RN's `progress-chart-layout.test.ts`, ported (#200), its label cases since replaced by the y axis
 * (#413): where the progress chart puts its dots and lines, as arithmetic. How it looks is checked
 * in the running app.
 */
class ProgressChartLayoutTest {
    @Nested
    inner class Dots {
        /** A latest session that fell back once got the filled dot the key calls "New best". */
        @Test
        fun `marks a latest session that fell back as the latest, not as a new best`() {
            val marks =
                layout(
                    session(0, 10.0),
                    session(20, 12.0, newBest = true),
                    session(40, 11.0),
                ).points.map { it.mark }

            assertEquals(listOf(null, ChartMark.NewBest, ChartMark.Latest), marks)
        }

        @Test
        fun `marks a latest session that did set a new best as a new best`() {
            val marks = layout(session(0, 10.0), session(40, 12.0, newBest = true)).points.map { it.mark }

            assertEquals(listOf(null, ChartMark.NewBest), marks)
        }
    }

    /** The y axis (#413): round steps from zero, the line scaled to the top one, in the reader's units. */
    @Nested
    inner class Axis {
        @Test
        fun `steps in ones, twos or fives, from zero to a round top over the highest value`() {
            val ticks = layout(session(0, 30.0), session(40, 48.5, newBest = true)).ticks.map { it.value }

            assertEquals(listOf(0.0, 20.0, 40.0, 60.0), ticks)
        }

        @Test
        fun `lands on the highest value when it is round already`() {
            assertEquals(listOf(0.0, 5.0, 10.0), layout(session(0, 6.0), session(9, 10.0)).ticks.map { it.value })
        }

        /** The top tick is the plot's top, and the zero tick is the baseline the line rises from. */
        @Test
        fun `puts the zero tick on the baseline and the top one where the plot ends`() {
            val result = layout(session(0, 30.0), session(40, 48.5, newBest = true))

            assertEquals(result.baseline, result.ticks.first().y)
            assertTrue(result.ticks.last().y < result.points.minOf { it.y })
            assertEquals(20f, result.ticks.last().y)
        }

        /** 22 kg is 48.5 lb: a pounds reader's axis is round in pounds, not in kilograms converted. */
        @Test
        fun `is round in the reader's units`() {
            val result =
                progressChartLayout(
                    listOf(session(0, 13.6), session(40, 22.0)),
                    FROM..TO,
                    WIDTH,
                    display = { it * 2.20462 },
                )

            assertEquals(listOf(0.0, 20.0, 40.0, 60.0), result.ticks.map { it.value })
        }

        @Test
        fun `has no axis when every value is zero, with nothing to scale by`() {
            val result = layout(session(0, 0.0), session(7, 0.0))

            assertEquals(emptyList<ChartTick>(), result.ticks)
            assertTrue(result.points.all { it.y == result.baseline })
        }

        /** A best of one rep: steps of a third would round to half a rep, which nobody did. */
        @Test
        fun `steps no finer than the measure's own least step`() {
            val result = progressChartLayout(listOf(session(0, 1.0), session(7, 1.0)), FROM..TO, WIDTH, minStep = 1.0)

            assertEquals(listOf(0.0, 1.0), result.ticks.map { it.value })
        }

        /** 3 × 0.2 is 0.6000000000000001 in binary, which a label would print whole. */
        @Test
        fun `puts each tick on its round value, not the step's binary remainder`() {
            assertEquals(listOf(0.0, 0.2, 0.4, 0.6), layout(session(0, 0.5), session(7, 0.6)).ticks.map { it.value })
        }

        @Test
        fun `rounds a small step to a power of ten's one, two or five`() {
            assertEquals(
                listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0),
                listOf(0.07, 0.15, 0.4, 0.9, 1.2, 3.0, 6.0).map(::niceStep),
            )
        }
    }
}

// An eight-week window, and a drawing about as wide as the chart card on a phone.
private val FROM = Instant.parse("2026-07-17T00:00:00.000Z")
private val TO = Instant.parse("2026-09-11T00:00:00.000Z")
private const val WIDTH = 320f

private fun session(
    day: Int,
    value: Double,
    newBest: Boolean = false,
) = ProgressSession(FROM + day.days, value, newBest)

private fun layout(vararg sessions: ProgressSession) = progressChartLayout(sessions.toList(), FROM..TO, WIDTH)
