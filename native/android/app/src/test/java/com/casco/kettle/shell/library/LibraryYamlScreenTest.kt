package com.casco.kettle.shell.library

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.casco.kettle.domain.Exercise
import com.casco.kettle.domain.ExerciseConfig
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.Preferences
import com.casco.kettle.domain.ThemePreference
import com.casco.kettle.domain.UnitSystem
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.shell.files.FakeStoredFiles
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.theme.KettleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private fun show(files: Files = Files()) {
        val latest =
            LatestFiles(files, CoroutineScope(Dispatchers.Unconfined), io = Dispatchers.Unconfined).apply { reread() }
        compose.setContent { KettleTheme(darkTheme = false) { LibraryYamlScreen(latest) } }
    }

    @Test
    fun `shows the library as the app writes it, under the file's name`() {
        show()

        compose.onNodeWithText("exercises.yaml").assert(isHeading())
        compose.onNodeWithText(serializeLibraryYaml(squatLibrary)).assertExists()
    }

    /** The file keeps kilograms whatever the reader's units, so a pounds reader is told what they see. */
    @Test
    fun `keeps kilograms for a reader in pounds, and says so`() {
        show(Files(Preferences(UnitSystem.IMPERIAL, ThemePreference.SYSTEM, false, true, null)))

        compose.onNodeWithText("Os pesos estão em quilos, como o arquivo os guarda.").assertExists()
        compose.onNodeWithText("target_weight: 60", substring = true).assertExists()
    }

    /** Null is a library not read yet, and the screen shows no YAML before it has one. */
    @Test
    fun `shows no YAML before there is a library`() {
        show(Files(library = null))

        compose.onNodeWithText("exercises:", substring = true).assertDoesNotExist()
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
