package com.casco.kettle.shell.importer

import android.app.Activity
import android.content.ActivityNotFoundException
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.rn.FileKind
import com.casco.kettle.shell.rn.JsBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Where Import is registered, as Home's first-run link, Library and Settings open it. */
const val IMPORT_PATH = "/import"

/** What `shellRoutes` registers at [IMPORT_PATH], behind the library flip: the screen on SAF and the shell. */
@Composable
fun ImportRoute() {
    val activity = LocalActivity.current ?: return
    val pickFile = rememberFilePicker(activity)
    ImportScreen(
        LocalLatestFiles.current,
        remember { ShippedContent(activity.assets) },
        remember(pickFile) {
            ImportActions(
                pickFile = pickFile,
                close = JsBridge::back,
                libraryChanged = { JsBridge.filesChanged(FileKind.LIBRARY) },
            )
        },
    )
}

/**
 * `OpenDocument` as one suspending call: the file's name, size and text, read off the main thread, or
 * why it couldn't be. Any type, as RN's picker: YAML has no MIME type providers agree on, and a filter
 * that greyed the user's own file out would be worse than one that let a photo through to the parser.
 * A process killed behind the picker loses the answer, and the row is tapped again.
 */
@Composable
private fun rememberFilePicker(activity: Activity): suspend () -> FilePick {
    val waiting = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> waiting[0]?.complete(uri) }
    return remember(launcher) {
        suspend {
            val answer = CompletableDeferred<Uri?>().also { waiting[0] = it }
            try {
                launcher.launch(arrayOf("*/*"))
                answer.await()?.let { withContext(Dispatchers.IO) { activity.read(it) } } ?: FilePick.Cancelled
            } catch (e: ActivityNotFoundException) {
                FilePick.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }
}

/** The platform's own message on a failure: it says what went wrong, and the screen says at which step. */
private fun Activity.read(uri: Uri): FilePick =
    try {
        val (name, size) =
            contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    cursor.getString(0) to if (cursor.isNull(1)) null else cursor.getLong(1)
                } ?: (null to null)
        val bytes =
            contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException(uri.toString())
        FilePick.Picked(name ?: uri.lastPathSegment.orEmpty(), size ?: bytes.size.toLong(), bytes.decodeToString())
    } catch (e: IOException) {
        FilePick.Failed(e.message ?: e.javaClass.simpleName)
    } catch (e: SecurityException) {
        FilePick.Failed(e.message ?: e.javaClass.simpleName)
    }
