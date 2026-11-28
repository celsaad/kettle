package com.casco.kettle.storage

import java.io.File
import java.io.IOException

internal actual fun readTextOrNull(path: String): String? =
    try {
        File(path).readText()
    } catch (_: IOException) {
        null
    }

internal actual fun listFileNames(dir: String): List<String> =
    File(dir)
        .listFiles()
        .orEmpty()
        .filter { it.isFile }
        .map { it.name }
