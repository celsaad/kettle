package com.casco.kettle.shell.library

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** One exercise as YAML, read-only (#444). In `pt`, so the notes are the bundle's, not literals. */
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
        compose.onNodeWithText("Os pesos aqui estão em quilogramas, como o arquivo os guarda.").assertExists()
        compose
            .onNodeWithText(
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

        assertEquals(ParseResult.Ok(squatLibrary), parseLibraryYaml(clipboardText()))
        // Said, but not announced: the system announces a copy itself, and a live region doubled it.
        compose.onNodeWithText("Copiado").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
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

    // The route with no id: the whole-file view it once opened is gone, so it reads as a missing exercise.
    @Test
    fun `says so, with no YAML, for a route that names no exercise`() {
        show()

        compose.onNodeWithText("exercises:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Este exercício não está mais na sua biblioteca.").assertExists()
    }

    /** Null is a library not read yet, and the screen shows no YAML before it has one. */
    @Test
    fun `shows no YAML before there is a library`() {
        show(Files(library = null), exerciseId = "squat")

        compose.onNodeWithText("exercises:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Este exercício não está mais na sua biblioteca.").assertDoesNotExist()
    }

    // A sheet that won't open must not drop the document: it is copied, and the note says why.
    @Test
    fun `copies the YAML and says so when the share sheet can't open`() {
        noSharingApp()
        show(exerciseId = "squat")

        compose.onNodeWithText("Compartilhar").performScrollTo().performClick()

        compose.onNodeWithText(SHARE_COPIED).assertExists()
        assertEquals(ParseResult.Ok(squatLibrary), parseLibraryYaml(clipboardText()))
    }

    // No browser is common on Android: the format link copies its URL instead of doing nothing.
    @Test
    fun `copies the format link and says so when nothing can open it`() {
        noSharingApp()
        show(exerciseId = "squat")

        compose.onNodeWithText("Como o formato funciona").performScrollTo().performClick()

        compose.onNodeWithText(LINK_COPIED).assertExists()
        assertEquals(FORMAT_URL, clipboardText())
    }

    // A refused copy must not claim one, and a failure is the one outcome announced as it appears.
    @Test
    @Config(shadows = [RefusingClipboard::class])
    fun `says the copy failed, as a polite live region, when the clipboard refuses it`() {
        show(exerciseId = "squat")

        compose.onNodeWithText("Copiar").performScrollTo().performClick()

        compose.onNodeWithText(COPY_FAILED).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
        )
        compose.onNodeWithText("Copiado").assertDoesNotExist()
    }

    // "Copiado" is for reading, not for describing a later copy: two seconds, as Import's.
    @Test
    fun `clears Copied after about two seconds`() {
        show(exerciseId = "squat")
        tapWithClockPaused("Copiar")

        showsFor("Copiado", shownAt = 1_500, goneBy = 2_500)
    }

    // A second Copy restarts the two seconds rather than inheriting what was left of the first's.
    @Test
    fun `restarts the two seconds on a second Copy`() {
        show(exerciseId = "squat")
        tapWithClockPaused("Copiar")
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithText("Copiar").performClick()
        compose.mainClock.advanceTimeByFrame()

        showsFor("Copiado", shownAt = 1_500, goneBy = 2_500)
    }

    // A sentence saying why holds three times as long, so it can be read, and still goes.
    @Test
    fun `holds the share fallback's note for about six seconds`() {
        noSharingApp()
        show(exerciseId = "squat")
        tapWithClockPaused("Compartilhar")

        showsFor(SHARE_COPIED, shownAt = 2_500, goneBy = 6_500)
    }

    @Test
    fun `holds the format link's note for about six seconds`() {
        noSharingApp()
        show(exerciseId = "squat")
        tapWithClockPaused("Como o formato funciona")

        showsFor(LINK_COPIED, shownAt = 2_500, goneBy = 6_500)
    }

    // A failure left up would describe the next copy too.
    @Test
    @Config(shadows = [RefusingClipboard::class])
    fun `clears a refused copy's note after about six seconds`() {
        show(exerciseId = "squat")
        tapWithClockPaused("Copiar")

        showsFor(COPY_FAILED, shownAt = 2_500, goneBy = 6_500)
    }

    private fun clipboardText() =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
            .primaryClip!!
            .getItemAt(0)
            .text
            .toString()

    /** No share sheet or browser to open: an unresolvable intent throws, as on a device with neither. */
    private fun noSharingApp() =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).checkActivities(true)

    /**
     * Taps [label] with the clock paused from then on, scrolled to first while it still runs: a scroll
     * waits on animations a paused clock never ends.
     */
    private fun tapWithClockPaused(label: String) {
        compose.onNodeWithText(label).performScrollTo()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(label).performClick()
        compose.mainClock.advanceTimeByFrame()
    }

    /** [text] is still up [shownAt] ms from now and gone by [goneBy]. */
    private fun showsFor(
        text: String,
        shownAt: Long,
        goneBy: Long,
    ) {
        compose.mainClock.advanceTimeBy(shownAt)
        compose.onNodeWithText(text).assertExists()
        compose.mainClock.advanceTimeBy(goneBy - shownAt)
        compose.onNodeWithText(text).assertDoesNotExist()
    }

    private class Files(
        private val preferences: Preferences? = null,
        private val library: Library? = squatLibrary,
    ) : FakeStoredFiles() {
        override fun library() = library

        override fun preferences() = preferences
    }
}

private const val SHARE_COPIED = "Não foi possível abrir o compartilhamento, então foi copiado."
private const val LINK_COPIED = "Nenhum navegador para abrir, então o link foi copiado."
private const val COPY_FAILED = "Não foi possível copiar."

private val squatLibrary =
    Library(
        1,
        listOf(Exercise("squat", "Squat", ExerciseConfig.Reps(3, 5, restSec = 120.0, targetWeightKg = 60.0))),
        emptyList(),
        emptyList(),
    )

/** A clipboard that refuses every write, as one with its permission withdrawn does. */
@Implements(ClipboardManager::class)
class RefusingClipboard {
    // The signature is the framework's, so the shadow can't drop the clip it refuses.
    @Suppress("UnusedParameter")
    @Implementation
    fun setPrimaryClip(clip: ClipData): Unit = throw SecurityException("refused")
}
