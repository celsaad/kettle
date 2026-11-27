package com.casco.kettle.shell.importer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * A library another app handed Kettle (#514): YAML shared as text, as it leaves a chat where an assistant
 * wrote it, or a file opened from Files or a download. Either lands in Import's preview, and nothing is
 * written until Merge there, as with a paste or a picked file.
 */
sealed interface SharedLibrary {
    data class Text(
        val text: String,
    ) : SharedLibrary

    data class File(
        val uri: Uri,
    ) : SharedLibrary
}

/**
 * The library in [this] intent, by the manifest's filters: `SEND` of `text/plain`, its text or else
 * its stream, and `VIEW` of a `content:` file typed as text or YAML. Not `application/octet-stream`,
 * which `.yaml` often arrives as: filtering on it would list Kettle for every binary file, so such a
 * file goes through Import's own picker. Null for anything else, a `kettle://` link included, and for
 * an intent Recents replays, which would import the same thing again.
 */
fun Intent.sharedLibrary(): SharedLibrary? {
    val replayed = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    return when {
        replayed -> null
        action == Intent.ACTION_SEND && type?.lowercase() == SHARED_TEXT_TYPE -> sentLibrary()
        action == Intent.ACTION_VIEW -> data?.takeIf { viewable(it) }?.let { SharedLibrary.File(it) }
        else -> null
    }
}

/** A `SEND`'s text, or else its stream: a file shared from Files comes as the latter. */
private fun Intent.sentLibrary(): SharedLibrary? {
    val text = getStringExtra(Intent.EXTRA_TEXT)
    if (!text.isNullOrBlank()) return SharedLibrary.Text(text)
    return IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.let { SharedLibrary.File(it) }
}

private fun Intent.viewable(uri: Uri) =
    uri.scheme == ContentResolver.SCHEME_CONTENT && type?.lowercase() in VIEWED_TYPES

/** Taken off [this] intent, so an activity made again with it doesn't import it twice. */
fun Intent.forgetSharedLibrary() {
    action = null
    data = null
    removeExtra(Intent.EXTRA_TEXT)
    removeExtra(Intent.EXTRA_STREAM)
}

/**
 * Where a shared library waits between the activity that received it and the Import screen it opens:
 * a route's params are strings saved with the back stack, no place for a whole library. One slot, taken
 * once; lost with the process, when Import opens with its sources as usual.
 */
object SharedLibraryInbox {
    private var waiting: SharedLibrary? = null

    fun put(shared: SharedLibrary) {
        waiting = shared
    }

    fun take(): SharedLibrary? = waiting.also { waiting = null }
}

private const val SHARED_TEXT_TYPE = "text/plain"

/** The manifest's `VIEW` types: `.yaml` has no registered one, so each that providers use. */
private val VIEWED_TYPES = setOf("text/plain", "application/x-yaml", "application/yaml", "text/yaml")
