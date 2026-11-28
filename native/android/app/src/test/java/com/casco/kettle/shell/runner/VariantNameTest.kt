package com.casco.kettle.shell.runner

import com.casco.kettle.domain.IntervalVariant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An interval's name as its lines say it (#462): the library's words, never the YAML id, and Cardio
 * capitalised as its badge is, where the lower-case "cardio" is a list's word. In `pt`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "pt")
class VariantNameTest {
    private val resources = RuntimeEnvironment.getApplication().resources

    @Test
    fun `names each variant as the library does`() {
        assertEquals("HIIT", resources.variantName(IntervalVariant.HIIT))
        assertEquals("EMOM", resources.variantName(IntervalVariant.EMOM))
        assertEquals("AMRAP", resources.variantName(IntervalVariant.AMRAP))
        assertEquals("Cardio", resources.variantName(IntervalVariant.CARDIO))
    }
}
