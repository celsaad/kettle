package com.casco.kettle.shell.importer

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which intents hand Kettle a library (#514), by the manifest's decided filters: `SEND` of `text/plain`,
 * and `VIEW` of a `content:` file typed as text or YAML, never `application/octet-stream`.
 */
@RunWith(RobolectricTestRunner::class)
class SharedLibraryTest {
    private val file = Uri.parse("content://downloads/public/7")

    private fun send(type: String) = Intent(Intent.ACTION_SEND).setType(type)

    private fun view(
        uri: Uri,
        type: String?,
    ) = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)

    @Test
    fun `text shared from a chat is the library's text`() {
        val intent = send("text/plain").putExtra(Intent.EXTRA_TEXT, "version: 1")
        assertEquals(SharedLibrary.Text("version: 1"), intent.sharedLibrary())
    }

    @Test
    fun `a text file shared by its stream is the file`() {
        val intent = send("text/plain").putExtra(Intent.EXTRA_STREAM, file)
        assertEquals(SharedLibrary.File(file), intent.sharedLibrary())
    }

    @Test
    fun `blank shared text falls back to its stream, and with neither there is nothing`() {
        assertEquals(
            SharedLibrary.File(file),
            send("text/plain").putExtra(Intent.EXTRA_TEXT, "  ").putExtra(Intent.EXTRA_STREAM, file).sharedLibrary(),
        )
        assertNull(send("text/plain").sharedLibrary())
    }

    @Test
    fun `a share of anything but text is not a library`() {
        assertNull(send("image/png").putExtra(Intent.EXTRA_STREAM, file).sharedLibrary())
    }

    @Test
    fun `a file opened as text or YAML is the file`() {
        for (type in listOf("text/plain", "application/x-yaml", "application/yaml", "text/yaml", "Text/YAML")) {
            assertEquals(type, SharedLibrary.File(file), view(file, type).sharedLibrary())
        }
    }

    /** It would list Kettle for every binary file; such a `.yaml` goes through Import's picker. */
    @Test
    fun `a file opened as octet-stream is not taken`() {
        assertNull(view(file, "application/octet-stream").sharedLibrary())
    }

    @Test
    fun `a kettle link is a link, not a library`() {
        assertNull(view(Uri.parse("kettle://history"), null).sharedLibrary())
    }

    /** Recents hands the task's first intent back: importing it again would offer the same merge twice. */
    @Test
    fun `an intent Recents replays is not taken again`() {
        val intent =
            send(
                "text/plain",
            ).putExtra(Intent.EXTRA_TEXT, "version: 1").addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(intent.sharedLibrary())
    }

    /** An activity made again is handed the same intent, which mustn't import a second time. */
    @Test
    fun `a library taken off its intent isn't found there again`() {
        val text = send("text/plain").putExtra(Intent.EXTRA_TEXT, "version: 1")
        val opened = view(file, "text/yaml")
        text.forgetSharedLibrary()
        opened.forgetSharedLibrary()
        assertNull(text.sharedLibrary())
        assertNull(opened.sharedLibrary())
    }

    @Test
    fun `the inbox gives its library once`() {
        SharedLibraryInbox.put(SharedLibrary.Text("version: 1"))
        assertEquals(SharedLibrary.Text("version: 1"), SharedLibraryInbox.take())
        assertNull(SharedLibraryInbox.take())
    }
}
