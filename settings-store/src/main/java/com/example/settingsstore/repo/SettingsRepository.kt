package com.example.settingsstore.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.settingsstore.db.LauncherDatabase
import com.example.settingsstore.db.ThemePresetEntity
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.GestureAction
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SurfaceThemeConfig
import com.example.settingsstore.model.WallpaperMode
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "dotos_settings")

class SettingsRepository(private val context: Context) {
    private val database = LauncherDatabase.getInstance(context)
    private val themePresetDao = database.themePresetDao()

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val settingsAdapter = moshi.adapter(LauncherSettings::class.java)
    private val themeAdapter = moshi.adapter(SurfaceThemeConfig::class.java)

    private val keySettingsJson = stringPreferencesKey("launcher_settings_json")

    private val _settings = MutableStateFlow(LauncherSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    val themePresets: Flow<List<Pair<ThemePresetEntity, SurfaceThemeConfig>>> =
        themePresetDao.observePresets().map { list ->
            list.mapNotNull { entity ->
                runCatching { themeAdapter.fromJson(entity.themeJson) }.getOrNull()?.let { entity to it }
            }
        }

    suspend fun initialize() {
        seedBuiltInPresets()
        val storedJson = context.launcherDataStore.data.map { it[keySettingsJson] }.first()
        if (!storedJson.isNullOrBlank()) {
            runCatching { settingsAdapter.fromJson(storedJson) }.getOrNull()?.let { loaded ->
                val storedVersion = Regex("\"schemaVersion\"\\s*:\\s*(\\d+)")
                    .find(storedJson)
                    ?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0
                val finalSettings = if (storedVersion < 4) {
                    var migrated = loaded.copy(schemaVersion = 4)
                    if (storedVersion < 3) {
                        // v1.3 defaults: clean 5-slot dock, no accidental search-on-swipe, immersive on.
                        migrated = migrated.copy(
                            fullScreenHideStatusBar = true,
                            grid = migrated.grid.copy(
                                dockMiniChips = emptyList(),
                                dockPageCount = 1,
                                dockSlotsPerPage = 5
                            ),
                            gestures = migrated.gestures.copy(
                                swipeUp = GestureAction.NONE,
                                swipeDown = if (migrated.gestures.swipeDown == GestureAction.OPEN_SEARCH) {
                                    GestureAction.NONE
                                } else {
                                    migrated.gestures.swipeDown
                                },
                                iconSwipeUp = if (migrated.gestures.iconSwipeUp == GestureAction.OPEN_SEARCH) {
                                    GestureAction.NONE
                                } else {
                                    migrated.gestures.iconSwipeUp
                                }
                            )
                        )
                    }
                    persistSettings(migrated)
                    migrated
                } else {
                    loaded
                }
                _settings.value = finalSettings
            }
        }
    }

    suspend fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        persistSettings(updated)
    }

    private suspend fun persistSettings(settings: LauncherSettings) {
        withContext(Dispatchers.IO) {
            val json = settingsAdapter.toJson(settings)
            context.launcherDataStore.edit { prefs ->
                prefs[keySettingsJson] = json
            }
        }
    }

    suspend fun saveThemePreset(name: String, theme: SurfaceThemeConfig) {
        val id = "custom_${System.currentTimeMillis()}"
        val cleanTheme = theme.copy(presetId = id, presetName = name)
        themePresetDao.upsertPreset(
            ThemePresetEntity(
                id = id,
                name = name,
                themeJson = themeAdapter.toJson(cleanTheme),
                isBuiltIn = false
            )
        )
        updateSettings { it.copy(theme = cleanTheme) }
    }

    suspend fun applyPresetById(presetId: String) {
        val presets = themePresetDao.observePresets().first()
        val found = presets.firstOrNull { it.id == presetId } ?: return
        val parsed = runCatching { themeAdapter.fromJson(found.themeJson) }.getOrNull() ?: return
        updateSettings { it.copy(theme = parsed) }
    }

    suspend fun deleteCustomPreset(id: String) {
        themePresetDao.deleteCustomPreset(id)
    }

    fun exportToJson(): String = settingsAdapter.toJson(_settings.value)

    suspend fun importFromJson(rawJsonOrPayload: String): Result<LauncherSettings> = withContext(Dispatchers.IO) {
        runCatching {
            val json = if (rawJsonOrPayload.startsWith("DOTOS_QR1:")) {
                decompressPayload(rawJsonOrPayload.removePrefix("DOTOS_QR1:"))
            } else {
                rawJsonOrPayload.trim()
            }
            val parsed = settingsAdapter.fromJson(json)
                ?: throw IllegalArgumentException("Invalid DotOS configuration payload")
            _settings.value = parsed
            persistSettings(parsed)
            parsed
        }
    }

