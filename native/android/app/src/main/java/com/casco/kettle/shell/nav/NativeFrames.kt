package com.casco.kettle.shell.nav

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver

/** One route's screen in the native sheet: the path RN reported, and the params it reported it with. */
internal data class SheetContent(
    val path: String,
    val params: Map<String, String>,
)

/**
 * When the native sheet's screen is there to show (#197), as `RnFrames` knows it for RN's: a transition
 * started before [view] has composed and laid out the route it arrives at slides in an empty sheet.
 *
 * [NativeSheet] reports each composition to [onComposed], after it applies and before that frame's
 * layout, so the frame's pre-draw is the first that can show it.
 */
class NativeFrames(
    private val view: View,
) {
    /** What the native sheet composed last, or null for nothing. */
    private var composed: SheetContent? = null

    /** An arrival waiting for its screen. */
    private var holding: Hold? = null

    internal fun onComposed(content: SheetContent?) {
        composed = content
    }

    /**
     * Runs [then] at the first pre-draw with [wanted] composed. Until then it holds the screen as it is,
     * by cancelling each frame; that one is cancelled too, so the frame after is the transition's first.
     * A composition lands within a frame or two, so [HOLD_MS] is only a backstop. A second call runs the
     * first's [then] straight away, so the second starts from where the first ends.
     */
    internal fun awaitComposed(
        wanted: SheetContent,
        then: () -> Unit,
    ) {
        flush()
        holding = Hold(wanted, then).also { it.begin() }
    }

    /** Runs a held arrival now: the next one is about to start. */
    fun flush() {
        holding?.release()
    }

    private inner class Hold(
        private val wanted: SheetContent,
        private val then: () -> Unit,
    ) : ViewTreeObserver.OnPreDrawListener {
        private val since = SystemClock.uptimeMillis()

        fun begin() {
            view.viewTreeObserver.addOnPreDrawListener(this)
            view.invalidate()
        }

        override fun onPreDraw(): Boolean {
            if (composed == wanted) {
                release()
            } else if (SystemClock.uptimeMillis() - since >= HOLD_MS) {
                Log.w(TAG, "The native sheet hadn't composed ${wanted.path} after ${HOLD_MS}ms")
                release()
            }
            return false
        }

        fun release() {
            if (holding !== this) return
            holding = null
            view.viewTreeObserver.removeOnPreDrawListener(this)
            then()
        }
    }

    private companion object {
        const val TAG = "NativeFrames"
        const val HOLD_MS = 500L
    }
}
