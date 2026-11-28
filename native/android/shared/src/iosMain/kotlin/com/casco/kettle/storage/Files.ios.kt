package com.casco.kettle.storage

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.open
import platform.posix.read

/**
 * POSIX rather than `NSString(contentsOfFile:)`, which refuses a file that isn't valid UTF-8 where the
 * JVM's `readText` decodes it with replacement characters. [decodeToString] does what the JVM does, so
 * a damaged file reads the same on both and the parser, not the reader, is what refuses it.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun readTextOrNull(path: String): String? {
    val fd = open(path, O_RDONLY)
    if (fd < 0) return null
    return try {
        readAll(fd)?.decodeToString()
    } finally {
        close(fd)
    }
}

/** Every byte left in [fd], or null where a read fails partway. */
@OptIn(ExperimentalForeignApi::class)
private fun readAll(fd: Int): ByteArray? {
    val chunk = ByteArray(CHUNK_BYTES)
    var bytes = ByteArray(0)
    var count: Long
    do {
        count = chunk.usePinned { read(fd, it.addressOf(0), CHUNK_BYTES.toULong()) }
        if (count > 0) bytes += chunk.copyOf(count.toInt())
    } while (count > 0)
    return if (count < 0) null else bytes
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun listFileNames(dir: String): List<String> {
    val manager = NSFileManager.defaultManager
    val names = manager.contentsOfDirectoryAtPath(dir, error = null).orEmpty()
    return names.filterIsInstance<String>().filter { name ->
        // Through a symlink, as java.io's `isFile` is.
        memScoped {
            val isDirectory = alloc<BooleanVar>()
            manager.fileExistsAtPath("$dir/$name", isDirectory = isDirectory.ptr) && !isDirectory.value
        }
    }
}

private const val CHUNK_BYTES = 64 * 1024