    fun generateQrBitmap(sizePx: Int = 640): Bitmap {
        val current = _settings.value
        val prunedWidgetConfigs = current.widgetConfigs.filter { (key, value) ->
            val wt = runCatching { CustomWidgetType.valueOf(key) }.getOrNull()
            wt != null && value != WidgetAppearanceConfig(widgetType = wt)
        }
        val compactSettings = current.copy(widgetConfigs = prunedWidgetConfigs)
        val json = settingsAdapter.toJson(compactSettings)
        val compactPayload = "DOTOS_QR1:" + compressPayload(json)
        val hints = mapOf(
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
        )
        val bitMatrix = QRCodeWriter().encode(compactPayload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }

    fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return runCatching {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val decodeHints = mapOf(
                com.google.zxing.DecodeHintType.TRY_HARDER to true,
                com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            MultiFormatReader().decode(binaryBitmap, decodeHints).text
        }.getOrNull()
    }

    private fun compressPayload(input: String): String {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(input.toByteArray(Charsets.UTF_8)) }
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }

    private fun decompressPayload(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        return GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private suspend fun seedBuiltInPresets() {
        val builtIns = defaultThemePresets().map { theme ->
            ThemePresetEntity(
                id = theme.presetId,
                name = theme.presetName,
                themeJson = themeAdapter.toJson(theme),
                isBuiltIn = true,
                createdAt = 0L
            )
        }
        themePresetDao.insertBuiltIns(builtIns)
    }

    companion object {
        fun defaultThemePresets(): List<SurfaceThemeConfig> = listOf(
            SurfaceThemeConfig(
                presetId = "classic_mono",
                presetName = "Classic Monochrome",
                oledPureBlack = true,
                backgroundHex = "#000000",
                surfaceHex = "#111114",
                accentHex = "#D71921",
                textPrimaryHex = "#FFFFFF",
                textSecondaryHex = "#9A9AA2",
                glyphHighlightHex = "#D71921",
                wallpaperMode = WallpaperMode.DOT_MATRIX_GRID
            ),
            SurfaceThemeConfig(
                presetId = "dark_glyph",
                presetName = "Dark Glyph",
                oledPureBlack = false,
                backgroundHex = "#0B0C10",
                surfaceHex = "#16181F",
                accentHex = "#FFFFFF",
                textPrimaryHex = "#F5F5F7",
                textSecondaryHex = "#8E93A4",
                glyphHighlightHex = "#FFFFFF",
                wallpaperMode = WallpaperMode.TOPOGRAPHIC_CONTOURS
            ),
            SurfaceThemeConfig(
                presetId = "inverted_light",
                presetName = "Inverted Light",
                oledPureBlack = false,
                backgroundHex = "#F2F2F0",
                surfaceHex = "#FFFFFF",
                accentHex = "#D71921",
                textPrimaryHex = "#111113",
                textSecondaryHex = "#5A5A62",
                drawerBgHex = "#EAEAE6",
                widgetBgHex = "#FFFFFF",
                folderBgHex = "#F7F7F5",
                glyphHighlightHex = "#D71921",
                wallpaperMode = WallpaperMode.DOT_MATRIX_GRID
            ),
            SurfaceThemeConfig(
                presetId = "amber_terminal",
                presetName = "Amber Terminal",
                oledPureBlack = true,
                backgroundHex = "#000000",
                surfaceHex = "#151008",
                accentHex = "#FF9E1B",
                textPrimaryHex = "#FFD580",
                textSecondaryHex = "#9E7840",
                drawerBgHex = "#0D0904",
                widgetBgHex = "#161108",
                folderBgHex = "#1B140A",
                glyphHighlightHex = "#FF9E1B",
                wallpaperMode = WallpaperMode.DOT_MATRIX_GRID
            ),
            SurfaceThemeConfig(
                presetId = "cyber_green",
                presetName = "Cyber Green",
                oledPureBlack = true,
                backgroundHex = "#000000",
                surfaceHex = "#08140E",
                accentHex = "#00E676",
                textPrimaryHex = "#D6FFE8",
                textSecondaryHex = "#5EA882",
                drawerBgHex = "#040D08",
                widgetBgHex = "#0A1710",
                folderBgHex = "#0D1E15",
                glyphHighlightHex = "#00E676",
                wallpaperMode = WallpaperMode.TOPOGRAPHIC_CONTOURS
            )
        )
    }
}
