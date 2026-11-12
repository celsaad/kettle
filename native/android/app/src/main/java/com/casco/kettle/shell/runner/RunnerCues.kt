package com.casco.kettle.shell.runner

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import com.casco.kettle.domain.RunnerCue
import java.io.IOException

/**
 * The session's three sounds (#296), the ones RN's `use-session-sounds.ts` played, read from the same
 * files in the repo's `assets/sounds/` (packaged by `app/build.gradle.kts`).
 *
 * - `SoundPool`, preloaded when the service starts, on `USAGE_MEDIA`: they follow the media volume
 *   and still play with the ringer on silent, as RN's `playsInSilentMode` did.
 * - No audio focus: a cue mixes over whatever else is playing and leaves it alone. Asking for
 *   transient may-duck focus, as this once did, let any app that answers a transient loss by pausing
 *   (which many music players do) stop the music for every cue, ticks included. The cost is a cue
 *   that loud music can mask.
 * - With the phone in a call, ringing or screening, a cue is skipped, not queued: a late tick is
 *   wrong. Without focus nothing refuses it for us, so the audio mode is what says so.
 * - Do Not Disturb is respected: anything but "all" silences them.
 *
 * In a debuggable build, each cue logs whether it played or was skipped and why:
 * `scripts/session-check.sh` reads its cadence from those lines, since a cue that leaves no focus
 * request behind has no other trace outside the app. Debug-only, as `docs/decisions.md` has it, so a
 * release session doesn't log a line a second.
 *
 * Only called from the runner's [loop].
 */
class RunnerCues(
    private val context: Context,
    private val loop: RunnerLoop,
    private val soundsOn: () -> Boolean,
) : CueSounds {
    private val attributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private val pool =
        SoundPool
            .Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private val sounds: Map<RunnerCue, Int> =
        RunnerCue.entries.mapNotNull { cue -> load(cue)?.let { cue to it } }.toMap()
    private val loaded = mutableSetOf<Int>()
    private var whenLoaded: (() -> Unit)? = null

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            loop.post {
                if (status == 0) loaded += id
                if (loaded.containsAll(sounds.values)) fireLoaded()
            }
        }
    }

    /**
     * Runs [then] once every sound has loaded, or after [timeoutMs] with whatever has, so the
     * count-in's first tick isn't lost to a sound still loading.
     */
    fun whenLoaded(
        timeoutMs: Long,
        then: () -> Unit,
    ) = loop.post {
        whenLoaded = then
        if (loaded.containsAll(sounds.values)) fireLoaded() else loop.postDelayed(timeoutMs, ::fireLoaded)
    }

    override fun on(): Boolean = soundsOn()

    override fun play(cue: RunnerCue) {
        val id = sounds[cue]?.takeIf { it in loaded }
        val mode = audio.mode
        val skipped =
            when {
                id == null -> "not loaded"
                doNotDisturb() -> "Do Not Disturb"
                mode != AudioManager.MODE_NORMAL -> "audio mode $mode"
                else -> null
            }
        if (id != null && skipped == null) pool.play(id, 1f, 1f, 1, 0, 1f)
        if (debuggable) Log.i(TAG, if (skipped == null) "played ${cue.name}" else "skipped ${cue.name}: $skipped")
    }

    fun close() = pool.release()

    private fun fireLoaded() {
        val then = whenLoaded ?: return
        whenLoaded = null
        then()
    }

    private fun load(cue: RunnerCue): Int? =
        try {
            context.assets.openFd(fileOf(cue)).use { pool.load(it, 1) }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't load the ${cue.name} cue; it will be silent", e)
            null
        }

    private fun doNotDisturb(): Boolean =
        notifications.currentInterruptionFilter.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL && it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }
}

/** The repo's `assets/sounds/` names, packaged as they are. */
private fun fileOf(cue: RunnerCue): String =
    when (cue) {
        RunnerCue.TICK -> "tick.wav"
        RunnerCue.EXERCISE_CHANGE -> "exercise-change.wav"
        RunnerCue.MILESTONE -> "milestone.wav"
    }

private const val TAG = "RunnerCues"
