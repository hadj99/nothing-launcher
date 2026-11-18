package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.appindexer.AppShortcutInfo
import com.example.appindexer.IndexedApp
import com.example.appindexer.InstalledIconPack
import com.example.appindexer.ScreenTimeVelocity
import com.example.contextautomation.ActiveMediaState
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.contextautomation.LiveNotificationBridge
import com.example.customwidgets.WeatherState
import com.example.settingsstore.db.LauncherDatabase
import com.example.settingsstore.db.StepHistoryEntity
import com.example.settingsstore.db.ThemePresetEntity
import com.example.settingsstore.model.AppCustomization
import com.example.settingsstore.model.ContextRule
import com.example.settingsstore.model.CustomSearchEngine
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.DockMiniChipType
import com.example.settingsstore.model.GestureAction
import com.example.settingsstore.model.HomePageConfig
import com.example.settingsstore.model.LauncherFolder
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SurfaceThemeConfig
import com.example.settingsstore.model.TimeFormatMode
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.example.settingsstore.model.YearViewMode
import com.example.universalsearch.UniversalSearchState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DotLauncherApplication
    val settingsRepo = app.settingsRepository
    val appIndexer = app.appIndexer
    val searchEngine = app.searchEngine
    val contextEngine = app.contextEngine
    val stepRepo = app.stepRepository
    val weatherRepo = app.weatherRepository
    val widgetHostManager = app.widgetHostManager

    private val database = LauncherDatabase.getInstance(application)

    val settings: StateFlow<LauncherSettings> = settingsRepo.settings
    val allApps: StateFlow<List<IndexedApp>> = appIndexer.allApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mostUsedApps: StateFlow<List<IndexedApp>> = appIndexer.mostUsedApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val iconVersion: StateFlow<Int> = appIndexer.iconVersion
    val screenTimeVelocity: StateFlow<ScreenTimeVelocity> = appIndexer.screenTimeVelocity

    val themePresets: StateFlow<List<Pair<ThemePresetEntity, SurfaceThemeConfig>>> =
        settingsRepo.themePresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stepHistory: StateFlow<List<StepHistoryEntity>> = stepRepo.last7Days
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todaySteps: StateFlow<Int> = stepRepo.todaySteps
    val stepSensorAvailable: StateFlow<Boolean> = stepRepo.sensorAvailable

    val weatherState: StateFlow<WeatherState> = weatherRepo.weatherState
    val telemetry: StateFlow<DeviceTelemetrySnapshot> = contextEngine.telemetry

    val recentQueries: StateFlow<List<String>> = searchEngine.recentQueries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val badgeCounts: StateFlow<Map<String, Int>> = LiveNotificationBridge.badgeCounts
    val mediaState: StateFlow<ActiveMediaState> = LiveNotificationBridge.mediaState

    private val _contextHiddenPackages = MutableStateFlow<Set<String>>(emptySet())
    val contextHiddenPackages: StateFlow<Set<String>> = _contextHiddenPackages.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchState = MutableStateFlow(UniversalSearchState())
    val searchState: StateFlow<UniversalSearchState> = _searchState.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _isDrawerVisible = MutableStateFlow(false)
    val isDrawerVisible: StateFlow<Boolean> = _isDrawerVisible.asStateFlow()

    private val _isSettingsVisible = MutableStateFlow(false)
    val isSettingsVisible: StateFlow<Boolean> = _isSettingsVisible.asStateFlow()

    private val _settingsInitialTab = MutableStateFlow(0)
    val settingsInitialTab: StateFlow<Int> = _settingsInitialTab.asStateFlow()

    private val _isVaultVisible = MutableStateFlow(false)
    val isVaultVisible: StateFlow<Boolean> = _isVaultVisible.asStateFlow()

    private val _vaultUnlocked = MutableStateFlow(false)
    val vaultUnlocked: StateFlow<Boolean> = _vaultUnlocked.asStateFlow()

    private val _showPinPrompt = MutableStateFlow(false)
    val showPinPrompt: StateFlow<Boolean> = _showPinPrompt.asStateFlow()

    private val _isAmbientAodVisible = MutableStateFlow(false)
    val isAmbientAodVisible: StateFlow<Boolean> = _isAmbientAodVisible.asStateFlow()

    private val _pendingFrictionApp = MutableStateFlow<IndexedApp?>(null)
    val pendingFrictionApp: StateFlow<IndexedApp?> = _pendingFrictionApp.asStateFlow()

    private val _installedIconPacks = MutableStateFlow<List<InstalledIconPack>>(emptyList())
    val installedIconPacks: StateFlow<List<InstalledIconPack>> = _installedIconPacks.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _installedIconPacks.value = appIndexer.discoverInstalledIconPacks()
            searchEngine.refreshLiveFxRates()
            evaluateContextAutomation()
        }
        viewModelScope.launch {
            combine(_searchQuery, allApps, settings) { q, apps, cfg ->
                Triple(q, apps, cfg)
            }.collect { (q, apps, cfg) ->
                searchJob?.cancel()
                searchJob = launch {
                    _searchState.value = searchEngine.search(q, apps, cfg)
                }
            }
        }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch {
            val prevPack = settings.value.iconMask.selectedIconPackPackage
            settingsRepo.updateSettings(transform)
            val newPack = settings.value.iconMask.selectedIconPackPackage
            if (newPack != prevPack) {
                appIndexer.refreshInstalledApps(newPack)
            }
        }
    }

    fun refreshAppsAndStats() {
        viewModelScope.launch {
            appIndexer.refreshInstalledApps(settings.value.iconMask.selectedIconPackPackage)
            contextEngine.refreshTelemetry()
            evaluateContextAutomation()
        }
    }

    fun evaluateContextAutomation() {
        viewModelScope.launch {
            val rule = contextEngine.evaluateMatchingRule(settings.value.contextRules)
            if (rule == null) {
                _contextHiddenPackages.value = emptySet()
                return@launch
            }
            _contextHiddenPackages.value = rule.hiddenPackages.toSet()
            if (settings.value.activeProfileName != rule.profileName) {
                settingsRepo.applyPresetById(rule.targetPresetId)
                settingsRepo.updateSettings {
                    it.copy(
                        activeProfileName = rule.profileName,
                        deepFocusActive = rule.autoDeepFocus || it.deepFocusActive
                    )
                }
            }
        }
    }

    fun onAppClicked(appItem: IndexedApp, splitScreen: Boolean = false) {
        val custom = settings.value.appCustomizations[appItem.packageName]
        val friction = custom?.frictionSeconds ?: 0
        if (friction > 0) {
            _pendingFrictionApp.value = appItem
        } else {
            launchAppImmediately(appItem, splitScreen)
        }
    }

    fun launchAppImmediately(appItem: IndexedApp, splitScreen: Boolean = false) {
        _pendingFrictionApp.value = null
        appIndexer.launchApp(appItem.packageName, splitScreen)
    }

    fun dismissFrictionTimer() {
        _pendingFrictionApp.value = null
    }

    fun launchShortcut(sc: AppShortcutInfo) {
        appIndexer.launchShortcut(sc)
    }

    fun openSearch() {
        _isSearchVisible.value = true
    }

    fun closeSearch() {
        _isSearchVisible.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
        if (q.length >= 2) {
            viewModelScope.launch {
                searchEngine.recordQuery(q, settings.value.searchConfig.incognitoMode)
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchEngine.clearHistory() }
    }

    fun clearClipboardHistory() {
        viewModelScope.launch {
            searchEngine.clearClipboardHistory()
            showBanner("Clipboard search history cleared")
        }
    }

    fun openDrawer() {
        _isDrawerVisible.value = true
    }

    fun closeDrawer() {
        _isDrawerVisible.value = false
    }

    fun openSettings(tabIndex: Int = 0) {
        _settingsInitialTab.value = tabIndex
        _isSettingsVisible.value = true
        _isSearchVisible.value = false
    }

    fun closeSettings() {
        _isSettingsVisible.value = false
    }

    fun requestOpenVault() {
        _isVaultVisible.value = true
    }

    fun setVaultUnlocked(unlocked: Boolean) {
        _vaultUnlocked.value = unlocked
    }

    fun closeVault() {
        _isVaultVisible.value = false
        _vaultUnlocked.value = false
        _showPinPrompt.value = false
    }

    fun setShowPinPrompt(show: Boolean) {
        _showPinPrompt.value = show
    }

    fun openAmbientAod() {
        _isAmbientAodVisible.value = true
    }

    fun closeAmbientAod() {
        _isAmbientAodVisible.value = false
    }

    fun toggleDeepFocus() {
        updateSettings { it.copy(deepFocusActive = !it.deepFocusActive) }
    }

    fun cycleTimeWidgetFormat() {
        val current = settings.value.widgetConfigFor(CustomWidgetType.TIME_DATE)
        val entries = TimeFormatMode.entries
        val next = entries[(entries.indexOf(current.timeFormatMode) + 1) % entries.size]
        updateWidgetConfig(CustomWidgetType.TIME_DATE) { it.copy(timeFormatMode = next) }
    }

    fun cycleYearWidgetMode() {
        val current = settings.value.widgetConfigFor(CustomWidgetType.YEAR_PROGRESS)
        val entries = YearViewMode.entries
        val next = entries[(entries.indexOf(current.yearViewMode) + 1) % entries.size]
        updateWidgetConfig(CustomWidgetType.YEAR_PROGRESS) { it.copy(yearViewMode = next) }
    }

    fun updateWidgetConfig(type: CustomWidgetType, transform: (WidgetAppearanceConfig) -> WidgetAppearanceConfig) {
        updateSettings { cfg ->
            val current = cfg.widgetConfigFor(type)
            val updatedMap = cfg.widgetConfigs.toMutableMap()
            updatedMap[type.name] = transform(current)
            cfg.copy(widgetConfigs = updatedMap)
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val city = settings.value.widgetConfigFor(CustomWidgetType.WEATHER).weatherCity
            weatherRepo.refreshWeather(city)
        }
    }

    fun startStepTracking() {
        val goal = settings.value.widgetConfigFor(CustomWidgetType.STEP_COUNTER).dailyStepGoal
        stepRepo.startListening(goal)
        viewModelScope.launch { stepRepo.syncTodayGoal(goal) }
    }

    fun updateAppCustomization(packageName: String, transform: (AppCustomization) -> AppCustomization) {
        updateSettings { cfg ->
            val current = cfg.appCustomizations[packageName] ?: AppCustomization(packageName)
            val updated = cfg.appCustomizations.toMutableMap()
            updated[packageName] = transform(current)
            cfg.copy(appCustomizations = updated)
        }
    }

    fun toggleAppOnHomePage(packageName: String, pageIndex: Int = 0) {
        updateSettings { cfg ->
            val pages = cfg.homeAppPages.toMutableList()
            while (pages.size <= pageIndex) pages.add(emptyList())
            val list = pages[pageIndex].toMutableList()
            if (list.contains(packageName)) list.remove(packageName) else list.add(packageName)
            pages[pageIndex] = list
            cfg.copy(homeAppPages = pages)
        }
    }

    fun toggleAppInDock(packageName: String, dockPage: Int = 0) {
        updateSettings { cfg ->
            val pages = cfg.dockPages.toMutableList()
            while (pages.size <= dockPage) pages.add(emptyList())
            val list = pages[dockPage].toMutableList()
            if (list.contains(packageName)) {
                list.remove(packageName)
            } else if (list.size < cfg.grid.dockSlotsPerPage) {
                list.add(packageName)
            }
            pages[dockPage] = list
            cfg.copy(dockPages = pages)
        }
    }

    fun addAppToFolder(folderName: String, packageName: String, pageIndex: Int = 0) {
        updateSettings { cfg ->
            val folders = cfg.folders.toMutableList()
            val existingIdx = folders.indexOfFirst { it.name.equals(folderName, ignoreCase = true) }
            if (existingIdx >= 0) {
                val existing = folders[existingIdx]
                if (!existing.packageNames.contains(packageName)) {
                    folders[existingIdx] = existing.copy(packageNames = existing.packageNames + packageName)
                }
            } else {
                folders.add(
                    LauncherFolder(
                        id = "folder_${System.currentTimeMillis()}",
                        name = folderName,
                        packageNames = listOf(packageName),
                        pageIndex = pageIndex
                    )
                )
            }
            cfg.copy(folders = folders)
        }
    }

    fun addHomePage() {
        updateSettings { cfg ->
            val nextIdx = cfg.homeAppPages.size + 1
            val configs = cfg.homePageConfigs.toMutableList()
            while (configs.size < cfg.homeAppPages.size) {
                configs.add(HomePageConfig(name = "PAGE ${configs.size + 1}"))
            }
            configs.add(HomePageConfig(name = "PAGE $nextIdx"))
            cfg.copy(
                homeAppPages = cfg.homeAppPages + listOf(emptyList()),
                homePageConfigs = configs
            )
        }
    }

    fun moveHomeApp(pageIndex: Int, packageName: String, delta: Int) {
        updateSettings { cfg ->
            val pages = cfg.homeAppPages.toMutableList()
            if (pageIndex !in pages.indices) return@updateSettings cfg
            val list = pages[pageIndex].toMutableList()
            val idx = list.indexOf(packageName)
            val target = idx + delta
            if (idx >= 0 && target in list.indices) {
                list.removeAt(idx)
                list.add(target, packageName)
                pages[pageIndex] = list
            }
            cfg.copy(homeAppPages = pages)
        }
    }

    fun moveHomeAppToPage(fromPage: Int, packageName: String, toPage: Int) {
        updateSettings { cfg ->
            val pages = cfg.homeAppPages.toMutableList()
            while (pages.size <= toPage) pages.add(emptyList())
            val sourceList = pages[fromPage].toMutableList()
            if (sourceList.remove(packageName)) {
                val destList = pages[toPage].toMutableList()
                if (!destList.contains(packageName)) destList.add(packageName)
                pages[fromPage] = sourceList
                pages[toPage] = destList
            }
            cfg.copy(homeAppPages = pages)
        }
    }

    fun updateHomePageConfig(pageIndex: Int, transform: (HomePageConfig) -> HomePageConfig) {
        updateSettings { cfg ->
            val configs = cfg.homePageConfigs.toMutableList()
            while (configs.size <= pageIndex) {
                configs.add(HomePageConfig(name = "PAGE ${configs.size + 1}"))
            }
            configs[pageIndex] = transform(configs[pageIndex])
            cfg.copy(homePageConfigs = configs)
        }
    }

    fun toggleDockMiniChip(chip: DockMiniChipType) {
        updateSettings { cfg ->
            val current = cfg.grid.dockMiniChips.toMutableList()
            if (chip in current) current.remove(chip) else if (current.size < 3) current.add(chip)
            cfg.copy(grid = cfg.grid.copy(dockMiniChips = current))
        }
    }

    fun upsertContextRule(rule: ContextRule) {
        updateSettings { cfg ->
            val list = cfg.contextRules.toMutableList()
            val idx = list.indexOfFirst { it.id == rule.id }
            if (idx >= 0) list[idx] = rule else list.add(rule)
            cfg.copy(contextRules = list)
        }
    }

    fun deleteContextRule(ruleId: String) {
        updateSettings { cfg ->
            cfg.copy(contextRules = cfg.contextRules.filterNot { it.id == ruleId })
        }
    }

    fun saveCustomPreset(name: String) {
        viewModelScope.launch {
            settingsRepo.saveThemePreset(name, settings.value.theme)
            showBanner("Preset '$name' saved")
        }
    }

    fun applyThemePreset(presetId: String) {
        viewModelScope.launch {
            settingsRepo.applyPresetById(presetId)
        }
    }

    fun deleteThemePreset(presetId: String) {
        viewModelScope.launch {
            settingsRepo.deleteCustomPreset(presetId)
        }
    }

    fun addCustomSearchEngine(name: String, urlTemplate: String) {
        if (name.isBlank() || urlTemplate.isBlank()) return
        val normalizedUrl = if (urlTemplate.contains("%s")) urlTemplate else "$urlTemplate%s"
        updateSettings { cfg ->
            val newEngine = CustomSearchEngine(
                id = "eng_${System.currentTimeMillis()}",
                name = name.trim(),
                urlTemplate = normalizedUrl.trim(),
                enabled = true
            )
            cfg.copy(
                searchConfig = cfg.searchConfig.copy(
                    customEngines = cfg.searchConfig.customEngines + newEngine
                )
            )
        }
    }

    fun addSafFolderUri(uri: String) {
        updateSettings { cfg ->
            if (cfg.safSearchFolderUris.contains(uri)) cfg
            else cfg.copy(safSearchFolderUris = cfg.safSearchFolderUris + uri)
        }
    }

    fun importBackupPayload(payload: String) {
        viewModelScope.launch {
            settingsRepo.importFromJson(payload)
                .onSuccess { showBanner("Configuration imported cleanly") }
                .onFailure { showBanner("Import failed: ${it.localizedMessage}") }
        }
    }

    fun executeGestureAction(action: GestureAction, onCycleDockPage: () -> Unit = {}) {
        when (action) {
            GestureAction.NONE -> Unit
            GestureAction.OPEN_SEARCH -> openSearch()
            GestureAction.OPEN_DRAWER -> openDrawer()
            GestureAction.OPEN_SETTINGS -> openSettings(0)
            GestureAction.OPEN_VAULT -> requestOpenVault()
            GestureAction.TOGGLE_DEEP_FOCUS -> toggleDeepFocus()
            GestureAction.OPEN_AMBIENT_AOD -> openAmbientAod()
            GestureAction.EXPAND_NOTIFICATIONS -> {
                showBanner("Pull down status bar for system notifications")
            }
            GestureAction.TOGGLE_FLASHLIGHT -> {
                contextEngine.triggerQuickToggle(com.example.settingsstore.model.QuickToggleItem.FLASHLIGHT)
            }
            GestureAction.NEXT_DOCK_PAGE -> onCycleDockPage()
        }
    }

    fun showBanner(message: String) {
        _statusBanner.value = message
        viewModelScope.launch {
            delay(2600L)
            if (_statusBanner.value == message) {
                _statusBanner.value = null
            }
        }
    }
}
