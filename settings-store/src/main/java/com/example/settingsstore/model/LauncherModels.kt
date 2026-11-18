package com.example.settingsstore.model

import androidx.compose.runtime.Immutable
import com.squareup.moshi.JsonClass

@Immutable
enum class IconMaskShape(val displayName: String) {
    RAW("Raw"),
    CIRCLE("Circle"),
    SQUIRCLE("Squircle"),
    TEARDROP("Teardrop"),
    HEXAGON("Hexagon"),
    ROUNDED_HEXAGON("Rounded Hexagon"),
    SHARP_SQUARE("Sharp Square"),
    STAR("Star"),
    DIAMOND("Diamond"),
    CUSTOM_POLYGON("Custom Polygon")
}

@Immutable
@JsonClass(generateAdapter = true)
data class IconMaskConfig(
    val shape: IconMaskShape = IconMaskShape.SQUIRCLE,
    val innerPaddingDp: Float = 5f,
    val borderRadiusPercent: Float = 28f,
    val polygonSides: Int = 5,
    val starInnerRatio: Float = 0.48f,
    val rotationDegrees: Float = 0f,
    val borderWidthDp: Float = 1f,
    val borderColorHex: String = "#33FFFFFF",
    val maskBackgroundHex: String = "#16161A",
    val rimGlowEnabled: Boolean = false,
    val rimGlowHex: String = "#D71921",
    val iconScale: Float = 1.0f,
    val monochromeFilter: Boolean = false,
    val duotoneFilter: Boolean = false,
    val dotMatrixOverlay: Boolean = false,
    val labelOffsetDp: Float = 4f,
    val selectedIconPackPackage: String? = null
)

@Immutable
enum class FontFamilyChoice(val displayName: String) {
    NDOT_55("NDOT-55"),
    NDOT_77("NDOT-77"),
    NTYPE_82("NType-82"),
    SANS_CLEAN("Clean Sans"),
    CUSTOM_FILE("Custom Font")
}

@Immutable
enum class TextCaseMode(val displayName: String) {
    ORIGINAL("Original"),
    UPPERCASE("UPPERCASE"),
    LOWERCASE("lowercase"),
    TITLE_CASE("Title Case")
}

@Immutable
enum class TypographySlot(val displayName: String) {
    HOME_LABEL("Home & Dock Labels"),
    DRAWER_LABEL("Drawer & Tabs"),
    WIDGET_TITLE("Widget Titles"),
    WIDGET_DATA("Widget Data Readouts"),
    SEARCH_TEXT("Search & Menus")
}

@Immutable
@JsonClass(generateAdapter = true)
data class TypographyElementConfig(
    val fontChoice: FontFamilyChoice = FontFamilyChoice.SANS_CLEAN,
    val customFontPath: String? = null,
    val fontWeight: Int = 500,
    val sizeSp: Float = 12f,
    val letterSpacingSp: Float = 0.2f,
    val lineHeightSp: Float = 16f,
    val textCase: TextCaseMode = TextCaseMode.ORIGINAL
)

@Immutable
enum class DockMiniChipType(val displayName: String) {
    BATTERY_DOT("Battery"),
    STEPS_MINI("Steps"),
    CLOCK_MINI("Clock"),
    TORCH_MINI("Torch"),
    FOCUS_MINI("Focus"),
    NOTIF_MINI("Alerts")
}

