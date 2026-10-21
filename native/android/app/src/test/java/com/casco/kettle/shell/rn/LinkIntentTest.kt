package com.casco.kettle.shell.rn

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * React Native reads `intent.data` as the initial URL only under ACTION_VIEW (an M0 build fact), so a
 * link that arrives under any other action would open the app at its home screen instead.
 */
@RunWith(RobolectricTestRunner::class)
class LinkIntentTest {
    @Test
    fun `a launcher intent carrying a link is turned into a view of it`() {
        val intent = Intent(Intent.ACTION_MAIN, Uri.parse("kettle://history"))

        assertEquals(Intent.ACTION_VIEW, intent.withViewAction().action)
    }

    @Test
    fun `a plain launch is left as it is`() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        assertEquals(Intent.ACTION_MAIN, intent.withViewAction().action)
    }
}
