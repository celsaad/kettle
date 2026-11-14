package com.casco.kettle.shell.settings

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.casco.kettle.domain.Preferences
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.reminder.reconcileReminder
import com.casco.kettle.shell.support.DiskSupporterFile
import com.casco.kettle.shell.system.copyToClipboard

/** What the route table draws at `/settings`: the screen on the real files, SAF, the share sheet and the shell. */
@Composable
fun SettingsRoute() {
    val activity = LocalActivity.current ?: return
    val files = LocalLatestFiles.current
    val navigator = LocalNavigator.current
    val paths = remember { StoragePaths(activity.filesDir) }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val actions =
        remember(files, navigator) {
            SettingsActions(
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
        version = remember { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName },
        actions,
    )
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
