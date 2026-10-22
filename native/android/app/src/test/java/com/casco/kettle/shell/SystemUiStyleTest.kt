package com.casco.kettle.shell

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * expo-system-ui sets AppCompat's night mode from this string on every launch, and its own default is
 * `light`, which pins the whole app light and leaves Settings → Appearance → System nothing to follow.
 * Prebuild wrote app.json's `userInterfaceStyle` over that default; the shell has no prebuild, so its
 * build has to.
 */
@RunWith(RobolectricTestRunner::class)
class SystemUiStyleTest {
    @Test
    fun followsTheSystemSchemeAsAppJsonAsks() {
        val style =
            RuntimeEnvironment.getApplication().getString(
                expo.modules.systemui.R.string.expo_system_ui_user_interface_style,
            )
        assertEquals("automatic", style)
    }
}
