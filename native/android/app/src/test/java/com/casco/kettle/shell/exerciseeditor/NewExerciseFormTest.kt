package com.casco.kettle.shell.exerciseeditor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.shell.theme.KettleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quick-add the workout editor's pickers embed (#241), case for case from RN's
 * `new-exercise-form.test.tsx`, in `pt`. RN's was a second copy of the editor's form, which is why
 * jest pinned it; here it is the same form and the same `submitExercise`, and this holds that it
 * stays wired to them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class NewExerciseFormTest {
    @get:Rule
    val compose = createComposeRule()

    private val created = mutableListOf<Exercise>()
    private var cancelled = 0

    private fun show() =
        compose.setContent {
            KettleTheme(darkTheme = false) {
                // Hosted as the workout editor's picker will: in a screen that scrolls, which it doesn't.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    NewExerciseForm(UnitSystem.METRIC, onCreate = { created += it }, onCancel = { cancelled += 1 })
                }
            }
        }

    private fun type(
        name: String,
        text: String,
    ) = compose.onNode(hasContentDescription(name)).performScrollTo().performTextReplacement(text)

    private fun press(label: String) = compose.onNode(hasText(label) and isButton).performScrollTo().performClick()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun `renders its labels as text, not as keys`() {
        show()

        compose.onNodeWithText("Reps").assertExists()
        compose.onNodeWithText("Séries").assertExists()
        compose.onNode(hasText("exerciseForm", substring = true)).assertDoesNotExist()
    }

    // The same check the editor runs: skipping it would reopen the 0-sets hole through the quick-add.
    @Test
    fun `runs the same config validation as the full editor`() {
        show()

        type("Nome", "Pull-ups")
        type("Séries", "0")
        press("Criar e adicionar")

        compose.onNodeWithText("Séries precisa ser pelo menos 1.").assertExists()
        assertEquals(emptyList<Exercise>(), created)
    }

    @Test
    fun `refuses a name that yields no id`() {
        show()

        type("Nome", "🏋️")
        press("Criar e adicionar")

        compose.onNodeWithText("Não foi possível gerar um id a partir desse nome.").assertExists()
        assertEquals(emptyList<Exercise>(), created)
    }

    @Test
    fun `hands back a built exercise with numeric config`() {
        show()

        type("Nome", "Pull-ups")
        type("Séries", "4")
        type("Reps alvo", "6")
        type("Descanso (sec)", "90")
        press("Criar e adicionar")

        assertEquals(
            listOf(Exercise("pull-ups", "Pull-ups", ExerciseConfig.Reps(sets = 4, targetRepsMin = 6, restSec = 90.0))),
            created,
        )
    }

    // Unlike the editor's, the type stays open: nothing exists yet whose config a switch could break.
    @Test
    fun `switches the field set when the type changes`() {
        show()

        press("HIIT")

        compose.onNodeWithText("Trabalho (sec)").assertExists()
        compose.onNodeWithText("Séries").assertDoesNotExist()
    }

    @Test
    fun `cancels without creating anything`() {
        show()

        type("Nome", "Pull-ups")
        press("Cancelar")

        assertEquals(1, cancelled)
        assertEquals(emptyList<Exercise>(), created)
    }

    @Test
    fun `every control is a button at least 44dp tall`() {
        show()

        for (label in listOf("Reps", "Cancelar", "Criar e adicionar")) {
            compose.onNode(hasText(label) and isButton).performScrollTo().assertHeightIsAtLeast(44.dp)
        }
    }
}
