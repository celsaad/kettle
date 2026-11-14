package com.casco.kettle.shell.runner

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.casco.kettle.domain.HISTORY_BACKUP_NAME
import com.casco.kettle.domain.NextUp
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.backupFolderLabel
import com.casco.kettle.domain.nextUpView
import com.casco.kettle.shell.R
import com.casco.kettle.shell.home.weekLabel
import com.casco.kettle.shell.theme.KettleType
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

// The receipt's last two lines (#416): what the program has next, and where the backup went.

/** The program's next slot after the one just finished: its week and day, and its workout, null for a rest. */
internal data class NextInProgram(
    val program: String,
    val week: Long,
    val day: String?,
    val workout: String?,
)

/**
 * Today's card's own reading, taken now that the finished session is in the log: so the receipt and
 * Today, a tap away, name the same next slot. Null for a session not run as a program day, or a program
 * since deleted.
 */
@Composable
internal fun rememberNextInProgram(
    session: Session,
    environment: RunnerEnvironment,
): NextInProgram? {
    val library = environment.library
    val programId = session.program
    return remember(session, library, environment.priorSessions) {
        val program = library?.programs?.firstOrNull { it.id == programId } ?: return@remember null
        when (
            val next =
                nextUpView(
                    library,
                    environment.priorSessions,
                    Clock.System.now(),
                    TimeZone.currentSystemDefault(),
                )
        ) {
            is NextUp.Queued -> {
                next.weekNumber?.let {
                    NextInProgram(
                        program.name,
                        it,
                        next.weekDay,
                        next.workout.name,
                    )
                }
            }

            is NextUp.Rest -> {
                NextInProgram(program.name, next.weekNumber, next.weekDay, null)
            }

            null -> {
                null
            }
        }
    }
}

/** What the program has next, for a session run as a program day; nothing for a plain workout. */
@Composable
internal fun NextInProgram(
    view: RunnerView.Completed,
    environment: RunnerEnvironment,
) {
    val next = rememberNextInProgram(view.session, environment) ?: return
    val slot = weekLabel(next.week, next.day)
    val what = next.workout ?: stringResource(R.string.session_complete_rest)
    Text(
        stringResource(R.string.session_complete_nextIn, "$slot · $what", next.program),
        style = KettleType.small,
        color = runnerColors.textSecondary,
    )
}

/**
 * Where the log went, when a folder is set and the copy landed; the one line saying it didn't when it
 * failed. Nothing at all with no folder: the session files are private to the app, so there is no path
 * worth naming.
 */
@Composable
internal fun Backup(
    state: FinishBackupState,
    folder: String?,
) {
    when {
        state == FinishBackupState.FAILED -> {
            Text(
                stringResource(R.string.session_complete_backupFailed),
                style = KettleType.small,
                color = runnerColors.accentCalmOnSoft,
            )
        }

        state == FinishBackupState.OK && folder != null -> {
            Text(
                stringResource(R.string.session_complete_backedUp, "${backupFolderLabel(folder)}/$HISTORY_BACKUP_NAME"),
                style = KettleType.small.copy(fontFamily = FontFamily.Monospace),
                color = runnerColors.textSecondary,
            )
        }
    }
}
