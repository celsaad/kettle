package com.casco.kettle.shell.importer

import androidx.compose.ui.test.hasContentDescription
import com.casco.kettle.domain.Library
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Import opened on one source by the file sheet (#413): that source alone, with the preview and Merge
 * after it unchanged, which the rest of the Import suite covers. In `pt`, as the harness is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class ImportStartTest : ImportHarness() {
    /** The row said "Open a .yaml file", so the picker is the next thing, not a second row to tap. */
    @Test
    fun `the file start goes straight to the picker, once`() {
        show(start = ImportStart.File)
        compose.waitForIdle()

        assertEquals(1, picks)
        absent(PASTE_TOGGLE)
        absent(FIRST_PACK)
    }

    /** Cancelling the picker is a normal answer, and leaves the row there to try again. */
    @Test
    fun `a cancelled pick leaves the file row to try again`() {
        show(start = ImportStart.File)

        press(FILE_ROW)

        assertEquals(2, picks)
    }

    @Test
    fun `a file picked from the start goes on to its preview`() {
        picks(Library(1, listOf(dips), emptyList(), emptyList()))
        show(start = ImportStart.File)

        shown("exercises.yaml")
        absent(FILE_ROW)
    }

    @Test
    fun `the paste start opens the paste box and shows nothing else`() {
        show(start = ImportStart.Paste)

        compose.onNode(hasContentDescription(PASTE_BOX)).assertExists()
        assertEquals(0, picks)
        absent(FILE_ROW)
        absent(FIRST_PACK)
    }

    @Test
    fun `the packs start shows the packs alone`() {
        show(start = ImportStart.Packs)

        shown(FIRST_PACK)
        absent(FILE_ROW)
        absent(PASTE_TOGGLE)
    }

    /** The brief goes out, and the reply comes back by paste, so the two are the assistant's start. */
    @Test
    fun `the assistant start offers the brief and the paste its reply comes back by`() {
        show(start = ImportStart.Assistant)

        button("Enviar o formato para um assistente").assertExists()
        button(PASTE_TOGGLE).assertExists()
        absent(FILE_ROW)
        absent(FIRST_PACK)
    }

    @Test
    fun `no start, or one the sheet doesn't know, shows every source`() {
        assertNull(ImportStart.of(null))
        assertNull(ImportStart.of("elsewhere"))
        assertEquals(ImportStart.Packs, ImportStart.of("packs"))
    }
}

private const val FILE_ROW = "Escolher exercises.yaml"
private const val PASTE_TOGGLE = "Colar YAML"
private const val FIRST_PACK = "Firme e Forte"
