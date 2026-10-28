package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Where Stats' two drawings put things, as arithmetic: the Getting stronger row's sparkline, and one
 * week of the training calendar. RN had no suite for either (both were flexbox); these pin the rules its
 * comments state. How they look is checked in the running app.
 */
class StatsLayoutTest {
    @Nested
    inner class Sparkline {
        /** Anchored at the window's minimum, 10 → 11 kg would draw as an empty bar and a full one. */
        @Test
        fun `scales from zero, so a small gain looks small`() {
            assertEquals(listOf(18f, 19f, 20f), sparklineBars(listOf(10.0, 10.5, 11.0)).map { it.height })
        }

        @Test
        fun `keeps the most recent seven and drops the rest off the left`() {
            val bars = sparklineBars((1..9).map(Int::toDouble))

            assertEquals(7, bars.size)
            // 3 of 9, rounded, is the oldest kept: 1 and 2 fell off.
            assertEquals(listOf(7f, 20f), listOf(bars.first().height, bars.last().height))
        }

        /** A genuine zero is a bar that happened, not a gap where a session is missing. */
        @Test
        fun `floors a bar at 2, including an all-zero window`() {
            assertEquals(listOf(2f, 20f), sparklineBars(listOf(0.0, 100.0)).map { it.height })
            assertEquals(listOf(2f, 2f), sparklineBars(listOf(0.0, 0.0)).map { it.height })
        }

        @Test
        fun `marks the latest bar, the one the row is about`() {
            assertEquals(listOf(false, false, true), sparklineBars(listOf(1.0, 2.0, 3.0)).map { it.latest })
        }

        @Test
        fun `places bars left to right at a fixed pitch`() {
            assertEquals(listOf(0f, 6f, 12f), sparklineBars(listOf(1.0, 2.0, 3.0)).map { it.x })
            assertEquals(16f, sparklineWidth(3))
        }
    }

    @Nested
    inner class CalendarCells {
        @Test
        fun `splits the row into seven equal slots that span it`() {
            val slots = calendarCells(140f).map { it.slot }

            assertEquals(List(7) { it * 20f }, slots.map { it.x })
            assertEquals(List(7) { 20f }, slots.map { it.width })
        }

        /**
         * Every slot reserves today's ring, so ringing a day moves nothing, and the cell sits inside it
         * with a gap of the card showing through rather than the ring eating into the shade.
         */
        @Test
        fun `insets each cell inside its slot, leaving room for the ring and a gap`() {
            val (slot, cell) = calendarCells(140f)[3]

            assertEquals(slot.x + CALENDAR_RING_INSET, cell.x)
            assertEquals(slot.width - 2 * CALENDAR_RING_INSET, cell.width)
            assertEquals(CALENDAR_CELL_HEIGHT, cell.height)
            assertEquals(CALENDAR_CELL_HEIGHT + 2 * CALENDAR_RING_INSET, slot.height)
        }
    }
}
