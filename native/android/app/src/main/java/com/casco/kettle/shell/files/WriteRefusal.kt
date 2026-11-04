package com.casco.kettle.shell.files

import java.io.IOException

/**
 * Runs [write], and returns why the disk refused it, or null once it landed: a full disk, or a
 * permission withdrawn (a `SecurityException`, which often has no message, so its name stands in and
 * the refusal still says something rather than Save looking dead). Every native write goes through it:
 * the editors' saves, History's delete and Settings' preferences.
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
