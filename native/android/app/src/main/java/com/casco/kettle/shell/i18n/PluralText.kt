package com.casco.kettle.shell.i18n

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * A plural from the generated resources, with i18next's zero step: for a count of 0 it reads the
 * plural's `_zero` string first, and only falls back to the plural rule where that string is empty.
 * Android's own `getQuantityString` never selects `zero` for Portuguese, which files 0 under `one` and
 * so rendered "0 sessão". Every generated plural `x` has its `x_zero`, so call this rather than
 * `pluralStringResource`. `count` is always the first argument; `args` follow it in the order the
 * comment above the resource gives.
 */
fun Resources.pluralText(
    @PluralsRes id: Int,
    @StringRes zeroId: Int,
    count: Int,
    vararg args: Any,
): String {
    if (count == 0 && getString(zeroId).isNotEmpty()) return getString(zeroId, count, *args)
    return getQuantityString(id, count, count, *args)
}

/** [Resources.pluralText] for Compose, re-read when the configuration (and so the locale) changes. */
@Composable
@ReadOnlyComposable
fun pluralText(
    @PluralsRes id: Int,
    @StringRes zeroId: Int,
    count: Int,
    vararg args: Any,
): String {
    LocalConfiguration.current
    return LocalContext.current.resources.pluralText(id, zeroId, count, *args)
}
