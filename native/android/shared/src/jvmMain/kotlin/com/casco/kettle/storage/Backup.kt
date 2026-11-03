package com.casco.kettle.storage

import com.casco.kettle.domain.HISTORY_BACKUP_NAME
import com.casco.kettle.domain.LIBRARY_BACKUP_NAME
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.parseBackupTargets
import com.casco.kettle.domain.serializeBackupTargets
import com.casco.kettle.domain.serializeSessionArchiveYaml
import java.io.File
import java.io.IOException

/** A document in the backup folder: the name it's listed under, and the URI that writes it. */
data class BackupDocument(
    val name: String,
    val uri: String,
)

/**
 * The folder the user chose, as the backup sees it, so the logic can be held to `backup.test.ts`
 * without a provider. The Android adapter is SAF's (`SafBackupFolder` in `:app`); `src/storage/backup.ts`
 * records what each SAF call does that a `file://` path doesn't.
 */
interface BackupFolder {
    /** False for a folder deleted, or one whose grant was revoked in system settings. */
    fun reachable(): Boolean

    fun list(): List<BackupDocument>

    /** A new document named [name]. A provider uniquifies a name that exists rather than overwriting it. */
    fun create(name: String): BackupDocument

    /** Replaces [document]'s whole contents with [text]: truncating, never overwriting from the start. */
    fun write(
        document: BackupDocument,
        text: String,
    )
}

/** Why a backup didn't happen. Only [WriteFailed] carries a platform string, the reason the OS gave. */
sealed interface BackupFailure {
    data object NoFolder : BackupFailure

    data object Unreachable : BackupFailure

    data class WriteFailed(
        val detail: String,
    ) : BackupFailure
}

/**
 * Writes `kettle-library.yaml` (the library file itself, byte for byte, so a backup and an export can't
 * disagree) and `kettle-history.yaml` (the archive, oldest-first) into [folder], `backUpNow` in
 * `src/storage/backup.ts`. Null where it backed up. [targets] is `backup-targets.json`, the record of
 * what the last backup wrote; [exportedAt] is the caller's clock.
 *
 * **Never throws**, and does its IO on the calling thread, so it's the caller's to run off the main
 * one: a folder backed by a cloud provider can take seconds. The runner's finish calls it too in the
 * flip release, and a workout has to outlive a backup that couldn't be written.
 */
fun backUpNow(
    folder: BackupFolder?,
    library: File,
    targets: File,
    sessions: List<Session>,
    exportedAt: String,
): BackupFailure? {
    if (folder == null) return BackupFailure.NoFolder
    return try {
        if (folder.reachable()) {
            writeBoth(folder, library, targets, serializeSessionArchiveYaml(sessions, exportedAt))
            null
        } else {
            BackupFailure.Unreachable
        }
    } catch (_: SecurityException) {
        // The grant lapsed after the folder answered: the write is what finds out, and the answer is
        // the same as a folder that couldn't be reached, choose it again.
        BackupFailure.Unreachable
    } catch (
        // Whatever else the provider throws: an IOException, or a provider's own. A backup that couldn't
        // be written is an answer, never a crash.
        @Suppress("TooGenericExceptionCaught") error: Exception,
    ) {
        BackupFailure.WriteFailed(error.message ?: error.toString())
    }
}

/** The library, when there is one to copy, then the archive, remembering what each landed in. */
private fun writeBoth(
    folder: BackupFolder,
    library: File,
    targets: File,
    archive: String,
) {
    // Listed once for both writes: the folder is the user's, and may hold a great deal more than ours.
    val children = folder.list()
    // Filled in by each write and saved even when one throws, since a document created before the
    // failure still has to be found next time.
    val remembered = loadTargets(targets).toMutableMap()
    try {
        if (library.exists()) writeChild(folder, children, remembered, LIBRARY_BACKUP_NAME, library.readText())
        writeChild(folder, children, remembered, HISTORY_BACKUP_NAME, archive)
    } finally {
        saveTargets(targets, remembered)
    }
}

/**
 * Finds the document this backup wrote last time, or makes it, and writes into it. By name first, and
 * by last time's URI second: a provider whose ids aren't names (Drive's) lists one the name can't find,
 * and creating unconditionally would leave `kettle-history (1).yaml`, `(2)`, `(3)` beside the original.
 * A remembered URI only counts while the folder still lists it, so one the user deleted is made again.
 */
private fun writeChild(
    folder: BackupFolder,
    children: List<BackupDocument>,
    remembered: MutableMap<String, String>,
    name: String,
    text: String,
) {
    val document =
        children.firstOrNull { it.name == name }
            ?: children.firstOrNull { it.uri == remembered[name] }
            ?: folder.create(name)
    // Before the write, not after it: a write that throws has still left a document in the folder.
    remembered[name] = document.uri
    folder.write(document, text)
}

/** A missing or unreadable record is nothing remembered: losing it costs a duplicate, never the backup. */
private fun loadTargets(file: File): Map<String, String> =
    try {
        if (file.exists()) parseBackupTargets(file.readText()) else emptyMap()
    } catch (_: IOException) {
        emptyMap()
    }

/** Never throws, for the same reason. */
private fun saveTargets(
    file: File,
    targets: Map<String, String>,
) {
    try {
        writeAtomically(file, serializeBackupTargets(targets))
    } catch (_: IOException) {
        // Swallowed on purpose: failing to remember costs a duplicate next time, not this backup.
    }
}
