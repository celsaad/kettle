package com.casco.kettle.shell.history

import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.EntryResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [entryResultText], each shape in `pt` as RN's `formatEntryResult` renders it, since a History row
 * shows what an entry put up in these words. In `pt` for the screen tests' reason, and because its
 * plurals have the `_zero` step English lacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class EntryResultsTest {
    private val resources = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun text(result: EntryResult) = resources.entryResultText(result)

    // As JS prints them: a whole second has no ".0", and a half keeps its ".5".
    @Test
    fun `holds are each set's seconds`() {
        assertEquals("40s · 52.5s", text(EntryResult.Holds(listOf(40.0, 52.5))))
    }

    @Test
    fun `reps are each set's count, in one phrase`() {
        assertEquals("8 · 6 repetições", text(EntryResult.Reps(listOf(8, 6))))
    }

    @Test
    fun `rounds add extra reps only when there are some`() {
        assertEquals("1 rodada", text(EntryResult.Rounds(1, null)))
        assertEquals("0 rodadas", text(EntryResult.Rounds(0, 0)))
        assertEquals("7 rodadas + 3 repetições", text(EntryResult.Rounds(7, 3)))
    }

    @Test
    fun `intervals add their reps only when any were entered`() {
        assertEquals("2 intervalos", text(EntryResult.Intervals(2, null)))
        assertEquals("3 intervalos · 1 repetição", text(EntryResult.Intervals(3, 1)))
    }

    @Test
    fun `cardio says whichever of time and distance it has`() {
        assertEquals("600s · 2000 m", text(EntryResult.Cardio(600.0, 2000.0)))
        assertEquals("1500 m", text(EntryResult.Cardio(null, 1500.0)))
    }

    @Test
    fun `rest is the seconds taken`() {
        assertEquals("90s", text(EntryResult.Rest(90.0)))
    }
}
