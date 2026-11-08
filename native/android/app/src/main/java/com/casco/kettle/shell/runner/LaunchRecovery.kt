package com.casco.kettle.shell.runner

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.RunnerRecovery
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.SessionFile
import com.casco.kettle.domain.parseSessionYaml
import com.casco.kettle.domain.recoverRunner
import com.casco.kettle.shell.files.writeOrRefusal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.IOException

/**
 * The launch's prompt after process death (#307): the session the process died under, until it's
 * Resumed or Finished. Process-wide like [RunnerHost], whose [RunnerHost.open] it gates.
 */
object LaunchRecovery {
    private val recoveries = MutableStateFlow<RecoveryState?>(null)

    /**
     * A session the process died under, at the launch's prompt or being resumed (#307). While it's set,
     * [RunnerHost.open] starts nothing: one session at a time, and that one is still unanswered.
     */
    val state: StateFlow<RecoveryState?> = recoveries.asStateFlow()

    /**
     * At launch, once the library is ready and before any screen can start a session (#307): a state
     * file left in [stateDir] with no session running means the process died under one, so the prompt
     * goes up. With the service alive there's nothing to ask, since the runner attaches to it instead.
     */
    fun recoverAtLaunch(
        stateDir: File,
        sessionsDir: File,
    ) {
        if (RunnerHost.session != null || RunnerHost.active != null || recoveries.value != null) return
        findRunnerRecovery(stateDir, sessionsDir)?.let(::ask)
    }

    /** Puts up the prompt for [found], which the launch found with no service running. */
    internal fun ask(found: FoundRecovery) {
        recoveries.value = RecoveryState.Asking(found)
    }

    /**
     * The prompt's Finish: [found]'s session [write]n finished and its state file gone, then the finish
     * backup (#220), as after any finish. Then the [next] state file the launch would have found, if
     * any, or sessions can start again. Returns why the disk refused, with the prompt left up.
     */
    suspend fun finish(
        found: FoundRecovery,
        write: suspend (Session) -> Unit,
        backUp: () -> Unit,
        next: suspend () -> FoundRecovery?,
    ): String? {
        val refused = finishRecovered(found, write)
        if (refused != null) return refused
        backUp()
        recoveries.value = next()?.let { RecoveryState.Asking(it) }
        return null
    }

    /**
     * The prompt's Resume: the service rebuilds the session from its state file, and the runner opens
     * once it's running. From the visible prompt, which is what lets a foreground service start (#293).
     */
    fun resume(context: Context) {
        val asking = recoveries.value as? RecoveryState.Asking ?: return
        recoveries.value = RecoveryState.Resuming(asking.found)
        ContextCompat.startForegroundService(context, RunnerService.resumeIntent(context))
    }

    /** The service couldn't rebuild it: the prompt again, whose Finish still works from the files. */
    internal fun resumeFailed() {
        recoveries.update { if (it is RecoveryState.Resuming) RecoveryState.Asking(it.found) else it }
    }

    /** Finished from the prompt, or resumed and running: sessions can start again. */
    fun answered() {
        recoveries.value = null
    }
}

/** A state file the launch found (#307), and what its session offers: Resume or Finish, or Finish only. */
data class FoundRecovery(
    val stateFile: File,
    val recovery: RunnerRecovery.Finishable,
)

/**
 * The first state file in [stateDir] whose session the prompt has to ask about, reading each against
 * its file in [sessionsDir] (#295). One the session can't use any more (its file gone, finished or
 * unparseable) is deleted on the way past, with nobody told; one whose files can't be read just now is
 * left for the next launch. Null when nothing is left to ask, which is every launch but one after a kill.
 *
 * Never throws: launch must not crash on a file the service left behind.
 */
fun findRunnerRecovery(
    stateDir: File,
    sessionsDir: File,
): FoundRecovery? {
    // Name order, so the prompt asks about the same one first on every launch.
    val files = stateDir.listFiles().orEmpty().sortedBy { it.name }
    for (file in files) {
        val sessionId = sessionIdOfStateFile(file) ?: continue
        val found =
            runCatching { recoveryFor(file, sessionsDir.resolve("$sessionId.yaml")) }
                .onFailure { Log.w(TAG, "Couldn't read the state file ${file.name}; left for the next launch", it) }
                .getOrNull()
        if (found != null) return found
    }
    return null
}

private fun recoveryFor(
    stateFile: File,
    sessionFile: File,
): FoundRecovery? {
    val text =
        try {
            stateFile.readText()
        } catch (_: IOException) {
            return null
        }
    return when (val recovery = recoverRunner(text, readSessionFile(sessionFile))) {
        is RunnerRecovery.Finishable -> {
            FoundRecovery(stateFile, recovery)
        }

        RunnerRecovery.DeleteSilently -> {
            if (!stateFile.delete()) Log.w(TAG, "Couldn't delete the state file ${stateFile.name}")
            null
        }

        RunnerRecovery.Leave -> {
            null
        }
    }
}

private fun readSessionFile(file: File): SessionFile =
    if (!file.exists()) {
        SessionFile.Absent
    } else {
        try {
            sessionIn(file.readText(), file.lastModified())
        } catch (_: IOException) {
            SessionFile.Unreadable
        }
    }

private fun sessionIn(
    text: String,
    modifiedAt: Long,
): SessionFile =
    (parseSessionYaml(text) as? ParseResult.Ok)?.let { SessionFile.Read(it.value, modifiedAt) } ?: SessionFile.Corrupt

/**
 * Finish from the prompt (#295): [write]s the session as it stands, stamped with its end, then deletes
 * the state file, so the prompt doesn't come back. Returns why the disk refused, with the state file
 * kept, so the session is still there to finish next time; null once it's done. The finish backup is
 * the caller's to start, as after any finish.
 */
internal suspend fun finishRecovered(
    found: FoundRecovery,
    write: suspend (Session) -> Unit,
): String? {
    val refused = writeOrRefusal { write(found.recovery.finished) }
    if (refused == null && !found.stateFile.delete()) {
        // Written and finished: a state file left now names a finished session, which the next launch
        // deletes silently.
        Log.w(TAG, "Couldn't delete the state file ${found.stateFile.name}")
    }
    return refused
}

private const val TAG = "LaunchRecovery"

/** Where the launch's prompt has got to (#307), as [LaunchRecovery.state] holds it; null once there's none. */
sealed interface RecoveryState {
    val found: FoundRecovery

    /** The prompt is up, and nothing can start until it's answered. */
    data class Asking(
        override val found: FoundRecovery,
    ) : RecoveryState

    /** Resume was tapped, and the service is rebuilding the session; still nothing else can start. */
    data class Resuming(
        override val found: FoundRecovery,
    ) : RecoveryState
}
