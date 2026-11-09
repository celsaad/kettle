package com.casco.kettle.shell.files

import android.content.Context
import com.casco.kettle.domain.withTargetWeight
import java.io.IOException

/**
 * The runner's one-tap adopt (#220): kilograms straight off a logged set, set as that `reps` exercise's
 * target load in the library on disk. Whether it wrote, which is when the screens have a library to
 * read again. Nothing to set (no library, an unknown id, another type) writes nothing, as the RN
 * store's `setTargetWeightKg` did, and neither does a disk that refuses: the adopt stays silent, as it
 * always was, and the set on screen keeps the load either way.
 *
 * Blocking, and the caller's to run off the main thread. Read and written in one place so the write
 * builds on the file as it is, not on a library some screen read earlier.
 */
fun StoredFiles.adoptTargetWeight(
    exerciseId: String,
    weightKg: Double,
): Boolean {
    val next = library()?.withTargetWeight(exerciseId, weightKg) ?: return false
    return try {
        writeLibrary(next)
        true
    } catch (_: IOException) {
        false
    }
}

/** [adoptTargetWeight] on the real files. */
fun Context.adoptTargetWeight(
    exerciseId: String,
    weightKg: Double,
): Boolean = DiskFiles(StoragePaths(filesDir)).adoptTargetWeight(exerciseId, weightKg)
