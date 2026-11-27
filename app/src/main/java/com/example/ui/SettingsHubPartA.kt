package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appindexer.AppIndexerRepository
import com.example.appindexer.IndexedApp
import com.example.appindexer.InstalledIconPack
import com.example.appindexer.NothingGlyphCatalog
import com.example.coreui.DotMatrixDivider
import com.example.coreui.MaskedAppIcon
import com.example.coreui.NothingGroupCard
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSectionHeader
import com.example.coreui.NothingSliderRow
import com.example.coreui.NothingSurfaceCard
import com.example.coreui.NothingToggleRow
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.settingsstore.db.ThemePresetEntity
import com.example.settingsstore.model.BadgeStyle
import com.example.settingsstore.model.ContextRule
import com.example.settingsstore.model.DockMiniChipType
import com.example.settingsstore.model.FontFamilyChoice
import com.example.settingsstore.model.HomePageConfig
import com.example.settingsstore.model.IconMaskShape
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SurfaceThemeConfig
import com.example.settingsstore.model.TextCaseMode
import com.example.settingsstore.model.TypographySlot
import com.example.settingsstore.model.WallpaperMode
import com.example.settingsstore.model.WallpaperWavePattern
import com.example.settingsstore.model.WidgetStackSlot
import com.example.settingsstore.repo.SettingsRepository
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingLiveWallpaperCanvas
import com.example.themeengine.NothingTypographyEngine
import com.example.themeengine.parseHexColor
import com.example.widgethost.LauncherWidgetHostManager
import kotlin.math.roundToInt

