package com.example

import android.app.Application
import com.example.appindexer.AppIndexerRepository
import com.example.contextautomation.ContextAutomationEngine
import com.example.customwidgets.StepCounterRepository
import com.example.customwidgets.WeatherRepository
import com.example.settingsstore.repo.SettingsRepository
import com.example.universalsearch.UniversalSearchEngine
import com.example.widgethost.LauncherWidgetHostManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DotLauncherApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var appIndexer: AppIndexerRepository
        private set
    lateinit var searchEngine: UniversalSearchEngine
        private set
    lateinit var contextEngine: ContextAutomationEngine
        private set
    lateinit var stepRepository: StepCounterRepository
        private set
    lateinit var weatherRepository: WeatherRepository
        private set
    lateinit var widgetHostManager: LauncherWidgetHostManager
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        appIndexer = AppIndexerRepository(this)
        searchEngine = UniversalSearchEngine(this)
        contextEngine = ContextAutomationEngine(this)
        stepRepository = StepCounterRepository(this)
        weatherRepository = WeatherRepository(this)
        widgetHostManager = LauncherWidgetHostManager(this)

        // Listen for actual app installs/updates/uninstalls instead of re-indexing on every resume
        appIndexer.startPackageMonitor()

        appScope.launch {
            settingsRepository.initialize()
            val settings = settingsRepository.settings.value
            appIndexer.refreshInstalledApps(settings.iconMask.selectedIconPackPackage)
            stepRepository.startListening(
                settings.widgetConfigFor(com.example.settingsstore.model.CustomWidgetType.STEP_COUNTER).dailyStepGoal
            )
        }
    }
}
