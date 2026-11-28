package com.casco.kettle.shell.importer

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MAX_IMPORT_BYTES
import com.casco.kettle.domain.serializeLibraryYaml
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A library another app handed over (#514), landing in Import's preview as a paste or a picked file
 * does, with nothing written before Merge. In `pt`, as the harness is.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt-w400dp-h4000dp")
class ImportSharedTest : ImportHarness() {
    private val incoming = Library(1, listOf(dips), emptyList(), emptyList())

    private fun share(
        start: ImportStart,
        shared: Incoming,
    ): Files {
        sharedIn = shared
        return show(start = start)
    }

    @Test
    fun `shared text goes straight to its preview, and nothing is written before Merge`() {
        val files = share(ImportStart.Paste, Incoming.Text(serializeLibraryYaml(incoming)))

        shown("YAML colado")
        merge().assertExists()
        assertEquals(0, picks)
        files.nothingWritten()
    }

    /** A chat sends prose around one fenced block: the block is reviewed, the box keeps all that came (#569). */
    @Test
    fun `a chat reply's fenced block goes to its preview`() {
        val chat = "Sure! Here it is:\n```yaml\n" + serializeLibraryYaml(incoming) + "```\n"
        val files = share(ImportStart.Paste, Incoming.Text(chat))

        shown("YAML colado")
        files.nothingWritten()
    }

    /** Two blocks could be a plan and its alternative: refused as it came, with the text in the box to fix. */
    @Test
    fun `a reply with two blocks is refused, with the text in the box to fix`() {
        val chat = "Sure! Here it is:\n```yaml\na: 1\n```\nor\n```yaml\nb: 2\n```\n"
        val files = share(ImportStart.Paste, Incoming.Text(chat))

        compose.onNode(hasContentDescription(PASTE_BOX)).assertTextContains("Sure! Here it is:", substring = true)
        absent("YAML colado")
        files.nothingWritten()
    }

    @Test
    fun `a share that lands before the library is read waits for it`() {
        sharedIn = Incoming.Text(serializeLibraryYaml(incoming))
        val io = Steps()
        val files = show(io = io, readFirst = false, start = ImportStart.Paste)
        absent("YAML colado")

        io.run()
        compose.waitForIdle()

        shown("YAML colado")
        files.nothingWritten()
    }

    /** Past the limit nothing is laid out in the box: it's refused as it arrived. */
    @Test
    fun `shared text too large is refused without filling the box`() {
        val files = share(ImportStart.Paste, Incoming.Text("#".repeat(MAX_IMPORT_BYTES + 1)))

        compose.onNode(hasContentDescription(PASTE_BOX)).assertTextEquals(PASTE_BOX, includeEditableText = false)
        compose.onNode(hasTextMatching(Regex(".*${MAX_IMPORT_BYTES / 1024}.*"))).assertExists()
        files.nothingWritten()
    }

    @Test
    fun `a shared file goes to its preview without opening the picker`() {
        var reads = 0
        val shared =
            Incoming.File {
                reads += 1
                FilePick.Picked("plan.yaml", 512, serializeLibraryYaml(incoming))
            }
        val files = share(ImportStart.File, shared)

        shown("plan.yaml")
        assertEquals(1, reads)
        assertEquals(0, picks)
        files.nothingWritten()
    }

    @Test
    fun `a shared file too large is refused, with the file row there to pick another`() {
        val files = share(ImportStart.File, Incoming.File { FilePick.TooLarge })

        compose.onNode(hasTextMatching(Regex(".*${MAX_IMPORT_BYTES / 1024}.*"))).assertExists()
        button("Escolher exercises.yaml").assertExists()
        files.nothingWritten()
    }
}
