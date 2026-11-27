package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.appindexer.IndexedApp
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.contextautomation.LiveNotificationBridge
import com.example.coreui.DotMatrixDivider
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingGroupCard
import com.example.coreui.NothingSectionHeader
import com.example.coreui.NothingSliderRow
import com.example.coreui.NothingSurfaceCard
import com.example.coreui.NothingToggleRow
import com.example.settingsstore.db.ThemePresetEntity
import com.example.settingsstore.model.BadgeStyle
import com.example.settingsstore.model.ContextRule
import com.example.settingsstore.model.ContextTriggerType
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.DrawerMode
import com.example.settingsstore.model.DrawerTabPosition
import com.example.settingsstore.model.FolderCoverStyle
import com.example.settingsstore.model.FolderOpenMode
import com.example.settingsstore.model.GestureAction
import com.example.settingsstore.model.IconMaskShape
import com.example.settingsstore.model.LaunchTransitionStyle
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.PageTransitionStyle
import com.example.settingsstore.model.QuickToggleItem
import com.example.settingsstore.model.SearchBarPosition
import com.example.settingsstore.model.SearchSourceType
import com.example.settingsstore.model.SurfaceThemeConfig
import com.example.settingsstore.model.TimeFormatMode
import com.example.settingsstore.model.WidgetStackSlot
import com.example.settingsstore.model.YearViewMode
import com.example.settingsstore.repo.SettingsRepository
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import java.util.Locale
import com.example.widgethost.LauncherWidgetHostManager
import kotlin.math.roundToInt

