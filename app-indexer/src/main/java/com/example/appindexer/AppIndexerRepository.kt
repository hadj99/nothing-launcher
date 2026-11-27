package com.example.appindexer

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.settingsstore.db.AppIndexEntity
import com.example.settingsstore.db.LauncherDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

@Stable
class AppIndexerRepository(private val context: Context) {
    private companion object {
        const val SEEDED_KEY = "__seeded"
    }

    private val pm = context.packageManager
    private val database = LauncherDatabase.getInstance(context)
    private val appIndexDao = database.appIndexDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // First-seen registry so freshly installed apps can glow in the launcher accent colour.
    private val firstSeenPrefs by lazy {
        context.getSharedPreferences("dotos_first_seen", Context.MODE_PRIVATE)
    }

    /** Records the first time we ever saw each package; returns the full map. */
    private fun syncFirstSeen(packages: Collection<String>): Map<String, Long> {
        synchronized(firstSeenPrefs) {
            val now = System.currentTimeMillis()
            // First ever scan: back-date everything so no existing app glows as "new".
            val seeded = firstSeenPrefs.getBoolean(SEEDED_KEY, false)
            val stamp = if (seeded) now else now - 10L * 24L * 60L * 60L * 1000L
            val editor = firstSeenPrefs.edit()
            var changed = false
            packages.forEach { pkg ->
                if (!firstSeenPrefs.contains(pkg)) {
                    editor.putLong(pkg, stamp)
                    changed = true
                }
            }
            if (!seeded) {
                editor.putBoolean(SEEDED_KEY, true)
                changed = true
            }
            if (changed) editor.apply()
            return packages.associateWith { firstSeenPrefs.getLong(it, now) }
        }
    }

    /** Packages first seen inside [days] days (only used when the accent-glow setting is on). */
    fun newlyInstalledPackages(days: Int): Set<String> {
        val cutoff = System.currentTimeMillis() - days.coerceIn(1, 30) * 24L * 60L * 60L * 1000L
        return synchronized(firstSeenPrefs) {
            firstSeenPrefs.all.entries
                .filter { (key, _) -> !key.startsWith("__") }
                .filter { (_, seen) -> (seen as? Long ?: 0L) >= cutoff }
                .map { it.key }
                .toSet()
        }
    }

    // Lock-free in-memory bitmap cache — never evicts normal app counts, zero main-thread jank.
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>(256)
    private val pendingIconLoads = ConcurrentHashMap.newKeySet<String>()
    private val refreshMutex = Mutex()

    @Volatile
    private var activeIconPackPkg: String? = null
    private var iconPackMapping: Map<String, String> = emptyMap()

    @Volatile
    private var lastUsageRefreshMs: Long = 0L

    @Volatile
    private var packageMonitorRegistered: Boolean = false
    private var iconNotifyJob: Job? = null

    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    private val _screenTimeVelocity = MutableStateFlow(ScreenTimeVelocity())
    val screenTimeVelocity: StateFlow<ScreenTimeVelocity> = _screenTimeVelocity.asStateFlow()

    private val _shortcutsByPackage = MutableStateFlow<Map<String, List<AppShortcutInfo>>>(emptyMap())
    val shortcutsByPackage: StateFlow<Map<String, List<AppShortcutInfo>>> = _shortcutsByPackage.asStateFlow()

    val allApps: Flow<List<IndexedApp>> = combine(
        appIndexDao.observeAllApps().distinctUntilChanged(),
        _shortcutsByPackage
    ) { entities, shortcutMap ->
        entities.map { entity ->
            IndexedApp(
                packageName = entity.packageName,
                activityName = entity.activityName,
                label = entity.label,
                category = entity.category,
                launchCount = entity.launchCount,
                lastUsedTimestamp = entity.lastUsedTimestamp,
                dailyMinutesUsed = entity.dailyMinutesUsed,
                isSystemApp = entity.isSystemApp,
                shortcuts = shortcutMap[entity.packageName].orEmpty()
            )
        }
    }.distinctUntilChanged()

