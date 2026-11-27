package com.example

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.animationphysics.LauncherPhysicsEngine
import com.example.animationphysics.ShakeGestureObserver
import com.example.appindexer.IndexedApp
import com.example.coreui.DotMatrixDivider
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSurfaceCard
import com.example.coreui.prewarmMaskedIcons
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.GestureAction
import com.example.settingsstore.model.WidgetStackSlot
import com.example.themeengine.DotLauncherTheme
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import com.example.ui.AmbientAodOverlay
import com.example.ui.NewsSpaceOverlay
import com.example.ui.BiometricVaultSheet
import com.example.ui.HomeScreenCanvas
import com.example.ui.LongPressAppMenuDialog
import com.example.ui.MindfulFrictionCountdownDialog
import com.example.ui.SettingsHubScreen
import com.example.universalsearch.UniversalSearchOverlay
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()
    private var isActivityResumed by mutableStateOf(true)

    private var pendingBindSlotId: String? = null
    private var pendingBindWidgetId: Int = -1

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val slotId = pendingBindSlotId
        val widgetId = pendingBindWidgetId
        if (result.resultCode == Activity.RESULT_OK && slotId != null && widgetId != -1) {
            attachBoundWidgetToSlot(slotId, widgetId)
        } else if (widgetId != -1) {
            viewModel.widgetHostManager.deleteWidgetId(widgetId)
        }
        pendingBindSlotId = null
        pendingBindWidgetId = -1
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        enableHighRefreshRate()
        viewModel.onLauncherResumed()
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableHighRefreshRate()
        }
    }

    private fun enableHighRefreshRate() {
        runCatching {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                this.display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return
            val current = display.mode
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
            if (best != null && best.modeId != current.modeId) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                }
            }
        }
    }

    override fun onDestroy() {
        viewModel.widgetHostManager.stopListening()
        super.onDestroy()
    }

    private fun handleBindSystemWidget(slot: WidgetStackSlot, provider: AppWidgetProviderInfo) {
        val widgetId = viewModel.widgetHostManager.allocateWidgetId()
        val allowed = viewModel.widgetHostManager.tryBindWidget(widgetId, provider)
        if (allowed) {
            attachBoundWidgetToSlot(slot.id, widgetId)
        } else {
            pendingBindSlotId = slot.id
            pendingBindWidgetId = widgetId
            val intent: Intent = viewModel.widgetHostManager.createBindIntent(widgetId, provider)
            bindWidgetLauncher.launch(intent)
        }
    }

    private fun attachBoundWidgetToSlot(slotId: String, appWidgetId: Int) {
        viewModel.updateSettings { cfg ->
            val updated = cfg.widgetStacks.map { slot ->
                if (slot.id != slotId) slot
                else slot.copy(stackedSystemWidgetIds = slot.stackedSystemWidgetIds + appWidgetId)
            }
            cfg.copy(widgetStacks = updated)
        }
        viewModel.showBanner("System widget added to stack")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        // Keep system widget host listening across app switches so returning home is instant
        viewModel.widgetHostManager.startListening()
        enableEdgeToEdge()
        setContent {
            // collectAsState keeps state warm while in background so pressing Home never reloads
            val settings by viewModel.settings.collectAsState()
            val allApps by viewModel.allApps.collectAsState()
            val mostUsedApps by viewModel.mostUsedApps.collectAsState()
            val iconVersion by viewModel.iconVersion.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val searchState by viewModel.searchState.collectAsState()
            val recentQueries by viewModel.recentQueries.collectAsState()
            val isSearchVisible by viewModel.isSearchVisible.collectAsState()
            val isSettingsVisible by viewModel.isSettingsVisible.collectAsState()
            val settingsInitialTab by viewModel.settingsInitialTab.collectAsState()
            val isVaultVisible by viewModel.isVaultVisible.collectAsState()
            val vaultUnlocked by viewModel.vaultUnlocked.collectAsState()
            val showPinPrompt by viewModel.showPinPrompt.collectAsState()
            val pendingFrictionApp by viewModel.pendingFrictionApp.collectAsState()
            val themePresets by viewModel.themePresets.collectAsState()
            val installedIconPacks by viewModel.installedIconPacks.collectAsState()
            val stepHistory by viewModel.stepHistory.collectAsState()
            val todaySteps by viewModel.todaySteps.collectAsState()
            val stepSensorAvailable by viewModel.stepSensorAvailable.collectAsState()
            val weatherState by viewModel.weatherState.collectAsState()
            val telemetry by viewModel.telemetry.collectAsState()
            val screenTimeVelocity by viewModel.screenTimeVelocity.collectAsState()
            val statusBanner by viewModel.statusBanner.collectAsState()
            val badgeCounts by viewModel.badgeCounts.collectAsState()
            val mediaState by viewModel.mediaState.collectAsState()
            val contextHiddenPackages by viewModel.contextHiddenPackages.collectAsState()
            val isAmbientAodVisible by viewModel.isAmbientAodVisible.collectAsState()
            val isNewsVisible by viewModel.isNewsVisible.collectAsState()
            val allAppsRequestTick by viewModel.allAppsRequestTick.collectAsState()

            val anyOverlayOpen = isSearchVisible || isSettingsVisible ||
                isVaultVisible || isAmbientAodVisible || isNewsVisible

            // Immersive full-screen: hide the system status bar (battery, Wi-Fi, notification icons)
            // while still letting the user reveal it with an edge swipe.
            LaunchedEffect(settings.fullScreenHideStatusBar) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (settings.fullScreenHideStatusBar) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            }

            var btPermissionTick by remember { mutableStateOf(0) }
            val hasBtPermission = remember(btPermissionTick) {
                viewModel.contextEngine.hasBluetoothConnectPermission()
            }
            val btPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                btPermissionTick++
                viewModel.contextEngine.refreshTelemetry()
            }
            val requestBtPermission = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }

            var longPressedApp by remember { mutableStateOf<IndexedApp?>(null) }
            var editingStackSlot by remember { mutableStateOf<WidgetStackSlot?>(null) }

            val handleAppClick = remember(viewModel) { { app: IndexedApp -> viewModel.onAppClicked(app) } }
            val handleAppLongPress = remember { { app: IndexedApp -> longPressedApp = app } }
            val handleOpenAllApps = remember(viewModel) { { viewModel.requestAllAppsFeed() } }
            val handleOpenSearch = remember(viewModel) { { viewModel.openSearch() } }
            val handleOpenSettingsZero = remember(viewModel) { { viewModel.openSettings(0) } }
            val handleOpenVault = remember(viewModel) { { viewModel.requestOpenVault() } }

            DotLauncherTheme(settings = settings) {
                val palette = LocalNothingPalette.current

                // Pre-bake all installed app icons into flat masked GPU textures in background
                LaunchedEffect(iconVersion, settings.iconMask, settings.deepFocusActive, palette.accent) {
                    prewarmMaskedIcons(
                        bitmaps = viewModel.appIndexer.getAllCachedBitmaps(),
                        maskConfig = settings.iconMask,
                        deepFocusActive = settings.deepFocusActive,
                        accentColor = palette.accent
                    )
                }

                ShakeGestureObserver(
                    enabled = isActivityResumed && !anyOverlayOpen &&
                        settings.gestures.deviceShake != GestureAction.NONE,
                    onShakeDetected = {
                        viewModel.executeGestureAction(settings.gestures.deviceShake)
                    }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    HomeScreenCanvas(
                        settings = settings,
                        allApps = allApps,
                        mostUsedApps = mostUsedApps,
                        appIndexer = viewModel.appIndexer,
                        iconVersion = iconVersion,
                        widgetHostManager = viewModel.widgetHostManager,
                        stepsToday = todaySteps,
                        stepSensorAvailable = stepSensorAvailable,
                        hasStepPermission = viewModel.stepRepo.hasActivityRecognitionPermission(),
                        stepHistory = stepHistory,
                        weatherState = weatherState,
                        telemetry = telemetry,
                        screenTimeVelocity = screenTimeVelocity,
                        contextEngine = viewModel.contextEngine,
                        badgeCounts = badgeCounts,
                        mediaState = mediaState,
                        hasBluetoothPermission = hasBtPermission,
                        contextHiddenPackages = contextHiddenPackages,
                        onRequestBluetoothPermission = requestBtPermission,
                        onAppClick = handleAppClick,
                        onAppLongPress = handleAppLongPress,
                        allAppsRequestTick = allAppsRequestTick,
                        onOpenSearch = handleOpenSearch,
                        onOpenSettings = { tab -> viewModel.openSettings(tab) },
                        onOpenAmbientAod = { viewModel.openAmbientAod() },
                        onTriggerGesture = { act, cycleDock -> viewModel.executeGestureAction(act, cycleDock) },
                        onRequestStepSync = { viewModel.startStepTracking() },
                        onCycleTimeFormat = { viewModel.cycleTimeWidgetFormat() },
                        onRefreshWeather = { viewModel.refreshWeather() },
                        onCycleYearMode = { viewModel.cycleYearWidgetMode() },
                        onToggleDeepFocus = { viewModel.toggleDeepFocus() },
                        onOpenUsageAccess = { viewModel.appIndexer.openUsageStatsSettings() },
                        onAddPage = { viewModel.addHomePage() },
                        onRemovePage = { page -> viewModel.removeHomePage(page) },
                        onAddWidgetToPage = { page, type -> viewModel.addCustomWidgetToPage(page, type) },
                        onUpdateWidgetConfig = { type, tr -> viewModel.updateWidgetConfig(type, tr) },
                        onSaveQuickNote = { text -> viewModel.saveQuickNote(text) },
                        onOpenNewsSpace = { viewModel.openNewsSpace() },
                        weatherRepo = viewModel.weatherRepo,
                        onMoveHomeApp = { page, pkg, delta -> viewModel.moveHomeApp(page, pkg, delta) },
                        onMoveHomeAppToPage = { fromPage, pkg, toPage -> viewModel.moveHomeAppToPage(fromPage, pkg, toPage) },
                        onOpenStackEditor = { slot -> editingStackSlot = slot },
                        wallpaperPaused = anyOverlayOpen || !isActivityResumed,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onUpdateWidgetSlot = { id, tr -> viewModel.updateWidgetSlot(id, tr) },
                        onAddWidgetSlot = { page -> viewModel.addWidgetSlot(page) },
                        onDeleteWidgetSlot = { id -> viewModel.removeWidgetSlot(id) },
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                val coveredByOpaqueOverlay =
                                    isSettingsVisible || isVaultVisible || isAmbientAodVisible || isNewsVisible
                                if (!coveredByOpaqueOverlay) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        }
                    )

                    // Universal Multi-Source Search Overlay
                    AnimatedVisibility(
                        visible = isSearchVisible,
                        enter = fadeIn(LauncherPhysicsEngine.launchTween(settings.physics)),
                        exit = fadeOut(LauncherPhysicsEngine.launchTween(settings.physics))
                    ) {
                        UniversalSearchOverlay(
                            query = searchQuery,
                            onQueryChange = { viewModel.setSearchQuery(it) },
                            searchState = searchState,
                            recentQueries = recentQueries,
                            settings = settings,
                            appIndexer = viewModel.appIndexer,
                            searchEngine = viewModel.searchEngine,
                            onLaunchApp = { viewModel.onAppClicked(it) },
                            onLaunchShortcut = { viewModel.launchShortcut(it) },
                            onOpenLauncherSettingsTab = { tab -> viewModel.openSettings(tab) },
                            onAddSafFolderUri = { uri -> viewModel.addSafFolderUri(uri) },
                            onToggleIncognito = {
                                viewModel.updateSettings {
                                    it.copy(searchConfig = it.searchConfig.copy(incognitoMode = !it.searchConfig.incognitoMode))
                                }
                            },
                            onClearHistory = { viewModel.clearSearchHistory() },
                            onCommitQuery = { viewModel.commitSearchQuery(it) },
                            onToggleTorch = {
                                viewModel.contextEngine.triggerQuickToggle(
                                    com.example.settingsstore.model.QuickToggleItem.FLASHLIGHT
                                )
                            },
                            onDismiss = { viewModel.closeSearch() }
                        )
                    }

                    // Settings Hub Overlay
                    AnimatedVisibility(
                        visible = isSettingsVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 3 },
                            animationSpec = LauncherPhysicsEngine.physicsSpring(settings.physics)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it / 3 },
                            animationSpec = LauncherPhysicsEngine.physicsSpring(settings.physics)
                        ) + fadeOut()
                    ) {
                        SettingsHubScreen(
                            initialTab = settingsInitialTab,
                            settings = settings,
                            allApps = allApps,
                            appIndexer = viewModel.appIndexer,
                            themePresets = themePresets,
                            installedIconPacks = installedIconPacks,
                            settingsRepo = viewModel.settingsRepo,
                            widgetHostManager = viewModel.widgetHostManager,
                            telemetry = telemetry,
                            hasBluetoothPermission = hasBtPermission,
                            onRequestBluetoothPermission = requestBtPermission,
                            onUpdateSettings = { viewModel.updateSettings(it) },
                            onSavePreset = { viewModel.saveCustomPreset(it) },
                            onApplyPreset = { viewModel.applyThemePreset(it) },
                            onDeletePreset = { viewModel.deleteThemePreset(it) },
                            onAddCustomSearchEngine = { n, u -> viewModel.addCustomSearchEngine(n, u) },
                            onClearClipboardHistory = { viewModel.clearClipboardHistory() },
                            onToggleDockMiniChip = { chip -> viewModel.toggleDockMiniChip(chip) },
                            onUpdatePageConfig = { idx, tr -> viewModel.updateHomePageConfig(idx, tr) },
                            onUpsertContextRule = { rule -> viewModel.upsertContextRule(rule) },
                            onDeleteContextRule = { id -> viewModel.deleteContextRule(id) },
                            onImportBackup = { payload -> viewModel.importBackupPayload(payload) },
                            onOpenVault = { viewModel.requestOpenVault() },
                            onBindSystemWidget = { slot, info -> handleBindSystemWidget(slot, info) },
                            onDismiss = { viewModel.closeSettings() }
                        )
                    }

                    // Always-On Ambient Desk Clock Overlay
                    AnimatedVisibility(
                        visible = isAmbientAodVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        AmbientAodOverlay(
                            settings = settings,
                            telemetry = telemetry,
                            mediaState = mediaState,
                            velocity = screenTimeVelocity,
                            totalNotificationCount = badgeCounts.values.sum(),
                            onDismiss = { viewModel.closeAmbientAod() }
                        )
                    }

                    // Dedicated News Space (feed + headlines)
                    AnimatedVisibility(
                        visible = isNewsVisible,
                        enter = slideInVertically(initialOffsetY = { it / 4 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 4 }) + fadeOut()
                    ) {
                        NewsSpaceOverlay(
                            settings = settings,
                            onDismiss = { viewModel.closeNewsSpace() }
                        )
                    }

                    // Biometric App Vault Overlay
                    AnimatedVisibility(
                        visible = isVaultVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        BiometricVaultSheet(
                            apps = allApps,
                            settings = settings,
                            appIndexer = viewModel.appIndexer,
                            vaultUnlocked = vaultUnlocked,
                            showPinPrompt = showPinPrompt,
                            onSetUnlocked = { viewModel.setVaultUnlocked(it) },
                            onSetShowPinPrompt = { viewModel.setShowPinPrompt(it) },
                            onLaunchApp = { viewModel.launchAppImmediately(it) },
                            onUnhideOrUnlockApp = { pkg ->
                                viewModel.updateAppCustomization(pkg) {
                                    it.copy(hidden = false, lockedInVault = false)
                                }
                            },
                            onDismiss = { viewModel.closeVault() }
                        )
                    }

                    // Long-press popup menu
                    longPressedApp?.let { appItem ->
                        LongPressAppMenuDialog(
                            app = appItem,
                            iconBitmap = viewModel.appIndexer.getCachedIcon(appItem.packageName, appItem.activityName),
                            settings = settings,
                            onDismiss = { longPressedApp = null },
                            onSaveCustomization = { transform ->
                                viewModel.updateAppCustomization(appItem.packageName, transform)
                            },
                            onToggleHome = { viewModel.toggleAppOnHomePage(appItem.packageName, 0) },
                            onToggleDock = { viewModel.toggleAppInDock(appItem.packageName, 0) },
                            onAddToFolder = { folderName -> viewModel.addAppToFolder(folderName, appItem.packageName, 0) },
                            onOpenAppInfo = { viewModel.appIndexer.openAppInfo(appItem.packageName) },
                            onLaunchSplitScreen = { viewModel.onAppClicked(appItem, splitScreen = true) },
                            onUninstall = { viewModel.appIndexer.uninstallApp(appItem.packageName) }
                        )
                    }

                    // Mindful Friction Timer countdown modal
                    pendingFrictionApp?.let { appItem ->
                        val custom = settings.appCustomizations[appItem.packageName]
                        MindfulFrictionCountdownDialog(
                            app = appItem,
                            frictionSeconds = custom?.frictionSeconds ?: 3,
                            dailyLimitMinutes = custom?.dailyLimitMinutes ?: 0,
                            onProceedLaunch = { viewModel.launchAppImmediately(appItem) },
                            onCancel = { viewModel.dismissFrictionTimer() }
                        )
                    }

                    // Widget Size & Stack Quick Editor (opened via long-press on any widget or stack pill)
                    editingStackSlot?.let { slot ->
                        val latestSlot = settings.widgetStacks.firstOrNull { it.id == slot.id }
                        if (latestSlot == null) {
                            editingStackSlot = null
                        } else {
                            WidgetStackEditorDialog(
                                slot = latestSlot,
                                onUpdateSlot = { transform ->
                                    viewModel.updateWidgetSlot(latestSlot.id, transform)
                                },
                                onToggleWidget = { wt ->
                                    viewModel.updateWidgetSlot(latestSlot.id) { s ->
                                        val list = s.stackedCustomWidgets.toMutableList()
                                        if (wt in list) list.remove(wt) else list.add(wt)
                                        s.copy(stackedCustomWidgets = list)
                                    }
                                },
                                onDeleteSlot = {
                                    viewModel.removeWidgetSlot(latestSlot.id)
                                    editingStackSlot = null
                                },
                                onDismiss = { editingStackSlot = null }
                            )
                        }
                    }

                    // Status toast banner
                    statusBanner?.let { msg ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 96.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(palette.accent)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = msg,
                                fontFamily = NothingTypographyEngine.ntype82Family,
                                fontSize = 11.5.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetStackEditorDialog(
    slot: WidgetStackSlot,
    onUpdateSlot: ((WidgetStackSlot) -> WidgetStackSlot) -> Unit,
    onToggleWidget: (CustomWidgetType) -> Unit,
    onDeleteSlot: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNothingPalette.current
    val currentHeight = slot.heightDp.coerceIn(96, 340)
    val currentScale = slot.contentScale.coerceIn(0.7f, 1.45f)
    val isHalfWidth = slot.widthFraction <= 0.55f

    Dialog(onDismissRequest = onDismiss) {
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "RESIZE & CUSTOMIZE WIDGET",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 14.sp,
                    color = palette.accent
                )

                // Quick Size Presets (Small / Medium / Large / XL)
                Text(
                    text = "Widget Size Presets",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple("Small", 112, 0.84f),
                        Triple("Medium", 156, 1.0f),
                        Triple("Large", 216, 1.12f),
                        Triple("XL", 280, 1.25f)
                    ).forEach { (label, h, sc) ->
                        NothingPillChip(
                            label = label,
                            selected = kotlin.math.abs(currentHeight - h) <= 14,
                            onClick = { onUpdateSlot { it.copy(heightDp = h, contentScale = sc) } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Exact Height Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Widget Height", fontSize = 12.sp, color = palette.textPrimary)
                    Text("${currentHeight}dp", fontSize = 12.sp, color = palette.accent)
                }
                Slider(
                    value = currentHeight.toFloat(),
                    onValueChange = { v -> onUpdateSlot { it.copy(heightDp = v.roundToInt()) } },
                    valueRange = 96f..340f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent
                    )
                )

                // Width Span: Full vs Half (side-by-side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NothingPillChip(
                        label = "Full Width",
                        selected = !isHalfWidth,
                        onClick = { onUpdateSlot { it.copy(widthFraction = 1.0f) } },
                        modifier = Modifier.weight(1f)
                    )
                    NothingPillChip(
                        label = "Half Width (1/2)",
                        selected = isHalfWidth,
                        onClick = { onUpdateSlot { it.copy(widthFraction = 0.5f) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Content / Text Scale Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Text & Content Scale", fontSize = 12.sp, color = palette.textPrimary)
                    Text("${(currentScale * 100).roundToInt()}%", fontSize = 12.sp, color = palette.accent)
                }
                Slider(
                    value = currentScale,
                    onValueChange = { v -> onUpdateSlot { it.copy(contentScale = v) } },
                    valueRange = 0.72f..1.42f,
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent
                    )
                )

                DotMatrixDivider()

                Text(
                    text = "STACKED WIDGETS (SWIPE UP/DOWN TO CYCLE)",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 12.sp,
                    color = palette.accent
                )
                CustomWidgetType.entries.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { wt ->
                            NothingPillChip(
                                label = wt.displayName,
                                selected = wt in slot.stackedCustomWidgets,
                                onClick = { onToggleWidget(wt) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDeleteSlot) {
                        Text("Remove Block", color = palette.accent)
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = palette.textPrimary)
                    }
                }
            }
        }
    }
}
