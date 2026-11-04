package com.casco.kettle.storage

import com.casco.kettle.domain.Library
import com.casco.kettle.domain.ParseResult
import com.casco.kettle.domain.parseLibraryYaml
import com.casco.kettle.domain.repairLibraryBounds
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** What [prepareLibrary] found in the file, and so what it did. */
sealed interface PreparedLibrary {
    /** A library that reads: left alone. */
    data object Found : PreparedLibrary

    /** No file, as on a first launch: the seed was written. */
    data object Seeded : PreparedLibrary

    /** Values past the ceilings, clamped back and saved, keeping everything else. [why] is the refusal. */
    data class Repaired(
        val why: String,
    ) : PreparedLibrary

    /**
     * Beyond repair: moved to [quarantined] and replaced with the seed. [quarantined] is null where the
     * rescue itself failed, which doesn't stop the reseed. [why] is the refusal.
     */
    data class Reseeded(
        val why: String,
        val quarantined: File?,
    ) : PreparedLibrary
}

/**
 * Makes `exercises.yaml` in [file] a library RN can read, before RN reads it (#220, #221): native owns
 * the file from the library flip, and RN's load is parse-only, so a launch RN found no readable library
 * on would leave it on an error screen.
 *
 * A missing file is written from [seed], which the caller picks in the device language and which is
 * then the user's data, so a later language change never renames it. A refused one is repaired first,
 * since the ceilings turned files that used to parse into refusals ([repairLibraryBounds] says why),
 * and only what the repair can't save is reseeded, after the unreadable file is copied aside to
 * `exercises.invalid-<stamp>.yaml`: the recovery costs the user their access to the library, not the
 * library. A failed rescue doesn't block the reseed that gets the app running again.
 *
 * Every write is atomic. Throws [IOException] when the disk refuses one, with the file left as it was.
 */
fun prepareLibrary(
    file: File,
    seed: () -> Library,
    now: () -> Instant = Instant::now,
): PreparedLibrary {
    val text = if (file.exists()) file.readText() else null
    val refusal = text?.let(::refusalOf)
    val repaired = if (text != null && refusal != null) repaired(text) else null
    return when {
        text == null -> {
            writeLibraryFile(file, seed())
            PreparedLibrary.Seeded
        }

        refusal == null -> {
            PreparedLibrary.Found
        }

        repaired != null -> {
            writeLibraryFile(file, repaired)
            PreparedLibrary.Repaired(refusal)
        }

        else -> {
            val quarantined = quarantine(file, text, now())
            writeLibraryFile(file, seed())
            PreparedLibrary.Reseeded(refusal, quarantined)
        }
    }
}

/** [text] with the ceilings' refusals clamped, where that leaves a library that reads. */
private fun repaired(text: String): Library? =
    repairLibraryBounds(text)?.let { (parseLibraryYaml(it) as? ParseResult.Ok)?.value }

/** Why the parser refuses [text], as RN's log line spelled it, or null where it reads. */
private fun refusalOf(text: String): String? =
    when (val result = parseLibraryYaml(text)) {
        is ParseResult.Ok -> null
        is ParseResult.InvalidYaml -> "invalidYaml: ${result.detail}"
        is ParseResult.SchemaMismatch -> "schemaMismatch: ${result.detail}"
    }

/** [text] beside [file], or null where the disk refused, which the reseed goes on without. */
private fun quarantine(
    file: File,
    text: String,
    at: Instant,
): File? {
    val aside = File(file.parentFile, "exercises.invalid-${STAMP.format(at)}.yaml")
    return try {
        writeAtomically(aside, text)
        aside
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    }
}

/** An ISO instant with `:` and `.` as `-`, as the quarantined files RN named before the flip were. */
private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss-SSS'Z'").withZone(ZoneOffset.UTC)
