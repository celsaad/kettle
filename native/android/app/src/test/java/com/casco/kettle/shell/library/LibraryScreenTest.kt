package com.casco.kettle.shell.library

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library (#234), case for case from RN's `library.test.tsx`, on the harness recipe (#193): in `pt`,
 * since an English assertion passes a hardcoded English string, with the library faked behind
 * [StoredFiles] and every route and export heard through parameters. Beyond jest's: the filter chips'
 * role and state, a target in pounds, what each control sends, the targets' size and re-tap-to-top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class LibraryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<Route>()
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files = Files(pullUps, dips, plank, breather)) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                LibraryScreen(
                    latest,
                    reselects,
                    open = { opened += it },
                )
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name, substring = true) and isButton)

    private fun search(query: String) = compose.onNode(hasSetTextAction()).performTextInput(query)

    /** As the board draws it: the type, then the target, and no rest between sets, which is the detail's. */
    @Test
    fun `a row's line drops the rest between sets`() {
        show()

        compose.onNodeWithText("de descanso", substring = true).assertDoesNotExist()
    }

    /** Rest is block structure rather than something anyone browses for, so it never lists. */
    @Test
    fun `lists every exercise but rest, its type leading its summary`() {
        show()

        row("Pull-ups").assertExists()
        row("Plank").assertExists()
        row("Breather").assertDoesNotExist()
        compose.onNodeWithText("Reps · 3 × 8–12").assertExists()
        compose.onNodeWithText("Isometria · 3 × 30–60s").assertExists()
    }

    @Test
    fun `narrows the list to matching names`() {
        show()

        search("dip")

        row("Dips").assertExists()
        row("Pull-ups").assertDoesNotExist()
    }

    @Test
    fun `quotes the query back when a search matched nothing`() {
        show()

        search("zzz")

        compose.onNodeWithText("Nada encontrado").assertExists()
        compose.onNodeWithText("Nenhum resultado para \"zzz\"", substring = true).assertExists()
    }

    /** Nothing was typed, so there is no query to quote: pointing at the filters is all the card can say. */
    @Test
    fun `points at the filters when a filter emptied the list instead`() {
        show()

        choose("Cardio")

        compose.onNodeWithText("Nada encontrado").assertExists()
        compose.onNodeWithText("Nada aqui se encaixa nesses filtros", substring = true).assertExists()
    }

    /**
     * Named by its own words, which Voice Access matches, with what it's set to as its state, and it says
     * whether its menu is open. It shows the type once one is chosen, so a narrowed list says so.
     */
    @Test
    fun `the Type filter narrows to its type and says which one is on`() {
        show()
        typeFilter()
            .assertHeightIsAtLeast(48.dp)
            .assert(hasText("Tipo"))
            .assert(hasStateDescription("todos os tipos"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        compose.onNode(hasContentDescription("Tipo", substring = true)).assertDoesNotExist()
        typeFilter().performClick()
        typeFilter().assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
        menuItem("Todos").assertIsSelected()
        compose.onNode(hasText("Isometria") and isSelectable).performClick()

        typeFilter().assert(hasText("Isometria")).assert(hasStateDescription("Isometria"))
        typeFilter().assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
        row("Plank").assertExists()
        row("Pull-ups").assertDoesNotExist()
        // The count is what the filter left, not the library: it describes the list under it.
        compose.onNode(hasSetTextAction() and hasText("Buscar 1 exercício")).assertExists()
    }

    /** Every type but rest is on the menu, in the order a library tends to hold them. */
    @Test
    fun `offers every type but rest`() {
        show()

        typeFilter().performClick()

        listOf("Todos", "HIIT", "Reps", "Isometria", "EMOM", "AMRAP", "Cardio").forEach { menuItem(it).assertExists() }
        menuItem("Descanso").assertDoesNotExist()
    }

    /** Stored in kilograms always; 60 kg reads as 132.3 lb, at pounds' one decimal. */
    @Test
    fun `shows a target weight in the reader's unit`() {
        show(Files(rows, preferences = imperial))

        compose.onNodeWithText("Reps · 3 × 5 · 132.3 lb").assertExists()
    }

    /** Each type's line, in its own words; the reps and ranged-hold lines are covered above. */
    @Test
    fun `summarises every other type in one line`() {
        val others =
            listOf(
                Exercise("burpees", "Burpees", ExerciseConfig.Hiit(40.0, 20.0, 8)) to
                    "40s de trabalho · 20s de descanso · ×8",
                Exercise("swings", "Swings", ExerciseConfig.Emom(60.0, 10.0)) to "intervalo de 60s · 10 min",
                Exercise("cindy", "Cindy", ExerciseConfig.Amrap(600.0)) to "limite de 600s",
                Exercise("run", "Run", ExerciseConfig.Cardio(distanceMeters = 5000.0)) to "5000 m",
                Exercise("bike", "Bike", ExerciseConfig.Cardio(durationSec = 1200.0)) to "1200s",
                Exercise("hang", "Dead hang", ExerciseConfig.TimedHold(3, restSec = 60.0)) to
                    "3 × isometria máxima",
            )
        show(Files(*others.map { it.first }.toTypedArray()))

        for ((_, line) in others) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(line, substring = true))
            compose.onNodeWithText(line, substring = true).assertExists()
        }
    }

    @Test
    fun `shows a target weight in kilograms by default`() {
        show(Files(rows))

        compose.onNodeWithText("Reps · 3 × 5 · 60 kg").assertExists()
    }

    @Test
    fun `a row opens its exercise's detail, which the form is one tap behind`() {
        show()

        row("Dips").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Route(Destination.ExerciseDetail, mapOf("id" to "dips"))), opened)
    }

    @Test
    fun `is translated`() {
        show()

        typeFilter().assert(hasText("Tipo")).assert(hasStateDescription("todos os tipos"))
        // The field is named by its placeholder, read inside the field's own node.
        compose.onNode(hasSetTextAction() and hasText("Buscar 3 exercícios")).assertExists()
    }

    /** #153 dropped re-tap-to-top with RN's bar; each tab's port brings it back for its own list. */
    @Test
    fun `a tap on the Library tab while it shows scrolls back to the top`() {
        val many = (1..30).map { Exercise("e$it", "Exercise $it", ExerciseConfig.Amrap(600.0)) }
        show(Files(*many.toTypedArray()))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        reselects.tryEmit(Tab.Progress)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
    }

    private fun typeFilter() =
        compose.onNode(isButton and SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))

    private fun menuItem(label: String) = compose.onNode(hasText(label) and isSelectable)

    private fun choose(label: String) {
        typeFilter().performClick()
        menuItem(label).performClick()
    }

    private val isSelectable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        vararg exercises: Exercise,
        private val library: Library? = Library(1, exercises.toList(), emptyList(), emptyList()),
        private val preferences: Preferences? = null,
    ) : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(emptyList(), emptyList())

        override fun library() = library

        override fun preferences() = preferences
    }
}

private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, 12, restSec = 90.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(3, 30.0, 60.0, 60.0))
private val breather = Exercise("breather", "Breather", ExerciseConfig.Rest(30.0))
private val rows = Exercise("rows", "Barbell row", ExerciseConfig.Reps(3, 5, targetWeightKg = 60.0, restSec = 120.0))
private val imperial = Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)
