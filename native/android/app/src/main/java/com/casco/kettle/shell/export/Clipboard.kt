package com.casco.kettle.shell.export

import android.content.ActivityNotFoundException
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
 */
internal fun Context.shareText(text: String): Boolean =
    try {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
