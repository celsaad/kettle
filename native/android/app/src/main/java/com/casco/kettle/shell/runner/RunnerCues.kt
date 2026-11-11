package com.casco.kettle.shell.runner

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
 * - Transient may-duck focus for each cue, released after it, so music dips and comes back. A cue the
 *   platform refuses focus for, as during a call, is skipped, not queued: a late tick is wrong.
 * - Do Not Disturb is respected: anything but "all" silences them.
 *
 * Only called from the runner's [loop], which also releases the focus.
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
    private val focus =
        AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .build()

    private val sounds: Map<RunnerCue, Int> =
        RunnerCue.entries.mapNotNull { cue -> load(cue)?.let { cue to it } }.toMap()
    private val loaded = mutableSetOf<Int>()
    private var whenLoaded: (() -> Unit)? = null
    private var release: Cancellable? = null

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
        val id = sounds[cue]?.takeIf { it in loaded } ?: return
        if (doNotDisturb() || !requestFocus()) return
        pool.play(id, 1f, 1f, 1, 0, 1f)
        release?.cancel()
        release = loop.postDelayed(FOCUS_HOLD_MS, ::abandonFocus)
    }

    fun close() {
        release?.cancel()
        abandonFocus()
        pool.release()
    }

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

    private fun requestFocus(): Boolean = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    private fun abandonFocus() {
        release = null
        audio.abandonAudioFocusRequest(focus)
    }
}

/** The repo's `assets/sounds/` names, packaged as they are. */
private fun fileOf(cue: RunnerCue): String =
    when (cue) {
        RunnerCue.TICK -> "tick.wav"
        RunnerCue.EXERCISE_CHANGE -> "exercise-change.wav"
        RunnerCue.MILESTONE -> "milestone.wav"
    }

/** Longer than the longest cue, so the duck lasts the sound and no more. */
private const val FOCUS_HOLD_MS = 800L
private const val TAG = "RunnerCues"
