package com.iwadjp.pixeltagdrawer

import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ThemeSelectionTest {

    @get:Rule
    val composeRule = createComposeRule()

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

    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun api30LightUsesFixedLightAndLightLaunchTheme() {
        assertScheme(lightColorScheme(), dark = false)
        assertLaunchTheme(dark = false)
    }

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun api30DarkUsesFixedDarkAndDarkLaunchTheme() {
        assertScheme(darkColorScheme(), dark = true)
        assertLaunchTheme(dark = true)
    }

    @Test
    @Config(sdk = [31], qualifiers = "notnight")
    fun api31LightUsesDynamicLightAndLightLaunchTheme() {
        assertScheme(dynamicLightColorScheme(ApplicationProvider.getApplicationContext()), dark = false)
        assertLaunchTheme(dark = false)
    }

    @Test
    @Config(sdk = [31], qualifiers = "night")
    fun api31DarkUsesDynamicDarkAndDarkLaunchTheme() {
        assertScheme(dynamicDarkColorScheme(ApplicationProvider.getApplicationContext()), dark = true)
        assertLaunchTheme(dark = true)
    }

    private fun assertScheme(expected: ColorScheme, dark: Boolean) {
        var actual: ColorScheme? = null
        composeRule.setContent {
            MaterialTheme(colorScheme = appColorScheme()) {
                actual = MaterialTheme.colorScheme
            }
        }
        composeRule.runOnIdle {
            val scheme = requireNotNull(actual)
            assertEquals(expected.primary, scheme.primary)
            assertEquals(expected.secondaryContainer, scheme.secondaryContainer)
            assertEquals(expected.onSecondaryContainer, scheme.onSecondaryContainer)
            assertEquals(expected.background, scheme.background)
            assertEquals(expected.surface, scheme.surface)
            assertEquals(expected.onSurface, scheme.onSurface)
            assertEquals(dark, scheme.surface.luminance() < 0.5f)
            assertEquals(dark, scheme.onSurface.luminance() > 0.5f)
        }
    }

    // MainActivity uses this application theme for both normal and forwarded shortcut launches.
    // Resolve it before Compose renders, rather than checking only the Compose palette.
    private fun assertLaunchTheme(dark: Boolean) {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.AppTheme)
        val attributes = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.windowBackground, android.R.attr.textColorPrimary),
        )
        try {
            val background = (attributes.getDrawable(0) as ColorDrawable).color
            val text = requireNotNull(attributes.getColorStateList(1)).defaultColor
            val backgroundLuminance = androidx.compose.ui.graphics.Color(background).luminance()
            val textLuminance = androidx.compose.ui.graphics.Color(text).luminance()
            assertEquals(dark, backgroundLuminance < 0.5f)
            assertEquals(dark, textLuminance > 0.5f)
        } finally {
            attributes.recycle()
        }
    }
}
