package com.casco.kettle.shell.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * `ThemedText`'s types (src/components/themed-text.tsx), at its sizes. The two share no code, so change
 * both. Set in the system font until the shell bundles RN's Space Grotesk and Hanken Grotesk: the
 * display types take Space Grotesk's semibold weight meanwhile, so the hierarchy reads the same.
 */
object KettleType {
    val default = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
    val subtitle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
    val heading = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
}
