package com.casco.kettle.storage

import com.casco.kettle.domain.Session
import com.casco.kettle.domain.serializeSessionYaml
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Writes session [session]'s file in [dir], `sessions/` beside the library: native's edit of a finished
 * session (#239). Named as `sessionFile` in `src/storage/paths.ts` names it. The two share no code, so
 * change both. Throws [IOException] when the disk refuses, with the last file left as it was.
 */
fun writeSessionFile(
    dir: File,
    session: Session,
) = writeAtomically(dir.resolve("${session.id}.yaml"), serializeSessionYaml(session))

/**
 * [text] into [file] through a synced temporary file and a rename, as `AtomicFile` does, so a write cut
 * short leaves the last file whole rather than truncated: a torn session reads as an error, and a torn
 * library as one to reseed. The temporary name doesn't end `.yaml`, so neither side's listing of the
 * log reads one left behind. Throws [IOException] when the disk refuses.
 */
fun writeAtomically(
    file: File,
    text: String,
) {
    file.parentFile?.mkdirs()
    val partial = File(file.parentFile, "${file.name}.partial")
    var landed = false
    try {
        FileOutputStream(partial).use { out ->
            out.write(text.toByteArray())
            out.fd.sync()
        }
        // File.renameTo() says only no; the name is what the reader can act on.
        if (!partial.renameTo(file)) throw IOException(file.name)
        landed = true
    } finally {
        // Whatever stopped it, an IOException or a SecurityException, the half-written file goes.
        if (!landed) partial.delete()
    }
}
