package com.example.universalsearch

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.appindexer.AppShortcutInfo
import com.example.appindexer.IndexedApp
import com.example.settingsstore.db.ClipboardEntryEntity
import com.example.settingsstore.db.LauncherDatabase
import com.example.settingsstore.db.SearchHistoryEntity
import com.example.settingsstore.model.CustomSearchEngine
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SearchSourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Immutable
data class ContactSearchResult(
    val id: String,
    val displayName: String,
    val phoneNumber: String
)

@Immutable
data class LocalFileResult(
    val name: String,
    val uri: Uri,
    val mimeType: String
)

@Immutable
data class SettingsJumpResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val androidAction: String? = null,
    val launcherTabIndex: Int? = null
)

@Immutable
data class ClipboardSnippetResult(
    val hash: String,
    val text: String,
    val isUrl: Boolean
)

@Immutable
data class UniversalSearchState(
    val query: String = "",
    val appMatches: List<IndexedApp> = emptyList(),
    val shortcutMatches: List<AppShortcutInfo> = emptyList(),
    val contactMatches: List<ContactSearchResult> = emptyList(),
    val fileMatches: List<LocalFileResult> = emptyList(),
    val settingMatches: List<SettingsJumpResult> = emptyList(),
    val utilityMatches: List<UtilityResult> = emptyList(),
    val clipboardMatches: List<ClipboardSnippetResult> = emptyList(),
    val webEngines: List<CustomSearchEngine> = emptyList(),
    val contactsPermissionGranted: Boolean = false
)

class UniversalSearchEngine(private val context: Context) {
    private val database = LauncherDatabase.getInstance(context)
    private val searchHistoryDao = database.searchHistoryDao()
    private val clipboardDao = database.clipboardHistoryDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val recentQueries: Flow<List<String>> = searchHistoryDao.observeHistory().map { list ->
        val raw = list.map { it.queryText.trim() }.filter { it.length >= 2 }
        // Automatically suppress any legacy keystroke-prefix spam (e.g. "ph", "pho", "phon" before "phone")
        raw.filterIndexed { idx, q ->
            val prev = raw.getOrNull(idx - 1)
            val next = raw.getOrNull(idx + 1)
            val isPrefixOfPrev = prev != null && !prev.equals(q, ignoreCase = true) && prev.startsWith(q, ignoreCase = true)
            val isPrefixOfNext = next != null && !next.equals(q, ignoreCase = true) && next.startsWith(q, ignoreCase = true)
            !isPrefixOfPrev && !isPrefixOfNext
        }.distinct()
    }

    val recentClipboard: Flow<List<ClipboardSnippetResult>> = clipboardDao.observeSnippets().map { list ->
        list.map {
            ClipboardSnippetResult(
                hash = it.contentHash,
                text = it.text,
                isUrl = it.text.startsWith("http://") || it.text.startsWith("https://")
            )
        }
    }

    init {
        startClipboardObserver()
        scope.launch { refreshLiveFxRates() }
    }

