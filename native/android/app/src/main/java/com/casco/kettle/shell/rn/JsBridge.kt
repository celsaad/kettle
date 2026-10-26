package com.casco.kettle.shell.rn

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.casco.kettle.brownfield.ReactNativeHostManager
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
     * Where RN reports its router to be, a pathname, on the main thread. The current activity's: it
     * clears it as it goes, since the runtime and this object outlive it.
     */
    var onRoute: ((String) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /** JS's messages arrive on its own thread. */
    private fun receive(message: Map<String, Any?>) {
        channel.receive(message)
        // What reportRoute in src/hooks/safe-brownfield.ts sends. The two share no code, so change both.
        if (message["type"] != "route") return
        val pathname = message["pathname"] as? String ?: return
        main.post { onRoute?.invoke(pathname) }
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
        watchingReloads = true
    }

    private const val TAG = "JsBridge"
}