@Composable
fun SettingsHubScreen(
    initialTab: Int,
    settings: LauncherSettings,
    allApps: List<IndexedApp>,
    appIndexer: AppIndexerRepository,
    themePresets: List<Pair<ThemePresetEntity, SurfaceThemeConfig>>,
    installedIconPacks: List<InstalledIconPack>,
    settingsRepo: SettingsRepository,
    widgetHostManager: LauncherWidgetHostManager,
    telemetry: DeviceTelemetrySnapshot = DeviceTelemetrySnapshot(),
    hasBluetoothPermission: Boolean = true,
    onRequestBluetoothPermission: () -> Unit = {},
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onAddCustomSearchEngine: (String, String) -> Unit,
    onClearClipboardHistory: () -> Unit = {},
    onToggleDockMiniChip: (DockMiniChipType) -> Unit = {},
    onUpdatePageConfig: (Int, (HomePageConfig) -> HomePageConfig) -> Unit = { _, _ -> },
    onUpsertContextRule: (ContextRule) -> Unit = {},
    onDeleteContextRule: (String) -> Unit = {},
    onImportBackup: (String) -> Unit,
    onOpenVault: () -> Unit,
    onBindSystemWidget: (WidgetStackSlot, android.appwidget.AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val palette = LocalNothingPalette.current
    var activeTab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 6)) }

    val tabTitles = listOf(
        "Surface & Wallpaper",
        "Typography",
        "Icons & Grid",
        "Search & Tools",
        "Motion & Gestures",
        "Widgets",
        "System & Vault"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("settings_hub_screen")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DOTOS STUDIO",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 18.sp,
                    color = palette.accent
                )
                Text(
                    text = "Live Preview Customization Engine",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("settings_close_button")) {
                Icon(Icons.Rounded.Close, "Close Settings", tint = palette.textPrimary)
            }
        }

        // Sticky Live Preview Card
        LiveCustomizationPreviewBanner(
            settings = settings,
            sampleApps = allApps.take(4),
            appIndexer = appIndexer,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Tab Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabTitles.forEachIndexed { idx, title ->
                NothingPillChip(
                    label = title,
                    selected = activeTab == idx,
                    onClick = { activeTab = idx },
                    modifier = Modifier.testTag("settings_tab_$idx")
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Active Tab Body
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                0 -> SurfaceWallpaperTab(
                    settings = settings,
                    themePresets = themePresets,
                    onUpdateSettings = onUpdateSettings,
                    onSavePreset = onSavePreset,
                    onApplyPreset = onApplyPreset,
                    onDeletePreset = onDeletePreset
                )
                1 -> TypographyEngineTab(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )
                2 -> IconMaskAndGridTab(
                    settings = settings,
                    installedIconPacks = installedIconPacks,
                    onUpdateSettings = onUpdateSettings,
                    onToggleDockMiniChip = onToggleDockMiniChip,
                    onUpdatePageConfig = onUpdatePageConfig
                )
                3 -> SearchAndDrawerTab(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                    onAddCustomSearchEngine = onAddCustomSearchEngine,
                    onClearClipboardHistory = onClearClipboardHistory
                )
                4 -> PhysicsAndGesturesTab(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings
                )
                5 -> WidgetsAndContextTab(
                    settings = settings,
                    allApps = allApps,
                    themePresets = themePresets,
                    telemetry = telemetry,
                    hasBluetoothPermission = hasBluetoothPermission,
                    onRequestBluetoothPermission = onRequestBluetoothPermission,
                    widgetHostManager = widgetHostManager,
                    onUpdateSettings = onUpdateSettings,
                    onUpsertContextRule = onUpsertContextRule,
                    onDeleteContextRule = onDeleteContextRule,
                    onBindSystemWidget = onBindSystemWidget
                )
                6 -> VaultAndBackupTab(
                    settings = settings,
                    settingsRepo = settingsRepo,
                    onUpdateSettings = onUpdateSettings,
                    onImportBackup = onImportBackup,
                    onOpenVault = onOpenVault
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LiveCustomizationPreviewBanner(
    settings: LauncherSettings,
    sampleApps: List<IndexedApp>,
    appIndexer: AppIndexerRepository,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val homeStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyHome)
    val widgetTitleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)

    NothingSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        borderWidth = settings.theme.widgetBorderDp.dp,
        backgroundColor = palette.widgetBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = NothingTypographyEngine.formatText(
                        "LIVE PREVIEW • ${settings.iconMask.shape.displayName}",
                        settings.typographyWidgetTitle.textCase
                    ),
                    style = widgetTitleStyle.copy(color = palette.accent, fontSize = 11.sp)
                )
                Text(
                    text = "PAD ${settings.iconMask.innerPaddingDp.roundToInt()}dp • RAD ${settings.iconMask.borderRadiusPercent.roundToInt()}%",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (sampleApps.isNotEmpty()) {
                    sampleApps.forEach { app ->
                        val bmp = appIndexer.getCachedIcon(app.packageName, app.activityName)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MaskedAppIcon(
                                iconBitmap = bmp,
                                maskConfig = settings.iconMask,
                                baseSize = 46.dp,
                                badgeCount = 3,
                                badgeStyle = settings.badgeStyle
                            )
                            Spacer(Modifier.height(settings.iconMask.labelOffsetDp.dp))
                            Text(
                                text = NothingTypographyEngine.formatText(app.label.take(8), settings.typographyHome.textCase),
                                style = homeStyle.copy(color = palette.textPrimary)
                            )
                        }
                    }
                } else {
                    listOf("bolt", "camera", "music", "terminal").forEach { glyphId ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MaskedAppIcon(
                                iconBitmap = null,
                                maskConfig = settings.iconMask,
                                baseSize = 46.dp,
                                customGlyphVector = NothingGlyphCatalog.resolve(glyphId),
                                badgeCount = 2,
                                badgeStyle = settings.badgeStyle
                            )
                            Spacer(Modifier.height(settings.iconMask.labelOffsetDp.dp))
                            Text(
                                text = NothingTypographyEngine.formatText(glyphId, settings.typographyHome.textCase),
                                style = homeStyle.copy(color = palette.textPrimary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurfaceWallpaperTab(
    settings: LauncherSettings,
    themePresets: List<Pair<ThemePresetEntity, SurfaceThemeConfig>>,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onSavePreset: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit
) {
    val palette = LocalNothingPalette.current
    var newPresetName by remember { mutableStateOf("") }

    NothingGroupCard(
        title = "Home Screen Behavior",
        subtitle = "Immersive mode & app label visibility"
    ) {
        NothingToggleRow(
            label = "Immersive Full-Screen",
            description = "Hide status bar icons (battery, Wi-Fi, notifications). Swipe from the top edge to peek.",
            checked = settings.fullScreenHideStatusBar,
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(fullScreenHideStatusBar = checked) }
            },
            modifier = Modifier.testTag("toggle_fullscreen_status_bar")
        )
        NothingToggleRow(
            label = "Show App Names",
            description = "Turn off for a clean icon-only Home Screen and All Apps grid.",
            checked = settings.showAppLabels,
            onCheckedChange = { checked ->
                onUpdateSettings { it.copy(showAppLabels = checked) }
            },
            modifier = Modifier.testTag("toggle_show_app_labels")
        )
    }

    NothingSectionHeader("Theme Presets", "Pre-loaded & unlimited custom saved presets")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        themePresets.forEach { (entity, cfg) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                NothingPillChip(
                    label = cfg.presetName,
                    selected = settings.theme.presetId == entity.id,
                    onClick = { onApplyPreset(entity.id) }
                )
                if (!entity.isBuiltIn) {
                    IconButton(onClick = { onDeletePreset(entity.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Delete, "Delete Preset", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newPresetName,
            onValueChange = { newPresetName = it },
            label = { Text("New Preset Name") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                if (newPresetName.isNotBlank()) {
                    onSavePreset(newPresetName.trim())
                    newPresetName = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
        ) {
            Text("Save", color = Color.White)
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Wallpaper & OLED", "Interactive dot-matrix & topographic canvas")

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WallpaperMode.entries.forEach { mode ->
            NothingPillChip(
                label = mode.displayName,
                selected = settings.theme.wallpaperMode == mode,
                onClick = {
                    onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperMode = mode)) }
                }
            )
        }
    }

    NothingToggleRow(
        label = "OLED Pure Black Mode (#000000)",
        description = "Turns off background OLED pixels for battery savings",
        checked = settings.theme.oledPureBlack,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(theme = it.theme.copy(oledPureBlack = checked)) }
        }
    )

    NothingToggleRow(
        label = "Wallpaper Tilt Parallax",
        description = "Hardware accelerometer parallax depth",
        checked = settings.theme.wallpaperTiltParallax,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperTiltParallax = checked)) }
        }
    )

    NothingToggleRow(
        label = "Material You Wallpaper Extraction",
        description = "Extract Android 12+ dynamic system accents",
        checked = settings.theme.useMaterialYou,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(theme = it.theme.copy(useMaterialYou = checked)) }
        }
    )

    NothingSliderRow(
        label = "Wallpaper Dot Density",
        value = settings.theme.wallpaperDotDensity,
        valueRange = 0.25f..1.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperDotDensity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )

    DotMatrixDivider()
    NothingSectionHeader(
        "Live Homescreen Animation",
        "Customize the background dot-matrix wave, contours, glow & tilt motion"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(18.dp))
            .testTag("wallpaper_animation_preview")
    ) {
        NothingLiveWallpaperCanvas(
            theme = settings.theme,
            deepFocusActive = settings.deepFocusActive,
            modifier = Modifier.fillMaxSize()
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.surface.copy(alpha = 0.85f))
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (settings.theme.wallpaperAnimationEnabled) palette.accent else palette.textSecondary)
            )
            Text(
                text = if (settings.theme.wallpaperAnimationEnabled) {
                    "${settings.theme.wallpaperMode.displayName.uppercase()} • ${settings.theme.wallpaperWavePattern.displayName.uppercase()}"
                } else {
                    "STATIC CANVAS (ANIMATION PAUSED)"
                },
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 9.5.sp,
                color = palette.textPrimary
            )
        }
    }

    NothingToggleRow(
        label = "Live Homescreen Animation",
        description = "Animate background dots, topographic contours & frosted glow",
        checked = settings.theme.wallpaperAnimationEnabled,
        onCheckedChange = { enabled ->
            onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperAnimationEnabled = enabled)) }
        },
        modifier = Modifier.testTag("toggle_wallpaper_animation")
    )

    Text("Wave & Motion Pattern", fontSize = 12.sp, color = palette.textSecondary)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WallpaperWavePattern.entries.forEach { pattern ->
            NothingPillChip(
                label = pattern.displayName,
                selected = settings.theme.wallpaperWavePattern == pattern,
                onClick = {
                    onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperWavePattern = pattern)) }
                },
                modifier = Modifier.testTag("wallpaper_pattern_${pattern.name.lowercase()}")
            )
        }
    }

    NothingSliderRow(
        label = "Animation Speed",
        value = settings.theme.wallpaperAnimSpeed,
        valueRange = 0.2f..3.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperAnimSpeed = v)) } },
        valueFormatter = { "${(it * 100).roundToInt() / 100f}x" },
        modifier = Modifier.testTag("slider_wallpaper_anim_speed")
    )

    NothingSliderRow(
        label = "Wave & Pulse Intensity",
        value = settings.theme.wallpaperAnimIntensity,
        valueRange = 0.0f..2.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperAnimIntensity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_wallpaper_anim_intensity")
    )

    NothingSliderRow(
        label = "Dot & Line Brightness",
        value = settings.theme.wallpaperDotBrightness,
        valueRange = 0.3f..2.2f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperDotBrightness = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_wallpaper_brightness")
    )

    NothingSliderRow(
        label = "Dot & Contour Size Scale",
        value = settings.theme.wallpaperDotSizeScale,
        valueRange = 0.4f..2.2f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperDotSizeScale = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_wallpaper_dot_size")
    )

    NothingSliderRow(
        label = "Accent Glyph Node Frequency",
        value = settings.theme.wallpaperAccentFrequency,
        valueRange = 0.0f..2.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperAccentFrequency = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_wallpaper_accent_freq")
    )

    NothingSliderRow(
        label = "Tilt Parallax Strength",
        value = settings.theme.wallpaperParallaxStrength,
        valueRange = 0.0f..2.5f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(wallpaperParallaxStrength = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_wallpaper_parallax")
    )

    DotMatrixDivider()
    NothingSectionHeader("Surface Hex Colors", "Accent, glyph highlight, background & surface")

    HexSwatchGrid(
        label = "Glyph & Accent Highlight",
        currentHex = settings.theme.accentHex,
        onPick = { hex ->
            onUpdateSettings {
                it.copy(theme = it.theme.copy(accentHex = hex, glyphHighlightHex = hex))
            }
        }
    )

    HexSwatchGrid(
        label = "Surface Background",
        currentHex = settings.theme.backgroundHex,
        onPick = { hex ->
            onUpdateSettings {
                it.copy(
                    theme = it.theme.copy(
                        backgroundHex = hex,
                        // A custom background must never be overridden by the OLED pure-black shortcut
                        oledPureBlack = hex.equals("#000000", ignoreCase = true)
                    )
                )
            }
        }
    )

    HexSwatchGrid(
        label = "Card & Widget Surface",
        currentHex = settings.theme.surfaceHex,
        onPick = { hex ->
            onUpdateSettings {
                it.copy(
                    theme = it.theme.copy(
                        surfaceHex = hex,
                        widgetBgHex = hex,
                        folderBgHex = hex,
                        drawerBgHex = hex
                    )
                )
            }
        }
    )

    HexSwatchGrid(
        label = "Primary Text",
        currentHex = settings.theme.textPrimaryHex,
        onPick = { hex ->
            onUpdateSettings { it.copy(theme = it.theme.copy(textPrimaryHex = hex)) }
        }
    )

    var customAccentHex by remember(settings.theme.accentHex) { mutableStateOf(settings.theme.accentHex) }
    OutlinedTextField(
        value = customAccentHex,
        onValueChange = { hex ->
            customAccentHex = hex
            if (hex.startsWith("#") && (hex.length == 7 || hex.length == 9)) {
                onUpdateSettings { it.copy(theme = it.theme.copy(accentHex = hex, glyphHighlightHex = hex)) }
            }
        },
        label = { Text("Custom Accent Hex (#RRGGBB)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    DotMatrixDivider()
    NothingSectionHeader("Opacity, Blur & Borders", "Independent surface geometry")

    NothingSliderRow(
        label = "App Drawer Opacity",
        value = settings.theme.drawerOpacity,
        valueRange = 0.2f..1.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(drawerOpacity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )
    NothingSliderRow(
        label = "Widgets Opacity",
        value = settings.theme.widgetOpacity,
        valueRange = 0.2f..1.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(widgetOpacity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )
    NothingSliderRow(
        label = "Widgets Border Thickness",
        value = settings.theme.widgetBorderDp,
        valueRange = 0f..4f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(widgetBorderDp = v)) } },
        valueFormatter = { "${(it * 10).roundToInt() / 10f}dp" }
    )
    NothingSliderRow(
        label = "Folders Opacity",
        value = settings.theme.folderOpacity,
        valueRange = 0.2f..1.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(folderOpacity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )
    NothingSliderRow(
        label = "Folders Border Thickness",
        value = settings.theme.folderBorderDp,
        valueRange = 0f..4f,
        onValueChange = { v -> onUpdateSettings { it.copy(theme = it.theme.copy(folderBorderDp = v)) } },
        valueFormatter = { "${(it * 10).roundToInt() / 10f}dp" }
    )
    NothingSliderRow(
        label = "Blur Radius",
        value = settings.theme.widgetBlurDp,
        valueRange = 0f..32f,
        onValueChange = { v ->
            onUpdateSettings {
                it.copy(theme = it.theme.copy(widgetBlurDp = v, drawerBlurDp = v, folderBlurDp = v))
            }
        },
        valueFormatter = { "${it.roundToInt()}dp" }
    )
}

@Composable
private fun TypographyEngineTab(
    settings: LauncherSettings,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    var activeSlot by remember { mutableStateOf(TypographySlot.HOME_LABEL) }
    val currentConfig = settings.typographyFor(activeSlot)

    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = NothingTypographyEngine.importCustomFont(context, uri)
            if (savedPath != null) {
                onUpdateSettings { cfg ->
                    updateSlotTypography(cfg, activeSlot) {
                        it.copy(fontChoice = FontFamilyChoice.CUSTOM_FILE, customFontPath = savedPath)
                    }
                }
            }
        }
    }

    NothingSectionHeader("Target Element", "Configure independent typography per UI surface")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TypographySlot.entries.forEach { slot ->
            NothingPillChip(
                label = slot.displayName,
                selected = activeSlot == slot,
                onClick = { activeSlot = slot }
            )
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Font Family & Custom TTF/OTF")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FontFamilyChoice.entries.forEach { choice ->
            NothingPillChip(
                label = choice.displayName,
                selected = currentConfig.fontChoice == choice,
                onClick = {
                    if (choice == FontFamilyChoice.CUSTOM_FILE && currentConfig.customFontPath == null) {
                        fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
                    } else {
                        onUpdateSettings { cfg ->
                            updateSlotTypography(cfg, activeSlot) { it.copy(fontChoice = choice) }
                        }
                    }
                }
            )
        }
    }

    Button(
        onClick = {
            fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream"))
        },
        colors = ButtonDefaults.buttonColors(containerColor = palette.surface)
    ) {
        Text("Import Local .TTF / .OTF Font File", color = palette.accent)
    }

    NothingSliderRow(
        label = "Font Size",
        value = currentConfig.sizeSp,
        valueRange = 9f..40f,
        onValueChange = { v ->
            onUpdateSettings { cfg -> updateSlotTypography(cfg, activeSlot) { it.copy(sizeSp = v) } }
        },
        valueFormatter = { "${it.roundToInt()}sp" }
    )
    NothingSliderRow(
        label = "Font Weight",
        value = currentConfig.fontWeight.toFloat(),
        valueRange = 300f..800f,
        onValueChange = { v ->
            onUpdateSettings { cfg ->
                updateSlotTypography(cfg, activeSlot) { it.copy(fontWeight = (v / 100f).roundToInt() * 100) }
            }
        },
        valueFormatter = { "${it.roundToInt()}" }
    )
    NothingSliderRow(
        label = "Letter Spacing (Kerning)",
        value = currentConfig.letterSpacingSp,
        valueRange = -1f..5f,
        onValueChange = { v ->
            onUpdateSettings { cfg -> updateSlotTypography(cfg, activeSlot) { it.copy(letterSpacingSp = v) } }
        },
        valueFormatter = { "${(it * 10).roundToInt() / 10f}sp" }
    )
    NothingSliderRow(
        label = "Line Height",
        value = currentConfig.lineHeightSp,
        valueRange = 12f..48f,
        onValueChange = { v ->
            onUpdateSettings { cfg -> updateSlotTypography(cfg, activeSlot) { it.copy(lineHeightSp = v) } }
        },
        valueFormatter = { "${it.roundToInt()}sp" }
    )

    Text("Text Case", fontSize = 12.sp, color = palette.textSecondary)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextCaseMode.entries.forEach { mode ->
            NothingPillChip(
                label = mode.displayName,
                selected = currentConfig.textCase == mode,
                onClick = {
                    onUpdateSettings { cfg -> updateSlotTypography(cfg, activeSlot) { it.copy(textCase = mode) } }
                }
            )
        }
    }
}

