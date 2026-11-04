package com.casco.kettle.shell.importer

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextReplacement
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.shell.files.ShippedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Import's hand-offs to an assistant (#244), case for case from `import.test.tsx`: the repair loop,
 * the assistant brief and pasting from the clipboard. The brief's own layout is `:shared`'s
 * `AssistantBriefTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w400dp-h4000dp")
class ImportHandoffTest : ImportHarness() {
    // the repair loop

    private fun refusedForUnknownExercise() {
        picks(Library(1, emptyList(), listOf(legDay), emptyList()))
        chooseFile()
    }

    // The ids survive verbatim: they're what the fix is addressed to.
    @Test
    fun `copies the refusal, framed as something to act on`() {
        refusedForUnknownExercise()
        press("Copiar erro")

        assertEquals(
            listOf(
                "O Kettle recusou este exercises.yaml na importação. Corrija o YAML e devolva o arquivo corrigido." +
                    "\n\nO treino \"leg-day\" referencia o exercício desconhecido \"squats\"",
            ),
            copies,
        )
    }

    // Focus stays on the button, so its "Copied" is a live region, or TalkBack wouldn't re-read it.
    @Test
    fun `confirms the copy on the button and to a screen reader`() {
        refusedForUnknownExercise()
        // Only while it says "Copied", so the label going back two seconds later isn't read out too.
        button("Copiar erro").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
        press("Copiar erro")

        button(COPIED).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    // A read failure is the platform's, and an assistant can do nothing about a denied file handle.
    @Test
    fun `offers nothing to copy when the failure was not about the content`() {
        picked = { FilePick.Failed("Permission denied") }
        chooseFile()

        shown("Não foi possível ler esse arquivo: Permission denied")
        absent("Copiar erro")
    }

    @Test
    fun `says so when the clipboard refuses, without losing the error or claiming a copy`() {
        clipboardTakes = false
        refusedForUnknownExercise()
        press("Copiar erro")

        shown(COPY_FAILED)
        shown("O treino \"leg-day\" referencia o exercício desconhecido \"squats\"")
        absent(COPIED)
    }

    // the assistant brief

    @Test
    fun `copies the schema and the library's own ids`() {
        show()
        press(COPY_BRIEF)

        val copied = copies.single()
        assertTrue("pull-ups (reps) — Pull-ups" in copied)
        assertTrue("push-day — Push day" in copied)
        assertTrue("Ids que já estão na minha biblioteca" in copied)
        shown(COPIED)
    }

    // The schema is the generated file, carried whole, not a copy written out somewhere else.
    @Test
    fun `carries the shipped schema`() {
        show()
        press(COPY_BRIEF)

        val schema = ShippedContent(RuntimeEnvironment.getApplication().assets).librarySchema().trimEnd()
        assertTrue(copies.single().endsWith("```json\n$schema\n```"))
        assertTrue("\"\$schema\"" in schema)
    }

    // With both on screen, a shared "copied" would light up the wrong one.
    @Test
    fun `confirms only the button that was pressed`() {
        picksText("exercises: [\n  - id: broken")
        chooseFile()
        press("Copiar erro")

        shown(COPIED)
        shown(COPY_BRIEF)
    }

    // Its whole value is the ids in it; offered against no library it would list nothing, confidently.
    @Test
    fun `is not offered before the library has loaded`() {
        show(Files(library = null))

        absent(COPY_BRIEF)
        absent(SHARE_BRIEF)
    }

    // One payload, so the sheet and the clipboard can't drift apart. The OS picks the app; nothing here
    // names one, and the app sends nothing itself.
    @Test
    fun `hands the same brief to the share sheet`() {
        show()
        press(SHARE_BRIEF)
        press(COPY_BRIEF)

        assertEquals(copies, shares)
        assertTrue("pull-ups (reps) — Pull-ups" in shares.single())
    }

    // Backing out of Android's sheet isn't reported to the app at all, so only a sheet that can't open is.
    @Test
    fun `degrades to a note when the share sheet will not open, keeping the clipboard path`() {
        sheetOpens = false
        show()
        press(SHARE_BRIEF)

        shown(SHARE_FAILED)

        press(COPY_BRIEF)

        // The stale failure clears, or it sits under a button that just worked.
        absent(SHARE_FAILED)
        shown(COPIED)
    }

    // pasting from the clipboard

    private fun openPasteBox() {
        show()
        press("Colar YAML")
    }

    // Not previewed yet: the box is where the chat's prose that came along can be seen.
    @Test
    fun `fills the box from the clipboard, and stops there`() {
        val yaml = serializeLibraryYaml(Library(1, listOf(dips), emptyList(), emptyList()))
        clipboard = yaml
        openPasteBox()
        press(PASTE_FROM_CLIPBOARD)

        compose.onNode(hasContentDescription(PASTE_BOX)).assert(hasText(yaml))
        absent("YAML colado")

        press("Revisar texto colado")

        shown("YAML colado")
    }

    // Android can't say whether it was empty or withheld, so the note names both and claims neither.
    @Test
    fun `says nothing came back rather than doing nothing, without claiming to know why`() {
        clipboard = "  \n "
        openPasteBox()
        press(PASTE_FROM_CLIPBOARD)

        shown(CLIPBOARD_EMPTY)
        compose.onNode(hasContentDescription(PASTE_BOX)).assert(hasText(""))
    }

    @Test
    fun `clears the note once the box is filled by hand`() {
        openPasteBox()
        press(PASTE_FROM_CLIPBOARD)
        compose.onNode(hasContentDescription(PASTE_BOX)).performTextReplacement("exercises: []")

        absent(CLIPBOARD_EMPTY)
    }

    // Its words never change, so as a live region it would only ever be noise.
    @Test
    fun `announces nothing about the paste button itself`() {
        openPasteBox()

        button(PASTE_FROM_CLIPBOARD).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))
    }

    @Test
    fun `says so when the clipboard cannot be read at all`() {
        clipboard = null
        openPasteBox()
        press(PASTE_FROM_CLIPBOARD)

        shown("Não foi possível ler a área de transferência.")
    }
}
