package com.casco.kettle.shell.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.backupFolderLabel
import com.casco.kettle.shell.R
import com.casco.kettle.shell.list.ListRowMinHeight
import com.casco.kettle.shell.list.ListRowSeparator
import com.casco.kettle.shell.settings.BackupActions
import com.casco.kettle.shell.settings.BackupMessage
import com.casco.kettle.shell.settings.Outcome
import com.casco.kettle.shell.settings.PreferencesSaver
import com.casco.kettle.shell.settings.choose
import com.casco.kettle.shell.settings.describe
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import com.casco.kettle.shell.theme.KettleTheme
import com.casco.kettle.shell.theme.KettleType
import com.casco.kettle.shell.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The backup folder as one row (#413): the folder, and Back up now beside it once there is one. With
 * none, the row chooses one; with one, it asks whether to change it or forget it, which Settings spread
 * over two more rows. The writing and its messages are Settings' own (`choose`, `describe`).
 */
@Composable
internal fun BackupRow(
    folderUri: String?,
    /** Null until the log's first read lands, and backing up then would archive nothing over the last. */
    sessions: List<Session>?,
    save: PreferencesSaver,
    scope: CoroutineScope,
    backup: BackupActions,
) {
    val colors = KettleTheme.colors
    val resources = LocalResources.current
    var message by remember { mutableStateOf<BackupMessage?>(null) }
    var backingUp by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val chooseFolder = {
        message = null
        scope.launch { message = choose(backup, save, resources) }
    }
    val label = folderUri?.let(::backupFolderLabel)
    ListRowSeparator()
    Row(
        Modifier.fillMaxWidth().heightIn(min = ListRowMinHeight),
        horizontalArrangement = Arrangement.spacedBy(Spacing.two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Folder(label, Modifier.weight(1f)) { if (label == null) chooseFolder() else asking = true }
        if (folderUri != null) {
            BackUpNow(busy = sessions == null || backingUp, backingUp) {
                val log = sessions ?: return@BackUpNow
                message = null
                backingUp = true
                scope.launch {
                    message =
                        backup.backUp(folderUri, log)?.let { BackupMessage(false, resources.describe(it)) }
                            ?: BackupMessage(true, resources.getString(R.string.settings_backupDone))
                    backingUp = false
                }
            }
        }
    }
    message?.let { Outcome(it.text, accent = !it.ok) }
    if (asking && label != null) {
        FolderDialog(
            label,
            onDismiss = { asking = false },
            onChange = {
                asking = false
                chooseFolder()
            },
            onForget = {
                asking = false
                message = null
                save.launch { it.copy(backupFolderUri = null) }
            },
        )
    }
}

/** "Backup folder" and where it is, or that there's none yet: one button, named by its two texts. */
@Composable
private fun Folder(
    label: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Column(
        modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.two),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.settings_backupFolder), style = KettleType.heading, color = colors.text)
        Text(
            label ?: stringResource(R.string.settings_backupFolderNone),
            style = KettleType.small,
            color = colors.textSecondary,
        )
    }
}

/** Back up now, beside the folder: "Writing…" while it runs, and off until the log has been read. */
@Composable
private fun BackUpNow(
    busy: Boolean,
    backingUp: Boolean,
    onClick: () -> Unit,
) {
    val colors = KettleTheme.colors
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .border(1.dp, colors.textSecondary, PillShape)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.three),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(if (backingUp) R.string.settings_backupInProgress else R.string.settings_backupNow),
            style = KettleType.smallMedium,
            color = colors.text,
        )
    }
}

/** The chosen folder, titled with its own path: change it, or forget it, which Settings' rows explain. */
@Composable
private fun FolderDialog(
    label: String,
    onDismiss: () -> Unit,
    onChange: () -> Unit,
    onForget: () -> Unit,
) = KettleAlertDialog(
    title = label,
    body = stringResource(R.string.settings_backupForgetDetail),
    onDismissRequest = onDismiss,
    confirm = DialogAction(stringResource(R.string.file_backupChange), onChange),
    dismiss = DialogAction(stringResource(R.string.settings_backupForget), onForget),
)

private val PillShape = RoundedCornerShape(percent = 50)
