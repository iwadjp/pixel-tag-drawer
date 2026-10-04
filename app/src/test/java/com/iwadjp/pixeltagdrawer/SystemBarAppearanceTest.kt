package com.iwadjp.pixeltagdrawer

import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class SystemBarAppearanceTest {
    @Test
    @Config(sdk = [30], qualifiers = "notnight")
    fun api30LightUsesDarkSystemBarIcons() = assertSystemBarAppearance(light = true)

    @Test
    @Config(sdk = [30], qualifiers = "night")
    fun api30DarkUsesLightSystemBarIcons() = assertSystemBarAppearance(light = false)

    @Test
    @Config(sdk = [35], qualifiers = "notnight")
    fun api35LightUsesDarkSystemBarIcons() = assertSystemBarAppearance(light = true)

    @Test
    @Config(sdk = [35], qualifiers = "night")
    fun api35DarkUsesLightSystemBarIcons() = assertSystemBarAppearance(light = false)

    private fun assertSystemBarAppearance(light: Boolean) {
        val activity = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val window = activity.get().window
            val bars = WindowInsetsControllerCompat(window, window.decorView)
            assertEquals("status bar appearance", light, bars.isAppearanceLightStatusBars)
            assertEquals("navigation bar appearance", light, bars.isAppearanceLightNavigationBars)
        } finally {
            activity.destroy()
        }
    }
}
