package spike

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** The common `pluralText` Phase 1 would need: i18next's zero step before CMP's plural rule. */
@Composable
fun pluralText(id: PluralStringResource, zeroId: StringResource, count: Int, vararg args: Any): String {
    val zero = stringResource(zeroId, count, *args)
    return if (count == 0 && zero.isNotEmpty()) zero else pluralStringResource(id, count, count, *args)
}
