package com.casco.kettle.shell.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.core.net.toUri
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.isoInstant
import com.casco.kettle.shell.export.YAML_MIME_TYPE
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.storage.BackupDocument
import com.casco.kettle.storage.BackupFailure
import com.casco.kettle.storage.BackupFolder
import com.casco.kettle.storage.backUpNow
import java.io.FileNotFoundException
import java.io.IOException

/**
 * The folder the user chose in Settings, through the Storage Access Framework: [tree] is the
 * `OpenDocumentTree` result whose grant [keepGrant] persisted. `docs/sdk-57-api-notes.md` records how
 * a `content://` document differs from a `file://` path; the two that cost data are kept below, at the
 * calls they're about.
 */
class SafBackupFolder(
    private val resolver: ContentResolver,
    private val tree: Uri,
) : BackupFolder {
    private val folder get() =
        DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )

    /** Deleted, and the grant revoked in system settings, both answer false: neither is worth throwing for. */
    override fun reachable(): Boolean =
        try {
            resolver.query(folder, arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)?.use { it.moveToFirst() } ==
                true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: FileNotFoundException) {
            false
        }

    /**
     * The folder's children by their display names, which a provider whose ids aren't names (Drive's)
     * still reports. Their URIs are built as expo-file-system's `list()` built them, so a record RN's
     * last backup left still matches.
     */
    override fun list(): List<BackupDocument> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(folder))
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
        val cursor =
            resolver.query(children, columns, null, null, null) ?: throw IOException("Couldn't list the folder")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, it.getString(0))
                    add(BackupDocument(it.getString(1).orEmpty(), uri.toString()))
                }
            }
        }
    }

    /** Uniquified by the provider where [name] exists, which is why the backup looks before it creates. */
    override fun create(name: String): BackupDocument {
        val uri = DocumentsContract.createDocument(resolver, folder, YAML_MIME_TYPE, name) ?: throw IOException(name)
        return BackupDocument(name, uri.toString())
    }

    /**
     * `"wt"`, never `"w"`: a SAF document opened `"w"` is overwritten from offset zero and not truncated,
     * so a backup shorter than the last one kept the old one's tail welded on, a `kettle-library.yaml`
     * that won't parse or parses into something wrong. Truncate-and-write rather than delete-and-create,
     * which leaves a moment with no backup at all. `use` closes the descriptor, which is what tells the
     * provider the write is done; RN needed a patch to expo-file-system for that.
     */
    override fun write(
        document: BackupDocument,
        text: String,
    ) {
        val stream = resolver.openOutputStream(document.uri.toUri(), "wt") ?: throw IOException(document.name)
        stream.use { it.write(text.toByteArray()) }
    }
}

/**
 * Keeps the grant on [tree] past this process, which is what makes a folder chosen once keep working.
 * False where the provider wouldn't: the folder works for now, and won't after a restart.
 */
fun ContentResolver.keepGrant(tree: Uri): Boolean =
    try {
        takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    } catch (_: SecurityException) {
        false
    }

/**
 * [sessions] into the folder at [folderUri] through SAF, now: Settings' Back up now and the finish
 * backup both. Blocking, since a folder backed by a cloud provider can take seconds, and
 * [oneBackupAtATime], since either can start while the other is still writing.
 */
fun Context.backUp(
    paths: StoragePaths,
    folderUri: String,
    sessions: List<Session>,
): BackupFailure? =
    oneBackupAtATime {
        val folder = SafBackupFolder(contentResolver, folderUri.toUri())
        backUpNow(folder, paths.library, paths.backupTargets, sessions, isoInstant(System.currentTimeMillis()))
    }

/**
 * Runs [backUp] while no other backup runs: two at once write the same documents and
 * `backup-targets.json`, and the second can create a duplicate the first's record never names. A
 * lock rather than a coroutine mutex, since every caller is already blocking off the main thread.
 */
internal fun <T> oneBackupAtATime(backUp: () -> T): T = synchronized(backupLock, backUp)

private val backupLock = Any()
