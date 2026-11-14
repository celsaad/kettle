package com.casco.kettle.shell.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.casco.kettle.domain.Preferences
import com.casco.kettle.shell.export.FileShare
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.reminder.reconcileReminder
import com.casco.kettle.shell.support.DiskSupporterFile
import com.casco.kettle.shell.system.copyToClipboard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the route table draws at `/settings`: the screen on the real files, SAF, the share sheet and the shell. */
@Composable
fun SettingsRoute() {
    val activity = LocalActivity.current ?: return
    val files = LocalLatestFiles.current
    val navigator = LocalNavigator.current
    val paths = remember { StoragePaths(activity.filesDir) }
    val share = remember { FileShare(activity, paths) }
    val backup = rememberBackupActions(activity, paths)
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val actions =
        remember(files, backup, navigator) {
            SettingsActions(
                backup,
                ExportActions(library = share::library, history = share::history),
                ContactActions(openLink = activity::openLink, copy = activity::copyToClipboard),
                ReminderActions(
                    askNotifications = {
                        if (activity.needsNotificationPermission()) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    changed = { preferences -> files.reconcileReminder(activity, preferences) },
                ),
                open = navigator::push,
            )
        }
    SettingsScreen(
        files,
        remember { DiskSupporterFile(paths.supporter) },
        canChooseFolder = rememberCanChooseFolder(activity),
        version = remember { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName },
        actions,
    )
}

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

/** The reminder's alarm, kept in step with the setting just written, once the log has been read (#236). */
private fun LatestFiles.reconcileReminder(
    activity: Activity,
    preferences: Preferences,
) {
    // Before the first read there's no newest session to anchor on; that read reconciles instead.
    val log = sessionLog ?: return
    activity.reconcileReminder(log, preferences)
}

private fun Activity.needsNotificationPermission() =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/** False where nothing on the device opens [url]: no mail app is not rare on Android. */
private fun Activity.openLink(url: String): Boolean =
    try {
        val uri = url.toUri()
        startActivity(Intent(if (uri.scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
