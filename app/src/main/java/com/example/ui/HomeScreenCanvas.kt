package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.animationphysics.LauncherPhysicsEngine
import com.example.animationphysics.LauncherPhysicsEngine.pagerTransitionDeferred
import com.example.animationphysics.NothingHaptics
import com.example.appindexer.AppIndexerRepository
import com.example.appindexer.IndexedApp
import com.example.appindexer.NothingGlyphCatalog
import com.example.appindexer.ScreenTimeVelocity
import com.example.contextautomation.ActiveMediaState
import com.example.contextautomation.ContextAutomationEngine
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.coreui.DotMatrixDivider
import com.example.coreui.MaskedAppIcon
import com.example.coreui.NothingSurfaceCard
import com.example.customwidgets.DockMiniWidgetChip
import com.example.customwidgets.WeatherState
import com.example.settingsstore.db.StepHistoryEntity
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.FolderCoverStyle
import com.example.settingsstore.model.FolderOpenMode
import com.example.settingsstore.model.LauncherFolder
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SearchBarPosition
import com.example.settingsstore.model.WidgetStackSlot
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingLiveWallpaperCanvas
import com.example.themeengine.NothingTypographyEngine
import com.example.widgethost.InteractiveWidgetStackCard
import com.example.widgethost.LauncherWidgetHostManager
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenCanvas(
    settings: LauncherSettings,
    allApps: List<IndexedApp>,
    mostUsedApps: List<IndexedApp>,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    widgetHostManager: LauncherWidgetHostManager,
    stepsToday: Int,
    stepSensorAvailable: Boolean,
    hasStepPermission: Boolean,
    stepHistory: List<StepHistoryEntity>,
    weatherState: WeatherState,
    telemetry: DeviceTelemetrySnapshot,
    screenTimeVelocity: ScreenTimeVelocity,
    contextEngine: ContextAutomationEngine,
    badgeCounts: Map<String, Int>,
    mediaState: ActiveMediaState,
    hasBluetoothPermission: Boolean,
    contextHiddenPackages: Set<String>,
    onRequestBluetoothPermission: () -> Unit,
    onAppClick: (IndexedApp) -> Unit,
    onAppLongPress: (IndexedApp) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: (Int) -> Unit,
    onOpenAmbientAod: () -> Unit,
    onTriggerGesture: (com.example.settingsstore.model.GestureAction, () -> Unit) -> Unit,
    onRequestStepSync: () -> Unit,
    onCycleTimeFormat: () -> Unit,
    onRefreshWeather: () -> Unit,
    onCycleYearMode: () -> Unit,
    onToggleDeepFocus: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onAddPage: () -> Unit,
    onMoveHomeApp: (Int, String, Int) -> Unit,
    onMoveHomeAppToPage: (Int, String, Int) -> Unit,
    onOpenStackEditor: (WidgetStackSlot) -> Unit,
    wallpaperPaused: Boolean = false,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit = {},
    onUpdateWidgetSlot: (String, (WidgetStackSlot) -> WidgetStackSlot) -> Unit = { _, _ -> },
    onAddWidgetSlot: (Int) -> Unit = {},
    onDeleteWidgetSlot: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageCount = settings.homeAppPages.size.coerceAtLeast(2)
    val homePagerState = rememberPagerState(pageCount = { pageCount })
    val dockPageCount = settings.grid.dockPageCount.coerceIn(1, 3)
    val dockPagerState = rememberPagerState(pageCount = { dockPageCount })

    var activeFolder by remember { mutableStateOf<LauncherFolder?>(null) }
    var inlineExpandedFolderId by remember { mutableStateOf<String?>(null) }
    var editLayoutMode by remember { mutableStateOf(false) }

    BackHandler(enabled = editLayoutMode) {
        editLayoutMode = false
    }

    val enterEditMode = {
        if (!editLayoutMode) {
            NothingHaptics.performConfirm(context, settings.physics)
            editLayoutMode = true
        }
    }

    val appMap = remember(allApps) { allApps.associateBy { it.packageName } }
    val homeLabelStyle = remember(settings.typographyHome, palette.textPrimary) {
        NothingTypographyEngine.resolveTextStyle(settings.typographyHome)
            .copy(color = palette.textPrimary)
    }

    val cycleDock = {
        scope.launch {
            val next = (dockPagerState.currentPage + 1) % dockPageCount
            dockPagerState.animateScrollToPage(next)
        }
        Unit
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(settings.gestures, editLayoutMode) {
                detectTapGestures(
                    onTap = {
                        if (editLayoutMode) editLayoutMode = false
                    },
                    onLongPress = {
                        enterEditMode()
                    },
                    onDoubleTap = {
                        NothingHaptics.performConfirm(context, settings.physics)
                        onTriggerGesture(settings.gestures.doubleTap, cycleDock)
                    }
                )
            }
            .pointerInput(settings.gestures) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.25f || zoom < 0.78f) {
                        onTriggerGesture(settings.gestures.pinchSpread, cycleDock)
                    }
                }
            }
            .pointerInput(settings.gestures) {
                var dragY = 0f
                detectDragGestures(
                    onDragStart = { dragY = 0f },
                    onDrag = { _, dragAmount -> dragY += dragAmount.y },
                    onDragEnd = {
                        if (dragY < -110f) {
                            NothingHaptics.performTick(context, settings.physics)
                            onTriggerGesture(settings.gestures.swipeUp, cycleDock)
                        } else if (dragY > 110f) {
                            NothingHaptics.performTick(context, settings.physics)
                            onTriggerGesture(settings.gestures.swipeDown, cycleDock)
                        }
                    }
                )
            }
    ) {
        // Live Wallpaper Layer (automatically paused when drawer/search/settings cover home)
        NothingLiveWallpaperCanvas(
            theme = settings.theme,
            deepFocusActive = settings.deepFocusActive,
            paused = wallpaperPaused
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Status & Quick Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = settings.grid.marginDp.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (settings.deepFocusActive) palette.textSecondary else palette.accent)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (settings.deepFocusActive) {
                            "FOCUS MODE  •  ${settings.activeProfileName.uppercase()}"
                        } else {
                            "DOTOS  •  ${settings.activeProfileName.uppercase()}"
                        },
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        color = palette.textSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { editLayoutMode = !editLayoutMode },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_edit_layout_button")
                    ) {
                        Icon(
                            imageVector = if (editLayoutMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                            contentDescription = if (editLayoutMode) "Finish Layout Edit" else "Edit Home Layout",
                            tint = if (editLayoutMode) palette.accent else palette.textPrimary
                        )
                    }
                    IconButton(
                        onClick = onOpenAmbientAod,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_aod_button")
                    ) {
                        Icon(Icons.Rounded.Bedtime, "Always-On Desk Clock", tint = palette.textPrimary)
                    }
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_search_button")
                    ) {
                        Icon(Icons.Rounded.Search, "Universal Search", tint = palette.textPrimary)
                    }
                    IconButton(
                        onClick = { onOpenSettings(0) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_settings_button")
                    ) {
                        Icon(Icons.Rounded.Tune, "Customize Everything", tint = palette.textPrimary)
                    }
                }
            }

            if (settings.searchConfig.position == SearchBarPosition.TOP) {
                HomeInlineSearchPill(
                    placeholder = settings.searchConfig.placeholderText,
                    settings = settings,
                    onClick = onOpenSearch,
                    modifier = Modifier.padding(horizontal = settings.grid.marginDp.dp, vertical = 4.dp)
                )
            }

            // Quick Home & Widget Customize Toolbar (shown on long-press or Edit button)
            AnimatedVisibility(visible = editLayoutMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = settings.grid.marginDp.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.surface.copy(alpha = 0.96f))
                        .border(1.dp, palette.accent.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Add Widget Block on current page
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.accent)
                            .clickable { onAddWidgetSlot(homePagerState.currentPage) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "WIDGET",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }

                    // Columns - / +
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.background.copy(alpha = 0.65f))
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Remove,
                            "Fewer Columns",
                            tint = palette.textPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onUpdateSettings { cfg ->
                                        cfg.copy(
                                            grid = cfg.grid.copy(
                                                homeColumns = (cfg.grid.homeColumns - 1).coerceIn(3, 7)
                                            )
                                        )
                                    }
                                }
                        )
                        Text(
                            text = "${settings.grid.homeColumns} COL",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 10.sp,
                            color = palette.textPrimary
                        )
                        Icon(
                            Icons.Rounded.Add,
                            "More Columns",
                            tint = palette.textPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onUpdateSettings { cfg ->
                                        cfg.copy(
                                            grid = cfg.grid.copy(
                                                homeColumns = (cfg.grid.homeColumns + 1).coerceIn(3, 7)
                                            )
                                        )
                                    }
                                }
                        )
                    }

                    // Icon Scale - / +
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.background.copy(alpha = 0.65f))
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Remove,
                            "Smaller Icons",
                            tint = palette.textPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onUpdateSettings { cfg ->
                                        cfg.copy(
                                            iconMask = cfg.iconMask.copy(
                                                iconScale = (cfg.iconMask.iconScale - 0.08f).coerceIn(0.7f, 1.35f)
                                            )
                                        )
                                    }
                                }
                        )
                        Text(
                            text = "ICON ${(settings.iconMask.iconScale * 100).toInt()}%",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 10.sp,
                            color = palette.textPrimary
                        )
                        Icon(
                            Icons.Rounded.Add,
                            "Larger Icons",
                            tint = palette.textPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    onUpdateSettings { cfg ->
                                        cfg.copy(
                                            iconMask = cfg.iconMask.copy(
                                                iconScale = (cfg.iconMask.iconScale + 0.08f).coerceIn(0.7f, 1.35f)
                                            )
                                        )
                                    }
                                }
                        )
                    }

                    // Toggle Most Used Section
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (settings.grid.showMostUsedSection) palette.accent.copy(alpha = 0.22f)
                                else palette.background.copy(alpha = 0.65f)
                            )
                            .border(
                                1.dp,
                                if (settings.grid.showMostUsedSection) palette.accent else palette.borderSubtle,
                                RoundedCornerShape(999.dp)
                            )
                            .clickable {
                                onUpdateSettings { cfg ->
                                    cfg.copy(grid = cfg.grid.copy(showMostUsedSection = !cfg.grid.showMostUsedSection))
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (settings.grid.showMostUsedSection) "MOST USED: ON" else "MOST USED: OFF",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 10.sp,
                            color = if (settings.grid.showMostUsedSection) palette.accent else palette.textSecondary
                        )
                    }

                    // Done chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.textPrimary)
                            .clickable { editLayoutMode = false }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "DONE",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 10.sp,
                            color = palette.background
                        )
                    }
                }
            }

            // Main Multi-Page Home Pager
            HorizontalPager(
                state = homePagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { pageIndex ->
                val pageConfig = settings.pageConfigFor(pageIndex)
                val cols = (pageConfig.columnsOverride ?: settings.grid.homeColumns).coerceIn(2, 12)
                val rows = (pageConfig.rowsOverride ?: settings.grid.homeRows).coerceIn(2, 12)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pagerTransitionDeferred(
                            pageOffsetProvider = {
                                (homePagerState.currentPage - pageIndex) +
                                    homePagerState.currentPageOffsetFraction
                            },
                            style = settings.physics.pageTransition,
                            intensity = settings.physics.pageTransitionIntensity
                        )
                        .pointerInput(editLayoutMode, settings.gestures) {
                            detectTapGestures(
                                onTap = {
                                    if (editLayoutMode) editLayoutMode = false
                                },
                                onLongPress = {
                                    enterEditMode()
                                },
                                onDoubleTap = {
                                    NothingHaptics.performConfirm(context, settings.physics)
                                    onTriggerGesture(settings.gestures.doubleTap, cycleDock)
                                }
                            )
                        }
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = settings.grid.marginDp.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Per-page header pill (Page Name + Edit Layout shortcut)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (editLayoutMode) editLayoutMode = false },
                                onLongClick = { enterEditMode() }
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pageConfig.name.uppercase()}  •  ${cols}X$rows",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = palette.textSecondary
                        )
                        Text(
                            text = if (editLayoutMode) "RESIZE WIDGETS BELOW  •  TAP DONE" else "HOLD TO CUSTOMIZE",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 9.5.sp,
                            color = if (editLayoutMode) palette.accent else palette.textSecondary.copy(alpha = 0.65f),
                            modifier = Modifier.clickable { editLayoutMode = !editLayoutMode }
                        )
                    }

                    // Widget Stack(s) for this page — supports Full-Width or Half-Width side-by-side!
                    val pageSlots = remember(settings.widgetStacks, pageIndex) {
                        settings.widgetStacks.filter { it.pageIndex == pageIndex }
                    }
                    val slotRows = remember(pageSlots) {
                        val grouped = mutableListOf<List<WidgetStackSlot>>()
                        var i = 0
                        while (i < pageSlots.size) {
                            val current = pageSlots[i]
                            if (current.widthFraction <= 0.55f &&
                                i + 1 < pageSlots.size &&
                                pageSlots[i + 1].widthFraction <= 0.55f
                            ) {
                                grouped.add(listOf(current, pageSlots[i + 1]))
                                i += 2
                            } else {
                                grouped.add(listOf(current))
                                i += 1
                            }
                        }
                        grouped
                    }
                    slotRows.forEach { rowSlots ->
                        if (rowSlots.size == 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                rowSlots.forEach { slot ->
                                    InteractiveWidgetStackCard(
                                        slot = slot,
                                        settings = settings,
                                        widgetHostManager = widgetHostManager,
                                        stepsToday = stepsToday,
                                        stepSensorAvailable = stepSensorAvailable,
                                        hasStepPermission = hasStepPermission,
                                        stepHistory = stepHistory,
                                        weatherState = weatherState,
                                        telemetry = telemetry,
                                        screenTimeVelocity = screenTimeVelocity,
                                        contextEngine = contextEngine,
                                        onRequestStepSync = onRequestStepSync,
                                        onCycleTimeFormat = onCycleTimeFormat,
                                        onRefreshWeather = onRefreshWeather,
                                        onCycleYearMode = onCycleYearMode,
                                        onToggleDeepFocus = onToggleDeepFocus,
                                        onOpenUsageAccess = onOpenUsageAccess,
                                        onOpenWidgetSettings = { onOpenSettings(5) },
                                        onOpenStackEditor = onOpenStackEditor,
                                        editMode = editLayoutMode,
                                        onLongPressSlot = { s ->
                                            enterEditMode()
                                            onOpenStackEditor(s)
                                        },
                                        onUpdateSlot = { tr -> onUpdateWidgetSlot(slot.id, tr) },
                                        onDeleteSlot = { onDeleteWidgetSlot(slot.id) },
                                        mediaState = mediaState,
                                        hasBluetoothPermission = hasBluetoothPermission,
                                        onRequestBluetoothPermission = onRequestBluetoothPermission,
                                        animationsPaused = wallpaperPaused || homePagerState.currentPage != pageIndex,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        } else {
                            val slot = rowSlots.first()
                            val singleWidthMod = if (slot.widthFraction <= 0.55f) {
                                Modifier.fillMaxWidth(0.5f)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                            InteractiveWidgetStackCard(
                                slot = slot,
                                settings = settings,
                                widgetHostManager = widgetHostManager,
                                stepsToday = stepsToday,
                                stepSensorAvailable = stepSensorAvailable,
                                hasStepPermission = hasStepPermission,
                                stepHistory = stepHistory,
                                weatherState = weatherState,
                                telemetry = telemetry,
                                screenTimeVelocity = screenTimeVelocity,
                                contextEngine = contextEngine,
                                onRequestStepSync = onRequestStepSync,
                                onCycleTimeFormat = onCycleTimeFormat,
                                onRefreshWeather = onRefreshWeather,
                                onCycleYearMode = onCycleYearMode,
                                onToggleDeepFocus = onToggleDeepFocus,
                                onOpenUsageAccess = onOpenUsageAccess,
                                onOpenWidgetSettings = { onOpenSettings(5) },
                                onOpenStackEditor = onOpenStackEditor,
                                editMode = editLayoutMode,
                                onLongPressSlot = { s ->
                                    enterEditMode()
                                    onOpenStackEditor(s)
                                },
                                onUpdateSlot = { tr -> onUpdateWidgetSlot(slot.id, tr) },
                                onDeleteSlot = { onDeleteWidgetSlot(slot.id) },
                                mediaState = mediaState,
                                hasBluetoothPermission = hasBluetoothPermission,
                                onRequestBluetoothPermission = onRequestBluetoothPermission,
                                animationsPaused = wallpaperPaused || homePagerState.currentPage != pageIndex,
                                modifier = singleWidthMod
                            )
                        }
                    }

                    // Dynamic Most Used Apps Section on Page 0
                    if (pageIndex == 0 && settings.grid.showMostUsedSection) {
                        val top8 = remember(mostUsedApps, allApps, settings.appCustomizations, contextHiddenPackages) {
                            val source = if (mostUsedApps.isNotEmpty()) mostUsedApps else allApps
                            source.filter {
                                val c = settings.appCustomizations[it.packageName]
                                c?.hidden != true && c?.lockedInVault != true && it.packageName !in contextHiddenPackages
                            }.take(8)
                        }
                        if (top8.isNotEmpty()) {
                            NothingSurfaceCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { enterEditMode() }
                                    ),
                                cornerRadius = 20.dp,
                                backgroundColor = palette.surface.copy(alpha = 0.68f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "MOST USED APPS",
                                            fontFamily = NothingTypographyEngine.ndot55Family,
                                            fontSize = 11.sp,
                                            letterSpacing = 1.2.sp,
                                            color = palette.accent
                                        )
                                        Text(
                                            text = "TOP ${top8.size}",
                                            fontFamily = NothingTypographyEngine.ntype82Family,
                                            fontSize = 10.sp,
                                            color = palette.textSecondary
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    top8.chunked(cols).forEach { rowApps ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(settings.grid.horizontalPaddingDp.dp)
                                        ) {
                                            rowApps.forEach { app ->
                                                HomeAppTile(
                                                    app = app,
                                                    settings = settings,
                                                    appIndexer = appIndexer,
                                                    iconVersion = iconVersion,
                                                    badgeCount = badgeCounts[app.packageName] ?: 0,
                                                    labelStyle = homeLabelStyle,
                                                    onClick = { onAppClick(app) },
                                                    onLongClick = { onAppLongPress(app) },
                                                    onSwipeUp = {
                                                        onTriggerGesture(settings.gestures.iconSwipeUp, cycleDock)
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            repeat((cols - rowApps.size).coerceAtLeast(0)) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Folders on this page
                    val pageFolders = settings.folders.filter { it.pageIndex == pageIndex }
                    if (pageFolders.isNotEmpty()) {
                        pageFolders.chunked(cols).forEach { folderRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(settings.grid.horizontalPaddingDp.dp)
                            ) {
                                folderRow.forEach { folder ->
                                    FolderTile(
                                        folder = folder,
                                        appMap = appMap,
                                        settings = settings,
                                        appIndexer = appIndexer,
                                        iconVersion = iconVersion,
                                        labelStyle = homeLabelStyle,
                                        onClick = {
                                            if (settings.folderConfig.openMode == FolderOpenMode.EXPAND_INLINE) {
                                                inlineExpandedFolderId =
                                                    if (inlineExpandedFolderId == folder.id) null else folder.id
                                            } else {
                                                activeFolder = folder
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat((cols - folderRow.size).coerceAtLeast(0)) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }

                        val expandedFolder = pageFolders.firstOrNull { it.id == inlineExpandedFolderId }
                        AnimatedVisibility(visible = expandedFolder != null) {
                            expandedFolder?.let { folder ->
                                NothingSurfaceCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    cornerRadius = 20.dp,
                                    borderWidth = settings.theme.folderBorderDp.dp,
                                    backgroundColor = palette.folderBg
                                ) {
                                    FolderGridContent(
                                        folder = folder,
                                        appMap = appMap,
                                        settings = settings,
                                        appIndexer = appIndexer,
                                        iconVersion = iconVersion,
                                        badgeCounts = badgeCounts,
                                        onAppClick = onAppClick,
                                        onAppLongPress = onAppLongPress,
                                        onClose = { inlineExpandedFolderId = null }
                                    )
                                }
                            }
                        }
                    }

                    // Pinned Home Apps Grid for this page
                    val pinnedPkgs = settings.homeAppPages.getOrNull(pageIndex).orEmpty()
                    val pinnedApps = pinnedPkgs.mapNotNull { appMap[it] }
                        .filter { it.packageName !in contextHiddenPackages }
                    val maxSlots = (cols * rows)
                    val displayApps = pinnedApps.take(maxSlots)

                    if (displayApps.isNotEmpty()) {
                        displayApps.chunked(cols).forEach { rowApps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(settings.grid.horizontalPaddingDp.dp)
                            ) {
                                rowApps.forEach { app ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        HomeAppTile(
                                            app = app,
                                            settings = settings,
                                            appIndexer = appIndexer,
                                            iconVersion = iconVersion,
                                            badgeCount = badgeCounts[app.packageName] ?: 0,
                                            labelStyle = homeLabelStyle,
                                            onClick = { onAppClick(app) },
                                            onLongClick = { onAppLongPress(app) },
                                            onSwipeUp = {
                                                onTriggerGesture(settings.gestures.iconSwipeUp, cycleDock)
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (editLayoutMode) {
                                            Row(
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.surface)
                                                        .clickable { onMoveHomeApp(pageIndex, app.packageName, -1) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Rounded.ChevronLeft, "Move Left", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(Modifier.width(4.dp))
                                                val targetPage = (pageIndex + 1) % pageCount
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(palette.accent.copy(alpha = 0.22f))
                                                        .clickable { onMoveHomeAppToPage(pageIndex, app.packageName, targetPage) }
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "P${targetPage + 1}",
                                                        fontFamily = NothingTypographyEngine.ntype82Family,
                                                        fontSize = 8.sp,
                                                        color = palette.accent
                                                    )
                                                }
                                                Spacer(Modifier.width(4.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(palette.surface)
                                                        .clickable { onMoveHomeApp(pageIndex, app.packageName, 1) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Rounded.ChevronRight, "Move Right", tint = palette.textPrimary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                                repeat((cols - rowApps.size).coerceAtLeast(0)) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else if (pageIndex > 0) {
                        NothingSurfaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDrawer() },
                            cornerRadius = 18.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "PAGE ${pageIndex + 1} READY",
                                    fontFamily = NothingTypographyEngine.ndot55Family,
                                    fontSize = 13.sp,
                                    color = palette.accent
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Open App Drawer and long-press any app to pin it here.",
                                    fontSize = 12.sp,
                                    color = palette.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (pageIndex == pageCount - 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                                    .clickable { onAddPage() }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Add, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ADD PAGE", fontFamily = NothingTypographyEngine.ntype82Family, fontSize = 10.sp, color = palette.textSecondary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Home Page Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pageCount) { idx ->
                    val active = idx == homePagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(if (active) 14.dp else 5.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(if (active) palette.accent else palette.borderSubtle)
                    )
                }
            }

            if (settings.searchConfig.position == SearchBarPosition.FLOATING_PILL ||
                settings.searchConfig.position == SearchBarPosition.BOTTOM
            ) {
                HomeInlineSearchPill(
                    placeholder = settings.searchConfig.placeholderText,
                    settings = settings,
                    onClick = onOpenSearch,
                    modifier = Modifier.padding(horizontal = settings.grid.marginDp.dp, vertical = 4.dp)
                )
            }

            // Multi-Page Swipeable Bottom Dock (up to 3 pages x 7 slots)
            NothingSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = settings.grid.marginDp.dp, vertical = 6.dp),
                cornerRadius = 24.dp,
                backgroundColor = palette.surface.copy(alpha = 0.86f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (settings.searchConfig.position == SearchBarPosition.DOCK_INTEGRATED) {
                        HomeInlineSearchPill(
                            placeholder = settings.searchConfig.placeholderText,
                            settings = settings,
                            onClick = onOpenSearch,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    HorizontalPager(
                        state = dockPagerState,
                        modifier = Modifier.fillMaxWidth()
                    ) { dPage ->
                        val dockSlots = settings.grid.dockSlotsPerPage.coerceIn(3, 7)
                        val dockPkgs = settings.dockPages.getOrNull(dPage).orEmpty()
                        val fallbackApps = remember(allApps, dPage) {
                            allApps.drop(dPage * dockSlots).take((dockSlots - 1).coerceAtLeast(2))
                        }
                        val appsInDock = if (dockPkgs.isNotEmpty()) {
                            dockPkgs.mapNotNull { appMap[it] }.take(dockSlots)
                        } else {
                            fallbackApps
                        }

                        val totalNotifCount = remember(badgeCounts) { badgeCounts.values.sum() }
                        val dockMiniChips = if (dPage == 0) settings.grid.dockMiniChips.take(2) else emptyList()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            appsInDock.forEach { app ->
                                val custom = settings.appCustomizations[app.packageName]
                                val bitmap = appIndexer.rememberAppIcon(app.packageName, app.activityName, iconVersion)
                                val dockInteraction = remember(app.packageName) { MutableInteractionSource() }
                                val dockPressed by dockInteraction.collectIsPressedAsState()
                                val dockScale by animateFloatAsState(
                                    targetValue = if (dockPressed) settings.physics.iconPressBounce.coerceIn(0.8f, 1.0f) else 1f,
                                    animationSpec = LauncherPhysicsEngine.physicsSpring(settings.physics),
                                    label = "dockPressBounce"
                                )
                                Box(
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .then(
                                            if (dockScale != 1f) {
                                                Modifier.graphicsLayer {
                                                    scaleX = dockScale
                                                    scaleY = dockScale
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .combinedClickable(
                                            interactionSource = dockInteraction,
                                            indication = LocalIndication.current,
                                            onClick = { onAppClick(app) },
                                            onLongClick = { onAppLongPress(app) }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    MaskedAppIcon(
                                        iconBitmap = bitmap,
                                        maskConfig = settings.iconMask,
                                        baseSize = 46.dp,
                                        customImageUri = custom?.customImageUri,
                                        customGlyphVector = NothingGlyphCatalog.resolve(custom?.customGlyphId),
                                        monochromeOverride = custom?.monochromeOverride,
                                        scaleOverride = custom?.iconScaleOverride,
                                        badgeCount = badgeCounts[app.packageName] ?: 0,
                                        badgeStyle = settings.badgeStyle,
                                        dimmedForFocus = settings.deepFocusActive
                                    )
                                }
                            }

                            dockMiniChips.forEach { chipType ->
                                DockMiniWidgetChip(
                                    chipType = chipType,
                                    settings = settings,
                                    telemetry = telemetry,
                                    stepsToday = stepsToday,
                                    totalNotificationCount = totalNotifCount,
                                    contextEngine = contextEngine,
                                    onToggleDeepFocus = onToggleDeepFocus,
                                    onOpenSettings = { onOpenSettings(2) }
                                )
                            }

                            // Drawer trigger slot in dock
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(palette.accent)
                                    .clickable { onOpenDrawer() }
                                    .testTag("open_app_drawer_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Apps, "All Apps", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    if (dockPageCount > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(dockPageCount) { idx ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (dockPagerState.currentPage == idx) 6.dp else 4.dp)
                                        .clip(CircleShape)
                                        .background(if (dockPagerState.currentPage == idx) palette.accent else palette.borderSubtle)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Folder Popup Card or Full-Screen Overlay
        activeFolder?.let { folder ->
            if (settings.folderConfig.openMode == FolderOpenMode.FULL_SCREEN) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.folderBg)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(20.dp)
                ) {
                    FolderGridContent(
                        folder = folder,
                        appMap = appMap,
                        settings = settings,
                        appIndexer = appIndexer,
                        iconVersion = iconVersion,
                        badgeCounts = badgeCounts,
                        onAppClick = {
                            activeFolder = null
                            onAppClick(it)
                        },
                        onAppLongPress = {
                            activeFolder = null
                            onAppLongPress(it)
                        },
                        onClose = { activeFolder = null }
                    )
                }
            } else {
                Dialog(onDismissRequest = { activeFolder = null }) {
                    NothingSurfaceCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 24.dp,
                        borderWidth = settings.theme.folderBorderDp.dp,
                        backgroundColor = palette.folderBg
                    ) {
                        FolderGridContent(
                            folder = folder,
                            appMap = appMap,
                            settings = settings,
                            appIndexer = appIndexer,
                            iconVersion = iconVersion,
                            badgeCounts = badgeCounts,
                            onAppClick = {
                                activeFolder = null
                                onAppClick(it)
                            },
                            onAppLongPress = {
                                activeFolder = null
                                onAppLongPress(it)
                            },
                            onClose = { activeFolder = null }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeInlineSearchPill(
    placeholder: String,
    settings: LauncherSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val shape = RoundedCornerShape(settings.searchConfig.cornerRadiusDp.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface.copy(alpha = settings.searchConfig.backgroundOpacity.coerceIn(0.2f, 1f)))
            .border(settings.searchConfig.borderWidthDp.dp, palette.borderSubtle, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("home_inline_search_pill"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = placeholder,
            style = NothingTypographyEngine.resolveTextStyle(settings.typographySearch).copy(
                color = palette.textSecondary,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppTile(
    app: IndexedApp,
    settings: LauncherSettings,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    badgeCount: Int,
    labelStyle: androidx.compose.ui.text.TextStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val custom = settings.appCustomizations[app.packageName]
    val label = remember(custom?.customLabel, app.label, settings.typographyHome.textCase) {
        NothingTypographyEngine.formatText(
            custom?.customLabel ?: app.label,
            settings.typographyHome.textCase
        )
    }
    val bitmap = appIndexer.rememberAppIcon(app.packageName, app.activityName, iconVersion)
    val interactionSource = remember(app.packageName) { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) settings.physics.iconPressBounce.coerceIn(0.8f, 1.0f) else 1f,
        animationSpec = LauncherPhysicsEngine.physicsSpring(settings.physics),
        label = "homeIconPressBounce"
    )

    Column(
        modifier = modifier
            .then(
                if (pressScale != 1f) {
                    Modifier.graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                } else {
                    Modifier
                }
            )
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .pointerInput(Unit) {
                var dy = 0f
                detectDragGestures(
                    onDragStart = { dy = 0f },
                    onDrag = { _, dragAmount -> dy += dragAmount.y },
                    onDragEnd = { if (abs(dy) > 65f && dy < 0f) onSwipeUp() }
                )
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MaskedAppIcon(
            iconBitmap = bitmap,
            maskConfig = settings.iconMask,
            baseSize = 48.dp,
            customImageUri = custom?.customImageUri,
            customGlyphVector = NothingGlyphCatalog.resolve(custom?.customGlyphId),
            monochromeOverride = custom?.monochromeOverride,
            scaleOverride = custom?.iconScaleOverride,
            badgeCount = badgeCount,
            badgeStyle = settings.badgeStyle,
            dimmedForFocus = settings.deepFocusActive
        )
        Spacer(Modifier.height(settings.iconMask.labelOffsetDp.dp))
        Text(
            text = label,
            style = labelStyle.copy(color = palette.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FolderTile(
    folder: LauncherFolder,
    appMap: Map<String, IndexedApp>,
    settings: LauncherSettings,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    labelStyle: androidx.compose.ui.text.TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val folderApps = folder.packageNames.mapNotNull { appMap[it] }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.folderBg)
                .border(settings.theme.folderBorderDp.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
                .padding(5.dp),
            contentAlignment = Alignment.Center
        ) {
            when (settings.folderConfig.coverStyle) {
                FolderCoverStyle.DOT_MATRIX_BADGE -> {
                    Text(
                        text = folder.name.take(3).uppercase(),
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 13.sp,
                        color = palette.accent
                    )
                }
                FolderCoverStyle.SINGLE_ICON -> {
                    val first = folderApps.firstOrNull()
                    val bmp = first?.let {
                        appIndexer.rememberAppIcon(it.packageName, it.activityName, iconVersion)
                    }
                    MaskedAppIcon(
                        iconBitmap = bmp,
                        maskConfig = settings.iconMask,
                        baseSize = 36.dp,
                        shapeOverride = settings.folderConfig.folderMaskOverride
                    )
                }
                FolderCoverStyle.GRID_2X2, FolderCoverStyle.GRID_3X3 -> {
                    val gridCols = if (settings.folderConfig.coverStyle == FolderCoverStyle.GRID_3X3) 3 else 2
                    val maxIcons = gridCols * gridCols
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        folderApps.take(maxIcons).chunked(gridCols).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                row.forEach { app ->
                                    val bmp = appIndexer.rememberAppIcon(app.packageName, app.activityName, iconVersion)
                                    MaskedAppIcon(
                                        iconBitmap = bmp,
                                        maskConfig = settings.iconMask,
                                        baseSize = if (gridCols == 3) 11.dp else 17.dp,
                                        shapeOverride = settings.folderConfig.folderMaskOverride
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(settings.iconMask.labelOffsetDp.dp))
        Text(
            text = NothingTypographyEngine.formatText(folder.name, settings.typographyHome.textCase),
            style = labelStyle.copy(color = palette.textPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FolderGridContent(
    folder: LauncherFolder,
    appMap: Map<String, IndexedApp>,
    settings: LauncherSettings,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    badgeCounts: Map<String, Int>,
    onAppClick: (IndexedApp) -> Unit,
    onAppLongPress: (IndexedApp) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalNothingPalette.current
    val folderApps = folder.packageNames.mapNotNull { appMap[it] }
    val labelStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyHome)

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = folder.name.uppercase(),
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 15.sp,
                color = palette.accent
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, "Close Folder", tint = palette.textPrimary)
            }
        }
        DotMatrixDivider()
        Spacer(Modifier.height(10.dp))
        val cols = settings.grid.folderColumns.coerceIn(2, 12)
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(settings.grid.horizontalPaddingDp.dp),
            verticalArrangement = Arrangement.spacedBy(settings.grid.verticalPaddingDp.dp),
            modifier = Modifier.height(240.dp)
        ) {
            items(folderApps, key = { it.packageName }) { app ->
                HomeAppTile(
                    app = app,
                    settings = settings,
                    appIndexer = appIndexer,
                    iconVersion = iconVersion,
                    badgeCount = badgeCounts[app.packageName] ?: 0,
                    labelStyle = labelStyle,
                    onClick = { onAppClick(app) },
                    onLongClick = { onAppLongPress(app) },
                    onSwipeUp = {}
                )
            }
        }
    }
}
