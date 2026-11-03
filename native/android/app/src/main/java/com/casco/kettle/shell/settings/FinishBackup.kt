package com.casco.kettle.shell.settings

import android.content.Context
import androidx.core.net.toUri
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.isoInstant
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.storage.BackupFailure
import com.casco.kettle.storage.backUpNow

/**
 * The runner's finish backup (#220): RN sends `sessionFinished` once `completeSession` has written the
 * session, and this backs the log on disk up into the folder the preferences name. Null where none is
 * set, which is nothing to report, and otherwise whether it failed, which RN's completion screen warns
 * from. Blocking, like [backUp]: the caller's to run off the main thread.
 */
fun finishBackup(
    files: StoredFiles,
    backUp: (folderUri: String, sessions: List<Session>) -> BackupFailure?,
): Boolean? {
    val folderUri = files.preferences()?.backupFolderUri ?: return null
    return backUp(folderUri, files.sessionLog().sessions) != null
}

/** [finishBackup] on the real files and the user's folder. */
fun Context.finishBackup(): Boolean? {
    val paths = StoragePaths(filesDir)
    return finishBackup(DiskFiles(paths)) { uri, sessions -> backUp(paths, uri, sessions) }
}

/**
 * [sessions] into the folder at [folderUri] through SAF, now: Settings' Back up now and the finish
 * backup both. Blocking, since a folder backed by a cloud provider can take seconds.
 */
fun Context.backUp(
    paths: StoragePaths,
    folderUri: String,
    sessions: List<Session>,
): BackupFailure? {
    val folder = SafBackupFolder(contentResolver, folderUri.toUri())
    return backUpNow(folder, paths.library, paths.backupTargets, sessions, isoInstant(System.currentTimeMillis()))
}
