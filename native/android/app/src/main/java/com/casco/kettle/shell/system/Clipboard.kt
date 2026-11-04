package com.casco.kettle.shell.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

/** False where the clipboard refused, so a control never claims a copy that didn't happen. */
internal fun Context.copyToClipboard(text: String): Boolean =
    try {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(text, text))
        true
    } catch (_: SecurityException) {
        false
    }

/**
 * The clipboard's text, coerced as a paste would be, blank when it holds none, or null where it
 * refused to be read. Android lets only the focused app read it, which the screen asking is.
 */
internal fun Context.readClipboard(): String? =
    try {
        val clip = getSystemService(ClipboardManager::class.java)?.primaryClip
        clip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
    } catch (_: SecurityException) {
        null
    }

/**
 * [text] handed to the system share sheet, as text rather than a file: a chat app takes text into its
 * compose box, and mostly doesn't take an attached `.md`. False where no sheet could open. Backing out
 * of one isn't seen here at all, so it can't be mistaken for a failure.
 *
 * No sheet at all is an `ActivityNotFoundException`; a brief past the binder's limit (about 1 MB; it
 * grows with the library) is a `RuntimeException` wrapping `TransactionTooLargeException`, which
 * nothing narrower than `RuntimeException` catches. Either is a sheet that won't open, which the screen
 * degrades to a note under the clipboard button.
 */
@Suppress("TooGenericExceptionCaught")
internal fun Context.shareText(text: String): Boolean =
    try {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
        true
    } catch (_: RuntimeException) {
        false
    }
