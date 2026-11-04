package com.casco.kettle.shell.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View

/**
 * Tells a screen reader a copy landed, where the system won't: Android 13 announces every copy itself
 * (its "Copied" toast), and its guidance is that an app doesn't double it; 12L and lower announce
 * nothing, so the app does.
 */
internal fun View.confirmCopy(confirmation: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
    // Deprecated from API 36 for live regions and pane titles; neither reaches a copy that changes no
    // layout, and this only runs on 32 and lower, where nothing is deprecated.
    @Suppress("DEPRECATION")
    announceForAccessibility(confirmation)
}

/**
 * False where the clipboard refused, so a control never claims a copy that didn't happen. No label:
 * one is metadata nothing here reads, and Settings' copy passed the text again as its label, sending
 * the whole of it to the binder twice.
 *
 * A clip past the binder's limit (about 1 MB) fails as a `RuntimeException` wrapping
 * `TransactionTooLargeException`, the same as [shareText]: the brief grows with the library, and the
 * copy is the fallback for exactly the brief too big to share, so it must say no rather than crash.
 * A withdrawn permission is a `SecurityException`, which is one too.
 */
@Suppress("TooGenericExceptionCaught")
internal fun Context.copyToClipboard(text: String): Boolean =
    try {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return false
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        true
    } catch (_: RuntimeException) {
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