@Composable
internal fun SearchAndDrawerTab(
    settings: LauncherSettings,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onAddCustomSearchEngine: (String, String) -> Unit,
    onClearClipboardHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    val listenerGranted = remember(settings) { LiveNotificationBridge.isNotificationAccessEnabled(context) }

    NothingGroupCard(
        title = "Search Everything Layout",
        subtitle = "Keep the search surface clean"
    ) {
        NothingToggleRow(
            label = "Quick Tools Row Under The Bar",
            description = "The pop-up strip of converters/timers right under the search field. Off = clean.",
            checked = settings.searchConfig.showQuickTools,
            onCheckedChange = { checked ->
                onUpdateSettings { cfg ->
                    cfg.copy(searchConfig = cfg.searchConfig.copy(showQuickTools = checked))
                }
            }
        )
    }

    NothingSectionHeader("App Drawer & Folders")

    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrawerMode.entries.forEach { mode ->
            NothingPillChip(
                label = mode.displayName,
                selected = settings.drawerMode == mode,
                onClick = { onUpdateSettings { it.copy(drawerMode = mode) } }
            )
        }
    }
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DrawerTabPosition.entries.forEach { pos ->
            NothingPillChip(
                label = "Tabs: ${pos.displayName}",
                selected = settings.drawerTabPosition == pos,
                onClick = { onUpdateSettings { it.copy(drawerTabPosition = pos) } }
            )
        }
    }

    Text("Folder Open Mode", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FolderOpenMode.entries.forEach { mode ->
            NothingPillChip(
                label = mode.displayName,
                selected = settings.folderConfig.openMode == mode,
                onClick = { onUpdateSettings { it.copy(folderConfig = it.folderConfig.copy(openMode = mode)) } }
            )
        }
    }

    Text("Folder Cover Style", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FolderCoverStyle.entries.forEach { style ->
            NothingPillChip(
                label = style.displayName,
                selected = settings.folderConfig.coverStyle == style,
                onClick = { onUpdateSettings { it.copy(folderConfig = it.folderConfig.copy(coverStyle = style)) } }
            )
        }
    }

    Text("Folder Mini-Icon Mask Override", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NothingPillChip(
            label = "Match Global",
            selected = settings.folderConfig.folderMaskOverride == null,
            onClick = { onUpdateSettings { it.copy(folderConfig = it.folderConfig.copy(folderMaskOverride = null)) } }
        )
        IconMaskShape.entries.forEach { shape ->
            NothingPillChip(
                label = shape.displayName,
                selected = settings.folderConfig.folderMaskOverride == shape,
                onClick = { onUpdateSettings { it.copy(folderConfig = it.folderConfig.copy(folderMaskOverride = shape)) } }
            )
        }
    }

    Text("Notification Badge Style", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BadgeStyle.entries.forEach { style ->
            NothingPillChip(
                label = style.displayName,
                selected = settings.badgeStyle == style,
                onClick = { onUpdateSettings { it.copy(badgeStyle = style) } }
            )
        }
    }

    NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                Text("Live Notification & Media Bridge", fontSize = 13.sp, color = palette.textPrimary)
                Text(
                    text = if (listenerGranted) "Active — Live badges & Vinyl widget synced" else "Grant Notification Access for live badge counts & Media Vinyl",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                )
            }
            NothingPillChip(
                label = if (listenerGranted) "Granted" else "Enable",
                selected = listenerGranted,
                onClick = { LiveNotificationBridge.openNotificationAccessSettings(context) }
            )
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Universal Search Bar", "Position, geometry, sources & custom engines")

    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SearchBarPosition.entries.forEach { pos ->
            NothingPillChip(
                label = pos.displayName,
                selected = settings.searchConfig.position == pos,
                onClick = { onUpdateSettings { it.copy(searchConfig = it.searchConfig.copy(position = pos)) } }
            )
        }
    }

    NothingSliderRow(
        label = "Search Bar Corner Radius",
        value = settings.searchConfig.cornerRadiusDp,
        valueRange = 0f..32f,
        onValueChange = { v -> onUpdateSettings { it.copy(searchConfig = it.searchConfig.copy(cornerRadiusDp = v)) } },
        valueFormatter = { "${it.roundToInt()}dp" }
    )
    NothingSliderRow(
        label = "Search Bar Background Opacity",
        value = settings.searchConfig.backgroundOpacity,
        valueRange = 0.2f..1f,
        onValueChange = { v -> onUpdateSettings { it.copy(searchConfig = it.searchConfig.copy(backgroundOpacity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )

    var placeholderText by remember(settings.searchConfig.placeholderText) {
        mutableStateOf(settings.searchConfig.placeholderText)
    }
    OutlinedTextField(
        value = placeholderText,
        onValueChange = { txt ->
            placeholderText = txt
            onUpdateSettings { it.copy(searchConfig = it.searchConfig.copy(placeholderText = txt)) }
        },
        label = { Text("Search Placeholder Text") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    NothingToggleRow(
        label = "Auto-Open Keyboard",
        checked = settings.searchConfig.autoOpenKeyboard,
        onCheckedChange = { v -> onUpdateSettings { it.copy(searchConfig = it.searchConfig.copy(autoOpenKeyboard = v)) } }
    )
    NothingToggleRow(
        label = "Incognito Search (Disable History)",
        checked = settings.searchConfig.incognitoMode,
        onCheckedChange = { v -> onUpdateSettings { it.copy(searchConfig = it.searchConfig.copy(incognitoMode = v)) } }
    )

    Text("Enabled Search Sources", fontSize = 12.sp, color = palette.textSecondary)
    SearchSourceType.entries.forEach { src ->
        val isOn = src in settings.searchConfig.enabledSources
        NothingToggleRow(
            label = src.displayName,
            checked = isOn,
            onCheckedChange = { enabled ->
                onUpdateSettings { cfg ->
                    val current = cfg.searchConfig.enabledSources.toMutableList()
                    if (enabled && !current.contains(src)) current.add(src)
                    else if (!enabled) current.remove(src)
                    cfg.copy(searchConfig = cfg.searchConfig.copy(enabledSources = current))
                }
            }
        )
    }

    var newEngineName by remember { mutableStateOf("") }
    var newEngineUrl by remember { mutableStateOf("https://example.com/?q=%s") }
    Text("Add Custom Search Engine URL", fontSize = 12.sp, color = palette.textSecondary)
    OutlinedTextField(
        value = newEngineName,
        onValueChange = { newEngineName = it },
        label = { Text("Engine Name (e.g. Wikipedia)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = newEngineUrl,
        onValueChange = { newEngineUrl = it },
        label = { Text("URL Template (%s for query)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            if (newEngineName.isNotBlank() && newEngineUrl.isNotBlank()) {
                onAddCustomSearchEngine(newEngineName, newEngineUrl)
                newEngineName = ""
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
    ) {
        Text("Add Search Engine", color = Color.White)
    }

    NothingPillChip(
        label = "Clear Saved Clipboard History",
        selected = false,
        onClick = onClearClipboardHistory
    )
}

@Composable
internal fun PhysicsAndGesturesTab(
    settings: LauncherSettings,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit
) {
    val palette = LocalNothingPalette.current

    NothingGroupCard(
        title = "Frame Rate & Performance",
        subtitle = "Pin a steady 30/60/120 FPS ceiling — weak GPUs and battery saver stay smooth"
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0 to "ADAPTIVE", 30 to "30 FPS", 60 to "60 FPS", 120 to "120 FPS").forEach { (cap, label) ->
                NothingPillChip(
                    label = label,
                    selected = settings.frameRateCap == cap,
                    onClick = {
                        onUpdateSettings { it.copy(frameRateCap = cap) }
                    }
                )
            }
        }
        Text(
            text = when (settings.frameRateCap) {
                30 -> "30 FPS cap active — maximum battery life & the steadiest pacing on low-end phones."
                60 -> "60 FPS cap active — smooth, predictable frame pacing."
                120 -> "120 FPS cap active for 90/120/144Hz panels."
                else -> "Adaptive — uses the panel's highest refresh rate automatically."
            },
            fontSize = 11.5.sp,
            color = palette.textSecondary
        )
    }

    NothingGroupCard(
        title = "Sleep Screen (Always-On Desk Clock)",
        subtitle = "Tune what the black sleep screen shows and how bright it burns"
    ) {
        NothingToggleRow(
            label = "Show Seconds",
            description = "Big ticking SEC readout under the clock.",
            checked = settings.aodShowSeconds,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(aodShowSeconds = checked) } }
        )
        NothingToggleRow(
            label = "Glyph Animations",
            description = "Breathing aura, orbiting comets and the pulsing dot equalizer.",
            checked = settings.aodGlyphs,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(aodGlyphs = checked) } }
        )
        NothingToggleRow(
            label = "Media Controls",
            description = "Show the now-playing bar so you can pause straight from sleep.",
            checked = settings.aodShowMedia,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(aodShowMedia = checked) } }
        )
        NothingToggleRow(
            label = "Burn-in Protection",
            description = "Nudges the whole clock around every 20 s to avoid OLED burn-in.",
            checked = settings.aodBurnInShift,
            onCheckedChange = { checked -> onUpdateSettings { it.copy(aodBurnInShift = checked) } }
        )
        NothingSliderRow(
            label = "Sleep Screen Brightness",
            value = settings.aodDimLevel,
            valueRange = 0.35f..1f,
            onValueChange = { v -> onUpdateSettings { it.copy(aodDimLevel = v) } },
            valueFormatter = { "${(it * 100).roundToInt()}%" }
        )
    }

    NothingSectionHeader("Physics & Animation Lab")

    NothingSliderRow(
        label = "Animation Speed",
        value = settings.physics.animationSpeed,
        valueRange = 0.25f..2.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(animationSpeed = v)) } },
        valueFormatter = { "${(it * 100).roundToInt() / 100f}x" }
    )
    NothingSliderRow(
        label = "Spring Stiffness",
        value = settings.physics.springStiffness,
        valueRange = 100f..1200f,
        onValueChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(springStiffness = v)) } },
        valueFormatter = { "${it.roundToInt()}" }
    )
    NothingSliderRow(
        label = "Damping Bounce",
        value = settings.physics.dampingBounce,
        valueRange = 0.3f..1.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(dampingBounce = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )

    Text("Home Page Transition", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PageTransitionStyle.entries.forEach { style ->
            NothingPillChip(
                label = style.displayName,
                selected = settings.physics.pageTransition == style,
                onClick = { onUpdateSettings { it.copy(physics = it.physics.copy(pageTransition = style)) } }
            )
        }
    }

    NothingSliderRow(
        label = "Home Page Transition Intensity",
        value = settings.physics.pageTransitionIntensity,
        valueRange = 0.2f..2.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(pageTransitionIntensity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_page_transition_intensity")
    )

    NothingSliderRow(
        label = "Icon Tap Press Bounce",
        value = settings.physics.iconPressBounce,
        valueRange = 0.80f..1.0f,
        onValueChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(iconPressBounce = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" },
        modifier = Modifier.testTag("slider_icon_press_bounce")
    )

    Text("App Launch Transition", fontSize = 12.sp, color = palette.textSecondary)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LaunchTransitionStyle.entries.forEach { style ->
            NothingPillChip(
                label = style.displayName,
                selected = settings.physics.launchTransition == style,
                onClick = { onUpdateSettings { it.copy(physics = it.physics.copy(launchTransition = style)) } }
            )
        }
    }

    NothingSliderRow(
        label = "Haptic Sharpness / Intensity",
        value = settings.physics.hapticIntensity,
        valueRange = 0f..1f,
        onValueChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(hapticIntensity = v)) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )
    NothingToggleRow(
        label = "Haptic Tick on Scroll & Swipes",
        checked = settings.physics.hapticOnScrollTick,
        onCheckedChange = { v -> onUpdateSettings { it.copy(physics = it.physics.copy(hapticOnScrollTick = v)) } }
    )

    DotMatrixDivider()
    NothingSectionHeader("Omnidirectional Gestures")

    GestureRowSelector("Swipe Up", settings.gestures.swipeUp) { act ->
        onUpdateSettings { it.copy(gestures = it.gestures.copy(swipeUp = act)) }
    }
    GestureRowSelector("Swipe Down", settings.gestures.swipeDown) { act ->
        onUpdateSettings { it.copy(gestures = it.gestures.copy(swipeDown = act)) }
    }
    GestureRowSelector("Double Tap", settings.gestures.doubleTap) { act ->
        onUpdateSettings { it.copy(gestures = it.gestures.copy(doubleTap = act)) }
    }
    GestureRowSelector("Two-Finger Pinch/Spread", settings.gestures.pinchSpread) { act ->
        onUpdateSettings { it.copy(gestures = it.gestures.copy(pinchSpread = act)) }
    }
    GestureRowSelector("Icon Swipe Up", settings.gestures.iconSwipeUp) { act ->
        onUpdateSettings { it.copy(gestures = it.gestures.copy(iconSwipeUp = act)) }
    }
    GestureRowSelector("Device Shake", settings.gestures.deviceShake) { act ->
        onUpdateSettings { it.copy(gestures = it.gestures.copy(deviceShake = act)) }
    }
}

