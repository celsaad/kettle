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
 *
 * The log is read inside [oneBackupAtATime], not before it: the archive is the whole log, so the
 * backup that writes last decides what the folder holds, and one that read before waiting its turn
 * could write an older log over a newer one. Read inside, whichever order waiters get through in, the
 * last writer has the newest log. `backUp` takes the same lock again, which a JVM lock allows.
 */
fun finishBackup(
    files: StoredFiles,
    backUp: (folderUri: String, sessions: List<Session>) -> BackupFailure?,
): Boolean? =
    oneBackupAtATime {
        val folderUri = files.preferences()?.backupFolderUri ?: return@oneBackupAtATime null
        backUp(folderUri, files.sessionLog().sessions) != null
    }

/** [finishBackup] on the real files and the user's folder. */
fun Context.backUpFinishedSession(): Boolean? {
    val paths = StoragePaths(filesDir)
    return finishBackup(DiskFiles(paths)) { uri, sessions -> backUp(paths, uri, sessions) }
}
