package com.casco.kettle.shell.settings

import android.content.Context
import com.casco.kettle.domain.Session
import com.casco.kettle.shell.files.DiskFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.files.StoredFiles
import com.casco.kettle.storage.BackupFailure

/**
 * The runner's finish backup (#220): RN sends `sessionFinished` once `completeSession` has written the
 * session, and this backs the log on disk up into the folder the preferences name. Null where none is
 * set, which is nothing to report, and otherwise whether it failed, which RN's completion screen warns
 * from. Blocking, like `backUp`: the caller's to run off the main thread.
 */
fun finishBackup(
    files: StoredFiles,
    backUp: (folderUri: String, sessions: List<Session>) -> BackupFailure?,
): Boolean? {
    val folderUri = files.preferences()?.backupFolderUri ?: return null
    return backUp(folderUri, files.sessionLog().sessions) != null
}

/** [finishBackup] on the real files and the user's folder. */
fun Context.backUpFinishedSession(): Boolean? {
    val paths = StoragePaths(filesDir)
    return finishBackup(DiskFiles(paths)) { uri, sessions -> backUp(paths, uri, sessions) }
}
