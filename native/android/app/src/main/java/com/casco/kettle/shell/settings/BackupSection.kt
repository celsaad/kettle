package com.casco.kettle.shell.settings

import android.content.res.Resources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.casco.kettle.domain.Session
import com.casco.kettle.shell.R
import com.casco.kettle.storage.BackupFailure

/**
 * Asks for a folder and keeps it, answering what to say about it. Backing out leaves the folder already
 * chosen alone. The grant or the URI, either one lost at a restart, leaves a row that looks set and
 * stops working, so either one says so.
 */
internal suspend fun choose(
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

/** The file sheet's Back up now: the folder written, and what to say about it. */
internal suspend fun backUpNow(
    backup: BackupActions,
    folderUri: String,
    sessions: List<Session>,
    resources: Resources,
): BackupMessage =
    backup.backUp(folderUri, sessions)?.let { BackupMessage(false, resources.describe(it)) }
        ?: BackupMessage(true, resources.getString(R.string.settings_backupDone))

/** Only a refused write carries the platform's own reason, inside a translated sentence. */
private fun Resources.describe(failure: BackupFailure) =
    when (failure) {
        BackupFailure.NoFolder -> getString(R.string.settings_backupNowNoFolder)
        BackupFailure.Unreachable -> getString(R.string.settings_backupUnreachable)
        is BackupFailure.WriteFailed -> getString(R.string.settings_backupWriteFailed, failure.detail)
    }
