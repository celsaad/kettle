package com.casco.kettle.shell.importer

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.WorkoutBlock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The review's own words (#443): the fields-cleared tile and what each update clears, names over
 * ids, and the count on Import. The merge and its refusals are `ImportScreenTest`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w400dp-h4000dp")
class ImportReviewTest : ImportHarness() {
    private val loaded =
        Exercise(
            "pull-ups",
            "Pull-ups",
            ExerciseConfig.Reps(3, 8, targetWeightKg = 20.0, restSec = 90.0),
            notes = "Dead hang first.",
        )

    /** The library has a weighted, noted Pull-ups; the file has it bare, and a new Dips. */
    private fun reviewsAClearingUpdate() {
        picks(Library(1, listOf(pullUps, dips), emptyList(), emptyList()))
        chooseFile(Files(Library(1, listOf(loaded), emptyList(), emptyList())))
    }

    // An update replaces whole, so what the file leaves out is cleared: counted, and said per item.
    @Test
    fun `counts the fields an update clears, and says it on the item`() {
        reviewsAClearingUpdate()

        // The tile is one node, its count and its noun: the count is what this asserts.
        compose.onNodeWithText("removidos").assert(hasText("2"))
        shown("2 removidos")
    }

    @Test
    fun `marks a cleared field as cleared, with what it was`() {
        reviewsAClearingUpdate()

        compose.onNodeWithContentDescription("Peso: era 20 kg, agora: removido").assertExists()
        compose.onNodeWithContentDescription("Notas: era Dead hang first., agora: removido").assertExists()
    }

    // A lost list keeps its dash on screen; the warning mark is all that says cleared, and TalkBack can't hear it.
    @Test
    fun `says a workout's lost exercises are cleared, out loud`() {
        // The file takes the dips out of a Push day that had them; it was pull-ups and dips.
        val both = pushDay.copy(blocks = listOf("pull-ups", "dips").map { WorkoutBlock.ExerciseBlock(it) })
        picks(Library(1, emptyList(), listOf(pushDay), emptyList()))
        chooseFile(Files(Library(1, listOf(pullUps, dips), listOf(both), emptyList())))

        compose.onNodeWithContentDescription("Exercícios: era dips, agora: removido").assertExists()
    }

    @Test
    fun `names each item over its id and kind`() {
        reviewsAClearingUpdate()

        shown("Pull-ups")
        shown("pull-ups · exercício")
        shown("Dips")
        shown("dips · exercício")
    }

    @Test
    fun `says on Import how many items it writes`() {
        reviewsAClearingUpdate()

        merge().assertExists()
        shown("Importar 2 itens")
    }

    // Before anything is picked there's nothing to count: "Importar 0 itens" read as a broken count.
    @Test
    fun `says plain Import until there is a review to count`() {
        show(Files())

        shown("Importar")
        absent("Importar 0 itens")
    }

    // A file that parses but holds nothing has nothing to count either.
    @Test
    fun `says plain Import for a review with nothing in it`() {
        picks(Library(1, emptyList(), emptyList(), emptyList()))
        chooseFile()

        shown("Importar")
        absent("Importar 0 itens")
    }

    // Each section is cut on its own: a run of updates can't push the new items behind Show all.
    @Test
    fun `keeps the new items in sight past a long run of updates`() {
        val nine = many.take(9)
        picks(Library(1, nine + dips, emptyList(), emptyList()))
        chooseFile(Files(Library(1, nine, emptyList(), emptyList())))

        shown("Novos")
        shown("dips · exercício")
    }

    // Open on its diff, the review being for what changes; a tap folds it, and it still says what it clears.
    @Test
    fun `folds an update's diff on a tap, and still says what it clears`() {
        reviewsAClearingUpdate()

        button("Pull-ups", substring = true).performClick()

        compose.onNodeWithContentDescription("Peso: era 20 kg, agora: removido").assertDoesNotExist()
        shown("2 removidos")
    }
}
