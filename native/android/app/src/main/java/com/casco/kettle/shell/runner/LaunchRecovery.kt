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
import com.casco.kettle.domain.strandedSessions
import com.casco.kettle.shell.files.writeOrRefusal
import com.casco.kettle.storage.writeSessionFile
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
     * goes up, after [clearAfterDeath] has taken down what the dead service left in the shade. With the
     * service alive, or a prompt already up or resuming, there's nothing to ask or clear: the runner
     * attaches, or the prompt carries on, and a clear would stop the service doing the work.
     *
     * The same quiet launch first ends what RN's runner left running ([sweepStrandedSessions]), once,
     * with [sweptMarker] saying it's done.
     */
    fun recoverAtLaunch(
        stateDir: File,
        sessionsDir: File,
        sweptMarker: File,
        clearAfterDeath: () -> Unit,
    ) {
        if (RunnerHost.session != null || RunnerHost.active != null || recoveries.value != null) return
        sweepStrandedSessions(stateDir, sessionsDir, sweptMarker)
        clearAfterDeath()
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
        try {
            ContextCompat.startForegroundService(context, RunnerService.resumeIntent(context))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: RuntimeException,
        ) {
            // Refused before the service ran, so the files are as the prompt read them: ask again.
            Log.w(TAG, "Couldn't start the service to resume", e)
            resumeFailed(asking.found)
        }
    }

    /**
     * The service couldn't rebuild it, so the prompt goes up again, about [found]: the files read
     * afresh, since a rebuild that failed partway may have written to them, and a prompt still holding
     * the old snapshot would Finish over that write. Null, with nothing left to ask, lets sessions start.
     * Every way a resume can fail ends here or in [answered], so the gate can't stay shut.
     */
    internal fun resumeFailed(found: FoundRecovery?) {
        recoveries.update { if (it is RecoveryState.Resuming) found?.let { RecoveryState.Asking(it) } else it }
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

/**
 * Ends each session RN's runner left running ([strandedSessions]), the first launch after the flip
 * release (#308): one RN had going as the update installed, or one it lost to a kill, which RN
 * wrote no state file for and no prompt can ask about. Silent, as RN's own crash path was, and at the
 * file's last write, as a Finish Only is. Once per install: [marker] is written when every session
 * was read and every stamp landed, and a launch that fell short tries again next time. From then on
 * an unfinished session is the native runner's, whose state file its prompt answers.
 *
 * Never throws, like the rest of the launch's recovery.
 */
fun sweepStrandedSessions(
    stateDir: File,
    sessionsDir: File,
    marker: File,
) {
    if (marker.exists()) return
    runCatching {
        val runnerIds =
            stateDir
                .listFiles()
                .orEmpty()
                .mapNotNull(::sessionIdOfStateFile)
                .toSet()
        val files = sessionsDir.listFiles { file -> file.name.endsWith(".yaml") }.orEmpty().map(::readSessionFile)
        val read = files.filterIsInstance<SessionFile.Read>()
        strandedSessions(read, runnerIds).forEach { writeSessionFile(sessionsDir, it) }
        // An unreadable file may be a stranded session that reads next time; a corrupt one never will.
        if (files.none { it == SessionFile.Unreadable }) {
            marker.parentFile?.mkdirs()
            marker.writeText("")
        }
    }.onFailure { Log.w(TAG, "Couldn't end what RN's runner left running; trying again next launch", it) }
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