    val mostUsedApps: Flow<List<IndexedApp>> = appIndexDao.observeMostUsed(8)
        .distinctUntilChanged()
        .map { entities ->
            entities.map { entity ->
                IndexedApp(
                    packageName = entity.packageName,
                    activityName = entity.activityName,
                    label = entity.label,
                    category = entity.category,
                    launchCount = entity.launchCount,
                    lastUsedTimestamp = entity.lastUsedTimestamp,
                    dailyMinutesUsed = entity.dailyMinutesUsed,
                    isSystemApp = entity.isSystemApp
                )
            }
        }
        .distinctUntilChanged()

    fun startPackageMonitor() {
        if (packageMonitorRegistered) return
        packageMonitorRegistered = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val pkg = intent?.data?.schemeSpecificPart
                if (!pkg.isNullOrBlank()) {
                    iconCache.remove("${activeIconPackPkg.orEmpty()}:$pkg")
                }
                scope.launch {
                    refreshInstalledApps(activeIconPackPkg)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    suspend fun refreshInstalledApps(iconPackPackage: String? = activeIconPackPkg) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            if (iconPackPackage != activeIconPackPkg) {
                loadIconPackMapping(iconPackPackage)
            }

            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            val selfPkg = context.packageName

            val entities = ArrayList<AppIndexEntity>(resolveInfos.size)
            val seenPkgs = HashSet<String>(resolveInfos.size)
            val activePkgs = ArrayList<String>(resolveInfos.size)

            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName ?: continue
                if (pkg == selfPkg || !seenPkgs.add(pkg)) continue
                activePkgs.add(pkg)

                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                val appInfo = ri.activityInfo.applicationInfo
                val category = resolveAppCategory(pkg, label, appInfo)
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                entities.add(
                    AppIndexEntity(
                        packageName = pkg,
                        activityName = ri.activityInfo.name,
                        label = label,
                        category = category,
                        isSystemApp = isSys
                    )
                )
            }

            if (activePkgs.isNotEmpty()) {
                syncFirstSeen(activePkgs)
            }

            if (entities.isNotEmpty()) {
                // Diff-sync: preserves launchCount & dailyMinutesUsed and skips DB writes when unchanged!
                appIndexDao.syncInstalledApps(entities)
            }

            // Pre-warm icons in the background so opening the drawer has 0ms icon decode lag
            scope.launch(Dispatchers.Default) {
                var addedAny = false
                for (entity in entities) {
                    val key = "${activeIconPackPkg.orEmpty()}:${entity.packageName}"
                    if (!iconCache.containsKey(key)) {
                        if (getOrLoadIcon(entity.packageName, entity.activityName) != null) {
                            addedAny = true
                        }
                    }
                }
                if (addedAny) {
                    _iconVersion.value++
                }
            }

            refreshUsageStats(force = true)
            if (_shortcutsByPackage.value.isEmpty()) {
                refreshShortcuts(activePkgs)
            }
        }
    }

    @Composable
    fun rememberAppIcon(
        packageName: String,
        activityName: String = "",
        iconVersion: Int = 0
    ): ImageBitmap? {
        val key = "${activeIconPackPkg.orEmpty()}:$packageName"
        val cached = iconCache[key]
        var bitmap by remember(packageName, iconVersion) { mutableStateOf(cached) }
        if (bitmap == null && cached != null) {
            bitmap = cached
        }
        if (bitmap == null) {
            LaunchedEffect(packageName, iconVersion) {
                val loaded = withContext(Dispatchers.IO) {
                    getOrLoadIcon(packageName, activityName)
                }
                if (loaded != null) {
                    bitmap = loaded
                }
            }
        }
        return bitmap
    }

    fun getAllCachedBitmaps(): List<ImageBitmap> = iconCache.values.toList()

    fun getCachedIcon(packageName: String, activityName: String = ""): ImageBitmap? {
        val key = "${activeIconPackPkg.orEmpty()}:$packageName"
        iconCache[key]?.let { return it }
        if (pendingIconLoads.add(key)) {
            scope.launch {
                try {
                    val loaded = getOrLoadIcon(packageName, activityName)
                    if (loaded != null) {
                        scheduleCoalescedIconBump()
                    }
                } finally {
                    pendingIconLoads.remove(key)
                }
            }
        }
        return null
    }

    private fun scheduleCoalescedIconBump() {
        if (iconNotifyJob?.isActive == true) return
        iconNotifyJob = scope.launch {
            delay(220L)
            _iconVersion.value++
        }
    }

    private fun getOrLoadIcon(packageName: String, activityName: String): ImageBitmap? {
        val cacheKey = "${activeIconPackPkg.orEmpty()}:$packageName"
        iconCache[cacheKey]?.let { return it }

        val drawable: Drawable? = tryLoadFromIconPack(packageName, activityName)
            ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

        val bitmap = drawable?.let { drawableToImageBitmap(it, 132) }
        if (bitmap != null) {
            iconCache[cacheKey] = bitmap
        }
        return bitmap
    }

    private fun tryLoadFromIconPack(packageName: String, activityName: String): Drawable? {
        val packPkg = activeIconPackPkg ?: return null
        val drawableName = iconPackMapping["$packageName/$activityName"]
            ?: iconPackMapping[packageName]
            ?: return null
        return runCatching {
            val res = pm.getResourcesForApplication(packPkg)
            val resId = res.getIdentifier(drawableName, "drawable", packPkg)
            if (resId != 0) res.getDrawable(resId, null) else null
        }.getOrNull()
    }

    fun loadIconPackMapping(packPkg: String?) {
        activeIconPackPkg = packPkg
        iconCache.clear()
        if (packPkg.isNullOrBlank()) {
            iconPackMapping = emptyMap()
            _iconVersion.value++
            return
        }
        val map = HashMap<String, String>()
        runCatching {
            val res = pm.getResourcesForApplication(packPkg)
            val xmlId = res.getIdentifier("appfilter", "xml", packPkg)
            if (xmlId != 0) {
                val parser = res.getXml(xmlId)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (!comp.isNullOrBlank() && !drawable.isNullOrBlank()) {
                            val clean = comp.removePrefix("ComponentInfo{").removeSuffix("}")
                            map[clean] = drawable
                            val pkgOnly = clean.substringBefore("/")
                            if (!map.containsKey(pkgOnly)) map[pkgOnly] = drawable
                        }
                    }
                    eventType = parser.next()
                }
            }
        }
        iconPackMapping = map
        _iconVersion.value++
    }

    fun discoverInstalledIconPacks(): List<InstalledIconPack> {
        val themes = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme"
        )
        val seen = HashSet<String>()
        val result = mutableListOf<InstalledIconPack>()
        for (action in themes) {
            val list = pm.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL)
            for (ri in list) {
                val pkg = ri.activityInfo.packageName ?: continue
                if (seen.add(pkg)) {
                    result.add(InstalledIconPack(pkg, ri.loadLabel(pm).toString()))
                }
            }
        }
        return result
    }

    suspend fun refreshUsageStats(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastUsageRefreshMs) < 300_000L) {
            return@withContext
        }
        lastUsageRefreshMs = now

        val granted = hasUsageStatsPermission(context)
        if (!granted) {
            if (_screenTimeVelocity.value.usagePermissionGranted) {
                _screenTimeVelocity.value = _screenTimeVelocity.value.copy(usagePermissionGranted = false)
            }
            return@withContext
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now).orEmpty()

        var totalMillis = 0L
        val minutesByPkg = HashMap<String, Int>()
        val lastUsedByPkg = HashMap<String, Long>()
        for (s in stats) {
            val fg = s.totalTimeInForeground
            if (fg > 0L) {
                totalMillis += fg
                val mins = (fg / 60_000L).toInt()
                val prev = minutesByPkg[s.packageName] ?: 0
                if (mins > prev) minutesByPkg[s.packageName] = mins
                if (s.lastTimeUsed > startOfDay) {
                    val prevTs = lastUsedByPkg[s.packageName] ?: 0L
                    if (s.lastTimeUsed > prevTs) lastUsedByPkg[s.packageName] = s.lastTimeUsed
                }
            }
        }
        if (minutesByPkg.isNotEmpty() || lastUsedByPkg.isNotEmpty()) {
            appIndexDao.applyUsageUpdates(minutesByPkg, lastUsedByPkg)
        }

        val recentHourStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 3_600_000L, now).orEmpty()
        val recentHourMinutes = (recentHourStats.sumOf { it.totalTimeInForeground } / 60_000L).toInt().coerceAtMost(60)
        val totalMins = (totalMillis / 60_000L).toInt()
        val velocity = ((recentHourMinutes / 45f) * 0.65f + (totalMins / 240f) * 0.35f).coerceIn(0.08f, 1.0f)

        _screenTimeVelocity.value = ScreenTimeVelocity(
            totalMinutesToday = totalMins,
            recentHourMinutes = recentHourMinutes,
            velocityScore = velocity,
            usagePermissionGranted = true
        )
    }

    private fun refreshShortcuts(packages: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
        if (!launcherApps.hasShortcutHostPermission()) return

        val query = LauncherApps.ShortcutQuery().apply {
            setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        }
        val result = HashMap<String, List<AppShortcutInfo>>()
        for (pkg in packages.take(40)) {
            runCatching {
                query.setPackage(pkg)
                val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
                if (shortcuts.isNotEmpty()) {
                    result[pkg] = shortcuts.take(4).map { sc ->
                        AppShortcutInfo(
                            id = sc.id,
                            packageName = sc.`package`,
                            shortLabel = sc.shortLabel?.toString().orEmpty(),
                            longLabel = sc.longLabel?.toString() ?: sc.shortLabel?.toString().orEmpty()
                        )
                    }
                }
            }
        }
        _shortcutsByPackage.value = result
    }

    fun launchApp(packageName: String, splitScreen: Boolean = false): Boolean {
        val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (splitScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            scope.launch {
                appIndexDao.recordLaunch(packageName, System.currentTimeMillis())
                val current = _screenTimeVelocity.value
                _screenTimeVelocity.value = current.copy(
                    totalMinutesToday = current.totalMinutesToday + 1,
                    velocityScore = (current.velocityScore + 0.03f).coerceAtMost(1f)
                )
            }
            true
        }.getOrDefault(false)
    }

    fun launchShortcut(shortcut: AppShortcutInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
                return runCatching {
                    launcherApps.startShortcut(
                        shortcut.packageName,
                        shortcut.id,
                        null,
                        null,
                        Process.myUserHandle()
                    )
                    true
                }.getOrDefault(false)
            }
        }
        return launchApp(shortcut.packageName)
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun uninstallApp(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun hasUsageStatsPermission(ctx: Context = context): Boolean {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun resolveAppCategory(pkg: String, label: String, info: ApplicationInfo): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (info.category) {
                ApplicationInfo.CATEGORY_GAME -> return "Games"
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return "Media"
                ApplicationInfo.CATEGORY_SOCIAL -> return "Social"
                ApplicationInfo.CATEGORY_NEWS -> return "News"
                ApplicationInfo.CATEGORY_MAPS -> return "Travel"
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return "Productivity"
            }
        }
        val lower = "${pkg.lowercase()} ${label.lowercase()}"
        return when {
            lower.contains("phone") || lower.contains("dialer") || lower.contains("messag") ||
                lower.contains("whatsapp") || lower.contains("telegram") || lower.contains("mail") ||
                lower.contains("gmail") || lower.contains("chat") -> "Communication"
            lower.contains("music") || lower.contains("spotify") || lower.contains("youtube") ||
                lower.contains("camera") || lower.contains("gallery") || lower.contains("photo") -> "Media"
            lower.contains("calendar") || lower.contains("notes") || lower.contains("docs") ||
                lower.contains("drive") || lower.contains("slack") || lower.contains("notion") -> "Productivity"
            lower.contains("chrome") || lower.contains("browser") || lower.contains("firefox") ||
                lower.contains("maps") || lower.contains("clock") || lower.contains("calc") -> "Utilities"
            lower.contains("bank") || lower.contains("pay") || lower.contains("wallet") -> "Finance"
            lower.contains("settings") || lower.contains("files") || lower.contains("system") -> "System"
            else -> "General"
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable, sizePx: Int): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}