    private fun startClipboardObserver() {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            cm.addPrimaryClipChangedListener {
                val clipText = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (!clipText.isNullOrBlank() && clipText.length <= 400) {
                    scope.launch {
                        clipboardDao.upsertSnippet(
                            ClipboardEntryEntity(
                                contentHash = clipText.hashCode().toString(),
                                text = clipText,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun refreshLiveFxRates() = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val ratesObj = json.optJSONObject("rates") ?: return@runCatching
            val parsed = HashMap<String, Double>()
            val keys = ratesObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                parsed[k] = ratesObj.optDouble(k, 0.0)
            }
            UtilityParsers.updateLiveFxRates(parsed)
        }
    }

    suspend fun recordQuery(query: String, incognito: Boolean) {
        if (incognito) return
        val clean = query.trim()
        // v1.3: only real submissions are stored — never intermediate keystroke prefixes.
        if (clean.length < 2) return
        // Drop any longer query that is merely a prefix extension of this one and vice versa.
        val existing = recentQueries.first()
        existing.filter { it.length < clean.length && clean.startsWith(it, ignoreCase = true) }
            .forEach { stale -> removeQuery(stale) }
        searchHistoryDao.recordQuery(SearchHistoryEntity(clean))
    }

    private suspend fun removeQuery(query: String) {
        searchHistoryDao.deleteQuery(query)
    }

    /** One-shot cleanup of legacy keystroke spam ("p", "ph", "pho", "phon", ...) at startup. */
    fun pruneLegacyPrefixHistory() {
        scope.launch {
            runCatching {
                val history = recentQueries.first()
                history.forEachIndexed { idx, q ->
                    val prev = history.getOrNull(idx - 1)
                    val next = history.getOrNull(idx + 1)
                    val isPrefix = (prev != null && prev.startsWith(q, ignoreCase = true) && !prev.equals(q, true)) ||
                        (next != null && next.startsWith(q, ignoreCase = true) && !next.equals(q, true))
                    if (isPrefix) removeQuery(q)
                }
            }
        }
    }

    suspend fun clearHistory() {
        searchHistoryDao.clearHistory()
    }

    suspend fun clearClipboardHistory() {
        clipboardDao.clearSnippets()
    }

    suspend fun search(
        query: String,
        apps: List<IndexedApp>,
        settings: LauncherSettings
    ): UniversalSearchState = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val hasContactsPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        val enabled = settings.searchConfig.enabledSources.toSet()

        if (trimmed.isEmpty()) {
            return@withContext UniversalSearchState(
                query = "",
                webEngines = settings.searchConfig.customEngines.filter { it.enabled },
                contactsPermissionGranted = hasContactsPerm
            )
        }

        val visibleApps = apps.filter { app ->
            val custom = settings.appCustomizations[app.packageName]
            custom?.hidden != true && custom?.lockedInVault != true
        }

        val appMatches = if (SearchSourceType.APPS_SHORTCUTS in enabled) {
            visibleApps
                .mapNotNull { app ->
                    val customLabel = settings.appCustomizations[app.packageName]?.customLabel
                    val effectiveName = customLabel ?: app.label
                    val score = fuzzyScore(trimmed, effectiveName)
                    if (score > 0) app to score else null
                }
                .sortedByDescending { it.second }
                .take(8)
                .map { it.first }
        } else emptyList()

        val shortcutMatches = if (SearchSourceType.APPS_SHORTCUTS in enabled) {
            visibleApps.flatMap { it.shortcuts }
                .filter {
                    it.shortLabel.contains(trimmed, ignoreCase = true) ||
                        it.longLabel.contains(trimmed, ignoreCase = true)
                }
                .take(5)
        } else emptyList()

        val utilityMatches = if (SearchSourceType.UTILITIES in enabled) {
            UtilityParsers.parse(trimmed)
        } else emptyList()

        val clipboardMatches = if (SearchSourceType.CLIPBOARD in enabled) {
            clipboardDao.searchSnippets(trimmed).map {
                ClipboardSnippetResult(
                    hash = it.contentHash,
                    text = it.text,
                    isUrl = it.text.startsWith("http://") || it.text.startsWith("https://")
                )
            }
        } else emptyList()

        val contactMatches = if (SearchSourceType.CONTACTS in enabled && hasContactsPerm) {
            queryContacts(trimmed)
        } else emptyList()

        val fileMatches = if (SearchSourceType.LOCAL_FILES in enabled) {
            queryLocalFiles(trimmed, settings.safSearchFolderUris)
        } else emptyList()

        val settingMatches = if (SearchSourceType.DEVICE_SETTINGS in enabled) {
            querySettingsCatalog(trimmed)
        } else emptyList()

        val webEngines = if (SearchSourceType.WEB_ENGINES in enabled) {
            settings.searchConfig.customEngines.filter { it.enabled }
        } else emptyList()

        UniversalSearchState(
            query = trimmed,
            appMatches = appMatches,
            shortcutMatches = shortcutMatches,
            contactMatches = contactMatches,
            fileMatches = fileMatches,
            settingMatches = settingMatches,
            utilityMatches = utilityMatches,
            clipboardMatches = clipboardMatches,
            webEngines = webEngines,
            contactsPermissionGranted = hasContactsPerm
        )
    }

    private fun fuzzyScore(query: String, target: String): Int {
        val q = query.lowercase()
        val t = target.lowercase()
        if (t == q) return 100
        if (t.startsWith(q)) return 85
        if (t.contains(q)) return 65
        var qi = 0
        for (c in t) {
            if (qi < q.length && c == q[qi]) qi++
        }
        return if (qi == q.length) 40 else 0
    }

    private fun queryContacts(query: String): List<ContactSearchResult> {
        val list = mutableListOf<ContactSearchResult>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("%$query%")
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val seen = HashSet<String>()
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && list.size < 6) {
                    val id = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val num = cursor.getString(numIdx) ?: continue
                    if (seen.add("$name:$num")) {
                        list.add(ContactSearchResult(id, name, num))
                    }
                }
            }
        }
        return list
    }

    private fun queryLocalFiles(query: String, safFolderUris: List<String>): List<LocalFileResult> {
        val results = mutableListOf<LocalFileResult>()
        for (uriStr in safFolderUris) {
            runCatching {
                val treeUri = Uri.parse(uriStr)
                val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching
                for (child in root.listFiles()) {
                    val name = child.name ?: continue
                    if (name.contains(query, ignoreCase = true) && results.size < 8) {
                        results.add(
                            LocalFileResult(
                                name = name,
                                uri = child.uri,
                                mimeType = child.type ?: "*/*"
                            )
                        )
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && results.size < 8) {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
                MediaStore.Downloads.MIME_TYPE
            )
            val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            runCatching {
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    arrayOf("%$query%"),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                    while (cursor.moveToNext() && results.size < 8) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol) ?: "*/*"
                        val contentUri = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                        results.add(LocalFileResult(name, contentUri, mime))
                    }
                }
            }
        }
        return results
    }

    private fun querySettingsCatalog(query: String): List<SettingsJumpResult> {
        val catalog = listOf(
            SettingsJumpResult("wifi", "Wi-Fi Settings", "System network & internet", Settings.ACTION_WIFI_SETTINGS),
            SettingsJumpResult("bt", "Bluetooth Settings", "System connected devices", Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingsJumpResult("display", "Display & Brightness", "System screen & dark theme", Settings.ACTION_DISPLAY_SETTINGS),
            SettingsJumpResult("battery", "Battery & Saver", "System power management", Settings.ACTION_BATTERY_SAVER_SETTINGS),
            SettingsJumpResult("sound", "Sound & Vibration", "System volume & DND", Settings.ACTION_SOUND_SETTINGS),
            SettingsJumpResult("apps", "System Apps", "Manage installed applications", Settings.ACTION_APPLICATION_SETTINGS),
            SettingsJumpResult("l_mask", "Icon Masking & Polygons", "Launcher: Star, Diamond, Hexagon, Polygon, Rim Glow", launcherTabIndex = 2),
            SettingsJumpResult("l_typo", "Typography Engine", "Launcher: NDOT fonts, kerning & case", launcherTabIndex = 1),
            SettingsJumpResult("l_theme", "Surface & Wallpaper", "Launcher: OLED black, blur, presets", launcherTabIndex = 0),
            SettingsJumpResult("l_search", "Search Bar Config", "Launcher: Position, sources & engines", launcherTabIndex = 3),
            SettingsJumpResult("l_physics", "Physics & Gestures", "Launcher: Spring bounce & swipes", launcherTabIndex = 4),
            SettingsJumpResult("l_widgets", "Widgets & Context Rules", "Launcher: 11 Nothing widgets & auto-profiles", launcherTabIndex = 5),
            SettingsJumpResult("l_backup", "Backup & QR Clone", "Launcher: Export/Import JSON & QR", launcherTabIndex = 6)
        )
        return catalog.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
        }.take(5)
    }

    fun executeWebSearch(engine: CustomSearchEngine, query: String) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val targetUrl = engine.urlTemplate.replace("%s", encoded)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun smsNumber(number: String) {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openMessagingApp(packageName: String, phoneNumber: String) {
        val cleanNum = phoneNumber.filter { it.isDigit() || it == '+' }
        val uri = if (packageName == "com.whatsapp") {
            Uri.parse("https://wa.me/${cleanNum.removePrefix("+")}")
        } else {
            Uri.parse("https://t.me/$cleanNum")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isFailure) {
            val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    fun fireTimer(result: UtilityResult.QuickTimer) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, result.durationSeconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, result.label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun fireAlarm(result: UtilityResult.QuickAlarm) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, result.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, result.minute)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openFile(file: LocalFileResult) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(file.uri, file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openSystemSetting(action: String) {
        val intent = Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
