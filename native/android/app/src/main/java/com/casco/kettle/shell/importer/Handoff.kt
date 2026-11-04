package com.casco.kettle.shell.importer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The two things worth putting on the clipboard, and the two directions of one loop: the brief goes out
 * to an assistant before anything is written, the report goes back to it after the importer refuses
 * what came of that.
 */
enum class CopyTarget { Brief, Report }

/** The last copy, by which button made it, since both can be on screen and only one may say "Copied". */
data class Copy(
    val target: CopyTarget,
    val took: Boolean,
)

/**
 * What goes out of Import to an assistant: the brief by the share sheet or the clipboard, and a
 * refusal's report by the clipboard. Kettle never calls a model; the sheet is the OS listing apps
 * that take text, and the user picks one.
 */
internal class Handoff(
    private val scope: CoroutineScope,
    private val actions: ImportActions,
) {
    var copied by mutableStateOf<Copy?>(null)
        private set

    /**
     * Apart from [copied]: a sheet that couldn't open says nothing about the clipboard, and folding the
     * two together would put "couldn't reach the clipboard" under a button that never touched it.
     */
    var shareFailed by mutableStateOf(false)
        private set

    private var expiry: Job? = null

    /**
     * The one path to the clipboard, for both directions of the loop. "Copied" holds for two seconds, as
     * RN's did, long enough to read and short enough not to describe a copy since replaced.
     */
    fun copy(
        target: CopyTarget,
        text: String,
    ) {
        shareFailed = false
        expiry?.cancel()
        val took = actions.copy(text)
        copied = Copy(target, took)
        if (took) {
            expiry =
                scope.launch {
                    delay(COPIED_FOR_MS)
                    copied = null
                }
        }
    }

    /** A sheet that won't open degrades to a note, with the clipboard button under it as the way through. */
    fun share(text: String) {
        forget()
        shareFailed = !actions.share(text)
    }

    /** A new refusal or a new hand-off, so no "Copied" stands over something it didn't copy. */
    fun forget() {
        expiry?.cancel()
        copied = null
    }
}

private const val COPIED_FOR_MS = 2000L
