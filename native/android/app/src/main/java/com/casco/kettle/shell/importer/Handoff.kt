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

/** How the last copy went, and which button made it: both can be on screen, and only one may say "Copied". */
data class CopyOutcome(
    val target: CopyTarget,
    val succeeded: Boolean,
)

/**
 * What goes out of Import to an assistant: the brief by the share sheet or the clipboard, and a
 * refusal's report by the clipboard. Kettle never calls a model; the sheet is the OS listing apps
 * that take text, and the user picks one.
 */
internal class Handoff(
    private val scope: CoroutineScope,
    /** The clipboard and the share sheet, each saying whether it took. */
    private val copyText: (String) -> Boolean,
    private val shareText: (String) -> Boolean,
) {
    private var lastCopy by mutableStateOf<CopyOutcome?>(null)

    /**
     * Apart from a copy's outcome: a sheet that couldn't open says nothing about the clipboard, and folding the
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
        val succeeded = copyText(text)
        lastCopy = CopyOutcome(target, succeeded)
        if (succeeded) {
            expiry =
                scope.launch {
                    delay(COPIED_FOR_MS)
                    lastCopy = null
                }
        }
    }

    /** [target]'s last copy took, and still says so. */
    fun confirmed(target: CopyTarget) = lastCopy == CopyOutcome(target, succeeded = true)

    /** [target]'s last copy was refused by the clipboard. */
    fun failed(target: CopyTarget) = lastCopy == CopyOutcome(target, succeeded = false)

    /** A sheet that won't open degrades to a note, with the clipboard button under it as the way through. */
    fun share(text: String) {
        forget()
        shareFailed = !shareText(text)
    }

    /** A new refusal or a new hand-off, so no "Copied" stands over something it didn't copy. */
    fun forget() {
        expiry?.cancel()
        lastCopy = null
    }
}

private const val COPIED_FOR_MS = 2000L
