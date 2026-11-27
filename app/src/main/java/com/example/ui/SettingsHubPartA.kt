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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    var searchQuery by remember { mutableStateOf("") }
    var showLivePreview by remember { mutableStateOf(false) }
    val bodyScroll = rememberScrollState()

    val tabs = remember { studioTabs }
    val results = remember(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            emptyList()
        } else {
            studioSearchIndex.filter { entry ->
                entry.title.contains(q, ignoreCase = true) ||
                    entry.summary.contains(q, ignoreCase = true) ||
                    entry.keywords.any { it.contains(q, ignoreCase = true) }
            }.take(30)
        }
    }

    LaunchedEffect(activeTab) {
        bodyScroll.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .then(
                if (settings.fullScreenHideStatusBar) Modifier.padding(top = 10.dp)
                else Modifier.statusBarsPadding()
            )
            .navigationBarsPadding()
            .testTag("settings_hub_screen")
    ) {
        // ── Android-17 style top app bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Settings",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 26.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "DotOS Studio  •  ${tabs.getOrNull(activeTab)?.title ?: "Home"}",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("settings_close_button")) {
                Icon(Icons.Rounded.Close, "Close Settings", tint = palette.textPrimary)
            }
        }

        // ── Settings search bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(palette.surface)
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(28.dp))
                .padding(start = 14.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = palette.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = palette.textPrimary,
                    fontSize = 14.sp,
                    fontFamily = NothingTypographyEngine.bodyInterFamily
                ),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .weight(1f)
                    .testTag("settings_search_input"),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search settings — wallpaper, labels, dock, hex…",
                            fontSize = 13.sp,
                            color = palette.textSecondary,
                            maxLines = 1
                        )
                    }
                    innerTextField()
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Clear, "Clear search", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }

        if (searchQuery.isBlank()) {
            // ── Section rail (Android-14/17 style scrollable categories) ─────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val selected = activeTab == idx
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) palette.accent.copy(alpha = 0.20f) else palette.surface)
                            .border(
                                1.dp,
                                if (selected) palette.accent else palette.borderSubtle,
                                RoundedCornerShape(999.dp)
                            )
                            .clickable { activeTab = idx }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("settings_tab_$idx"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (selected) palette.accent else palette.textSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = tab.title,
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 11.5.sp,
                            color = if (selected) palette.textPrimary else palette.textSecondary
                        )
                    }
                }
            }

            // ── Collapsed live preview ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.surface.copy(alpha = 0.6f))
                    .border(1.dp, palette.borderSubtle, RoundedCornerShape(16.dp))
                    .clickable { showLivePreview = !showLivePreview }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LIVE PREVIEW",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 11.sp,
                    letterSpacing = 1.2.sp,
                    color = palette.textSecondary
                )
                Text(
                    text = if (showLivePreview) "HIDE" else "SHOW",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 10.sp,
                    color = palette.accent
                )
            }

            if (showLivePreview) {
                Spacer(Modifier.height(8.dp))
                LiveCustomizationPreviewBanner(
                    settings = settings,
                    sampleApps = allApps.take(4),
                    appIndexer = appIndexer,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (searchQuery.isNotBlank()) {
            // ── Search results ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (results.isEmpty()) "NO SETTINGS MATCH FOR " + searchQuery.trim().uppercase() else "${results.size} MATCHING SETTINGS",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    color = if (results.isEmpty()) palette.textSecondary else palette.accent
                )
                results.forEach { entry ->
                    StudioSearchResultRow(
                        entry = entry,
                        tabTitle = tabs.getOrNull(entry.tab)?.title ?: "Settings",
                        onClick = {
                            activeTab = entry.tab
                            searchQuery = ""
                        }
                    )
                }
                if (results.isEmpty()) {
                    Text(
                        text = "Try: wallpaper, hex, labels, dock, gestures, widget, weather, news, aura, vault, backup, search history.",
                        fontSize = 12.sp,
                        color = palette.textSecondary
                    )
                }
                Spacer(Modifier.height(28.dp))
            }
        } else {
            // ── Active section body ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(bodyScroll)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

private data class StudioTabInfo(
    val title: String,
    val blurb: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private val studioTabs = listOf(
    StudioTabInfo("Surface", "Wallpapers, hex colours & immersive mode", Icons.Rounded.Palette),
    StudioTabInfo("Typography", "Fonts, weights, spacing & case", Icons.Rounded.TextFields),
    StudioTabInfo("Icons & Grid", "Mask shapes, icon scale, rows & dock", Icons.Rounded.GridView),
    StudioTabInfo("Search & Tools", "Search bar, sources, history & clipboard", Icons.Rounded.Search),
    StudioTabInfo("Motion", "Physics springs, transitions & gestures", Icons.Rounded.Animation),
    StudioTabInfo("Widgets", "15 widgets, precise weather & context rules", Icons.Rounded.Widgets),
    StudioTabInfo("System", "Vault, backup, QR sharing & presets", Icons.Rounded.Security)
)

private data class StudioSettingEntry(
    val title: String,
    val summary: String,
    val tab: Int,
    val keywords: List<String> = emptyList()
)

private val studioSearchIndex = listOf(
    StudioSettingEntry("Immersive Full-Screen", "Hide the status bar (battery, Wi-Fi, notifications)", 0, listOf("status bar", "fullscreen", "immersive", "hide")),
    StudioSettingEntry("Show App Names", "Icon-only Home & All Apps grid", 0, listOf("labels", "app names", "text")),
    StudioSettingEntry("Theme Presets", "Apply, save or delete surface presets", 0, listOf("preset", "theme")),
    StudioSettingEntry("Wallpaper Mode", "Dot-Matrix, Starfield Warp, Cyber Horizon, Aurora, Constellation, Glyph, Digital Rain, Topographic, Frosted Glass, OLED", 0, listOf("live wallpaper", "wallpaper", "animation", "background")),
    StudioSettingEntry("OLED Pure Black", "Turn off background pixels for battery savings", 0, listOf("black", "oled", "battery")),
    StudioSettingEntry("Wallpaper Tilt Parallax", "Accelerometer depth on the wallpaper", 0, listOf("parallax", "tilt")),
    StudioSettingEntry("Material You Colours", "Extract Android 12+ dynamic accents", 0, listOf("dynamic colour", "material you", "monet")),
    StudioSettingEntry("Wallpaper Animation Speed", "Speed of the wave & motion pattern", 0, listOf("speed", "motion")),
    StudioSettingEntry("Dot & Line Brightness", "Wallpaper dot brightness", 0, listOf("brightness", "dots")),
    StudioSettingEntry("Surface Hex Colours", "18 swatches each for accent, background, surface & text", 0, listOf("hex", "colour", "color", "swatch", "accent", "background")),
    StudioSettingEntry("Widget & Folder Opacity", "Independent transparency and border thickness", 0, listOf("opacity", "transparency", "border", "blur")),
    StudioSettingEntry("Font Family", "Pick a bundled font, custom TTF/OTF file or system font", 1, listOf("font", "typeface", "ttf", "otf")),
    StudioSettingEntry("Font Size & Weight", "Per-surface size, weight and letter spacing", 1, listOf("size", "weight", "spacing")),
    StudioSettingEntry("Text Case", "Uppercase, lowercase or capitalise labels", 1, listOf("uppercase", "case", "labels")),
    StudioSettingEntry("Icon Mask Shape", "Circle, squircle, hexagon, pebble and more", 2, listOf("icon", "mask", "shape")),
    StudioSettingEntry("Icon Scale & Padding", "Icon size, inner padding and border radius", 2, listOf("icon size", "scale", "padding")),
    StudioSettingEntry("Grid Geometry", "Home rows, columns, padding & margins", 2, listOf("grid", "rows", "columns")),
    StudioSettingEntry("Dock Slots & Pages", "5-slot dock, extra dock pages & mini chips", 2, listOf("dock", "slots", "phone", "chrome")),
    StudioSettingEntry("Icon Packs", "Installed third-party icon packs", 2, listOf("icon pack", "theme")),
    StudioSettingEntry("Badge Style", "Notification badge shape and colour", 2, listOf("badge", "notification")),
    StudioSettingEntry("Search Bar Position", "Top, floating pill, bottom or dock-integrated", 3, listOf("search bar", "search")),
    StudioSettingEntry("Search Sources", "Apps, contacts, files, settings, utilities, web", 3, listOf("sources", "priority", "web")),
    StudioSettingEntry("Custom Search Engines", "Add your own URL template engines", 3, listOf("engine", "google", "duckduckgo")),
    StudioSettingEntry("Search History", "Only submitted searches are stored now", 3, listOf("history", "recent", "privacy")),
    StudioSettingEntry("Incognito Search", "Never store queries or clipboard snippets", 3, listOf("incognito", "privacy")),
    StudioSettingEntry("Clipboard History", "Clear saved clipboard snippets", 3, listOf("clipboard", "clear")),
    StudioSettingEntry("Quick Tools Deck", "Calculator, converters, stopwatch, password generator", 3, listOf("tools", "calculator", "timer", "password")),
    StudioSettingEntry("Animation Physics", "Spring stiffness, damping and press bounce", 4, listOf("physics", "spring", "bounce")),
    StudioSettingEntry("Page Transition", "Slide, fade, depth or cube between pages", 4, listOf("transition", "page")),
    StudioSettingEntry("Gestures", "Double tap, swipe up/down, pinch & icon swipe actions", 4, listOf("gesture", "swipe", "pinch", "double tap")),
    StudioSettingEntry("Deep Focus Mode", "Monochrome calm mode with app dimming", 4, listOf("focus", "monochrome")),
    StudioSettingEntry("Widget Library", "All 15 custom widgets and their layouts", 5, listOf("widget", "stack")),
    StudioSettingEntry("Digital Aura Shape", "8 selectable aura geometries", 5, listOf("aura", "digital aura", "shape")),
    StudioSettingEntry("Precise Weather Location", "Fine GPS, city search or exact latitude/longitude", 5, listOf("weather", "gps", "location", "coordinates")),
    StudioSettingEntry("News Pulse", "Live headlines in the widget", 5, listOf("news", "headlines", "rss")),
    StudioSettingEntry("Quick Notes", "Sticky note pad stored locally", 5, listOf("notes", "note")),
    StudioSettingEntry("System Matrix", "RAM, storage and display refresh rate gauges", 5, listOf("ram", "storage", "hz")),
    StudioSettingEntry("Event Countdown", "Days remaining to your milestone", 5, listOf("countdown", "event")),
    StudioSettingEntry("Widget Stacking", "Stack widgets and swipe between them", 5, listOf("stack", "widget")),
    StudioSettingEntry("Context Auto-Profiles", "Auto switch themes by time, Wi-Fi, Bluetooth or battery", 5, listOf("context", "automation", "profile")),
    StudioSettingEntry("Biometric App Vault", "PIN & fingerprint protected hidden apps", 6, listOf("vault", "hidden", "pin", "biometric")),
    StudioSettingEntry("Backup & Restore", "Export or import the full launcher configuration", 6, listOf("backup", "restore", "export", "import")),
    StudioSettingEntry("QR Share", "Share your setup as a QR payload", 6, listOf("qr", "share")),
    StudioSettingEntry("My Wallpaper & Colours", "Pick a photo wallpaper and extract colours from it", 0, listOf("wallpaper", "photo", "colors", "colours", "extract")),
    StudioSettingEntry("Bottom Deck (Dock)", "Dock icon size, radius, transparency, slots & pages", 2, listOf("dock", "bottom deck", "deck")),
    StudioSettingEntry("Sleep Screen", "Always-on clock seconds, brightness, glyphs, media", 4, listOf("sleep", "aod", "always on", "clock")),
    StudioSettingEntry("Deep Focus Block-List", "Apps blocked and muted while Deep Focus is on", 5, listOf("focus", "block", "mute")),
    StudioSettingEntry("Home Icon Label Size", "A- / A+ quick label size for Home icons", 1, listOf("label", "font size", "names", "small")),
    StudioSettingEntry("Search Quick Tools Toggle", "Hide the pop-up tools under the search bar", 3, listOf("search popup", "quick tools")),
    StudioSettingEntry("Quick Tools Cluster", "One icon hides or reveals Edit, Sleep, News, Search, All Apps, Font & Names", 0, listOf("tools", "fab", "top bar", "cluster")),
    StudioSettingEntry("At a Glance Header", "Rotating calendar, weather, steps & battery glance card", 0, listOf("glance", "calendar", "at a glance")),
    StudioSettingEntry("Frame Rate Cap", "Adaptive or a hard 30 / 60 / 120 FPS ceiling", 4, listOf("fps", "frame rate", "hz", "refresh", "performance")),
    StudioSettingEntry("Sleep Screen Glyph Animation", "Animated aura, orbit dots & glyph equalizer on the desk clock", 4, listOf("aod", "sleep", "always on", "glyph", "animation"))
)

@Composable
private fun StudioSearchResultRow(
    entry: StudioSettingEntry,
    tabTitle: String,
    onClick: () -> Unit
) {
    val palette = LocalNothingPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface.copy(alpha = 0.8f))
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 13.5.sp,
                color = palette.textPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.summary,
                fontSize = 11.5.sp,
                color = palette.textSecondary,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tabTitle.uppercase(),
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
                color = palette.accent
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = palette.textSecondary,
            modifier = Modifier.size(18.dp)
        )
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
    val context = LocalContext.current
    var newPresetName by remember { mutableStateOf("") }
    var paletteStatus by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onUpdateSettings { cfg ->
                cfg.copy(
                    theme = cfg.theme.copy(
                        wallpaperMode = com.example.settingsstore.model.WallpaperMode.CUSTOM_PHOTO,
                        wallpaperImageUri = uri.toString()
                    )
                )
            }
            paletteStatus = "Wallpaper set"
        }
    }

    NothingGroupCard(
        title = "My Wallpaper & Launcher Colours",
        subtitle = "Pick any photo, then pull the launcher accent/surface straight out of it"
    ) {
        if (settings.theme.wallpaperImageUri == null) {
            Text(
                text = "No photo wallpaper yet — the generative wallpapers are still doing the job.",
                fontSize = 11.5.sp,
                color = palette.textSecondary
            )
        } else {
            Text(
                text = if (settings.theme.wallpaperMode ==
                    com.example.settingsstore.model.WallpaperMode.CUSTOM_PHOTO
                ) "ACTIVE: YOUR PHOTO" else "PHOTO SAVED (generative wallpaper is active)",
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = palette.accent
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { photoPicker.launch(arrayOf("image/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
            ) {
                Text("CHOOSE PHOTO", color = Color.White, fontSize = 12.sp)
            }
            Button(
                onClick = {
                    val uriStr = settings.theme.wallpaperImageUri
                    if (uriStr == null) {
                        paletteStatus = "Pick a photo first"
                    } else {
                        val extracted = com.example.themeengine.extractWallpaperPalette(context, uriStr)
                        if (extracted == null) {
                            paletteStatus = "Couldn't read that image"
                        } else {
                            onUpdateSettings { cfg ->
                                cfg.copy(
                                    theme = cfg.theme.copy(
                                        accentHex = extracted.accentHex,
                                        surfaceHex = extracted.surfaceHex,
                                        backgroundHex = extracted.backgroundHex,
                                        drawerBgHex = extracted.backgroundHex,
                                        widgetBgHex = extracted.surfaceHex,
                                        folderBgHex = extracted.surfaceHex,
                                        glyphHighlightHex = extracted.accentHex
                                    )
                                )
                            }
                            paletteStatus = "Colours extracted from wallpaper"
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.surface)
            ) {
                Text("EXTRACT COLOURS", color = palette.accent, fontSize = 12.sp)
            }
        }
        paletteStatus?.let { status ->
            Text(text = status, fontSize = 11.sp, color = palette.textSecondary)
        }
        NothingSliderRow(
            label = "Photo Dim (readability)",
            value = settings.theme.wallpaperImageDim,
            valueRange = 0f..0.85f,
            onValueChange = { v ->
                onUpdateSettings { cfg ->
                    cfg.copy(theme = cfg.theme.copy(wallpaperImageDim = v))
                }
            },
            valueFormatter = { "${(it * 100).roundToInt()}%" }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NothingPillChip(
                label = "USE MY PHOTO",
                selected = settings.theme.wallpaperMode ==
                    com.example.settingsstore.model.WallpaperMode.CUSTOM_PHOTO,
                onClick = {
                    onUpdateSettings { cfg ->
                        cfg.copy(
                            theme = cfg.theme.copy(
                                wallpaperMode = com.example.settingsstore.model.WallpaperMode.CUSTOM_PHOTO
                            )
                        )
                    }
                }
            )
            NothingPillChip(
                label = "USE GENERATIVE",
                selected = settings.theme.wallpaperMode !=
                    com.example.settingsstore.model.WallpaperMode.CUSTOM_PHOTO,
                onClick = {
                    onUpdateSettings { cfg ->
                        cfg.copy(
                            theme = cfg.theme.copy(
                                wallpaperMode = com.example.settingsstore.model.WallpaperMode.DOT_MATRIX_GRID
                            )
                        )
                    }
                }
            )
        }
    }

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
            checked = settings.grid.showAppLabels,
            onCheckedChange = { checked ->
                onUpdateSettings { cfg ->
                    cfg.copy(grid = cfg.grid.copy(showAppLabels = checked))
                }
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
    // Home icon labels get their own size, independent of the preset, wired to the A- / A+ tools.
    NothingSliderRow(
        label = "Home Icon Label Size (A- / A+ chips)",
        value = settings.homeLabelSizeSp,
        valueRange = 8f..20f,
        onValueChange = { v ->
            onUpdateSettings { cfg -> cfg.copy(homeLabelSizeSp = v) }
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

    NothingGroupCard(
        title = "Bottom Deck (Dock)",
        subtitle = "Size it, shape it, fade it or hide it completely"
    ) {
        NothingToggleRow(
            label = "Show Bottom Deck",
            description = "Turn off for a completely clean Home screen.",
            checked = settings.grid.dockVisible,
            onCheckedChange = { checked ->
                onUpdateSettings { cfg -> cfg.copy(grid = cfg.grid.copy(dockVisible = checked)) }
            }
        )
        NothingSliderRow(
            label = "Dock Icon Size",
            value = settings.grid.dockIconSizeDp,
            valueRange = 30f..68f,
            onValueChange = { v ->
                onUpdateSettings { cfg -> cfg.copy(grid = cfg.grid.copy(dockIconSizeDp = v)) }
            },
            valueFormatter = { "${it.roundToInt()}dp" }
        )
        NothingSliderRow(
            label = "Dock Corner Radius",
            value = settings.grid.dockCornerRadiusDp,
            valueRange = 8f..36f,
            onValueChange = { v ->
                onUpdateSettings { cfg -> cfg.copy(grid = cfg.grid.copy(dockCornerRadiusDp = v)) }
            },
            valueFormatter = { "${it.roundToInt()}dp" }
        )
        NothingSliderRow(
            label = "Dock Transparency",
            value = settings.grid.dockOpacity,
            valueRange = 0.2f..1f,
            onValueChange = { v ->
                onUpdateSettings { cfg -> cfg.copy(grid = cfg.grid.copy(dockOpacity = v)) }
            },
            valueFormatter = { "${(it * 100).roundToInt()}%" }
        )
        NothingSliderRow(
            label = "Dock Inner Padding",
            value = settings.grid.dockVerticalPaddingDp,
            valueRange = 2f..20f,
            onValueChange = { v ->
                onUpdateSettings { cfg -> cfg.copy(grid = cfg.grid.copy(dockVerticalPaddingDp = v)) }
            },
            valueFormatter = { "${it.roundToInt()}dp" }
        )
        Text("Icons per dock page", fontSize = 12.sp, color = palette.textSecondary)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (3..7).forEach { slots ->
                NothingPillChip(
                    label = "$slots ICONS",
                    selected = settings.grid.dockSlotsPerPage == slots,
                    onClick = {
                        onUpdateSettings { cfg ->
                            cfg.copy(grid = cfg.grid.copy(dockSlotsPerPage = slots))
                        }
                    }
                )
            }
        }
        Text("Dock pages", fontSize = 12.sp, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..3).forEach { pages ->
                NothingPillChip(
                    label = if (pages == 1) "1 PAGE" else "$pages PAGES",
                    selected = settings.grid.dockPageCount == pages,
                    onClick = {
                        onUpdateSettings { cfg ->
                            cfg.copy(grid = cfg.grid.copy(dockPageCount = pages))
                        }
                    }
                )
            }
        }
    }

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
