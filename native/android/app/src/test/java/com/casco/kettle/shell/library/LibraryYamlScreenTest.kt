package com.casco.kettle.shell.library

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The library as YAML, read-only (#413). In `pt`, so the units note is the bundle's, not a literal. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class LibraryYamlScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(
        files: Files = Files(),
        exerciseId: String? = null,
    ) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent { KettleTheme(darkTheme = false) { LibraryYamlScreen(latest, exerciseId) } }
    }

    @Test
    fun `shows the library as the app writes it, under the file's name`() {
        show()

        compose.onNodeWithText("exercises.yaml").assert(isHeading())
        // The block draws the document without its closing newline.
        compose.onNodeWithText(serializeLibraryYaml(squatLibrary).trimEnd('\n')).assertExists()
    }

    /** The file keeps kilograms whatever the reader's units, so a pounds reader is told what they see. */
    @Test
    fun `keeps kilograms for a reader in pounds, and says so`() {
        show(Files(Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)))

        compose.onNodeWithText("Os pesos estão em quilos, como o arquivo os guarda.").assertExists()
        compose.onNodeWithText("target_weight: 60", substring = true).assertExists()
    }

    /** Exercise detail's YAML (#444): the one exercise as a complete file, so a copy imports on its own. */
    @Test
    fun `shows one exercise as a complete file of its own`() {
        show(exerciseId = "squat")

        compose.onNodeWithText(serializeLibraryYaml(squatLibrary).trimEnd('\n')).assertExists()
        compose.onNodeWithText("workouts: []", substring = true).assertExists()
    }

    @Test
    fun `heads one exercise with its name over As YAML, and says what the document is`() {
        show(exerciseId = "squat")

        compose.onNodeWithText("Squat").assertExists()
        compose.onNode(hasText("Como YAML") and isHeading()).assertExists()
        compose
            .onNodeWithText(
                "Os pesos aqui estão em quilogramas, como o arquivo os guarda. " +
                    "Escrito do zero, então os comentários do seu arquivo original não estão aqui.",
            ).assertExists()
    }

    @Test
    fun `tells a reader in pounds that the app shows pounds`() {
        show(Files(Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)), exerciseId = "squat")

        compose.onNodeWithText("o app mostra libras", substring = true).assertExists()
    }

    // The ticket's promise: what Copy hands over imports on its own, the exercise and nothing missing.
    @Test
    fun `copies a document that parses as a library of that one exercise`() {
        show(exerciseId = "squat")

        compose.onNodeWithText("Copiar").performScrollTo().performClick()

        val clipboard =
            ApplicationProvider.getApplicationContext<Context>().getSystemService(
                ClipboardManager::class.java,
            )
        val copied =
            clipboard.primaryClip!!
                .getItemAt(0)
                .text
                .toString()
        assertEquals(ParseResult.Ok(squatLibrary), parseLibraryYaml(copied))
        compose.onNodeWithText("Copiado").assertExists()
    }

    // The block's numbers are for the eye: out of the copy's selection and out of what TalkBack reads.
    @Test
    fun `keeps the line numbers out of what a screen reader reads`() {
        show(exerciseId = "squat")

        compose.onAllNodesWithText("1\n2", substring = true).assertCountEquals(0)
    }

    @Test
    fun `says so, with no YAML, for an exercise the library no longer has`() {
        show(exerciseId = "deleted")

        compose.onNodeWithText("- id:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Este exercício não está mais na sua biblioteca.").assertExists()
    }

    /** Null is a library not read yet, and the screen shows no YAML before it has one. */
    @Test
    fun `shows no YAML before there is a library`() {
        show(Files(library = null))

        compose.onNodeWithText("exercises:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Este exercício não está mais na sua biblioteca.").assertDoesNotExist()
    }

    private class Files(
        private val preferences: Preferences? = null,
        private val library: Library? = squatLibrary,
    ) : FakeStoredFiles() {
        override fun library() = library

        override fun preferences() = preferences
    }
}

private val squatLibrary =
    Library(
        1,
        listOf(Exercise("squat", "Squat", ExerciseConfig.Reps(3, 5, restSec = 120.0, targetWeightKg = 60.0))),
        emptyList(),
        emptyList(),
    )
