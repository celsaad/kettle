package com.casco.kettle.conformance

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/**
 * Gradle sets `SIMCTL_CHILD_KETTLE_CONFORMANCE_DIR`, and `simctl spawn` hands it to the test binary
 * with the prefix dropped. The simulator sees the Mac's file system, so the corpus is read in place.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun conformanceDirSetting(): String? = getenv("KETTLE_CONFORMANCE_DIR")?.toKString()

internal actual fun goldenWriter(): ((path: String, text: String) -> Unit)? = null
