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

/**
 * The launch's prompt after process death (#307), while [LaunchRecovery] holds a session. Composed once,
 * in the shell, over whatever is showing; the dialog is its own window, so it covers RN's surface and
 * the tab bar too.
 *
 * - **Resume** has the service rebuild the session. The prompt stays up, inert, until the service says
 *   how that went, so nothing can be tapped in between, then the runner opens on what it published: it
 *   attaches rather than seeing nothing running and starting a session of its own.
 * - **Finish** writes the session finished, deletes its state file, and runs the finish backup.
 */
@Composable
fun RecoveryPrompt() {
    // Before anything returns early: a Resume's wait for the runner outlives the prompt it was tapped in.
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val files = LocalLatestFiles.current
    val state by LaunchRecovery.state.collectAsState()
    val found = state?.found
    var failure by remember(found) { mutableStateOf<String?>(null) }
    var finishing by remember(found) { mutableStateOf(false) }
    if (found == null) return
    val inert = finishing || state is RecoveryState.Resuming
    val session = found.recovery.session
    val library = files.library
    val name = LocalResources.current.sessionName(library?.let { workoutNameFor(session, it) } ?: session.workout)
    RecoveryDialog(
        found.recovery,
        name,
        failure,
        onResume = {
            if (!inert) {
                LaunchRecovery.resume(context)
                scope.launch { openOnceResumed(recoveredStart(session)) }
            }
        },
        onFinish = {
            if (!inert) {
                finishing = true
                scope.launch {
                    failure = finishFromPrompt(context, files, found)
                    finishing = false
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
 * The runner, opened once the service has answered the Resume with a session, running or, when its last
 * step ran out during the kill, completed. A resume that failed puts the prompt back instead.
 */
private suspend fun openOnceResumed(start: RunnerStart) {
    LaunchRecovery.state.first { it !is RecoveryState.Resuming }
    when (RunnerHost.view.value) {
        is RunnerView.Active, is RunnerView.Completed -> openRunner(start)
        RunnerView.Idle -> Log.w(TAG, "The resume failed; the prompt is back")
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

private const val TAG = "RecoveryPrompt"
