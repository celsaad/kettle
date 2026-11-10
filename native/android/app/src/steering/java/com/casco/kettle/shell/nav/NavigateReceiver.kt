package com.casco.kettle.shell.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Lets adb steer the running shell's [Navigator] to a route path, as a link would: what
 * `scripts/route-check.sh` and its `.ps1` twin send, one route at a time.
 *
 *     adb shell am broadcast -n com.casco.kettle.dev/com.casco.kettle.shell.nav.NavigateReceiver --es url /history
 *
 * In the debug and benchmark builds only, never Play's release, and behind a permission only the shell
 * user holds, so no other app can send it. On the
 * main thread, as every receiver is, so it can move the navigator directly. With no activity up, or a
 * path to nowhere, the message is dropped.
 */
class NavigateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val url = intent.getStringExtra("url") ?: return
        val route = Route.parse(url)
        if (route == null) {
            Log.w(TAG, "No route at $url")
            return
        }
        Navigator.current?.navigate(route)
    }

    private companion object {
        const val TAG = "NavigateReceiver"
    }
}
