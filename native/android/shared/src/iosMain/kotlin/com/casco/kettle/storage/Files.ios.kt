package com.casco.kettle.storage

import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSFileManager
import platform.posix.EINTR
import platform.posix.O_RDONLY
import platform.posix.close
import platform.posix.errno
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

/**
 * Every byte left in [fd], or null where a read fails partway. A read interrupted by a signal is tried
 * again rather than read as a failure; the bytes grow in one buffer, doubled as it fills, so a large
 * file costs no more than reading it.
 */
@OptIn(ExperimentalForeignApi::class)
private fun readAll(fd: Int): ByteArray? {
    var bytes = ByteArray(CHUNK_BYTES)
    var size = 0
    while (true) {
        if (size == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
        val room = bytes.size - size
        val count = bytes.usePinned { read(fd, it.addressOf(size), room.convert()) }
        when {
            count > 0 -> size += count.toInt()
            count == 0L -> return bytes.copyOf(size)
            errno != EINTR -> return null
        }
    }
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
