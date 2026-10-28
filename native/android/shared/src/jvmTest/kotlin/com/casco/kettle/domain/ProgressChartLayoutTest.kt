package com.casco.kettle.domain

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days

/**
 * RN's `progress-chart-layout.test.ts`, case for case, deleted with its layout once this port landed
 * (#200): where the progress chart puts its dots and labels, as arithmetic. How it looks is checked
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

    @Nested
    inner class LabelsStayInsideTheDrawing {
        /** Trained twice early in the window: the latest point sits a few pixels from the left edge. */
        @Test
        fun `keeps the latest label inside the left edge`() {
            val last = layout(session(1, 10.0), session(2, 12.0, newBest = true), last = 50f).lastLabel

            assertTrue(last!!.x >= 0f)
        }

        @Test
        fun `keeps the first label inside the right edge`() {
            // Far apart in value, so the labels don't collide and the first is drawn at all.
            val first = layout(session(54, 1.0), session(55, 10.0, newBest = true)).firstLabel

            assertTrue(first!!.x + 40f <= WIDTH)
        }
    }

    @Nested
    inner class LabelsNeverPrintOverEachOther {
        /** 137.5 → 142.5 lb two weeks apart: the labels need about 88px, at almost the same height. */
        @Test
        fun `drops the first label when two close values would collide`() {
            val result = layout(session(0, 137.5), session(14, 142.5, newBest = true), first = 44f, last = 44f)

            assertNull(result.firstLabel)
            assertNotNull(result.lastLabel)
        }

        @Test
        fun `keeps both when the values sit far apart vertically`() {
            assertNotNull(layout(session(0, 5.0), session(7, 20.0, newBest = true)).firstLabel)
        }

        @Test
        fun `keeps both when the ends are far apart`() {
            val result = layout(session(0, 137.5), session(50, 142.5, newBest = true), first = 44f, last = 44f)

            assertNotNull(result.firstLabel)
        }

        @Test
        fun `places no label before it has been measured`() {
            val result = layout(session(0, 10.0), session(40, 12.0, newBest = true), first = 0f, last = 0f)

            assertNull(result.firstLabel)
            assertNull(result.lastLabel)
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

private fun layout(
    vararg sessions: ProgressSession,
    first: Float = 40f,
    last: Float = 40f,
) = progressChartLayout(sessions.toList(), FROM..TO, WIDTH, first, last)
