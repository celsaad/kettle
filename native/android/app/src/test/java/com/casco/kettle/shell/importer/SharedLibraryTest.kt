package com.casco.kettle.shell.importer

import android.content.Intent
import android.net.Uri
import android.text.SpannedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

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
    fun `the inbox gives its library once, and only for its token`() {
        val inbox = SharedLibraryInbox()
        val token = inbox.put(SharedLibrary.Text("version: 1"))
        assertNull(inbox.take("true"))
        assertEquals(SharedLibrary.Text("version: 1"), inbox.take(token))
        assertNull(inbox.take(token))
    }

    /** The alias is exported, so an app can skip the filters: a `file:` path would be read as Kettle. */
    @Test
    fun `a stream that isn't another app's content file is not taken`() {
        val own = Uri.parse("content://com.casco.kettle.shell.files/shared/exercises.yaml")
        val ownAsUser = Uri.parse("content://0@com.casco.kettle.shell.files/shared/exercises.yaml")
        for (uri in listOf(Uri.parse("file:///data/data/com.casco.kettle/files/x.yaml"), own, ownAsUser)) {
            val sent = send("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
            assertNull(uri.toString(), sent.sharedLibrary(ownAuthority = "com.casco.kettle.shell.files"))
            assertNull(
                uri.toString(),
                view(uri, "text/yaml").sharedLibrary(ownAuthority = "com.casco.kettle.shell.files"),
            )
        }
    }

    /** Some apps put a caption beside the file: reviewed instead, it would be refused. */
    @Test
    fun `a stream wins over the text beside it`() {
        val intent = send("text/plain").putExtra(Intent.EXTRA_TEXT, "plan.yaml").putExtra(Intent.EXTRA_STREAM, file)
        assertEquals(SharedLibrary.File(file), intent.sharedLibrary())
    }

    /** A styled `CharSequence` comes back null when read as a `String`. */
    @Test
    fun `styled shared text is still the library's text`() {
        val intent = send("text/plain").putExtra(Intent.EXTRA_TEXT, SpannedString("version: 1"))
        assertEquals(SharedLibrary.Text("version: 1"), intent.sharedLibrary())
    }

    /**
     * The system matched the filter on the provider's type, and left it out of the intent: taken as it is,
     * with no second ask of the provider on the main thread (#569). An explicit type is still held.
     */
    @Test
    fun `a file opened with no type is taken, and one typed otherwise is not`() {
        assertEquals(SharedLibrary.File(file), view(file, null).sharedLibrary())
        assertNull(view(file, "application/octet-stream").sharedLibrary())
    }

    /** The manifest's filters and [VIEWED_TYPES] are two copies of one list: held together here. */
    @Test
    fun `the manifest routes exactly the decided types to Kettle`() {
        val packages = RuntimeEnvironment.getApplication().packageManager

        fun resolves(intent: Intent) =
            packages.queryIntentActivities(intent, 0).any {
                it.activityInfo.packageName ==
                    RuntimeEnvironment.getApplication().packageName
            }
        for (type in VIEWED_TYPES) assertTrue(type, resolves(view(file, type)))
        assertTrue(resolves(send("text/plain")))
        assertFalse(resolves(view(file, "application/octet-stream")))
        assertFalse(resolves(send("image/png")))
    }
}
