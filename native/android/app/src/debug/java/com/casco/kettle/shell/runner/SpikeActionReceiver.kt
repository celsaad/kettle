package com.casco.kettle.shell.runner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.casco.kettle.domain.RunnerCommand

// Spike for #543, never merged: the session controls' taps, from the shade, the lock screen or the wrist.
// Logs every tap with the transport and visit it came from, so the checklist can read arrival and latency
// from `adb logcat -s KettleSpike`.
class SpikeActionReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val cmd = intent.getStringExtra("cmd")
        val visit = intent.getLongExtra("visit", -1)
        val running = RunnerHost.active as? RunnerView.Running
        Log.i(
            "KettleSpike",
            "tap cmd=$cmd from=${intent.getStringExtra("from")} visit=$visit current=${running?.snapshot?.visit} " +
                "postedAt=${intent.getLongExtra("postedAt", 0)} now=${System.currentTimeMillis()}",
        )
        if (RunnerHost.session == null) return Log.i("KettleSpike", "dropped: no session").let { }
        when (cmd) {
            "advance" -> RunnerHost.send(RunnerCommand.Advance(visit))
            "prev" -> RunnerHost.send(RunnerCommand.Prev)
            "add30" -> RunnerHost.send(RunnerCommand.AddRestSeconds(30.0))
            "pause" -> RunnerHost.send(RunnerCommand.TogglePause)
        }
    }
}