@Immutable
@JsonClass(generateAdapter = true)
data class HomePageConfig(
    val name: String = "CORE",
    val columnsOverride: Int? = null,
    val rowsOverride: Int? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class GridGeometryConfig(
    val homeRows: Int = 5,
    val homeColumns: Int = 4,
    val drawerRows: Int = 6,
    val drawerColumns: Int = 4,
    val folderRows: Int = 3,
    val folderColumns: Int = 3,
    val horizontalPaddingDp: Float = 10f,
    val verticalPaddingDp: Float = 10f,
    val marginDp: Float = 12f,
    val showMostUsedSection: Boolean = true,
    val dockPageCount: Int = 2,
    val dockSlotsPerPage: Int = 5,
    val dockMiniChips: List<DockMiniChipType> = listOf(DockMiniChipType.FOCUS_MINI)
)

@Immutable
enum class DrawerMode(val displayName: String) {
    VERTICAL_LIST("Vertical List"),
    ALPHABETICAL_GRID("Alphabetical Grid"),
    CATEGORIZED_TABS("Categorized Tabs"),
    HORIZONTAL_PAGED("Horizontal Paged")
}

@Immutable
enum class DrawerTabPosition(val displayName: String) {
    TOP("Top"),
    BOTTOM("Bottom")
}

@Immutable
enum class FolderOpenMode(val displayName: String) {
    POPUP_CARD("Popup Card"),
    FULL_SCREEN("Full-Screen Overlay"),
    EXPAND_INLINE("Expand in Place")
}

@Immutable
enum class FolderCoverStyle(val displayName: String) {
    GRID_2X2("2x2 Mini Grid"),
    GRID_3X3("3x3 Mini Grid"),
    SINGLE_ICON("Single Icon"),
    DOT_MATRIX_BADGE("Dot-Matrix Badge")
}

@Immutable
@JsonClass(generateAdapter = true)
data class FolderAppearanceConfig(
    val openMode: FolderOpenMode = FolderOpenMode.POPUP_CARD,
    val coverStyle: FolderCoverStyle = FolderCoverStyle.GRID_2X2,
    val folderMaskOverride: IconMaskShape? = null
)

@Immutable
enum class SearchBarPosition(val displayName: String) {
    TOP("Top"),
    BOTTOM("Thumb Bottom"),
    FLOATING_PILL("Floating Pill"),
    DOCK_INTEGRATED("Dock Integrated")
}

@Immutable
enum class SearchSourceType(val displayName: String) {
    APPS_SHORTCUTS("Apps & Shortcuts"),
    CONTACTS("Contacts"),
    LOCAL_FILES("Local Files"),
    DEVICE_SETTINGS("Settings"),
    UTILITIES("Math / Units / Color"),
    CLIPBOARD("Clipboard Snippets"),
    WEB_ENGINES("Web Engines")
}

@Immutable
@JsonClass(generateAdapter = true)
data class CustomSearchEngine(
    val id: String,
    val name: String,
    val urlTemplate: String,
    val enabled: Boolean = true
)

@Immutable
@JsonClass(generateAdapter = true)
data class SearchConfig(
    val position: SearchBarPosition = SearchBarPosition.FLOATING_PILL,
    val cornerRadiusDp: Float = 24f,
    val borderWidthDp: Float = 1f,
    val backgroundOpacity: Float = 0.86f,
    val placeholderText: String = "SEARCH EVERYTHING...",
    val autoOpenKeyboard: Boolean = true,
    val incognitoMode: Boolean = false,
    val enabledSources: List<SearchSourceType> = SearchSourceType.entries.toList(),
    val sourcePriorityOrder: List<SearchSourceType> = SearchSourceType.entries.toList(),
    val customEngines: List<CustomSearchEngine> = listOf(
        CustomSearchEngine("google", "Google", "https://www.google.com/search?q=%s"),
        CustomSearchEngine("ddg", "DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        CustomSearchEngine("brave", "Brave", "https://search.brave.com/search?q=%s"),
        CustomSearchEngine("yt", "YouTube", "https://www.youtube.com/results?search_query=%s"),
        CustomSearchEngine("play", "Play Store", "https://play.google.com/store/search?q=%s&c=apps"),
        CustomSearchEngine("maps", "Maps", "https://maps.google.com/?q=%s"),
        CustomSearchEngine("spotify", "Spotify", "https://open.spotify.com/search/%s")
    )
)

@Immutable
enum class LaunchTransitionStyle(val displayName: String) {
    SCALE("Scale"),
    FADE("Fade"),
    SLIDE("Slide"),
    DOT_DISSOLVE("Dot Dissolve"),
    INSTANT("Instant")
}

@Immutable
enum class PageTransitionStyle(val displayName: String) {
    FLAT("Flat"),
    DEPTH("Depth"),
    CUBE("Cube"),
    PARALLAX("Parallax"),
    ACCORDION("Accordion"),
    STACK("Stack")
}

@Immutable
@JsonClass(generateAdapter = true)
data class PhysicsConfig(
    val animationSpeed: Float = 1.0f,
    val springStiffness: Float = 520f,
    val dampingBounce: Float = 0.78f,
    val launchTransition: LaunchTransitionStyle = LaunchTransitionStyle.SCALE,
    val pageTransition: PageTransitionStyle = PageTransitionStyle.PARALLAX,
    val pageTransitionIntensity: Float = 1.0f,
    val iconPressBounce: Float = 0.92f,
    val hapticIntensity: Float = 0.7f,
    val hapticOnScrollTick: Boolean = true
)

@Immutable
enum class GestureAction(val displayName: String) {
    NONE("None"),
    OPEN_SEARCH("Universal Search"),
    OPEN_DRAWER("App Drawer"),
    OPEN_SETTINGS("Settings Hub"),
    OPEN_VAULT("Biometric Vault"),
    TOGGLE_DEEP_FOCUS("Deep Focus Lock"),
    OPEN_AMBIENT_AOD("Always-On Desk Clock"),
    EXPAND_NOTIFICATIONS("Notifications"),
    TOGGLE_FLASHLIGHT("Flashlight"),
    NEXT_DOCK_PAGE("Cycle Dock Page")
}

@Immutable
@JsonClass(generateAdapter = true)
data class GestureConfig(
    val swipeUp: GestureAction = GestureAction.OPEN_DRAWER,
    val swipeDown: GestureAction = GestureAction.OPEN_SEARCH,
    val dockSwipeLeftRight: GestureAction = GestureAction.NEXT_DOCK_PAGE,
    val doubleTap: GestureAction = GestureAction.TOGGLE_DEEP_FOCUS,
    val pinchSpread: GestureAction = GestureAction.OPEN_SETTINGS,
    val iconSwipeUp: GestureAction = GestureAction.OPEN_SEARCH,
    val deviceShake: GestureAction = GestureAction.TOGGLE_FLASHLIGHT
)

@Immutable
enum class BadgeStyle(val displayName: String) {
    NONE("Hidden"),
    MINIMAL_DOT("Minimal Dot"),
    DOT_MATRIX_COUNT("Dot-Matrix Count"),
    GLYPH_PULSE("Glyph Pulse Border")
}

@Immutable
enum class WallpaperMode(val displayName: String) {
    DOT_MATRIX_GRID("Dot-Matrix Grid"),
    TOPOGRAPHIC_CONTOURS("Topographic Contours"),
    FROSTED_GLASS("Frosted Glass"),
    OLED_PURE_BLACK("OLED Battery Saver")
}

@Immutable
enum class WallpaperWavePattern(val displayName: String) {
    DIAGONAL_WAVE("Diagonal Wave"),
    RADIAL_RIPPLE("Radial Ripple"),
    MATRIX_RAIN("Dot Rain"),
    BREATHING_PULSE("Breathing Pulse"),
    STATIC("Static (No Motion)")
}

@Immutable
@JsonClass(generateAdapter = true)
data class SurfaceThemeConfig(
    val presetId: String = "classic_mono",
    val presetName: String = "Classic Monochrome",
    val oledPureBlack: Boolean = true,
    val backgroundHex: String = "#000000",
    val surfaceHex: String = "#111114",
    val accentHex: String = "#D71921",
    val textPrimaryHex: String = "#FFFFFF",
    val textSecondaryHex: String = "#9A9AA2",
    val drawerOpacity: Float = 0.92f,
    val drawerBlurDp: Float = 16f,
    val drawerBorderDp: Float = 1f,
    val drawerBgHex: String = "#0A0A0C",
    val widgetOpacity: Float = 0.88f,
    val widgetBlurDp: Float = 12f,
    val widgetBorderDp: Float = 1f,
    val widgetBgHex: String = "#121215",
    val folderOpacity: Float = 0.94f,
    val folderBlurDp: Float = 18f,
    val folderBorderDp: Float = 1f,
    val folderBgHex: String = "#151519",
    val contextMenuOpacity: Float = 0.96f,
    val contextMenuBorderDp: Float = 1f,
    val glyphHighlightHex: String = "#D71921",
    val useMaterialYou: Boolean = false,
    val wallpaperMode: WallpaperMode = WallpaperMode.DOT_MATRIX_GRID,
    val wallpaperTiltParallax: Boolean = true,
    val wallpaperDotDensity: Float = 0.65f,
    val wallpaperAnimationEnabled: Boolean = true,
    val wallpaperWavePattern: WallpaperWavePattern = WallpaperWavePattern.DIAGONAL_WAVE,
    val wallpaperAnimSpeed: Float = 1.0f,
    val wallpaperAnimIntensity: Float = 1.0f,
    val wallpaperDotBrightness: Float = 1.0f,
    val wallpaperDotSizeScale: Float = 1.0f,
    val wallpaperParallaxStrength: Float = 1.0f,
    val wallpaperAccentFrequency: Float = 1.0f
)

@Immutable
enum class CustomWidgetType(val displayName: String, val subtitle: String) {
    DIGITAL_AURA("Digital Aura", "Focus pulse & screen-time velocity"),
    TIME_DATE("Time & Date", "Dot-clock & dual timezone"),
    WEATHER("Weather", "Glyph forecast & hourly dot chart"),
    STEP_COUNTER("Step Counter", "Live pedometer & goal ring"),
    STEP_ANALYTICS("Step Analytics", "7-day dot/bar progress & streak"),
    MOON_PHASE("Moon Phase", "Lunar illumination & rise/set"),
    QUICK_SETTINGS("Quick Settings", "Hardware toggle strip"),
    YEAR_PROGRESS("Year Progress", "365-dot year matrix & countdown"),
    MEDIA_VINYL("Media Vinyl", "Spinning dot-matrix turntable & media controls"),
    BATTERY_MATRIX("Battery Matrix", "Phone & Bluetooth peripheral dot gauges"),
    POMODORO_FOCUS("Focus Pomodoro", "25/5 dot timer synced with Deep Focus")
}

@Immutable
enum class TimeFormatMode(val displayName: String) {
    VERTICAL_DOT("Vertical Dot Clock"),
    ANALOG_DOT_HYBRID("Analog-Dot Hybrid"),
    DUAL_TIMEZONE("Dual Timezone"),
    SINGLE_LINE("Minimalist Line")
}

@Immutable
enum class YearViewMode(val displayName: String) {
    DOT_MATRIX_365("365-Dot Grid"),
    PERCENT_BAR("Percentage Bar"),
    COUNTDOWN("Days Countdown")
}

@Immutable
enum class QuickToggleItem(val label: String) {
    WIFI("Wi-Fi"),
    BLUETOOTH("BT"),
    FLASHLIGHT("Torch"),
    DND("DND"),
    BATTERY_SAVER("Battery"),
    MOBILE_DATA("Data"),
    HOTSPOT("Hotspot"),
    ROTATION_LOCK("Rotate")
}

@Immutable
@JsonClass(generateAdapter = true)
data class WidgetAppearanceConfig(
    val widgetType: CustomWidgetType = CustomWidgetType.DIGITAL_AURA,
    val opacity: Float = 0.90f,
    val borderWidthDp: Float = 1f,
    val fontSizeScale: Float = 1.0f,
    val dotDensity: Float = 0.7f,
    val tapAction: String = "OPEN_DETAILS",
    val dailyStepGoal: Int = 8000,
    val timeFormatMode: TimeFormatMode = TimeFormatMode.VERTICAL_DOT,
    val showMoonTimes: Boolean = true,
    val showWeatherExtras: Boolean = true,
    val weatherCity: String = "San Francisco",
    val quickSettingToggles: List<QuickToggleItem> = listOf(
        QuickToggleItem.WIFI,
        QuickToggleItem.BLUETOOTH,
        QuickToggleItem.FLASHLIGHT,
        QuickToggleItem.DND,
        QuickToggleItem.BATTERY_SAVER
    ),
    val yearViewMode: YearViewMode = YearViewMode.DOT_MATRIX_365,
    val auraSensitivity: Float = 1.0f,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val mediaShowVisualizer: Boolean = true
)

@Immutable
enum class ContextTriggerType(val displayName: String) {
    MANUAL("Manual Profile"),
    TIME_WINDOW("Time of Day"),
    WIFI_CONNECTED("Wi-Fi Connected"),
    BLUETOOTH_CONNECTED("Bluetooth Device"),
    LOW_BATTERY("Low Battery")
}

@Immutable
@JsonClass(generateAdapter = true)
data class ContextRule(
    val id: String,
    val profileName: String,
    val enabled: Boolean = true,
    val triggerType: ContextTriggerType = ContextTriggerType.MANUAL,
    val startHour: Int = 9,
    val endHour: Int = 17,
    val wifiSsid: String = "",
    val bluetoothDeviceName: String = "",
    val batteryThreshold: Int = 20,
    val targetPresetId: String = "classic_mono",
    val autoDeepFocus: Boolean = false,
    val hiddenPackages: List<String> = emptyList()
)

@Immutable
@JsonClass(generateAdapter = true)
data class AppCustomization(
    val packageName: String,
    val customLabel: String? = null,
    val customGlyphId: String? = null,
    val customImageUri: String? = null,
    val monochromeOverride: Boolean? = null,
    val iconScaleOverride: Float? = null,
    val pinnedToTop: Boolean = false,
    val hidden: Boolean = false,
    val lockedInVault: Boolean = false,
    val frictionSeconds: Int = 0,
    val dailyLimitMinutes: Int = 0,
    val customCategory: String? = null
)

@Immutable
@JsonClass(generateAdapter = true)
data class LauncherFolder(
    val id: String,
    val name: String,
    val packageNames: List<String>,
    val pageIndex: Int = 0
)

@Immutable
@JsonClass(generateAdapter = true)
data class WidgetStackSlot(
    val id: String,
    val pageIndex: Int = 0,
    val stackedCustomWidgets: List<CustomWidgetType> = listOf(
        CustomWidgetType.DIGITAL_AURA,
        CustomWidgetType.TIME_DATE,
        CustomWidgetType.WEATHER
    ),
    val stackedSystemWidgetIds: List<Int> = emptyList(),
    val activeIndex: Int = 0
)

@Immutable
@JsonClass(generateAdapter = true)
data class LauncherSettings(
    val iconMask: IconMaskConfig = IconMaskConfig(),
    val typographyHome: TypographyElementConfig = TypographyElementConfig(
        fontChoice = FontFamilyChoice.SANS_CLEAN,
        sizeSp = 11.5f,
        letterSpacingSp = 0.4f,
        lineHeightSp = 14f,
        textCase = TextCaseMode.UPPERCASE
    ),
    val typographyDrawer: TypographyElementConfig = TypographyElementConfig(
        fontChoice = FontFamilyChoice.NTYPE_82,
        sizeSp = 13f,
        letterSpacingSp = 0.5f,
        lineHeightSp = 18f,
        textCase = TextCaseMode.ORIGINAL
    ),
    val typographyWidgetTitle: TypographyElementConfig = TypographyElementConfig(
        fontChoice = FontFamilyChoice.NDOT_55,
        sizeSp = 12f,
        letterSpacingSp = 1.2f,
        lineHeightSp = 15f,
        textCase = TextCaseMode.UPPERCASE
    ),
    val typographyWidgetData: TypographyElementConfig = TypographyElementConfig(
        fontChoice = FontFamilyChoice.NDOT_77,
        sizeSp = 28f,
        letterSpacingSp = 1.0f,
        lineHeightSp = 32f,
        textCase = TextCaseMode.UPPERCASE
    ),
    val typographySearch: TypographyElementConfig = TypographyElementConfig(
        fontChoice = FontFamilyChoice.NTYPE_82,
        sizeSp = 14f,
        letterSpacingSp = 0.4f,
        lineHeightSp = 20f,
        textCase = TextCaseMode.ORIGINAL
    ),
    val grid: GridGeometryConfig = GridGeometryConfig(),
    val drawerMode: DrawerMode = DrawerMode.CATEGORIZED_TABS,
    val drawerTabPosition: DrawerTabPosition = DrawerTabPosition.TOP,
    val folderConfig: FolderAppearanceConfig = FolderAppearanceConfig(),
    val searchConfig: SearchConfig = SearchConfig(),
    val physics: PhysicsConfig = PhysicsConfig(),
    val gestures: GestureConfig = GestureConfig(),
    val badgeStyle: BadgeStyle = BadgeStyle.DOT_MATRIX_COUNT,
    val theme: SurfaceThemeConfig = SurfaceThemeConfig(),
    val deepFocusActive: Boolean = false,
    val vaultPinHash: String = "0000",
    val activeProfileName: String = "Default",
    val widgetConfigs: Map<String, WidgetAppearanceConfig> = CustomWidgetType.entries.associate {
        it.name to WidgetAppearanceConfig(widgetType = it)
    },
    val widgetStacks: List<WidgetStackSlot> = listOf(
        WidgetStackSlot(
            id = "stack_primary",
            pageIndex = 0,
            stackedCustomWidgets = listOf(
                CustomWidgetType.DIGITAL_AURA,
                CustomWidgetType.TIME_DATE,
                CustomWidgetType.WEATHER,
                CustomWidgetType.MEDIA_VINYL,
                CustomWidgetType.POMODORO_FOCUS,
                CustomWidgetType.MOON_PHASE
            )
        ),
        WidgetStackSlot(
            id = "stack_secondary",
            pageIndex = 1,
            stackedCustomWidgets = listOf(
                CustomWidgetType.STEP_COUNTER,
                CustomWidgetType.STEP_ANALYTICS,
                CustomWidgetType.BATTERY_MATRIX,
                CustomWidgetType.YEAR_PROGRESS,
                CustomWidgetType.QUICK_SETTINGS
            )
        )
    ),
    val dockPages: List<List<String>> = listOf(emptyList(), emptyList(), emptyList()),
    val homeAppPages: List<List<String>> = listOf(emptyList(), emptyList()),
    val homePageConfigs: List<HomePageConfig> = listOf(
        HomePageConfig(name = "CORE"),
        HomePageConfig(name = "WIDGETS")
    ),
    val folders: List<LauncherFolder> = emptyList(),
    val appCustomizations: Map<String, AppCustomization> = emptyMap(),
    val contextRules: List<ContextRule> = listOf(
        ContextRule("work", "Work", enabled = false, triggerType = ContextTriggerType.TIME_WINDOW, startHour = 9, endHour = 17, targetPresetId = "dark_glyph", autoDeepFocus = true),
        ContextRule("home", "Home", enabled = false, triggerType = ContextTriggerType.WIFI_CONNECTED, targetPresetId = "classic_mono"),
        ContextRule("night", "Night", enabled = false, triggerType = ContextTriggerType.TIME_WINDOW, startHour = 22, endHour = 6, targetPresetId = "amber_terminal"),
        ContextRule("focus", "Focus", enabled = false, triggerType = ContextTriggerType.MANUAL, targetPresetId = "classic_mono", autoDeepFocus = true)
    ),
    val safSearchFolderUris: List<String> = emptyList()
) {
    fun typographyFor(slot: TypographySlot): TypographyElementConfig = when (slot) {
        TypographySlot.HOME_LABEL -> typographyHome
        TypographySlot.DRAWER_LABEL -> typographyDrawer
        TypographySlot.WIDGET_TITLE -> typographyWidgetTitle
        TypographySlot.WIDGET_DATA -> typographyWidgetData
        TypographySlot.SEARCH_TEXT -> typographySearch
    }

    fun widgetConfigFor(type: CustomWidgetType): WidgetAppearanceConfig =
        widgetConfigs[type.name] ?: WidgetAppearanceConfig(widgetType = type)

    fun pageConfigFor(index: Int): HomePageConfig =
        homePageConfigs.getOrNull(index) ?: HomePageConfig(name = "PAGE ${index + 1}")
}