private fun updateSlotTypography(
    cfg: LauncherSettings,
    slot: TypographySlot,
    transform: (com.example.settingsstore.model.TypographyElementConfig) -> com.example.settingsstore.model.TypographyElementConfig
): LauncherSettings = when (slot) {
    TypographySlot.HOME_LABEL -> cfg.copy(typographyHome = transform(cfg.typographyHome))
    TypographySlot.DRAWER_LABEL -> cfg.copy(typographyDrawer = transform(cfg.typographyDrawer))
    TypographySlot.WIDGET_TITLE -> cfg.copy(typographyWidgetTitle = transform(cfg.typographyWidgetTitle))
    TypographySlot.WIDGET_DATA -> cfg.copy(typographyWidgetData = transform(cfg.typographyWidgetData))
    TypographySlot.SEARCH_TEXT -> cfg.copy(typographySearch = transform(cfg.typographySearch))
}

@Composable
private fun IconMaskAndGridTab(
    settings: LauncherSettings,
    installedIconPacks: List<InstalledIconPack>,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onToggleDockMiniChip: (DockMiniChipType) -> Unit,
    onUpdatePageConfig: (Int, (HomePageConfig) -> HomePageConfig) -> Unit
) {
    val palette = LocalNothingPalette.current

    NothingSectionHeader(
        "Advanced Icon Masking",
        "Star, Diamond, Rounded Hexagon, Custom Polygon, Rim Glow & padding"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconMaskShape.entries.forEach { shape ->
            NothingPillChip(
                label = shape.displayName,
                selected = settings.iconMask.shape == shape,
                onClick = {
                    onUpdateSettings { it.copy(iconMask = it.iconMask.copy(shape = shape)) }
                },
                modifier = Modifier.testTag("mask_shape_${shape.name.lowercase()}")
            )
        }
    }

    NothingSliderRow(
        label = "Inner Padding Within Mask",
        value = settings.iconMask.innerPaddingDp,
        valueRange = 0f..18f,
        onValueChange = { v ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(innerPaddingDp = v)) }
        },
        valueFormatter = { "${(it * 10).roundToInt() / 10f}dp" },
        modifier = Modifier.testTag("slider_mask_inner_padding")
    )

    NothingSliderRow(
        label = "Border Radius Within Mask",
        value = settings.iconMask.borderRadiusPercent,
        valueRange = 0f..100f,
        onValueChange = { v ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(borderRadiusPercent = v)) }
        },
        valueFormatter = { "${it.roundToInt()}%" },
        modifier = Modifier.testTag("slider_mask_border_radius")
    )

    if (settings.iconMask.shape == IconMaskShape.STAR || settings.iconMask.shape == IconMaskShape.CUSTOM_POLYGON) {
        NothingSliderRow(
            label = if (settings.iconMask.shape == IconMaskShape.STAR) "Star Points" else "Polygon Sides",
            value = settings.iconMask.polygonSides.toFloat(),
            valueRange = 3f..12f,
            steps = 8,
            onValueChange = { v ->
                onUpdateSettings { it.copy(iconMask = it.iconMask.copy(polygonSides = v.roundToInt())) }
            },
            valueFormatter = { "${it.roundToInt()}" },
            modifier = Modifier.testTag("slider_mask_polygon_sides")
        )
    }

    if (settings.iconMask.shape == IconMaskShape.STAR) {
        NothingSliderRow(
            label = "Star Spike Depth",
            value = settings.iconMask.starInnerRatio,
            valueRange = 0.25f..0.80f,
            onValueChange = { v ->
                onUpdateSettings { it.copy(iconMask = it.iconMask.copy(starInnerRatio = v)) }
            },
            valueFormatter = { "${(it * 100).roundToInt()}%" },
            modifier = Modifier.testTag("slider_mask_star_depth")
        )
    }

    if (settings.iconMask.shape in setOf(
            IconMaskShape.STAR,
            IconMaskShape.DIAMOND,
            IconMaskShape.HEXAGON,
            IconMaskShape.ROUNDED_HEXAGON,
            IconMaskShape.CUSTOM_POLYGON
        )
    ) {
        NothingSliderRow(
            label = "Mask Rotation Angle",
            value = settings.iconMask.rotationDegrees,
            valueRange = -180f..180f,
            onValueChange = { v ->
                onUpdateSettings { it.copy(iconMask = it.iconMask.copy(rotationDegrees = v)) }
            },
            valueFormatter = { "${it.roundToInt()}°" },
            modifier = Modifier.testTag("slider_mask_rotation")
        )
    }

    NothingSliderRow(
        label = "Mask Border Stroke",
        value = settings.iconMask.borderWidthDp,
        valueRange = 0f..4f,
        onValueChange = { v ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(borderWidthDp = v)) }
        },
        valueFormatter = { "${(it * 10).roundToInt() / 10f}dp" }
    )

    NothingSliderRow(
        label = "Icon Size Scale",
        value = settings.iconMask.iconScale,
        valueRange = 0.5f..1.5f,
        onValueChange = { v ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(iconScale = v)) }
        },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )

    NothingSliderRow(
        label = "Label Offset Spacing",
        value = settings.iconMask.labelOffsetDp,
        valueRange = 0f..16f,
        onValueChange = { v ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(labelOffsetDp = v)) }
        },
        valueFormatter = { "${it.roundToInt()}dp" }
    )

    NothingToggleRow(
        label = "Rim Glow Gradient Border",
        description = "Applies sweep-gradient highlight halo around masked icons",
        checked = settings.iconMask.rimGlowEnabled,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(rimGlowEnabled = checked)) }
        },
        modifier = Modifier.testTag("toggle_mask_rim_glow")
    )

    if (settings.iconMask.rimGlowEnabled) {
        val glowSwatches = listOf("#D71921", "#FFFFFF", "#38BDF8", "#00E676", "#FF9E1B", "#A855F7")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            glowSwatches.forEach { hex ->
                val selected = settings.iconMask.rimGlowHex.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(parseHexColor(hex, Color.Red))
                        .border(if (selected) 2.dp else 1.dp, Color.White, CircleShape)
                        .clickable {
                            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(rimGlowHex = hex)) }
                        }
                )
            }
        }
    }

    NothingToggleRow(
        label = "Auto-Monochrome Dot-Matrix Filter",
        description = "Turns colorful app icons into crisp Nothing monochrome",
        checked = settings.iconMask.monochromeFilter,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(monochromeFilter = checked)) }
        }
    )

    NothingToggleRow(
        label = "2-Tone Accent Duotone Filter",
        description = "Tints icon artwork in your active accent color",
        checked = settings.iconMask.duotoneFilter,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(duotoneFilter = checked)) }
        },
        modifier = Modifier.testTag("toggle_mask_duotone")
    )

    NothingToggleRow(
        label = "Dot-Matrix Perforation Overlay",
        description = "Applies subtle Nothing dot texture over masked icons",
        checked = settings.iconMask.dotMatrixOverlay,
        onCheckedChange = { checked ->
            onUpdateSettings { it.copy(iconMask = it.iconMask.copy(dotMatrixOverlay = checked)) }
        }
    )

    Text("Third-Party Icon Packs (${installedIconPacks.size} found)", fontSize = 12.sp, color = palette.textSecondary)
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NothingPillChip(
            label = "System Icons",
            selected = settings.iconMask.selectedIconPackPackage == null,
            onClick = {
                onUpdateSettings { it.copy(iconMask = it.iconMask.copy(selectedIconPackPackage = null)) }
            }
        )
        installedIconPacks.forEach { pack ->
            NothingPillChip(
                label = pack.label,
                selected = settings.iconMask.selectedIconPackPackage == pack.packageName,
                onClick = {
                    onUpdateSettings { it.copy(iconMask = it.iconMask.copy(selectedIconPackPackage = pack.packageName)) }
                }
            )
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Dock Mini-Widget Chips", "Embed up to 2 live telemetry pills directly inside the dock")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DockMiniChipType.entries.forEach { chip ->
            NothingPillChip(
                label = chip.displayName,
                selected = chip in settings.grid.dockMiniChips,
                onClick = { onToggleDockMiniChip(chip) },
                modifier = Modifier.testTag("dock_chip_${chip.name.lowercase()}")
            )
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Unrestricted Grid & Per-Page Overrides", "2x2 up to 12x12 freeform grids")

    NothingToggleRow(
        label = "Show Most Used Apps Section",
        description = "Auto-ranked Top 8 frequently used apps on Page 1",
        checked = settings.grid.showMostUsedSection,
        onCheckedChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(showMostUsedSection = v)) } }
    )

    NothingSliderRow(
        label = "Global Home Grid Columns",
        value = settings.grid.homeColumns.toFloat(),
        valueRange = 2f..12f,
        steps = 9,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(homeColumns = v.roundToInt())) } },
        valueFormatter = { "${it.roundToInt()} cols" }
    )
    NothingSliderRow(
        label = "Global Home Grid Rows",
        value = settings.grid.homeRows.toFloat(),
        valueRange = 2f..12f,
        steps = 9,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(homeRows = v.roundToInt())) } },
        valueFormatter = { "${it.roundToInt()} rows" }
    )

    // Per-page name & column override editor
    var selectedPageIdx by remember { mutableIntStateOf(0) }
    val pageCount = settings.homeAppPages.size.coerceAtLeast(2)
    val safePageIdx = selectedPageIdx.coerceIn(0, pageCount - 1)
    val pageCfg = settings.pageConfigFor(safePageIdx)

    Text("Per-Page Customization", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pageCount) { idx ->
            val cfg = settings.pageConfigFor(idx)
            NothingPillChip(
                label = "P${idx + 1}: ${cfg.name}",
                selected = safePageIdx == idx,
                onClick = { selectedPageIdx = idx }
            )
        }
    }
    var pageNameText by remember(safePageIdx, pageCfg.name) { mutableStateOf(pageCfg.name) }
    OutlinedTextField(
        value = pageNameText,
        onValueChange = { txt ->
            pageNameText = txt
            onUpdatePageConfig(safePageIdx) { it.copy(name = txt.ifBlank { "PAGE ${safePageIdx + 1}" }) }
        },
        label = { Text("Page ${safePageIdx + 1} Label") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NothingPillChip(
            label = "Global Cols (${settings.grid.homeColumns})",
            selected = pageCfg.columnsOverride == null,
            onClick = { onUpdatePageConfig(safePageIdx) { it.copy(columnsOverride = null) } }
        )
        listOf(3, 4, 5, 6, 8).forEach { colOpt ->
            NothingPillChip(
                label = "$colOpt Cols",
                selected = pageCfg.columnsOverride == colOpt,
                onClick = { onUpdatePageConfig(safePageIdx) { it.copy(columnsOverride = colOpt) } }
            )
        }
    }

    NothingSliderRow(
        label = "Drawer Grid Columns",
        value = settings.grid.drawerColumns.toFloat(),
        valueRange = 2f..12f,
        steps = 9,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(drawerColumns = v.roundToInt())) } },
        valueFormatter = { "${it.roundToInt()} cols" }
    )
    NothingSliderRow(
        label = "Folder Grid Columns",
        value = settings.grid.folderColumns.toFloat(),
        valueRange = 2f..12f,
        steps = 9,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(folderColumns = v.roundToInt())) } },
        valueFormatter = { "${it.roundToInt()} cols" }
    )
    NothingSliderRow(
        label = "Multi-Page Dock Pages",
        value = settings.grid.dockPageCount.toFloat(),
        valueRange = 1f..3f,
        steps = 1,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(dockPageCount = v.roundToInt())) } },
        valueFormatter = { "${it.roundToInt()} pages" }
    )
    NothingSliderRow(
        label = "Dock Slots Per Page",
        value = settings.grid.dockSlotsPerPage.toFloat(),
        valueRange = 3f..7f,
        steps = 3,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(dockSlotsPerPage = v.roundToInt())) } },
        valueFormatter = { "${it.roundToInt()} slots" }
    )
    NothingSliderRow(
        label = "Grid Cell Padding",
        value = settings.grid.horizontalPaddingDp,
        valueRange = 2f..24f,
        onValueChange = { v ->
            onUpdateSettings { it.copy(grid = it.grid.copy(horizontalPaddingDp = v, verticalPaddingDp = v)) }
        },
        valueFormatter = { "${it.roundToInt()}dp" }
    )
    NothingSliderRow(
        label = "Screen Edge Margin",
        value = settings.grid.marginDp,
        valueRange = 4f..28f,
        onValueChange = { v -> onUpdateSettings { it.copy(grid = it.grid.copy(marginDp = v)) } },
        valueFormatter = { "${it.roundToInt()}dp" }
    )
}

/** 18-swatch hex palette picker used across every Surface colour slot. */
@Composable
private fun HexSwatchGrid(
    label: String,
    currentHex: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val swatches = listOf(
        "#D71921", "#FF3B30", "#FF9E1B", "#FFD60A", "#C6FF00", "#00E676",
        "#00C8A0", "#38BDF8", "#2E6BFF", "#7C4DFF", "#A855F7", "#FF2D95",
        "#FFFFFF", "#E5E5EA", "#9A9AA2", "#5A5A62", "#1C1C20", "#000000"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = palette.textSecondary)
        Spacer(Modifier.height(6.dp))
        swatches.chunked(9).forEach { rowHexes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowHexes.forEach { hex ->
                    val selected = currentHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(hex, Color.Gray))
                            .border(
                                if (selected) 2.5.dp else 1.dp,
                                if (selected) palette.accent else palette.borderSubtle,
                                CircleShape
                            )
                            .clickable { onPick(hex) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
