package com.casco.kettle.shell.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * False where nothing on the device opens [url]: no mail app, or no browser, is not rare on Android,
 * so a caller falls back to copying it. Settings' contact links and the YAML view's format link (#444).
 */
internal fun Context.openLink(url: String): Boolean =
    try {
        val uri = url.toUri()
        startActivity(Intent(if (uri.scheme == "mailto") Intent.ACTION_SENDTO else Intent.ACTION_VIEW, uri))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
