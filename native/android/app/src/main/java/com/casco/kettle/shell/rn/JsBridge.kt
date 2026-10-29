package com.casco.kettle.shell.rn

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.casco.kettle.brownfield.ReactNativeHostManager
import com.casco.kettle.shell.nav.RouteReport
import com.facebook.react.modules.core.DeviceEventManagerModule
import expo.modules.brownfield.BrownfieldMessaging

/**
 * The one channel to JS, for the life of the process: the JS runtime outlives any activity (the M0
 * spike's Q2), so the channel does too.
 */
object JsBridge {
    val channel = JsChannel(BrownfieldMessaging::sendMessage)

    /**
     * Moves the one RN surface to [url], a route path such as `/history`: a second surface can't be
     * routed by deep link (Q3), so native steers this one instead. The tab bar sends it, and so does the
     * debug build's `NavigateReceiver`.
     */
    fun navigate(url: String) {
        // What src/hooks/safe-brownfield.ts listens for. The two share no code, so change both.
        channel.send(mapOf("type" to "navigate", "url" to url))
    }

    /**
     * Opens [pathname] with [params] on top of what shows, or in its place, as [how] says: an RN route
     * or a native one behind its stub (#190). Where [navigate] would dismiss everything first.
     */
    fun open(
        how: Stacking,
        pathname: String,
        params: Map<String, String> = emptyMap(),
    ) = channel.send(how.message(pathname, params))

    /**
     * Tells RN that native has just written [kind], so the store reading it reads it again: the half of
     * "one writer per file" that keeps RN's screens current once a native one writes. Sent before JS is
     * up, it waits in [channel] like any other message.
     */
    fun filesChanged(kind: FileKind) {
        // What src/state/files-changed.ts listens for. The two share no code, so change both.
        channel.send(mapOf("type" to "filesChanged", "kind" to kind.wire))
    }

    /**
     * Asks RN to share session [id]'s file, or the whole log as one archive when [id] is null: native
     * History's Export and Export all. RN keeps export until M5, when export and share move native with
     * Settings, which shares the same files; until then one implementation serves both screens.
     */
    fun export(id: String?) {
        // What src/state/export-requested.ts listens for. The two share no code, so change both.
        channel.send(if (id == null) mapOf("type" to "exportHistory") else mapOf("type" to "exportSession", "id" to id))
    }

    /**
     * Back, as hardware back reaches RN: the sheet's ×, grabber and predictive back send it, so RN stays
     * the router. What `setUpNativeBackHandling`'s callback does with native back off, which it is.
     */
    fun back() {
        ReactNativeHostManager.shared
            .getReactHost()
            ?.currentReactContext
            ?.getNativeModule(DeviceEventManagerModule::class.java)
            ?.emitHardwareBackPressed()
    }

    /**
     * Where RN reports its router to be, on the main thread. The current activity's: it clears it as
     * it goes, since the runtime and this object outlive it.
     */
    var onRoute: ((RouteReport) -> Unit)? = null

    /**
     * The type of the navigation action RN is about to commit, on the main thread, ahead of the report
     * of where it lands. The current activity's, like [onRoute].
     */
    var onRouteWillChange: ((String) -> Unit)? = null

    /**
     * Whether an action of this type wants a picture of the frame it replaces, asked on the JS thread.
     * The current activity's, like [onRoute]; with none, nothing is pictured.
     */
    @Volatile var wantsPicture: ((String) -> Boolean)? = null

    private val main = Handler(Looper.getMainLooper())

    /** JS's messages arrive on its own thread. */
    private fun receive(message: Map<String, Any?>) {
        channel.receive(message)
        when (message["type"]) {
            // What reportRouteWillChange in src/hooks/safe-brownfield.ts sends. The two share no code, so
            // change both. Armed here, on the JS thread, because JS sends it before the action's commit:
            // a post to main could land after the commit's batch had mounted (#178).
            "routeWillChange" -> {
                val action = message["action"] as? String ?: return
                if (wantsPicture?.invoke(action) == true) RnFrames.arm()
                main.post { onRouteWillChange?.invoke(action) }
            }

            // What reportRoute sends. The two share no code, so change both.
            "route" -> {
                val report = RouteReport.from(message) ?: return
                main.post { onRoute?.invoke(report) }
            }
        }
    }

    private var listening = false
    private var watchingReloads = false

    /**
     * Listens to JS, and to the runtime being replaced. Idempotent, since every new activity calls it.
     *
     * The listener needs no host, so it is always in place and "ready" can't go unheard. Watching
     * reloads does: it's called once the first surface has opened, which creates the host, and logs
     * rather than throws if that ever stops being true, since nothing sends yet.
     *
     * The channel holds again as the old runtime is torn down, not when the new one starts: a reload
     * runs before-destroy listeners before it creates the new instance, so nothing is sent into a dead
     * runtime in between, and the reset can't land after the new runtime's "ready".
     */
    fun attach() {
        if (!listening) {
            BrownfieldMessaging.addListener(::receive)
            listening = true
        }
        if (watchingReloads) return
        val host = ReactNativeHostManager.shared.getReactHost()
        if (host == null) {
            Log.w(TAG, "No React host yet; a reload won't re-hold messages until the next activity")
            return
        }
        host.addBeforeDestroyListener(channel::onReload)
        RnFrames.attach(host)
        watchingReloads = true
    }

    private const val TAG = "JsBridge"
}
