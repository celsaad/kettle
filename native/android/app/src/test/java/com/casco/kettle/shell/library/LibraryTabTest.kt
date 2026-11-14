package com.casco.kettle.shell.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Program
import com.casco.kettle.domain.ProgramWeek
import com.casco.kettle.domain.SessionLog
import com.casco.kettle.domain.Workout
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.nav.Navigator
import com.casco.kettle.shell.nav.Route
import com.casco.kettle.shell.nav.Section
import com.casco.kettle.shell.nav.Tab
import com.casco.kettle.shell.nav.tabRoute
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Library's chrome (#413): the title, the + that makes what the section shows, and the sections as
 * tabs over the real screens. In `pt`, so a hardcoded English label would show (AGENTS.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class LibraryTabTest {
    @get:Rule
    val compose = createComposeRule()

    private val navigator = Navigator()

    private fun show() {
        val latest =
            LatestFiles(Files(), CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent {
            KettleTheme(darkTheme = false) {
                CompositionLocalProvider(LocalNavigator provides navigator, LocalLatestFiles provides latest) {
                    LibraryTab(navigator.tabRoute(Tab.Library))
                }
            }
        }
    }

    private fun tab(label: String) = compose.onNode(hasText(label) and isSelectable() and isTab)

    private fun newButton(label: String) = compose.onNode(hasContentDescription(label) and isButton)

    private fun left(node: SemanticsNodeInteraction) = node.getBoundsInRoot().left

    @Test
    fun `is titled Library, with its tabs in the canvas order`() {
        show()

        compose.onNodeWithText("Biblioteca").assert(isHeading())
        assertTrue(left(tab("Treinos")) < left(tab("Programas")))
        assertTrue(left(tab("Programas")) < left(tab("Exercícios")))
        tab("Treinos").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `opens on Workouts, and a tab moves to its section through the route`() {
        show()
        tab("Treinos").assertIsSelected()
        compose.onNode(hasSetTextAction() and hasText("Buscar 1 treino")).assertExists()

        tab("Exercícios").performClick()

        assertEquals(Route.of(Section.EXERCISES), navigator.tabRoute(Tab.Library))
        tab("Exercícios").assertIsSelected()
        tab("Treinos").assertIsNotSelected()
        compose.onNode(hasSetTextAction() and hasText("Buscar 1 exercício")).assertExists()
    }

    /** One +, named for what it makes on the section showing, since a plus says nothing. */
    @Test
    fun `the plus makes whatever the section shows`() {
        show()
        newButton("Novo treino").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        assertEquals(Route(Destination.WorkoutEditor), navigator.sheet)

        navigator.back()
        tab("Programas").performClick()
        newButton("Novo programa").performClick()
        assertEquals(Route(Destination.ProgramEditor), navigator.sheet)

        navigator.back()
        tab("Exercícios").performClick()
        newButton("Novo exercício").performClick()
        assertEquals(Route(Destination.ExerciseEditor), navigator.sheet)
    }

    /** Named by its own words, the file's name, which is also the sheet's title. */
    @Test
    fun `the file's name under the title opens the file sheet`() {
        show()

        compose.onNode(hasText("exercises.yaml") and isButton).assertHeightIsAtLeast(48.dp).performClick()

        assertEquals(Route(Destination.YourFile), navigator.sheet)
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private class Files : FakeStoredFiles() {
        override fun sessionLog() = SessionLog(emptyList(), emptyList())

        override fun library() =
            Library(
                1,
                listOf(Exercise("pushups", "Push-ups", ExerciseConfig.Reps(3, 10, restSec = 60.0))),
                listOf(Workout("push-day", "Push day", emptyList())),
                listOf(Program("base", "Base", listOf(ProgramWeek.Training(1, workoutId = "push-day")))),
            )
    }
}
