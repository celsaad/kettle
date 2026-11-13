package com.casco.kettle.storage

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.MAX_YAML_CODE_POINTS
import com.casco.kettle.domain.Session
import com.casco.kettle.domain.serializeLibraryYaml
import com.casco.kettle.domain.serializeSessionYaml
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Writes session [session]'s file in [dir], `sessions/` beside the library: native's edit of a finished
 * session (#239), at the name [sessionFile] gives it. Throws [IOException] when the disk refuses, with
 * the last file left as it was.
 */
fun writeSessionFile(
    dir: File,
    session: Session,
) = writeAtomically(sessionFile(dir, session.id), serializeSessionYaml(session))

/**
 * Session [id]'s file in [dir]. The id is the file's own content, which the schema only requires to be
 * non-empty, so one holding a separator would name a file outside `sessions/`: `../exercises` is the
 * library, which editing or deleting that session, or the launch's sweep of unfinished ones, would
 * overwrite. Throws [IOException] for such an id, as for any write the disk refuses, which every caller
 * already reports.
 */
fun sessionFile(
    dir: File,
    id: String,
): File {
    if (!isSessionFileId(id)) throw IOException("Not a session id: $id")
    return dir.resolve("$id.yaml")
}

/** Whether [id] names a file inside the log, as [sessionFile] needs: no separator, and no NUL. */
fun isSessionFileId(id: String): Boolean = id.none { it == '/' || it == '\\' || it == '\u0000' }

/**
 * Writes [library] over [file], `exercises.yaml`: native's library editors, from the library flip
 * (#222) on. Throws [IOException] when the disk refuses, with the last library left whole, since a
 * torn one would read as corrupt and be reseeded.
 */
fun writeLibraryFile(
    file: File,
    library: Library,
) {
    val text = serializeLibraryYaml(library)
    // Past the parser's cap the next launch would quarantine the whole library, so the write that
    // would get it there is refused instead, where the screen can say so. Chars, not code points: a
    // surrogate pair counts twice, which only ever refuses early.
    if (text.length > MAX_YAML_CODE_POINTS) throw IOException("The library would be too large to read back")
    writeAtomically(file, text)
}

/**
 * This stream's bytes, or null once it holds more than [limit]: a file too large to take is never held
 * whole, whatever size its provider reported.
 */
fun InputStream.readAtMost(limit: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = read(buffer)
        if (read < 0) return out.toByteArray()
        if (out.size() + read > limit) return null
        out.write(buffer, 0, read)
    }
}

/**
 * [text] into [file] through a synced temporary file and a rename, as `AtomicFile` does, so a write cut
 * short leaves the last file whole rather than truncated: a torn session reads as an error, and a torn
 * library as one to reseed. The temporary name doesn't end `.yaml`, so neither side's listing of the
 * log reads one left behind. Then the folder is synced too, best effort ([forceDirectory]), so the
 * rename outlasts a power cut. Throws [IOException] when the disk refuses.
 */
fun writeAtomically(
    file: File,
    text: String,
) = writeAtomically(file, text, beforeRename = {}, syncDirectory = ::forceDirectory)

/** [writeAtomically], with the step before its rename and its directory sync for the tests to replace. */
internal fun writeAtomically(
    file: File,
    text: String,
    beforeRename: () -> Unit,
    syncDirectory: (File) -> Unit,
) {
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, "${file.name}.partial")
    var landed = false
    try {
        FileOutputStream(partial).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        beforeRename()
        // File.renameTo() says only no; the name is what the reader can act on.
        if (!partial.renameTo(file)) throw IOException(file.name)
        landed = true
    } finally {
        // Whatever stopped it, an IOException or a SecurityException, the half-written file goes.
        if (!landed) partial.delete()
    }
    // Absolute, unlike the parentFile above: a bare relative name has no parent until it's made one.
    val dir = file.absoluteFile.parentFile ?: return
    // The write has already landed, so nothing here may undo it: an IOException (Windows can't open a
    // directory), a SecurityException, or an UnsupportedOperationException (a filesystem with no such
    // sync).
    try {
        syncDirectory(dir)
    } catch (_: IOException) {
    } catch (_: SecurityException) {
    } catch (_: UnsupportedOperationException) {
    }
}

/**
 * Makes a rename into [dir] durable (#333): without it, a power cut can lose the write, though never
 * empty the file, since the old one is whole until the rename is.
 */
private fun forceDirectory(dir: File) {
    FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { it.force(true) }
}
