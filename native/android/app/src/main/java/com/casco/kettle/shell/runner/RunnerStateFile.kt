package com.casco.kettle.shell.runner

import android.util.Log
import com.casco.kettle.domain.RunnerSnapshot
import com.casco.kettle.domain.encodeRunnerSnapshot
import com.casco.kettle.storage.writeAtomically
import java.io.File

/**
 * The running session's state file (#295): the snapshot as versioned JSON, so a session the process
 * died under can be rebuilt at the next launch (#307). One file per session in [dir], named for it, so
 * a snapshot too corrupt to read still says which session it was ([runnerStateFileFor]).
 *
 * Written at once on every transition, debounced by [debounceMs] for an input edit, so dialling reps
 * doesn't write per tap, and flushed when the runner leaves the screen, which is when the process is
 * likeliest to die. Deleted when the session ends, so a finished session leaves nothing behind.
 *
 * Only ever touched from the [loop]'s thread. A write the disk refuses is logged and the session keeps
 * running in memory (#297): losing the rebuild is a smaller cost than losing the workout.
 */
class RunnerStateFile(
    val dir: File,
    private val loop: RunnerLoop,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private var pending: RunnerSnapshot? = null
    private var timer: Cancellable? = null

    fun save(
        snapshot: RunnerSnapshot,
        debounce: Boolean,
    ) {
        if (!debounce) {
            dropPending()
            write(snapshot)
            return
        }
        pending = snapshot
        if (timer == null) timer = loop.postDelayed(debounceMs, ::flush)
    }

    /** Writes a debounced snapshot now, if one is waiting. */
    fun flush() {
        val snapshot = pending ?: return
        dropPending()
        write(snapshot)
    }

    /** Session [sessionId]'s, which has ended. */
    fun delete(sessionId: String) {
        dropPending()
        val file = runnerStateFileFor(dir, sessionId)
        if (file.exists() && !file.delete()) Log.w(TAG, "Couldn't delete the runner's state file")
    }

    private fun dropPending() {
        timer?.cancel()
        timer = null
        pending = null
    }

    private fun write(snapshot: RunnerSnapshot) {
        try {
            writeAtomically(runnerStateFileFor(dir, snapshot.sessionId), encodeRunnerSnapshot(snapshot))
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            // Any failure, not just the disk's: the debounced write runs from its own timer, outside the
            // effects' guard, so nothing else would catch it (#297).
            Log.w(TAG, "Couldn't write the runner's state file; the session runs on in memory", e)
        }
    }
}

/**
 * Where the state files live: app-private and outside Auto Backup, since restoring a running
 * session's state onto another phone would offer to resume a workout nobody is doing. Named in one
 * place for the recovery at launch (#307) to read.
 */
fun runnerStateDir(noBackupFilesDir: File): File = noBackupFilesDir.resolve("runner")

/** Session [sessionId]'s state file in [dir]. Session ids already name files (`sessions/<id>.yaml`). */
fun runnerStateFileFor(
    dir: File,
    sessionId: String,
): File = dir.resolve("$sessionId$STATE_SUFFIX")

/** The session [file] is the state file of, or null for anything else in the directory. */
internal fun sessionIdOfStateFile(file: File): String? =
    file.name.takeIf { file.isFile && it.endsWith(STATE_SUFFIX) }?.removeSuffix(STATE_SUFFIX)

// Not `.json.partial`, which is `writeAtomically`'s temporary: a reader must never take one for a state file.
private const val STATE_SUFFIX = ".json"

private const val DEBOUNCE_MS = 1000L
private const val TAG = "RunnerStateFile"
