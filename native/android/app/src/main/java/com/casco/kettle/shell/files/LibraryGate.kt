package com.casco.kettle.shell.files

import android.content.Context
import android.util.Log
import com.casco.kettle.storage.PreparedLibrary
import com.casco.kettle.storage.prepareLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * The launch's seed and repair of `exercises.yaml` (#220, #221), once per process, on an IO thread.
 * Everything that reads the library waits for it: native's reads through [prepared], and the launch's
 * runner recovery through [awaitPrepared].
 */
object LibraryGate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var preparing: Deferred<Unit>? = null

    /**
     * Starts the job, if this process hasn't, as early as the activity can, so it overlaps the
     * activity's own start. The seed is in the device's language, frozen from then on as the user's data.
     */
    @Synchronized
    fun start(context: Context) {
        if (preparing != null) return
        val app = context.applicationContext
        preparing = scope.async { prepare(app) }
    }

    /**
     * Suspends until the job has run: what native's own reads wait on (`LatestFiles`), so a first
     * launch's screens find the seed.
     */
    suspend fun prepared(context: Context) {
        start(context)
        checkNotNull(preparing).await()
    }

    /**
     * Blocks until the job has run, on the main thread: `recoverRunner` in `MainActivity` reads the
     * library before any screen can start a session. A read and parse of one small file on every
     * launch, and a write on the launches that seed or repair it. Making this async is a change of its
     * own, and the first lever if a cold-start benchmark regresses.
     */
    fun awaitPrepared(context: Context) = runBlocking { prepared(context) }

    @Suppress("TooGenericExceptionCaught") // Why is at the catch.
    private fun prepare(app: Context) {
        val paths = StoragePaths(app.filesDir)
        val language =
            app.resources.configuration.locales[0]
                .toLanguageTag()
        try {
            when (val prepared = prepareLibrary(paths.library, { ShippedContent(app.assets).seed(language) })) {
                PreparedLibrary.Found -> {
                    Unit
                }

                PreparedLibrary.Seeded -> {
                    Log.i(TAG, "No library yet; wrote the seed")
                }

                is PreparedLibrary.Repaired -> {
                    Log.w(TAG, "exercises.yaml held out-of-range values (${prepared.why}); clamped, the rest kept")
                }

                is PreparedLibrary.Reseeded -> {
                    val kept = prepared.quarantined?.let { "the old file kept as ${it.name}" } ?: "the old file lost"
                    Log.w(TAG, "exercises.yaml failed to parse (${prepared.why}); reseeded, $kept")
                }
            }
        } catch (error: Exception) {
            // Anything, not just a disk that refuses: this rethrows on the main thread from
            // awaitPrepared, before any screen, so a shipped-content defect or a SecurityException would
            // crash every launch. Degraded, the screens find what's there, which on a first launch is
            // nothing and shows their error, and the next launch tries again.
            Log.e(TAG, "Could not prepare the library", error)
        }
    }

    private const val TAG = "LibraryGate"
}
