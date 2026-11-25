package com.casco.kettle.shell.files

import android.content.Context
import com.casco.kettle.domain.Library
import com.casco.kettle.domain.withTargetWeight
import java.io.IOException

/**
 * The runner's one-tap adopt (#220): kilograms straight off a logged set, set as that `reps` exercise's
 * target load in the library on disk. The library it wrote, which the screens then show, or null for
 * none. Nothing to set (no library, an unknown id, another type) writes nothing, as the RN
 * store's `setTargetWeightKg` did, and neither does a disk that refuses: the adopt stays silent, as it
 * always was, and the set on screen keeps the load either way.
 *
 * Blocking, and the caller's to run off the main thread. Read and written in one place so the write
 * builds on the file as it is, not on a library some screen read earlier.
 */
fun StoredFiles.adoptTargetWeight(
    exerciseId: String,
    weightKg: Double,
): Library? {
    val next = library()?.withTargetWeight(exerciseId, weightKg) ?: return null
    return try {
        writeLibrary(next)
        next
    } catch (_: IOException) {
        null
    }
}

/** [adoptTargetWeight] on the real files. */
fun Context.adoptTargetWeight(
    exerciseId: String,
    weightKg: Double,
): Library? = DiskFiles(StoragePaths(filesDir)).adoptTargetWeight(exerciseId, weightKg)
