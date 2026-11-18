package com.example

import android.content.Context
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.coreui.PolygonMaskShape
import com.example.coreui.resolveMaskShape
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.DockMiniChipType
import com.example.settingsstore.model.HomePageConfig
import com.example.settingsstore.model.IconMaskConfig
import com.example.settingsstore.model.IconMaskShape
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.WallpaperWavePattern
import com.example.settingsstore.repo.SettingsRepository
import com.example.universalsearch.UtilityParsers
import com.example.universalsearch.UtilityResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `app name matches DotOS Launcher`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("DotOS Launcher", context.getString(R.string.app_name))
    }

    @Test
    fun `advanced icon mask shapes including custom polygon and rotation produce valid outlines`() {
        val density = Density(2f)
        val size = Size(100f, 100f)

        IconMaskShape.entries.forEach { shapeType ->
            val shape = resolveMaskShape(
                shape = shapeType,
                borderRadiusPercent = 45f,
                polygonSides = 7,
                starInnerRatio = 0.42f,
                rotationDegrees = 35f
            )
            val outline = shape.createOutline(size, LayoutDirection.Ltr, density)
            assertNotNull(outline)
            if (shapeType in setOf(
                    IconMaskShape.STAR,
                    IconMaskShape.DIAMOND,
                    IconMaskShape.ROUNDED_HEXAGON,
                    IconMaskShape.HEXAGON,
                    IconMaskShape.CUSTOM_POLYGON
                )
            ) {
                assertTrue(shape is PolygonMaskShape)
                assertTrue(outline is Outline.Generic)
            }
        }
    }

    @Test
    fun `utility parsers evaluate math live currency conversions color and timers`() {
        val math = UtilityParsers.parse("245 * 18").filterIsInstance<UtilityResult.MathEvaluation>().firstOrNull()
        assertNotNull(math)
        assertEquals("4,410", math?.formattedResult)

        UtilityParsers.updateLiveFxRates(mapOf("USD" to 1.0, "EUR" to 0.90, "DZD" to 135.0))
        val fxConv = UtilityParsers.parse("100 USD to EUR").filterIsInstance<UtilityResult.Conversion>().firstOrNull()
        assertNotNull(fxConv)
        assertTrue(fxConv!!.targetText.contains("90 EUR"))
        assertTrue(fxConv.note.contains("Live FX"))

        val conv = UtilityParsers.parse("5 km to miles").filterIsInstance<UtilityResult.Conversion>().firstOrNull()
        assertNotNull(conv)
        assertTrue(conv!!.targetText.contains("MILES"))

        val color = UtilityParsers.parse("#D71921").filterIsInstance<UtilityResult.HexColorPreview>().firstOrNull()
        assertNotNull(color)
        assertEquals("#D71921", color?.hex)

        val timer = UtilityParsers.parse("timer 10m").filterIsInstance<UtilityResult.QuickTimer>().firstOrNull()
        assertNotNull(timer)
        assertEquals(600, timer?.durationSeconds)
    }

    @Test
    fun `launcher models include 11 widgets and full hyper-customization roundtrip`() = runTest {
        assertEquals(11, CustomWidgetType.entries.size)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)
        repo.initialize()
        repo.updateSettings {
            it.copy(
                iconMask = IconMaskConfig(
                    shape = IconMaskShape.CUSTOM_POLYGON,
                    innerPaddingDp = 9.5f,
                    borderRadiusPercent = 42f,
                    polygonSides = 8,
                    starInnerRatio = 0.55f,
                    rotationDegrees = 45f,
                    rimGlowEnabled = true,
                    duotoneFilter = true
                ),
                grid = it.grid.copy(
                    dockMiniChips = listOf(DockMiniChipType.BATTERY_DOT, DockMiniChipType.CLOCK_MINI)
                ),
                homePageConfigs = listOf(
                    HomePageConfig(name = "WORKSPACE", columnsOverride = 5)
                )
            )
        }
        val json = repo.exportToJson()
        val restored = repo.importFromJson(json).getOrThrow()
        assertEquals(IconMaskShape.CUSTOM_POLYGON, restored.iconMask.shape)
        assertEquals(8, restored.iconMask.polygonSides)
        assertEquals(45f, restored.iconMask.rotationDegrees, 0.01f)
        assertTrue(restored.iconMask.rimGlowEnabled)
        assertTrue(restored.iconMask.duotoneFilter)
        assertEquals(2, restored.grid.dockMiniChips.size)
        assertEquals("WORKSPACE", restored.pageConfigFor(0).name)
        assertEquals(5, restored.pageConfigFor(0).columnsOverride)

        val qrBitmap = repo.generateQrBitmap(640)
        assertEquals(640, qrBitmap.width)
        val decoded = repo.decodeQrFromBitmap(qrBitmap)
        assertNotNull(decoded)
        val qrRestored = repo.importFromJson(decoded!!).getOrThrow()
        assertEquals(IconMaskShape.CUSTOM_POLYGON, qrRestored.iconMask.shape)
    }

    @Test
    fun `home screen opens ambient AOD and settings hub displays hyper-customization controls`() {
        composeTestRule.waitForIdle()

        // Launch Ambient AOD Desk Clock
        composeTestRule.onNodeWithTag("home_aod_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ambient_aod_overlay").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Open Settings Hub -> Icon Mask & Grid tab
        composeTestRule.onNodeWithTag("home_settings_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_hub_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_tab_2").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mask_shape_custom_polygon").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("slider_mask_polygon_sides").assertExists()
        composeTestRule.onNodeWithTag("slider_mask_rotation").assertExists()
        composeTestRule.onNodeWithTag("toggle_mask_rim_glow").assertExists()
        composeTestRule.onNodeWithTag("toggle_mask_duotone").assertExists()
        composeTestRule.onNodeWithTag("dock_chip_battery_dot").assertExists()
        composeTestRule.onNodeWithTag("mask_shape_star").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("slider_mask_star_depth").assertExists()

        // Switch to Tab 0 (Surface) and verify Live Homescreen Animation controls
        composeTestRule.onNodeWithTag("settings_tab_0").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wallpaper_animation_preview").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("toggle_wallpaper_animation").performScrollTo().assertExists()
        composeTestRule.onNodeWithTag("wallpaper_pattern_radial_ripple").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("wallpaper_pattern_matrix_rain").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("slider_wallpaper_anim_speed").assertExists()
        composeTestRule.onNodeWithTag("slider_wallpaper_anim_intensity").assertExists()
        composeTestRule.onNodeWithTag("slider_wallpaper_brightness").assertExists()
        composeTestRule.onNodeWithTag("slider_wallpaper_dot_size").assertExists()
        composeTestRule.onNodeWithTag("slider_wallpaper_accent_freq").assertExists()
        composeTestRule.onNodeWithTag("slider_wallpaper_parallax").assertExists()

        // Switch to Tab 4 (Physics & Gestures) and verify transition intensity & icon press bounce
        composeTestRule.onNodeWithTag("settings_tab_4").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("slider_page_transition_intensity").assertExists()
        composeTestRule.onNodeWithTag("slider_icon_press_bounce").assertExists()
    }

    @Test
    fun `homescreen animation settings persist across json and qr roundtrip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)
        repo.initialize()
        repo.updateSettings {
            it.copy(
                theme = it.theme.copy(
                    wallpaperAnimationEnabled = false,
                    wallpaperWavePattern = WallpaperWavePattern.MATRIX_RAIN,
                    wallpaperAnimSpeed = 2.15f,
                    wallpaperAnimIntensity = 1.65f,
                    wallpaperDotBrightness = 1.45f,
                    wallpaperDotSizeScale = 1.35f,
                    wallpaperParallaxStrength = 1.8f,
                    wallpaperAccentFrequency = 1.5f
                ),
                physics = it.physics.copy(
                    pageTransitionIntensity = 1.4f,
                    iconPressBounce = 0.86f
                )
            )
        }
        val json = repo.exportToJson()
        val restored = repo.importFromJson(json).getOrThrow()
        assertEquals(false, restored.theme.wallpaperAnimationEnabled)
        assertEquals(WallpaperWavePattern.MATRIX_RAIN, restored.theme.wallpaperWavePattern)
        assertEquals(2.15f, restored.theme.wallpaperAnimSpeed, 0.01f)
        assertEquals(1.65f, restored.theme.wallpaperAnimIntensity, 0.01f)
        assertEquals(1.45f, restored.theme.wallpaperDotBrightness, 0.01f)
        assertEquals(1.35f, restored.theme.wallpaperDotSizeScale, 0.01f)
        assertEquals(1.8f, restored.theme.wallpaperParallaxStrength, 0.01f)
        assertEquals(1.5f, restored.theme.wallpaperAccentFrequency, 0.01f)
        assertEquals(1.4f, restored.physics.pageTransitionIntensity, 0.01f)
        assertEquals(0.86f, restored.physics.iconPressBounce, 0.01f)

        val qrBitmap = repo.generateQrBitmap(640)
        val decoded = repo.decodeQrFromBitmap(qrBitmap)
        assertNotNull(decoded)
        val qrRestored = repo.importFromJson(decoded!!).getOrThrow()
        assertEquals(WallpaperWavePattern.MATRIX_RAIN, qrRestored.theme.wallpaperWavePattern)
        assertEquals(0.86f, qrRestored.physics.iconPressBounce, 0.01f)
    }
}

