package com.casco.kettle.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Library's lists in file order with the active program's rows leading (#488). */
class LibraryOrderTest {
    private val ids = listOf("a", "b", "c", "d")

    private fun order(leading: Collection<String>) = leadingFirst(ids, leading) { it }

    @Test
    fun `nothing leading leaves the file order`() {
        assertEquals(ids, order(emptyList()))
    }

    @Test
    fun `the leading rows come first in their own order, the rest after in the file's`() {
        assertEquals(listOf("d", "b", "a", "c"), order(listOf("d", "b")))
    }

    /** A program can name a workout the library no longer has; it leaves no gap and no duplicate. */
    @Test
    fun `a leading id the list doesn't hold is skipped`() {
        assertEquals(listOf("c", "a", "b", "d"), order(listOf("gone", "c")))
    }

    /**
     * A search ranks the ordered list: "Day one" leads "Bench day" on an equal match, though the file
     * has it later, and "Bench day" still beats "Holiday", a leading row that only matches inside a word.
     */
    @Test
    fun `under a search the leading rows only break ties`() {
        val file = listOf("Bench day", "Holiday", "Day one")
        val ordered = leadingFirst(file, listOf("Holiday", "Day one")) { it }

        assertEquals(listOf("Day one", "Bench day", "Holiday"), rankByName(ordered, "day") { it })
    }
}
