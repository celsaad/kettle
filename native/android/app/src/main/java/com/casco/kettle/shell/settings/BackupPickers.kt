package com.casco.kettle.shell.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.casco.kettle.shell.files.StoragePaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// The backup folder's plumbing on SAF, remembered per screen: Settings and the file sheet (#413) both
// build their backup rows on these.

/** The backup folder's two on SAF, as Settings and the file sheet both use them. */
@Composable
internal fun rememberBackupActions(
    activity: Activity,
    paths: StoragePaths,
): BackupActions {
    val pickFolder = rememberFolderPicker(activity)
    return remember(pickFolder) {
        BackupActions(
            pickFolder = pickFolder,
            // Off the main thread, since a folder backed by a cloud provider can take seconds.
            backUp = { uri, sessions -> withContext(Dispatchers.IO) { activity.backUp(paths, uri, sessions) } },
        )
    }
}

/** Whether this device has a folder picker at all; without one a backup row promises what can't work. */
@Composable
internal fun rememberCanChooseFolder(activity: Activity): Boolean =
    remember { Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(activity.packageManager) != null }

/**
 * `OpenDocumentTree` as one suspending call: the folder, with its grant kept, or [FolderPick.Cancelled]
 * where the user backed out. A process killed behind the picker loses the answer, and the row is
 * tapped again.
 */
@Composable
private fun rememberFolderPicker(activity: Activity): suspend () -> FolderPick {
    val waiting = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri -> waiting[0]?.complete(uri) }
    return remember(launcher) {
        suspend {
            val answer = CompletableDeferred<Uri?>().also { waiting[0] = it }
            try {
                launcher.launch(null)
                answer.await()?.let { FolderPick.Picked(it.toString(), activity.contentResolver.keepGrant(it)) }
                    ?: FolderPick.Cancelled
            } catch (_: ActivityNotFoundException) {
                FolderPick.Unavailable
            }
        }
    }
}