@Composable
private fun GestureRowSelector(
    label: String,
    current: GestureAction,
    onSelect: (GestureAction) -> Unit
) {
    val palette = LocalNothingPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label -> ${current.displayName}", fontSize = 12.5.sp, color = palette.textPrimary)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GestureAction.entries.forEach { act ->
                NothingPillChip(
                    label = act.displayName,
                    selected = current == act,
                    onClick = { onSelect(act) }
                )
            }
        }
    }
}

@Composable
internal fun WidgetsAndContextTab(
    settings: LauncherSettings,
    allApps: List<IndexedApp>,
    themePresets: List<Pair<ThemePresetEntity, SurfaceThemeConfig>>,
    telemetry: DeviceTelemetrySnapshot,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    widgetHostManager: LauncherWidgetHostManager,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onUpsertContextRule: (ContextRule) -> Unit,
    onDeleteContextRule: (String) -> Unit,
    onBindSystemWidget: (WidgetStackSlot, android.appwidget.AppWidgetProviderInfo) -> Unit
) {
    val palette = LocalNothingPalette.current
    var selectedWidget by remember { mutableStateOf(CustomWidgetType.DIGITAL_AURA) }
    var editingRule by remember { mutableStateOf<ContextRule?>(null) }
    var showFocusPicker by remember { mutableStateOf(false) }
    var focusQuery by remember { mutableStateOf("") }
    val wCfg = settings.widgetConfigFor(selectedWidget)

    NothingGroupCard(
        title = "Deep Focus Block-List",
        subtitle = "These apps can't be opened and their notifications are muted while Deep Focus is on"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (settings.deepFocusActive) "DEEP FOCUS IS ON" else "DEEP FOCUS IS OFF",
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                color = if (settings.deepFocusActive) palette.accent else palette.textSecondary,
                modifier = Modifier.weight(1f)
            )
            NothingPillChip(
                label = if (showFocusPicker) "CLOSE" else "PICK APPS",
                selected = showFocusPicker,
                onClick = { showFocusPicker = !showFocusPicker }
            )
        }
        Text(
            text = "${settings.focusBlockedPackages.size} app(s) blocked" +
                if (settings.focusBlockedPackages.isEmpty()) " — nothing is blocked yet" else "",
            fontSize = 11.5.sp,
            color = palette.textSecondary
        )
        if (showFocusPicker) {
            BasicTextField(
                value = focusQuery,
                onValueChange = { focusQuery = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = palette.textPrimary,
                    fontSize = 13.sp
                ),
                cursorBrush = SolidColor(palette.accent),
                decorationBox = { inner ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        if (focusQuery.isEmpty()) {
                            Text("Filter apps…", color = palette.textSecondary, fontSize = 13.sp)
                        }
                        inner()
                    }
                }
            )
            val filtered = allApps
                .filter { it.label.contains(focusQuery, ignoreCase = true) }
                .take(40)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                filtered.forEach { app ->
                    val blocked = app.packageName in settings.focusBlockedPackages
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (blocked) palette.accent.copy(alpha = 0.16f) else palette.surface)
                            .border(
                                1.dp,
                                if (blocked) palette.accent else palette.borderSubtle,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                val next = if (blocked) {
                                    settings.focusBlockedPackages - app.packageName
                                } else {
                                    settings.focusBlockedPackages + app.packageName
                                }
                                onUpdateSettings { it.copy(focusBlockedPackages = next) }
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.label,
                            fontSize = 12.5.sp,
                            color = palette.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (blocked) "BLOCKED" else "ALLOWED",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 9.sp,
                            color = if (blocked) palette.accent else palette.textSecondary
                        )
                    }
                }
            }
        }
    }

    NothingSectionHeader("15 Custom Nothing Widgets", "Configure per-widget opacity, border, density, precise location & behavior")
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CustomWidgetType.entries.forEach { wt ->
            NothingPillChip(
                label = wt.displayName,
                selected = selectedWidget == wt,
                onClick = { selectedWidget = wt }
            )
        }
    }

    fun updateWidget(transform: (com.example.settingsstore.model.WidgetAppearanceConfig) -> com.example.settingsstore.model.WidgetAppearanceConfig) {
        onUpdateSettings { cfg ->
            val updated = cfg.widgetConfigs.toMutableMap()
            updated[selectedWidget.name] = transform(cfg.widgetConfigFor(selectedWidget))
            cfg.copy(widgetConfigs = updated)
        }
    }

    NothingSliderRow(
        label = "${selectedWidget.displayName} Opacity",
        value = wCfg.opacity,
        valueRange = 0.2f..1f,
        onValueChange = { v -> updateWidget { it.copy(opacity = v) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )
    NothingSliderRow(
        label = "Border Stroke",
        value = wCfg.borderWidthDp,
        valueRange = 0f..4f,
        onValueChange = { v -> updateWidget { it.copy(borderWidthDp = v) } },
        valueFormatter = { "${(it * 10).roundToInt() / 10f}dp" }
    )
    NothingSliderRow(
        label = "Font Size Scale",
        value = wCfg.fontSizeScale,
        valueRange = 0.7f..1.4f,
        onValueChange = { v -> updateWidget { it.copy(fontSizeScale = v) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )
    NothingSliderRow(
        label = "Dot Matrix Density",
        value = wCfg.dotDensity,
        valueRange = 0.25f..1f,
        onValueChange = { v -> updateWidget { it.copy(dotDensity = v) } },
        valueFormatter = { "${(it * 100).roundToInt()}%" }
    )

    when (selectedWidget) {
        CustomWidgetType.STEP_COUNTER, CustomWidgetType.STEP_ANALYTICS -> {
            NothingSliderRow(
                label = "Daily Step Goal",
                value = wCfg.dailyStepGoal.toFloat(),
                valueRange = 2000f..20000f,
                onValueChange = { v -> updateWidget { it.copy(dailyStepGoal = (v / 500f).roundToInt() * 500) } },
                valueFormatter = { "${it.roundToInt()} steps" }
            )
        }
        CustomWidgetType.TIME_DATE -> {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeFormatMode.entries.forEach { m ->
                    NothingPillChip(
                        label = m.displayName,
                        selected = wCfg.timeFormatMode == m,
                        onClick = { updateWidget { it.copy(timeFormatMode = m) } }
                    )
                }
            }
        }
        CustomWidgetType.WEATHER -> {
            var city by remember(wCfg.weatherCity) { mutableStateOf(wCfg.weatherCity) }
            var showLocationDialog by remember { mutableStateOf(false) }

            Text(
                text = when {
                    wCfg.weatherCustomLat != null && wCfg.weatherCustomLon != null ->
                        "PRECISE COORDINATES • " +
                            String.format(Locale.US, "%.4f", wCfg.weatherCustomLat ?: 0.0) + ", " +
                            String.format(Locale.US, "%.4f", wCfg.weatherCustomLon ?: 0.0)
                    wCfg.weatherUsePreciseGps -> "PRECISE GPS FIX ENABLED"
                    else -> "CITY MODE • ${wCfg.weatherCity}"
                },
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 10.5.sp,
                color = palette.accent
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NothingPillChip(
                    label = "Detect GPS / Exact Location",
                    selected = wCfg.weatherUsePreciseGps,
                    onClick = { showLocationDialog = true }
                )
                NothingPillChip(
                    label = "City Mode",
                    selected = !wCfg.weatherUsePreciseGps && wCfg.weatherCustomLat == null,
                    onClick = {
                        updateWidget {
                            it.copy(
                                weatherUsePreciseGps = false,
                                weatherCustomLat = null,
                                weatherCustomLon = null
                            )
                        }
                    }
                )
                NothingPillChip(
                    label = "Enter Lat / Lon",
                    selected = wCfg.weatherCustomLat != null,
                    onClick = { showLocationDialog = true }
                )
            }
            OutlinedTextField(
                value = city,
                onValueChange = { c ->
                    city = c
                    updateWidget { it.copy(weatherCity = c) }
                },
                label = { Text("City / Fallback Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (showLocationDialog) {
                com.example.customwidgets.PreciseWeatherLocationDialog(
                    config = wCfg,
                    weatherRepo = null,
                    onApplyLocation = { updated -> updateWidget { updated } },
                    onDismiss = { showLocationDialog = false }
                )
            }
        }
        CustomWidgetType.DIGITAL_AURA -> {
            Text("AURA GEOMETRY (8 SELECTABLE SHAPES)", fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 11.sp, color = palette.accent)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.example.settingsstore.model.DigitalAuraShape.entries.forEach { shape ->
                    NothingPillChip(
                        label = shape.displayName,
                        selected = wCfg.auraShape == shape,
                        onClick = { updateWidget { it.copy(auraShape = shape) } }
                    )
                }
            }
            NothingSliderRow(
                label = "Screen-Time Velocity Sensitivity",
                value = wCfg.auraSensitivity,
                valueRange = 0.5f..2.0f,
                onValueChange = { v -> updateWidget { it.copy(auraSensitivity = v) } },
                valueFormatter = { "${(it * 100).roundToInt()}%" }
            )
        }
        CustomWidgetType.COUNTDOWN_DECK -> {
            var title by remember(wCfg.countdownTitle) { mutableStateOf(wCfg.countdownTitle) }
            OutlinedTextField(
                value = title,
                onValueChange = { t ->
                    title = t
                    updateWidget { it.copy(countdownTitle = t) }
                },
                label = { Text("Countdown Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            NothingSliderRow(
                label = "Days From Now",
                value = wCfg.countdownDaysFromNow.toFloat(),
                valueRange = 1f..365f,
                onValueChange = { v -> updateWidget { it.copy(countdownDaysFromNow = v.roundToInt()) } },
                valueFormatter = { "${it.roundToInt()} days" }
            )
        }
        CustomWidgetType.NEWS_HEADLINES -> {
            Text(
                text = "Live BBC / Hacker News headlines across 5 categories. Tap the widget to open News Space.",
                fontSize = 11.5.sp,
                color = palette.textSecondary
            )
        }
        CustomWidgetType.QUICK_NOTES -> {
            Text(
                text = "Sticky note pad stored locally in DataStore. Tap the widget to write or edit instantly.",
                fontSize = 11.5.sp,
                color = palette.textSecondary
            )
        }
        CustomWidgetType.SYSTEM_MONITOR -> {
            Text(
                text = "Live RAM, internal storage and display refresh-rate gauges refreshed every few seconds.",
                fontSize = 11.5.sp,
                color = palette.textSecondary
            )
        }
        CustomWidgetType.QUICK_SETTINGS -> {
            Text("Pick 3 to 6 Quick Toggles", fontSize = 12.sp, color = palette.textSecondary)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuickToggleItem.entries.forEach { item ->
                    val active = item in wCfg.quickSettingToggles
                    NothingPillChip(
                        label = item.label,
                        selected = active,
                        onClick = {
                            updateWidget { cur ->
                                val list = cur.quickSettingToggles.toMutableList()
                                if (active && list.size > 3) list.remove(item)
                                else if (!active && list.size < 6) list.add(item)
                                cur.copy(quickSettingToggles = list)
                            }
                        }
                    )
                }
            }
        }
        CustomWidgetType.YEAR_PROGRESS -> {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                YearViewMode.entries.forEach { ym ->
                    NothingPillChip(
                        label = ym.displayName,
                        selected = wCfg.yearViewMode == ym,
                        onClick = { updateWidget { it.copy(yearViewMode = ym) } }
                    )
                }
            }
        }
        CustomWidgetType.MOON_PHASE -> {
            NothingToggleRow(
                label = "Show Moonrise / Moonset Times",
                checked = wCfg.showMoonTimes,
                onCheckedChange = { v -> updateWidget { it.copy(showMoonTimes = v) } }
            )
        }
        CustomWidgetType.MEDIA_VINYL -> {
            NothingToggleRow(
                label = "Show Dot-Bar Audio Visualizer",
                checked = wCfg.mediaShowVisualizer,
                onCheckedChange = { v -> updateWidget { it.copy(mediaShowVisualizer = v) } }
            )
        }
        CustomWidgetType.BATTERY_MATRIX -> {
            if (!hasBluetoothPermission) {
                NothingPillChip(
                    label = "Grant Bluetooth Connect for Earbud/Watch Batteries",
                    selected = false,
                    onClick = onRequestBluetoothPermission
                )
            } else {
                Text(
                    text = "Bluetooth Battery Monitoring Ready (${telemetry.bluetoothBatteries.size} active peripheral gauges)",
                    fontSize = 12.sp,
                    color = palette.accent
                )
            }
        }
        CustomWidgetType.POMODORO_FOCUS -> {
            NothingSliderRow(
                label = "Focus Duration",
                value = wCfg.pomodoroFocusMinutes.toFloat(),
                valueRange = 10f..90f,
                steps = 15,
                onValueChange = { v -> updateWidget { it.copy(pomodoroFocusMinutes = v.roundToInt()) } },
                valueFormatter = { "${it.roundToInt()} min" }
            )
            NothingSliderRow(
                label = "Break Duration",
                value = wCfg.pomodoroBreakMinutes.toFloat(),
                valueRange = 2f..30f,
                steps = 13,
                onValueChange = { v -> updateWidget { it.copy(pomodoroBreakMinutes = v.roundToInt()) } },
                valueFormatter = { "${it.roundToInt()} min" }
            )
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Interactive Widget Stacking & System Widgets")
    val primarySlot = settings.widgetStacks.firstOrNull()
    if (primarySlot != null) {
        Text("Stack 1 Active Custom Widgets", fontSize = 12.sp, color = palette.textSecondary)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CustomWidgetType.entries.forEach { wt ->
                val inStack = wt in primarySlot.stackedCustomWidgets
                NothingPillChip(
                    label = wt.displayName,
                    selected = inStack,
                    onClick = {
                        onUpdateSettings { cfg ->
                            val updatedStacks = cfg.widgetStacks.map { slot ->
                                if (slot.id != primarySlot.id) slot
                                else {
                                    val list = slot.stackedCustomWidgets.toMutableList()
                                    if (inStack) list.remove(wt) else list.add(wt)
                                    slot.copy(stackedCustomWidgets = list)
                                }
                            }
                            cfg.copy(widgetStacks = updatedStacks)
                        }
                    }
                )
            }
        }

        val providers = remember { widgetHostManager.getInstalledProviders().take(8) }
        if (providers.isNotEmpty()) {
            Text("Bind Android System Widget to Stack 1", fontSize = 12.sp, color = palette.textSecondary)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { info ->
                    val label = info.loadLabel(LocalContext.current.packageManager)
                    NothingPillChip(
                        label = "+ $label",
                        selected = false,
                        onClick = { onBindSystemWidget(primarySlot, info) }
                    )
                }
            }
        }
    }

    DotMatrixDivider()
    NothingSectionHeader("Context-Aware Auto-Profiles", "Auto-switch themes, Deep Focus & hidden apps by time, Wi-Fi, BT or battery")

    Button(
        onClick = {
            editingRule = ContextRule(
                id = "rule_${System.currentTimeMillis()}",
                profileName = "Custom Profile",
                enabled = true,
                triggerType = ContextTriggerType.WIFI_CONNECTED,
                wifiSsid = telemetry.wifiSsid,
                targetPresetId = settings.theme.presetId
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        modifier = Modifier.testTag("add_context_rule_button")
    ) {
        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Create Custom Context Rule", color = Color.White)
    }

    settings.contextRules.forEach { rule ->
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
            Column(modifier = Modifier.padding(12.dp)) {
                NothingToggleRow(
                    label = "${rule.profileName} (${rule.triggerType.displayName})",
                    description = buildRuleSummary(rule),
                    checked = rule.enabled,
                    onCheckedChange = { enabled ->
                        onUpsertContextRule(rule.copy(enabled = enabled))
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { editingRule = rule }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Edit, "Edit Rule", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onDeleteContextRule(rule.id) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Delete, "Delete Rule", tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    editingRule?.let { draft ->
        ContextRuleEditorDialog(
            initialRule = draft,
            allApps = allApps,
            themePresets = themePresets,
            telemetry = telemetry,
            onSave = { saved ->
                onUpsertContextRule(saved)
                editingRule = null
            },
            onDismiss = { editingRule = null }
        )
    }
}

private fun buildRuleSummary(rule: ContextRule): String {
    val cond = when (rule.triggerType) {
        ContextTriggerType.TIME_WINDOW -> "${rule.startHour}:00–${rule.endHour}:00"
        ContextTriggerType.WIFI_CONNECTED -> if (rule.wifiSsid.isBlank()) "Any Wi-Fi" else "SSID '${rule.wifiSsid}'"
        ContextTriggerType.BLUETOOTH_CONNECTED -> if (rule.bluetoothDeviceName.isBlank()) "Any BT" else "BT '${rule.bluetoothDeviceName}'"
        ContextTriggerType.LOW_BATTERY -> "Battery <= ${rule.batteryThreshold}%"
        ContextTriggerType.MANUAL -> "Manual"
    }
    val hiddenNote = if (rule.hiddenPackages.isNotEmpty()) " • Hides ${rule.hiddenPackages.size} apps" else ""
    return "$cond • Preset: ${rule.targetPresetId} • Focus: ${rule.autoDeepFocus}$hiddenNote"
}

@Composable
private fun ContextRuleEditorDialog(
    initialRule: ContextRule,
    allApps: List<IndexedApp>,
    themePresets: List<Pair<ThemePresetEntity, SurfaceThemeConfig>>,
    telemetry: DeviceTelemetrySnapshot,
    onSave: (ContextRule) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNothingPalette.current
    var name by remember { mutableStateOf(initialRule.profileName) }
    var trigger by remember { mutableStateOf(initialRule.triggerType) }
    var startHour by remember { mutableIntStateOf(initialRule.startHour) }
    var endHour by remember { mutableIntStateOf(initialRule.endHour) }
    var wifiSsid by remember { mutableStateOf(initialRule.wifiSsid) }
    var btName by remember { mutableStateOf(initialRule.bluetoothDeviceName) }
    var batThresh by remember { mutableIntStateOf(initialRule.batteryThreshold) }
    var targetPresetId by remember { mutableStateOf(initialRule.targetPresetId) }
    var autoFocus by remember { mutableStateOf(initialRule.autoDeepFocus) }
    var hiddenSet by remember { mutableStateOf(initialRule.hiddenPackages.toSet()) }

    Dialog(onDismissRequest = onDismiss) {
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "EDIT CONTEXT RULE",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 14.sp,
                    color = palette.accent
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContextTriggerType.entries.forEach { tt ->
                        NothingPillChip(
                            label = tt.displayName,
                            selected = trigger == tt,
                            onClick = { trigger = tt }
                        )
                    }
                }
                when (trigger) {
                    ContextTriggerType.WIFI_CONNECTED -> {
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text("Wi-Fi SSID (blank = any)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (telemetry.wifiSsid.isNotBlank()) {
                            NothingPillChip(
                                label = "Use Current: ${telemetry.wifiSsid}",
                                selected = wifiSsid == telemetry.wifiSsid,
                                onClick = { wifiSsid = telemetry.wifiSsid }
                            )
                        }
                    }
                    ContextTriggerType.BLUETOOTH_CONNECTED -> {
                        OutlinedTextField(
                            value = btName,
                            onValueChange = { btName = it },
                            label = { Text("Bluetooth Device Name (blank = any)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ContextTriggerType.TIME_WINDOW -> {
                        NothingSliderRow(
                            label = "Start Hour",
                            value = startHour.toFloat(),
                            valueRange = 0f..23f,
                            onValueChange = { startHour = it.roundToInt() },
                            valueFormatter = { "${it.roundToInt()}:00" }
                        )
                        NothingSliderRow(
                            label = "End Hour",
                            value = endHour.toFloat(),
                            valueRange = 0f..23f,
                            onValueChange = { endHour = it.roundToInt() },
                            valueFormatter = { "${it.roundToInt()}:00" }
                        )
                    }
                    ContextTriggerType.LOW_BATTERY -> {
                        NothingSliderRow(
                            label = "Battery Threshold",
                            value = batThresh.toFloat(),
                            valueRange = 5f..50f,
                            onValueChange = { batThresh = it.roundToInt() },
                            valueFormatter = { "${it.roundToInt()}%" }
                        )
                    }
                    ContextTriggerType.MANUAL -> Unit
                }

                Text("Target Theme Preset", fontSize = 11.5.sp, color = palette.textSecondary)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    themePresets.forEach { (entity, cfg) ->
                        NothingPillChip(
                            label = cfg.presetName,
                            selected = targetPresetId == entity.id,
                            onClick = { targetPresetId = entity.id }
                        )
                    }
                }

                NothingToggleRow(
                    label = "Enable Deep Focus Lock",
                    checked = autoFocus,
                    onCheckedChange = { autoFocus = it }
                )

                if (allApps.isNotEmpty()) {
                    Text("Hide Distracting Apps in This Profile (${hiddenSet.size})", fontSize = 11.5.sp, color = palette.textSecondary)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allApps.take(18).forEach { app ->
                            val isHidden = app.packageName in hiddenSet
                            NothingPillChip(
                                label = app.label,
                                selected = isHidden,
                                onClick = {
                                    hiddenSet = if (isHidden) hiddenSet - app.packageName else hiddenSet + app.packageName
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            onSave(
                                initialRule.copy(
                                    profileName = name.ifBlank { "Custom Profile" },
                                    triggerType = trigger,
                                    startHour = startHour,
                                    endHour = endHour,
                                    wifiSsid = wifiSsid.trim(),
                                    bluetoothDeviceName = btName.trim(),
                                    batteryThreshold = batThresh,
                                    targetPresetId = targetPresetId,
                                    autoDeepFocus = autoFocus,
                                    hiddenPackages = hiddenSet.toList()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Save Rule", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun VaultAndBackupTab(
    settings: LauncherSettings,
    settingsRepo: SettingsRepository,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onImportBackup: (String) -> Unit,
    onOpenVault: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    var showQrCode by remember { mutableStateOf(false) }
    var showQrScanner by remember { mutableStateOf(false) }
    var pasteJson by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf(settings.vaultPinHash) }

    val exportDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(settingsRepo.exportToJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "JSON Backup saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importDocLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!text.isNullOrBlank()) onImportBackup(text)
            }
        }
    }

    NothingSectionHeader("Biometric App Vault", "PIN & BiometricPrompt protection for concealed apps")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newPin,
            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) newPin = it },
            label = { Text("4-Digit Vault PIN") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = {
                if (newPin.length == 4) {
                    onUpdateSettings { it.copy(vaultPinHash = newPin) }
                    Toast.makeText(context, "Vault PIN updated", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
        ) {
            Text("Set PIN", color = Color.White)
        }
    }
    NothingPillChip(label = "Unlock & Manage Vault Apps", selected = false, onClick = onOpenVault)

    DotMatrixDivider()
    NothingSectionHeader("1-Tap Backup, Clone & QR Share Engine")

    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NothingPillChip(
            label = "Export JSON File",
            selected = false,
            onClick = { exportDocLauncher.launch("dotos_setup_backup.json") }
        )
        NothingPillChip(
            label = "Copy JSON",
            selected = false,
            onClick = {
                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("DotOS Backup", settingsRepo.exportToJson()))
                Toast.makeText(context, "Setup JSON copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        )
        NothingPillChip(
            label = "Import JSON File",
            selected = false,
            onClick = { importDocLauncher.launch(arrayOf("application/json", "text/plain")) }
        )
    }

    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NothingPillChip(
            label = if (showQrCode) "Hide Setup QR" else "Generate Setup QR",
            selected = showQrCode,
            onClick = { showQrCode = !showQrCode }
        )
        NothingPillChip(
            label = "Scan / Import QR",
            selected = false,
            onClick = { showQrScanner = true }
        )
    }

    if (showQrCode) {
        val qrBitmap = remember(settings) {
            runCatching { settingsRepo.generateQrBitmap(540) }.getOrNull()
        }
        if (qrBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "DotOS Setup QR Code",
                    modifier = Modifier.size(220.dp)
                )
            }
        }
    }

    OutlinedTextField(
        value = pasteJson,
        onValueChange = { pasteJson = it },
        label = { Text("Paste Backup JSON or DOTOS_QR1 Payload") },
        maxLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            if (pasteJson.isNotBlank()) {
                onImportBackup(pasteJson)
                pasteJson = ""
            }
        },
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
    ) {
        Text("Restore Setup from Payload", color = Color.White)
    }

    if (showQrScanner) {
        QrScannerDialog(
            settingsRepo = settingsRepo,
            onQrDecoded = { payload ->
                showQrScanner = false
                onImportBackup(payload)
            },
            onDismiss = { showQrScanner = false }
        )
    }
}
