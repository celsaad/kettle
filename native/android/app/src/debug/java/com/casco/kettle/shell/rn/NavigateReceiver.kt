package com.casco.kettle.shell.rn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lets adb steer the running RN surface. It's how the M3 routing test (#152) drove every route in turn
 * on a device, and what `scripts/route-check.ps1` sends:
 *
 *     adb shell am broadcast -n com.casco.kettle.dev/com.casco.kettle.shell.rn.NavigateReceiver --es url /history
 *
 * Debug only, and behind a permission only the shell user holds, so no other app can send it.
 */
class NavigateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        intent.getStringExtra("url")?.let(JsBridge::navigate)
    }
}
