package com.casco.kettle.shell.system

import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Who tells a screen reader a copy landed (#244). From Android 13 the system does, with its own
 * "Copied" (seen on a device as a notification event), and its guidance is that an app doesn't double
 * it; below 13 nothing does, so the app has to. minSdk is 26, so both sides ship.
 */
@RunWith(RobolectricTestRunner::class)
class ConfirmCopyTest {
    private fun announced(): List<String> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val manager = shadowOf(activity.getSystemService(AccessibilityManager::class.java))
        manager.setEnabled(true)
        activity.window.decorView.confirmCopy("Copiado")
        // Every event's text, whatever its type: the question is only whether "Copiado" reached it.
        return manager.sentAccessibilityEvents.flatMap { it.text }.map(CharSequence::toString)
    }

    @Test
    @Config(sdk = [32])
    fun `announces the copy where the system won't`() {
        assertEquals(listOf("Copiado"), announced())
    }

    @Test
    @Config(sdk = [33])
    fun `leaves it to the system from Android 13, rather than saying it twice`() {
        assertEquals(emptyList<String>(), announced())
    }
}
