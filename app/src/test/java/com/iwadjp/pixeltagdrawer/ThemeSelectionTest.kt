package com.iwadjp.pixeltagdrawer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ThemeSelectionTest {

    @Test
    @Config(sdk = [30])
    fun api30UsesFixedLightColorScheme() {
        assertFalse(supportsDynamicColor())
    }

    @Test
    @Config(sdk = [31])
    fun api31UsesDynamicColorScheme() {
        assertTrue(supportsDynamicColor())
    }
}
