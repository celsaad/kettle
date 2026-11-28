package com.casco.kettle.shell.importer

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.IntentCompat
import com.casco.kettle.shell.nav.Destination
import com.casco.kettle.shell.nav.Navigator
import com.casco.kettle.shell.nav.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * A library another app handed Kettle (#514): YAML shared as text, as it leaves a chat where an assistant
 * wrote it, or a file opened from Files or a download. Either lands in Import's preview, and nothing is
 * written until Import is tapped there, as with a paste or a picked file.
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
 * The library in [this] intent, by the manifest's filters: `SEND` of `text/plain`, its stream or else
 * its text, and `VIEW` of a file typed as text or YAML. Not `application/octet-stream`, which `.yaml`
 * often arrives as: filtering on it would list Kettle for every binary file, so such a file goes through
 * Import's own picker. Null for anything else, a `kettle://` link included, and for an intent Recents
 * replays, which would import the same thing again.
 *
 * The launcher alias is exported, so an app can name it and skip the filters: a file is only ever a
 * `content:` URI ([fileUri]). An app naming it can open Import on any file, a picture say; the read is
 * bounded and off the main thread, and the parser refuses it.
 */
fun Intent.sharedLibrary(ownAuthority: String? = null): SharedLibrary? {
    val replayed = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0
    return when {
        replayed -> null
        action == Intent.ACTION_SEND && type?.lowercase() == SHARED_TEXT_TYPE -> sentLibrary(ownAuthority)
        action == Intent.ACTION_VIEW -> viewedLibrary(ownAuthority)
        else -> null
    }
}

/**
 * A `SEND`'s stream, else its text: a file shared from Files comes as a stream, and some apps put a
 * caption or the file's name in the text beside it, which reviewed instead would be refused. Read as a
 * `CharSequence`, since a styled one comes back null as a `String`.
 */
private fun Intent.sentLibrary(ownAuthority: String?): SharedLibrary? {
    val stream = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
    fileUri(stream, ownAuthority)?.let { return SharedLibrary.File(it) }
    val text = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
    return if (text.isNullOrBlank()) null else SharedLibrary.Text(text)
}

/**
 * A `VIEW` with no type in it is one the system matched on the provider's own type, which it leaves out
 * of the intent: asking the provider again, on the main thread, let a slow one stall the app (#569). An
 * explicit type is still held to the manifest's.
 */
private fun Intent.viewedLibrary(ownAuthority: String?): SharedLibrary? {
    val uri = fileUri(data, ownAuthority) ?: return null
    return if (type == null || type?.lowercase() in VIEWED_TYPES) SharedLibrary.File(uri) else null
}

/**
 * [uri] when it's another app's `content:` file: a `file:` path or `android.resource:` would be read
 * with Kettle's own access, a private file of its own included, and so would Kettle's own provider, which
 * no other app can be granted, so only a forged intent names it.
 */
private fun fileUri(
    uri: Uri?,
    ownAuthority: String?,
): Uri? =
    uri?.takeIf {
        // A user id before the authority, `0@…`, is stripped before the provider is found.
        it.scheme == ContentResolver.SCHEME_CONTENT && it.authority?.substringAfterLast('@') != ownAuthority
    }

/** Taken off [this] intent, so an activity made again with it doesn't import it twice. */
fun Intent.forgetSharedLibrary() {
    action = null
    data = null
    removeExtra(Intent.EXTRA_TEXT)
    removeExtra(Intent.EXTRA_STREAM)
}

/**
 * Where a shared library waits between the activity that received it and the Import screen it opens:
 * a route's params are strings saved with the back stack, no place for a whole library. One slot, under
 * a token the route carries, so each share is a route of its own (one equal to the Import showing would
 * be no move at all) and a `kettle://import?shared=…` link can't claim what it didn't bring. Lost with
 * the process, when Import opens with its sources as usual. Main thread only.
 */
class SharedLibraryInbox {
    private var waiting: Pair<String, SharedLibrary>? = null

    /**
     * A share waiting behind a session ([SharedOpener]): its token, and the sheet the session opened over.
     * Here rather than in the activity, so an activity made again mid-session still opens it.
     */
    var held: Held? = null

    /** A held share's [token], and the sheet that was under the runner when it arrived, if any. */
    data class Held(
        val token: String,
        val beneath: Route?,
    )

    /**
     * Holds [shared], replacing any earlier, and returns the token its route carries. A share held before
     * it is let go with the library it named: its token would open an empty Import.
     */
    fun put(shared: SharedLibrary): String =
        UUID.randomUUID().toString().also {
            waiting = it to shared
            held = null
        }

    /** The library under [token], once; null for any other token. */
    fun take(token: String?): SharedLibrary? {
        val held = waiting?.takeIf { it.first == token } ?: return null
        waiting = null
        return held.second
    }

    companion object {
        /** The process's: the activity that receives a share and the screen that opens it are both in it. */
        val process = SharedLibraryInbox()
    }
}

/**
 * Opens what another app shared in Import (#514), over whatever sheet shows ([Navigator.arrive]), so an
 * editor it lands on keeps its draft for back (#569). Or holds it behind a running session, whose slot
 * drops every other move, and opens it once the session ends: a share is the user's data, and dropping it
 * as a `kettle://` link is dropped would lose it without a word. Over the sheet the session was started
 * from, so back returns to it; but not while a sheet opened since covers it, such as the session editor
 * straight after Done, which the share would otherwise bury.
 */
class SharedOpener(
    private val navigator: Navigator,
    private val inbox: SharedLibraryInbox = SharedLibraryInbox.process,
) {
    fun open(shared: SharedLibrary) {
        val token = inbox.put(shared)
        if (navigator.runnerShown) {
            inbox.held = SharedLibraryInbox.Held(token, navigator.sheet)
        } else {
            navigator.arrive(route(token))
        }
    }

    /** Opens a held share as soon as the screen is back where the session left it, while [scope] lives. */
    fun watch(scope: CoroutineScope) {
        scope.launch { snapshotFlow { navigator.runnerShown to navigator.sheet }.collect { openHeld() } }
    }

    /**
     * A share held until now opens, once the runner has closed and the screen is back on the sheet it
     * started from, or that sheet has left the stack altogether, when it opens over whatever is there.
     */
    fun openHeld() {
        val held = inbox.held ?: return
        // Or once the sheet it was held over has gone from the stack altogether (#569): after a relaunch
        // with no saved stack, or a link over the editor, it would otherwise wait for that sheet for good.
        val gone = held.beneath != null && held.beneath !in navigator.sheets
        if (navigator.runnerShown || (navigator.sheet != held.beneath && !gone)) return
        inbox.held = null
        navigator.arrive(route(held.token))
    }

    private fun route(token: String) = Route(Destination.Import, mapOf(IMPORT_SHARED_PARAM to token))
}

private const val SHARED_TEXT_TYPE = "text/plain"

/** The manifest's `VIEW` types: `.yaml` has no registered one, so each that providers use. */
internal val VIEWED_TYPES = setOf("text/plain", "application/x-yaml", "application/yaml", "text/yaml")
