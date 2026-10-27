package com.casco.kettle.shell.rn

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.facebook.react.ReactHost
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.UIManager
import com.facebook.react.bridge.UIManagerListener
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import java.util.concurrent.atomic.AtomicBoolean

/**
 * When RN's frames change, as the shell's sheet needs to know it (#178): the first frame that shows a
 * reported route, and the last one before an action replaces it.
 *
 * Never animate on the route report itself: it ran 1–3 frames ahead of RN's draw both ways (#177).
 * RN sends it a task late (reportRoute), so the commit's mount is queued by the time it arrives. From
 * there [awaitDrawn] waits for Fabric's next mount dispatch, then one main-looper post, then the
 * surface's next pre-draw, which is the first frame showing the route.
 *
 * Fabric's `UIManagerListener` is `@UnstableReactNativeAPI`: verified on RN 0.86.3 and
 * react-native-screens 4.26.2. Re-check both on any upgrade of either.
 */
@OptIn(UnstableReactNativeAPI::class)
object RnFrames {
    private val main = Handler(Looper.getMainLooper())

    /** Set on the JS thread by [arm], before the action's commit is queued; cleared on the UI thread. */
    private val armed = AtomicBoolean(false)

    /**
     * The picture-taker for the frame an action is about to replace, on the main thread. The current
     * activity's, which clears it as it goes.
     */
    var onCapture: (() -> Unit)? = null

    /** What runs at the drawn frame, once Fabric next dispatches. Main thread only. */
    private var afterDispatch: (() -> Unit)? = null

    /** The UI manager the listener is on, so a reload can take it off the right one. */
    @Volatile private var listeningTo: UIManager? = null

    private val listener =
        object : UIManagerListener {
            override fun willDispatchViewUpdates(uiManager: UIManager) = Unit

            override fun didScheduleMountItems(uiManager: UIManager) = Unit

            override fun didMountItems(uiManager: UIManager) = Unit

            // UI thread, before a non-empty batch applies: the batch an armed action queued, or an earlier
            // one, and either way the screen still shows the frame the user last saw. Only once per arming:
            // the editors commit on every keystroke, and a picture is 8–28ms (#177).
            override fun willMountItems(uiManager: UIManager) {
                if (!armed.getAndSet(false)) return
                onCapture?.invoke()
            }

            // Every frame, even with nothing to mount, so the check is the one field read.
            override fun didDispatchMountItems(uiManager: UIManager) {
                val then = afterDispatch ?: return
                afterDispatch = null
                // react-native-screens 4.26.2 posts its fragment transaction to the main looper during the
                // mount (ScreenContainer.onScreenChanged), and it is what swaps the screen. This post lands
                // after it, first in first out, so the next pre-draw shows the new route. A version that
                // defers that transaction further breaks this silently: re-check it on any upgrade.
                main.post(then)
            }
        }

    /**
     * The JS thread's word that an action is about to commit (routeWillChange). Arms the next mount's
     * picture before the action's batch can be queued, since JS sends it synchronously.
     */
    fun arm() = armed.set(true)

    /**
     * The reported route has been drawn, so its batch has certainly mounted: whatever the action was, its
     * picture is taken or moot. Not on the report's arrival, which only means the batch is queued, and
     * can beat the UI thread to the willMountItems that pictures it.
     */
    fun disarm() = armed.set(false)

    /**
     * Runs [then] at the first frame [surface] draws after the route reported last, on the main thread,
     * from its pre-draw. A second call before the first has run replaces it: the latest report decides.
     * Without Fabric's listener (no runtime yet, or between a reload's two) it waits for the pre-draw
     * alone, which may be early but never strands a report; so does [FALLBACK_MS] without a dispatch.
     */
    fun awaitDrawn(
        surface: View,
        then: () -> Unit,
    ) {
        main.removeCallbacks(fallback)
        val atPreDraw = { onNextPreDraw(surface, then) }
        if (listeningTo == null) {
            Log.w(TAG, "No Fabric listener; the sheet may run ahead of RN's draw")
            afterDispatch = null
            main.post(atPreDraw)
            return
        }
        afterDispatch = atPreDraw
        fallbackTo = atPreDraw
        main.postDelayed(fallback, FALLBACK_MS)
    }

    private var fallbackTo: (() -> Unit)? = null

    private val fallback =
        Runnable {
            val then = afterDispatch ?: return@Runnable
            if (then !== fallbackTo) return@Runnable
            Log.w(TAG, "No Fabric dispatch within ${FALLBACK_MS}ms of a route report")
            afterDispatch = null
            then()
        }

    /**
     * The surface's next pre-draw, which cancels its own frame: what it runs changes the layout, and the
     * frame after is drawn with the change in place. The frame cancelled is the first of the new route,
     * so the user goes from the last frame of the old one straight to the transition's first.
     */
    private fun onNextPreDraw(
        surface: View,
        then: () -> Unit,
    ) {
        val observer = surface.viewTreeObserver
        observer.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    surface.viewTreeObserver.removeOnPreDrawListener(this)
                    then()
                    return false
                }
            },
        )
        surface.invalidate()
    }

    /**
     * Listens to [host]'s Fabric UI manager, now and after every reload. Idempotent, like
     * `JsBridge.attach`, since every new activity calls it.
     */
    fun attach(host: ReactHost) {
        if (attachedTo === host) return
        attachedTo = host
        host.currentReactContext?.let(::listenTo)
        host.addReactInstanceEventListener(
            object : ReactInstanceEventListener {
                override fun onReactContextInitialized(context: ReactContext) = listenTo(context)
            },
        )
        // Main thread, before the old instance goes: no listener is left on a dead UI manager.
        host.addBeforeDestroyListener {
            listeningTo?.removeUIManagerEventListener(listener)
            listeningTo = null
        }
    }

    private var attachedTo: ReactHost? = null

    private fun listenTo(context: ReactContext) {
        val uiManager = UIManagerHelper.getUIManager(context, UIManagerType.FABRIC) ?: return
        if (uiManager === listeningTo) return
        listeningTo?.removeUIManagerEventListener(listener)
        uiManager.addUIManagerEventListener(listener)
        listeningTo = uiManager
    }

    private const val TAG = "RnFrames"

    /** Long enough that a frame or two never trips it; short enough that a broken listener shows. */
    private const val FALLBACK_MS = 500L
}
