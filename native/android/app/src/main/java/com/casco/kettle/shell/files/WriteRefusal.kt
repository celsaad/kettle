package com.casco.kettle.shell.files

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.casco.kettle.domain.MAX_YAML_CODE_POINTS
import com.casco.kettle.shell.R
import com.casco.kettle.storage.LibraryTooLargeException
import java.io.IOException

/**
 * Runs [write], and returns why the disk refused it, or null once it landed: a full disk, or a
 * permission withdrawn (a `SecurityException`, which often has no message, so its name stands in and
 * the refusal still says something rather than Save looking dead). Every [StoredFiles] write goes
 * through it, so each screen words a refusal the same way.
 */
internal suspend fun writeOrRefusal(write: suspend () -> Unit): String? =
    try {
        write()
        null
    } catch (e: IOException) {
        e.message ?: e.javaClass.simpleName
    } catch (e: SecurityException) {
        e.message ?: e.javaClass.simpleName
    }

/** Why a library write didn't land: the disk's own words, or a library too large to open again. */
sealed interface LibraryWriteRefusal {
    data class Disk(
        val detail: String,
    ) : LibraryWriteRefusal

    data object TooLarge : LibraryWriteRefusal
}

/**
 * [writeOrRefusal], for a write of the library, which alone can be refused for its size: that refusal is
 * ours to word, so it comes back as [LibraryWriteRefusal.TooLarge] for the screen to translate rather
 * than as an English detail.
 */
internal suspend fun libraryWriteOrRefusal(write: suspend () -> Unit): LibraryWriteRefusal? =
    try {
        write()
        null
    } catch (_: LibraryTooLargeException) {
        // Before the IOException it is, which would take it as the disk's.
        LibraryWriteRefusal.TooLarge
    } catch (e: IOException) {
        LibraryWriteRefusal.Disk(e.message ?: e.javaClass.simpleName)
    } catch (e: SecurityException) {
        LibraryWriteRefusal.Disk(e.message ?: e.javaClass.simpleName)
    }

/** [this] in words: the disk's detail inside [framing] ("Couldn't save: {{detail}}"), or the size's own. */
@Composable
internal fun LibraryWriteRefusal.text(
    @StringRes framing: Int,
): String =
    when (this) {
        is LibraryWriteRefusal.Disk -> {
            stringResource(framing, detail)
        }

        LibraryWriteRefusal.TooLarge -> {
            stringResource(
                R.string.common_libraryTooLarge,
                MAX_YAML_CODE_POINTS / BYTES_PER_MB,
            )
        }
    }

private const val BYTES_PER_MB = 1024 * 1024
