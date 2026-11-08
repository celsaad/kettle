package com.casco.kettle.shell.runner

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.domain.RunnerStart
import com.casco.kettle.domain.recoveredStart
import com.casco.kettle.domain.workoutNameFor
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LatestFiles
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.shell.nav.openRunner
import com.casco.kettle.shell.settings.backUpFinishedSession
import com.casco.kettle.shell.theme.DialogAction
import com.casco.kettle.shell.theme.KettleAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The launch's prompt after process death (#307), while [LaunchRecovery] is asking: Resume or Finish, or
 * Finish only. Composed once, in the shell, over whatever is showing; the dialog is its own window, so
 * it covers RN's surface and the tab bar too.
 *
 * - **Resume** has the service rebuild the session, then opens the runner on it once it's running, so
 *   the runner attaches rather than seeing nothing running and starting a session of its own.
 * - **Finish** writes the session finished, deletes its state file, and runs the finish backup.
 */
@Composable
fun RecoveryPrompt() {
    // Before anything returns early: a Resume's wait for the runner outlives the prompt it was tapped in.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val files = LocalLatestFiles.current
    val state by LaunchRecovery.state.collectAsState()
    val asking = state as? RecoveryState.Asking
    var failure by remember(asking) { mutableStateOf<String?>(null) }
    var busy by remember(asking) { mutableStateOf(false) }
    if (asking == null) return
    val session = asking.found.recovery.session
    val library = files.library
    val name = LocalResources.current.sessionName(library?.let { workoutNameFor(session, it) } ?: session.workout)
    RecoveryDialog(
        asking.found.recovery,
        name,
        failure,
        onResume = {
            if (!busy) {
                busy = true
                LaunchRecovery.resume(context)
                scope.launch { openOnceRunning(recoveredStart(session)) }
            }
        },
        onFinish = {
            if (!busy) {
                busy = true
                scope.launch {
                    failure = finishFromPrompt(context, files, asking.found)
                    busy = false
                }
            }
        },
    )
}

/**
 * Resume or Finish for [recovery]'s session, called [workoutName] (the user's, verbatim). Nothing
 * dismisses it but an answer: one session at a time, and this one is still open (#295). [failure] is
 * why the disk refused a Finish.
 */
@Composable
fun RecoveryDialog(
    recovery: RunnerRecovery.Finishable,
    workoutName: String,
    failure: String?,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    val resumable = recovery is RunnerRecovery.Prompt
    val body =
        stringResource(
            if (resumable) R.string.session_recovery_resumeBody else R.string.session_recovery_finishOnlyBody,
            workoutName,
        )
    val refused = failure?.let { stringResource(R.string.common_saveFailed, it) }
    val finish = DialogAction(stringResource(R.string.session_recovery_finish), onFinish)
    KettleAlertDialog(
        title = stringResource(R.string.session_recovery_title),
        body = listOfNotNull(body, refused).joinToString("\n\n"),
        onDismissRequest = null,
        confirm = if (resumable) DialogAction(stringResource(R.string.session_recovery_resume), onResume) else finish,
        dismiss = finish.takeIf { resumable },
    )
}

/**
 * The runner, opened once the service has published the resumed session, or its completion when its
 * last step ran out during the kill. A resume the service gave up on puts the prompt back instead.
 */
private suspend fun openOnceRunning(start: RunnerStart) {
    val shown =
        withTimeoutOrNull(RESUME_ANSWER_MS) {
            RunnerHost.view.first { it is RunnerView.Active || it is RunnerView.Completed }
        }
    if (shown != null) {
        openRunner(start)
    } else {
        // Still rebuilding, or it gave up and the prompt is back. A session that does start later is in
        // the shade, whose tap attaches.
        Log.w(TAG, "No resumed session to open the runner on")
    }
}

/** Finish from the prompt, returning why the disk refused, if it did. */
private suspend fun finishFromPrompt(
    context: Context,
    files: LatestFiles,
    found: FoundRecovery,
): String? {
    val app = context.applicationContext
    return LaunchRecovery.finish(
        found,
        write = files::writeSession,
        // Off the prompt's scope: a backup the user's folder takes seconds over outlives it.
        backUp = { background.launch { app.backUpFinishedSession() } },
        next = {
            withContext(Dispatchers.IO) {
                findRunnerRecovery(runnerStateDir(app.noBackupFilesDir), StoragePaths(app.filesDir).sessions)
            }
        },
    )
}

private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private const val RESUME_ANSWER_MS = 5000L
private const val TAG = "RecoveryPrompt"
