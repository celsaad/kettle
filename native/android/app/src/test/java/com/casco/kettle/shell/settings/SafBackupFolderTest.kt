package com.casco.kettle.shell.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.casco.kettle.storage.BackupDocument
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The SAF lesson that costs data rather than failing: a document opened `"w"` is overwritten from the
 * start and not truncated, so a backup shorter than the last kept the old one's tail welded on.
 * `backup.test.ts`'s truncation case, against a provider whose files open in the mode asked for, as a
 * real one's do. Writing `"w"` in [SafBackupFolder.write] fails this.
 */
@RunWith(RobolectricTestRunner::class)
class SafBackupFolderTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `truncates, so a shorter backup leaves no tail of the previous one behind`() {
        backing = folder.newFile("kettle-library.yaml").apply { writeText("exercises:\n  - id: one\n  - id: two\n") }
        Robolectric.setupContentProvider(Documents::class.java, AUTHORITY)
        val resolver = RuntimeEnvironment.getApplication().contentResolver

        SafBackupFolder(resolver, Uri.parse("content://$AUTHORITY/tree/primary%3ADocuments"))
            .write(BackupDocument("kettle-library.yaml", "content://$AUTHORITY/document/library"), "exercises: []\n")

        assertEquals("exercises: []\n", backing.readText())
    }

    /** A provider of one document, [backing], opened in whatever mode the resolver passes. */
    class Documents : ContentProvider() {
        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor = ParcelFileDescriptor.open(backing, ParcelFileDescriptor.parseMode(mode))

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ) = 0
    }

    private companion object {
        const val AUTHORITY = "com.casco.kettle.test.documents"
    }
}

/** The provider's one document. A provider is constructed by Robolectric, so it can't be handed one. */
private lateinit var backing: File
