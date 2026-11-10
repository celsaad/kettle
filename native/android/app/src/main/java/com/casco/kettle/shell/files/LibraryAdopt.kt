package com.casco.kettle.shell.files

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The runner's adopt (#220): a load the session ran becomes the exercise's target in the library. For
 * the life of the process, since a write may outlive the activity that asked for it.
 */
object LibraryAdopt {
    /**
     * The write itself, blocking: whether it wrote the library. Set once, on the application's context
     * rather than an activity's.
     */
    @Volatile var adoptTargetWeight: ((exerciseId: String, weightKg: Double) -> Boolean)? = null

    /**
     * Native wrote the library, on the main thread, so the screens read it again: an editor opened after
     * the runner builds on the file, not on the library from before the adopt. The current activity's,
     * which clears it as it goes.
     */
    var onLibraryWritten: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /** Where an adopt runs: off the main thread, for the life of the process. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * [weightKg] becomes [exerciseId]'s target load in the library, off the main thread, and every screen
     * reads it again. The runner calls it directly (#297), and it takes the library's one lock.
     */
    fun adoptLoad(
        exerciseId: String,
        weightKg: Double,
    ) {
        val write = adoptTargetWeight
        if (write == null) {
            // MainActivity sets it before any screen shows, so this is a shell wired wrong.
            Log.w(TAG, "A load was adopted with no library to write it to; nothing was written")
            return
        }
        background.launch {
            if (!LibraryWrites.lock.withLock { write(exerciseId, weightKg) }) return@launch
            main.post { onLibraryWritten?.invoke() }
        }
    }

    private const val TAG = "LibraryAdopt"
}
