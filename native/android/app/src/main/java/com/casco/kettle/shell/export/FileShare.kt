package com.casco.kettle.shell.export

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.FileProvider
import com.casco.kettle.domain.HISTORY_BACKUP_NAME
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.isoInstant
import com.casco.kettle.domain.serializeSessionArchiveYaml
import com.casco.kettle.shell.R
import com.casco.kettle.shell.files.StoragePaths
import com.casco.kettle.storage.sessionFile
import com.casco.kettle.storage.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Export, through the platform share sheet: Settings', History's and Library's (#237, #238). The files
 * go as they are, the library and a session as the files themselves and the whole log as one archive,
 * so an export and a backup can't disagree.
 *
 * Each answers null where the sheet opened, or the reason it didn't, for the screen to say: a share
 * that does nothing on the one row whose job it is reads as broken.
 */
class FileShare(
    private val activity: Activity,
    private val paths: StoragePaths,
) {
    fun library(): String? = share(paths.library, activity.getString(R.string.settings_shareLibraryTitle))

    /** The file [sessionFile] names, which refuses an id that would reach outside the log. */
    fun session(id: String): String? {
        val file =
            try {
                sessionFile(paths.sessions, id)
            } catch (error: IOException) {
                return error.reason()
            }
        return share(file, activity.getString(R.string.settings_shareSessionTitle, id))
    }

    /**
     * Writes [sessions] as one archive under the cache, replaced each time, and shares that: a share
     * hands over one file, so a whole log can't arrive as the directory it's kept in.
     */
    suspend fun history(sessions: List<Session>): String? {
        val file = File(activity.cacheDir, "$EXPORTS/$HISTORY_BACKUP_NAME")
        try {
            withContext(Dispatchers.IO) {
                writeAtomically(file, serializeSessionArchiveYaml(sessions, isoInstant(System.currentTimeMillis())))
            }
        } catch (error: IOException) {
            return error.reason()
        }
        return share(file, activity.getString(R.string.settings_shareHistoryTitle))
    }

    /**
     * `application/x-yaml`, as RN's. The chooser's title is translated, since Android shows it; the
     * session id in it is user data, interpolated.
     */
    private fun share(
        file: File,
        title: String,
    ): String? =
        try {
            if (!file.exists()) throw FileNotFoundException(file.name)
            val uri = FileProvider.getUriForFile(activity, "${activity.packageName}$AUTHORITY", file)
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = YAML_MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    // The clip as well as the extra: the grant reaches the app chosen through it.
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            activity.startActivity(Intent.createChooser(send, title))
            null
        } catch (error: FileNotFoundException) {
            error.reason()
        } catch (error: ActivityNotFoundException) {
            error.reason()
        } catch (error: IllegalArgumentException) {
            // getUriForFile's refusal of a path outside res/xml/shared_files.xml.
            error.reason()
        }

    private companion object {
        /** The manifest's `ShellFileProvider` authority, after the package name. */
        const val AUTHORITY = ".shell.files"
        const val EXPORTS = "exports"
    }
}

/** This activity's [FileShare], or null outside one, as in a preview. */
@Composable
fun rememberFileShare(): FileShare? {
    val activity = LocalActivity.current ?: return null
    return remember(activity) { FileShare(activity, StoragePaths(activity.filesDir)) }
}

/**
 * Logs why a share from a screen with nowhere to say so didn't open: History's and Library's, which
 * RN's export only warned about to the console too.
 */
fun logShareRefused(reason: String?) {
    if (reason != null) Log.w("FileShare", "The share sheet didn't open: $reason")
}

/** What both the share and the backup folder call a library or an archive: RN's, kept. */
internal const val YAML_MIME_TYPE = "application/x-yaml"

/** The reason to show for [this], which a platform exception doesn't always carry as a message. */
private fun Throwable.reason() = message ?: toString()

/**
 * The shell's own, so its paths and authority can't collide with a library's provider in the merged
 * manifest, which keys providers by class name. Its paths are the manifest's meta-data, not a
 * constructor argument: `getUriForFile` finds them by authority, and never asks the instance.
 */
class ShellFileProvider : FileProvider()
