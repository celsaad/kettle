package com.casco.kettle.shell.library

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
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
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.rn.Stacking
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

    private val opened = mutableListOf<Triple<Stacking, String, Map<String, String>>>()
    private var exports = 0
    private val reselects = MutableSharedFlow<Tab>(extraBufferCapacity = 1)

    private fun show(files: Files = Files(pullUps, dips, plank, breather)) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                LibraryScreen(
                    latest,
                    reselects,
                    open = { how, path, params -> opened += Triple(how, path, params) },
                    export = { exports++ },
                )
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasText(name, substring = true) and isButton)

    private fun search(query: String) = compose.onNode(hasSetTextAction()).performTextInput(query)

    /** Rest is block structure rather than something anyone browses for, so it never lists. */
    @Test
    fun `lists every exercise but rest, with its summary and type`() {
        show()

        row("Pull-ups").assertExists()
        row("Plank").assertExists()
        row("Breather").assertDoesNotExist()
        compose.onNodeWithText("3 × 8–12 · 90s de descanso").assertExists()
        compose.onNodeWithText("3 × 30–60s · 60s de descanso").assertExists()
        compose.onNodeWithText("HOLD").assertExists()
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

        chip("Cardio").performClick()

        compose.onNodeWithText("Nada encontrado").assertExists()
        compose.onNodeWithText("Nada aqui se encaixa nesses filtros", substring = true).assertExists()
    }

    @Test
    fun `a filter chip narrows to its type and says it is the one selected`() {
        show()
        chip("Todos").assertIsSelected()

        chip("Isometria").assertHeightIsAtLeast(48.dp).performClick()

        chip("Isometria").assertIsSelected()
        chip("Todos").assertIsNotSelected()
        row("Plank").assertExists()
        row("Pull-ups").assertDoesNotExist()
        // The count is what the filter left, not the library: it describes the list under it.
        compose.onNode(hasSetTextAction() and hasText("Buscar 1 exercício")).assertExists()
    }

    /** Every type but rest has a chip, in the order a library tends to hold them. */
    @Test
    fun `offers a chip for every type but rest`() {
        show()

        listOf("Todos", "HIIT", "Reps", "Isometria", "EMOM", "AMRAP", "Cardio").forEach { chip(it).assertExists() }
    }

    /** Stored in kilograms always; 60 kg reads as 132.3 lb, at pounds' one decimal. */
    @Test
    fun `shows a target weight in the reader's unit`() {
        show(Files(rows, preferences = imperial))

        compose.onNodeWithText("3 × 5 · 132.3 lb · 120s de descanso").assertExists()
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
                    "3 × isometria máxima · 60s de descanso",
            )
        show(Files(*others.map { it.first }.toTypedArray()))

        for ((_, line) in others) {
            compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(line))
            compose.onNodeWithText(line).assertExists()
        }
    }

    @Test
    fun `shows a target weight in kilograms by default`() {
        show(Files(rows))

        compose.onNodeWithText("3 × 5 · 60 kg · 120s de descanso").assertExists()
    }

    @Test
    fun `Export asks RN to share the library`() {
        show()

        compose.onNode(hasText("Exportar") and isButton).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(1, exports)
        assertEquals(emptyList<Any>(), opened)
    }

    @Test
    fun `Import opens the import sheet`() {
        show()

        compose.onNode(hasText("Importar") and isButton).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/import", emptyMap<String, String>())), opened)
    }

    @Test
    fun `a row opens its exercise in the editor`() {
        show()

        row("Dips").assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/exercise-editor", mapOf("id" to "dips"))), opened)
    }

    @Test
    fun `New exercise opens an empty editor`() {
        show()

        compose
            .onNode(hasContentDescription("Novo exercício") and isButton)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
            .performClick()

        assertEquals(listOf(Triple(Stacking.Push, "/exercise-editor", emptyMap<String, String>())), opened)
    }

    @Test
    fun `is translated`() {
        show()

        compose.onNodeWithText("Biblioteca").assert(isHeading())
        // The field is named by its placeholder, read inside the field's own node.
        compose.onNode(hasSetTextAction() and hasText("Buscar 3 exercícios")).assertExists()
    }

    /** #153 dropped re-tap-to-top with RN's bar; each tab's port brings it back for its own list. */
    @Test
    fun `a tap on the Library tab while it shows scrolls back to the top`() {
        val many = (1..30).map { Exercise("e$it", "Exercise $it", ExerciseConfig.Amrap(600.0)) }
        show(Files(*many.toTypedArray()))
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(many.size)
        compose.onNodeWithText("Biblioteca").assertDoesNotExist()

        reselects.tryEmit(Tab.Programs)
        compose.waitForIdle()
        compose.onNodeWithText("Biblioteca").assertDoesNotExist()

        reselects.tryEmit(Tab.Library)
        compose.waitForIdle()
        compose.onNodeWithText("Biblioteca").assertIsDisplayed()
    }

    private fun chip(label: String) = compose.onNode(hasText(label) and isButton and isSelectable)

    private val isSelectable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private class Files(
        vararg exercises: Exercise,
        private val library: Library? = Library(1, exercises.toList(), emptyList(), emptyList()),
        private val preferences: Preferences? = null,
    ) : StoredFiles {
        override fun sessionLog() = SessionLog(emptyList(), emptyList())

        override fun library() = library

        override fun preferences() = preferences

        override fun deleteSession(id: String) = error("Library deletes nothing")
    }
}

private val pullUps = Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(3, 8, 12, restSec = 90.0))
private val dips = Exercise("dips", "Dips", ExerciseConfig.Reps(3, 10, restSec = 60.0))
private val plank = Exercise("plank", "Plank", ExerciseConfig.TimedHold(3, 30.0, 60.0, 60.0))
private val breather = Exercise("breather", "Breather", ExerciseConfig.Rest(30.0))
private val rows = Exercise("rows", "Barbell row", ExerciseConfig.Reps(3, 5, targetWeightKg = 60.0, restSec = 120.0))
private val imperial = Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)
