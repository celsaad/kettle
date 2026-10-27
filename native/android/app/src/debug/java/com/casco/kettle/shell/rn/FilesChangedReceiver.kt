package com.casco.kettle.shell.rn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Lets adb say a file changed, as a native screen will once one writes (M5). Until then it's the only
 * way to see an RN store re-read on a device: push an edited file with run-as, then
 *
 *     adb shell am broadcast -n com.casco.kettle.dev/com.casco.kettle.shell.rn.FilesChangedReceiver --es kind library
 *
 * Debug only, and behind the same permission as [NavigateReceiver].
 */
class FilesChangedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        intent.getStringExtra("kind")?.let(FileKind::fromWire)?.let(JsBridge::filesChanged)
    }
}
