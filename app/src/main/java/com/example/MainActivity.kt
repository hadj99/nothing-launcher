package com.example

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animationphysics.LauncherPhysicsEngine
import com.example.animationphysics.ShakeGestureObserver
import com.example.appindexer.IndexedApp
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSurfaceCard
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.GestureAction
import com.example.settingsstore.model.WidgetStackSlot
import com.example.themeengine.DotLauncherTheme
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import com.example.ui.AmbientAodOverlay
import com.example.ui.AppDrawerSheet
import com.example.ui.BiometricVaultSheet
import com.example.ui.HomeScreenCanvas
import com.example.ui.LongPressAppMenuDialog
import com.example.ui.MindfulFrictionCountdownDialog
import com.example.ui.SettingsHubScreen
import com.example.universalsearch.UniversalSearchOverlay

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

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

    override fun onStart() {
        super.onStart()
        viewModel.widgetHostManager.startListening()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAppsAndStats()
    }

    override fun onStop() {
        super.onStop()
        viewModel.widgetHostManager.stopListening()
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
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val allApps by viewModel.allApps.collectAsStateWithLifecycle()
            val mostUsedApps by viewModel.mostUsedApps.collectAsStateWithLifecycle()
            val iconVersion by viewModel.iconVersion.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val searchState by viewModel.searchState.collectAsStateWithLifecycle()
            val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
            val isSearchVisible by viewModel.isSearchVisible.collectAsStateWithLifecycle()
            val isDrawerVisible by viewModel.isDrawerVisible.collectAsStateWithLifecycle()
            val isSettingsVisible by viewModel.isSettingsVisible.collectAsStateWithLifecycle()
            val settingsInitialTab by viewModel.settingsInitialTab.collectAsStateWithLifecycle()
            val isVaultVisible by viewModel.isVaultVisible.collectAsStateWithLifecycle()
            val vaultUnlocked by viewModel.vaultUnlocked.collectAsStateWithLifecycle()
            val showPinPrompt by viewModel.showPinPrompt.collectAsStateWithLifecycle()
            val pendingFrictionApp by viewModel.pendingFrictionApp.collectAsStateWithLifecycle()
            val themePresets by viewModel.themePresets.collectAsStateWithLifecycle()
            val installedIconPacks by viewModel.installedIconPacks.collectAsStateWithLifecycle()
            val stepHistory by viewModel.stepHistory.collectAsStateWithLifecycle()
            val todaySteps by viewModel.todaySteps.collectAsStateWithLifecycle()
            val stepSensorAvailable by viewModel.stepSensorAvailable.collectAsStateWithLifecycle()
            val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
            val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
            val screenTimeVelocity by viewModel.screenTimeVelocity.collectAsStateWithLifecycle()
            val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
            val badgeCounts by viewModel.badgeCounts.collectAsStateWithLifecycle()
            val mediaState by viewModel.mediaState.collectAsStateWithLifecycle()
            val contextHiddenPackages by viewModel.contextHiddenPackages.collectAsStateWithLifecycle()
            val isAmbientAodVisible by viewModel.isAmbientAodVisible.collectAsStateWithLifecycle()

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

            DotLauncherTheme(settings = settings) {
                val palette = LocalNothingPalette.current

                ShakeGestureObserver(
                    enabled = settings.gestures.deviceShake != GestureAction.NONE,
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
                        onAppClick = { viewModel.onAppClicked(it) },
                        onAppLongPress = { longPressedApp = it },
                        onOpenDrawer = { viewModel.openDrawer() },
                        onOpenSearch = { viewModel.openSearch() },
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
                        onMoveHomeApp = { page, pkg, delta -> viewModel.moveHomeApp(page, pkg, delta) },
                        onMoveHomeAppToPage = { fromPage, pkg, toPage -> viewModel.moveHomeAppToPage(fromPage, pkg, toPage) },
                        onOpenStackEditor = { slot -> editingStackSlot = slot }
                    )

                    // App Drawer Overlay
                    AnimatedVisibility(
                        visible = isDrawerVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = LauncherPhysicsEngine.physicsSpring(settings.physics)
                        ) + fadeIn(LauncherPhysicsEngine.launchTween(settings.physics)),
                        exit = slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = LauncherPhysicsEngine.physicsSpring(settings.physics)
                        ) + fadeOut(LauncherPhysicsEngine.launchTween(settings.physics))
                    ) {
                        AppDrawerSheet(
                            apps = allApps,
                            settings = settings,
                            appIndexer = viewModel.appIndexer,
                            iconVersion = iconVersion,
                            badgeCounts = badgeCounts,
                            contextHiddenPackages = contextHiddenPackages,
                            onAppClick = { viewModel.onAppClicked(it) },
                            onAppLongPress = { longPressedApp = it },
                            onOpenSearch = { viewModel.openSearch() },
                            onOpenSettings = { viewModel.openSettings(0) },
                            onOpenVault = { viewModel.requestOpenVault() },
                            onDismiss = { viewModel.closeDrawer() }
                        )
                    }

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

                    // Widget stack quick editor
                    editingStackSlot?.let { slot ->
                        val latestSlot = settings.widgetStacks.firstOrNull { it.id == slot.id } ?: slot
                        WidgetStackEditorDialog(
                            slot = latestSlot,
                            onToggleWidget = { wt ->
                                viewModel.updateSettings { cfg ->
                                    val updated = cfg.widgetStacks.map { s ->
                                        if (s.id != latestSlot.id) s
                                        else {
                                            val list = s.stackedCustomWidgets.toMutableList()
                                            if (wt in list) list.remove(wt) else list.add(wt)
                                            s.copy(stackedCustomWidgets = list)
                                        }
                                    }
                                    cfg.copy(widgetStacks = updated)
                                }
                            },
                            onDismiss = { editingStackSlot = null }
                        )
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
    onToggleWidget: (CustomWidgetType) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNothingPalette.current
    Dialog(onDismissRequest = onDismiss) {
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "EDIT WIDGET STACK",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 14.sp,
                    color = palette.accent
                )
                Text(
                    text = "Select widgets to stack vertically in this slot. Swipe up/down on the widget to cycle.",
                    fontSize = 12.sp,
                    color = palette.textSecondary
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                }
            }
        }
    }
}
