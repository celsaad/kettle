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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.MAX_IMPORT_CHARS
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.LocalLatestFiles
import com.casco.kettle.shell.files.ShippedContent
import com.casco.kettle.shell.nav.LocalNavigator
import com.casco.kettle.shell.system.confirmCopy
import com.casco.kettle.shell.system.copyToClipboard
import com.casco.kettle.shell.system.readClipboard
import com.casco.kettle.shell.system.shareText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * What the route table draws at `Destination.Import`, as Home's first-run link, Library and Settings open
 * it: the screen on SAF and the shell.
 */
@Composable
fun ImportRoute() {
    val activity = LocalActivity.current ?: return
    val pickFile = rememberFilePicker(activity)
    val view = LocalView.current
    val copied = stringResource(R.string.import_copied)
    val navigator = LocalNavigator.current
    ImportScreen(
        LocalLatestFiles.current,
        remember { ShippedContent(activity.assets) },
        remember(pickFile, view, copied, navigator) {
            ImportActions(
                pickFile = pickFile,
                close = { navigator.back() },
                copy = { text -> activity.copyToClipboard(text).also { if (it) view.confirmCopy(copied) } },
                readClipboard = activity::readClipboard,
                share = activity::shareText,
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
        readPick(uri)
    } catch (e: IOException) {
        FilePick.Failed(e.message ?: e.javaClass.simpleName)
    } catch (e: SecurityException) {
        FilePick.Failed(e.message ?: e.javaClass.simpleName)
    }

private fun Activity.readPick(uri: Uri): FilePick {
    val (name, size) =
        contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getString(0) to if (cursor.isNull(1)) null else cursor.getLong(1)
            } ?: (null to null)
    // Bounded as well as checked: the provider's size is optional, and can be wrong.
    val bytes =
        if (size != null && size > MAX_IMPORT_CHARS) {
            null
        } else {
            val stream = contentResolver.openInputStream(uri) ?: throw IOException(uri.toString())
            stream.use { it.readAtMost(MAX_IMPORT_CHARS) }
        }
    return if (bytes == null) {
        FilePick.TooLarge
    } else {
        FilePick.Picked(name ?: uri.lastPathSegment.orEmpty(), size ?: bytes.size.toLong(), bytes.decodeToString())
    }
}

/** This stream's bytes, or null once it holds more than [limit]: a huge file is never held whole. */
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) return null
        out.write(buffer, 0, read)
    }
}
