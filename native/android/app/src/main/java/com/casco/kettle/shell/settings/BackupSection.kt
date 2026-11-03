package com.casco.kettle.shell.settings

import android.content.res.Resources
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.backupFolderLabel
import com.casco.kettle.shell.R
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.storage.BackupFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a backup said, ok or not: one line, so a stale "backed up" can't sit under a fresh failure. */
private data class BackupMessage(
    val ok: Boolean,
    val text: String,
)

/**
 * The backup folder's rows (#237): choose it, back up now, and forget it, which only shows once there's
 * a choice to forget. The folder is the user's own path, shown verbatim.
 */
@Composable
internal fun BackupFolder(
    folderUri: String?,
    /** Null until the log's first read lands, and backing up then would archive nothing over the last. */
    sessions: List<Session>?,
    save: PreferencesSaver,
    scope: CoroutineScope,
    backup: BackupActions,
) {
    var message by remember { mutableStateOf<BackupMessage?>(null) }
    var backingUp by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    ActionRow(
        stringResource(R.string.settings_backupFolder),
        folderUri?.let(::backupFolderLabel) ?: stringResource(R.string.settings_backupFolderNone),
    ) {
        message = null
        scope.launch { message = choose(backup, save, resources) }
    }
    val nowDetail =
        when {
            backingUp -> R.string.settings_backupInProgress
            folderUri != null -> R.string.settings_backupNowDetail
            else -> R.string.settings_backupNowNoFolder
        }
    ActionRow(
        stringResource(R.string.settings_backupNow),
        stringResource(nowDetail),
        enabled = folderUri != null && sessions != null && !backingUp,
    ) {
        val folder = folderUri ?: return@ActionRow
        val log = sessions ?: return@ActionRow
        message = null
        backingUp = true
        scope.launch {
            message =
                backup.backUp(folder, log)?.let { BackupMessage(false, resources.describe(it)) }
                    ?: BackupMessage(true, resources.getString(R.string.settings_backupDone))
            backingUp = false
        }
    }
    if (folderUri != null) {
        ActionRow(
            stringResource(R.string.settings_backupForget),
            stringResource(R.string.settings_backupForgetDetail),
        ) {
            message = null
            save.launch { it.copy(backupFolderUri = null) }
        }
    }
    message?.let { Outcome(it.text, accent = !it.ok) }
    Caption(stringResource(R.string.settings_backupWhat))
    // The honest half: the library comes back, the log doesn't, so nothing here says "restore".
    Caption(stringResource(R.string.settings_backupLimits))
}

/**
 * Asks for a folder and keeps it, answering what to say about it. Backing out leaves the folder already
 * chosen alone. The grant or the URI, either one lost at a restart, leaves a row that looks set and
 * stops working, so either one says so.
 */
private suspend fun choose(
    backup: BackupActions,
    save: PreferencesSaver,
    resources: Resources,
): BackupMessage? =
    when (val pick = backup.pickFolder()) {
        FolderPick.Cancelled -> {
            null
        }

        FolderPick.Unavailable -> {
            BackupMessage(false, resources.getString(R.string.settings_backupUnsupported))
        }

        is FolderPick.Picked -> {
            val written = save.write { it.copy(backupFolderUri = pick.uri) }
            if (written.landed &&
                pick.kept
            ) {
                null
            } else {
                BackupMessage(false, resources.getString(R.string.settings_backupFolderNotSaved))
            }
        }
    }

/** Only a refused write carries the platform's own reason, inside a translated sentence. */
private fun Resources.describe(failure: BackupFailure) =
    when (failure) {
        BackupFailure.NoFolder -> getString(R.string.settings_backupNowNoFolder)
        BackupFailure.Unreachable -> getString(R.string.settings_backupUnreachable)
        is BackupFailure.WriteFailed -> getString(R.string.settings_backupWriteFailed, failure.detail)
    }

/** In the folder's place where there's no picker: the export-and-import story it replaced. */
@Composable
internal fun ExportCopy() {
    Text(
        stringResource(R.string.settings_filesLocal),
        style = KettleType.small,
        color = KettleTheme.colors.textSecondary,
    )
    Caption(stringResource(R.string.settings_filesSync))
}
