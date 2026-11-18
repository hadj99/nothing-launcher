package com.example.appindexer

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.settingsstore.db.AppIndexEntity
import com.example.settingsstore.db.LauncherDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar

class AppIndexerRepository(private val context: Context) {
    private val pm = context.packageManager
    private val database = LauncherDatabase.getInstance(context)
    private val appIndexDao = database.appIndexDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val iconCache = object : LruCache<String, ImageBitmap>(320) {}
    private var activeIconPackPkg: String? = null
    private var iconPackMapping: Map<String, String> = emptyMap()

    private val _iconVersion = MutableStateFlow(0)
    val iconVersion: StateFlow<Int> = _iconVersion.asStateFlow()

    private val _screenTimeVelocity = MutableStateFlow(ScreenTimeVelocity())
    val screenTimeVelocity: StateFlow<ScreenTimeVelocity> = _screenTimeVelocity.asStateFlow()

    private val _shortcutsByPackage = MutableStateFlow<Map<String, List<AppShortcutInfo>>>(emptyMap())
    val shortcutsByPackage: StateFlow<Map<String, List<AppShortcutInfo>>> = _shortcutsByPackage.asStateFlow()

    val allApps: Flow<List<IndexedApp>> = appIndexDao.observeAllApps().map { entities ->
        val shortcutMap = _shortcutsByPackage.value
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
    }

    val mostUsedApps: Flow<List<IndexedApp>> = appIndexDao.observeMostUsed(8).map { entities ->
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

    suspend fun refreshInstalledApps(iconPackPackage: String? = activeIconPackPkg) = withContext(Dispatchers.IO) {
        if (iconPackPackage != activeIconPackPkg) {
            loadIconPackMapping(iconPackPackage)
        }

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        val selfPkg = context.packageName

        val entities = mutableListOf<AppIndexEntity>()
        val activePkgs = mutableListOf<String>()

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName ?: continue
            if (pkg == selfPkg) continue
            if (activePkgs.contains(pkg)) continue
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
            // Pre-cache bitmap off main thread for sub-16ms scrolling
            getOrLoadIcon(pkg, ri.activityInfo.name)
        }

        if (entities.isNotEmpty()) {
            appIndexDao.upsertApps(entities)
            appIndexDao.pruneRemoved(activePkgs)
        }

        refreshUsageStats()
        refreshShortcuts(activePkgs)
    }

    fun getCachedIcon(packageName: String, activityName: String = ""): ImageBitmap? {
        val key = "${activeIconPackPkg.orEmpty()}:$packageName"
        iconCache.get(key)?.let { return it }
        scope.launch {
            getOrLoadIcon(packageName, activityName)
            _iconVersion.value++
        }
        return null
    }

    private fun getOrLoadIcon(packageName: String, activityName: String): ImageBitmap? {
        val cacheKey = "${activeIconPackPkg.orEmpty()}:$packageName"
        iconCache.get(cacheKey)?.let { return it }

        val drawable: Drawable? = tryLoadFromIconPack(packageName, activityName)
            ?: runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

        val bitmap = drawable?.let { drawableToImageBitmap(it, 144) }
        if (bitmap != null) {
            iconCache.put(cacheKey, bitmap)
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
        iconCache.evictAll()
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

    suspend fun refreshUsageStats() = withContext(Dispatchers.IO) {
        val granted = hasUsageStatsPermission(context)
        if (!granted) {
            _screenTimeVelocity.value = _screenTimeVelocity.value.copy(usagePermissionGranted = false)
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
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now).orEmpty()

        var totalMillis = 0L
        for (s in stats) {
            val fg = s.totalTimeInForeground
            if (fg > 0L) {
                totalMillis += fg
                val mins = (fg / 60_000L).toInt()
                appIndexDao.updateDailyMinutes(s.packageName, mins)
                if (s.lastTimeUsed > startOfDay) {
                    appIndexDao.recordLaunch(s.packageName, s.lastTimeUsed)
                }
            }
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
