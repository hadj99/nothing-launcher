package com.example.customwidgets

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.coreui.DotMatrixDivider
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSurfaceCard
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// PRECISE WEATHER LOCATION PICKER DIALOG (GPS + City/Neighborhood Search + Exact Lat/Lon)
@Composable
fun PreciseWeatherLocationDialog(
    config: WidgetAppearanceConfig,
    weatherRepo: WeatherRepository?,
    onApplyLocation: (WidgetAppearanceConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNothingPalette.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf(config.weatherCity) }
    var searchResults by remember { mutableStateOf<List<GeocodingResultDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    var latText by remember { mutableStateOf(config.weatherCustomLat?.toString().orEmpty()) }
    var lonText by remember { mutableStateOf(config.weatherCustomLon?.toString().orEmpty()) }
    var showCoordsInput by remember { mutableStateOf(config.weatherCustomLat != null) }

    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onApplyLocation(
            config.copy(
                weatherUsePreciseGps = true,
                weatherCustomLat = null,
                weatherCustomLon = null
            )
        )
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PRECISE WEATHER LOCATION",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 15.sp,
                    color = palette.accent
                )
                Text(
                    text = "Use live GPS coordinates, search your exact city/district, or enter exact latitude & longitude.",
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )

                // Option 1: 1-Tap Precise GPS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .border(1.dp, palette.accent, RoundedCornerShape(14.dp))
                        .clickable {
                            gpsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.MyLocation, null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "USE MY PRECISE GPS LOCATION",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 12.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "Auto-detects exact street/district GPS fix",
                            fontSize = 10.5.sp,
                            color = palette.textSecondary
                        )
                    }
                }

                DotMatrixDivider()

                // Option 2: Search City / District / Town
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("City, Town, or District") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (weatherRepo != null && searchQuery.isNotBlank()) {
                                searching = true
                                scope.launch {
                                    searchResults = weatherRepo.searchLocations(searchQuery)
                                    searching = false
                                }
                            } else if (searchQuery.isNotBlank()) {
                                onApplyLocation(
                                    config.copy(
                                        weatherCity = searchQuery.trim(),
                                        weatherUsePreciseGps = false,
                                        weatherCustomLat = null,
                                        weatherCustomLon = null
                                    )
                                )
                                onDismiss()
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Search, "Search City", tint = palette.accent)
                    }
                }

                if (searching) {
                    Text("Searching matching locations...", fontSize = 11.sp, color = palette.textSecondary)
                }

                searchResults.forEach { place ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(12.dp))
                            .clickable {
                                onApplyLocation(
                                    config.copy(
                                        weatherCity = place.displayLabel(),
                                        weatherUsePreciseGps = false,
                                        weatherCustomLat = place.latitude,
                                        weatherCustomLon = place.longitude
                                    )
                                )
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(place.displayLabel(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                            Text(
                                text = String.format(Locale.US, "%.4f°, %.4f°", place.latitude, place.longitude),
                                fontFamily = NothingTypographyEngine.ntype82Family,
                                fontSize = 10.sp,
                                color = palette.textSecondary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                    }
                }

                // Option 3: Exact Latitude / Longitude
                NothingPillChip(
                    label = if (showCoordsInput) "Hide Exact Coordinates" else "Enter Exact Lat / Lon Coordinates",
                    selected = showCoordsInput,
                    onClick = { showCoordsInput = !showCoordsInput }
                )

                if (showCoordsInput) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitude (e.g. 35.697)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it },
                            label = { Text("Longitude (e.g. -0.631)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(6.dp))
                    Button(
                        onClick = {
                            val parsedLat = latText.trim().toDoubleOrNull()
                            val parsedLon = lonText.trim().toDoubleOrNull()
                            if (showCoordsInput && parsedLat != null && parsedLon != null) {
                                onApplyLocation(
                                    config.copy(
                                        weatherCity = searchQuery.trim().ifBlank {
                                            String.format(Locale.US, "%.3f°, %.3f°", parsedLat, parsedLon)
                                        },
                                        weatherUsePreciseGps = false,
                                        weatherCustomLat = parsedLat,
                                        weatherCustomLon = parsedLon
                                    )
                                )
                            } else {
                                onApplyLocation(
                                    config.copy(
                                        weatherCity = searchQuery.trim().ifBlank { "Oran" },
                                        weatherUsePreciseGps = false,
                                        weatherCustomLat = null,
                                        weatherCustomLon = null
                                    )
                                )
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Save & Update", color = Color.White)
                    }
                }
            }
        }
    }
}

// 12. NEWS PULSE WIDGET (Live Headlines on Home Screen)
@Composable
fun NewsPulseWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onOpenNewsSpace: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    val scope = rememberCoroutineScope()
    val newsState by NewsRepository.state.collectAsState()
    var storyIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        NewsRepository.refresh(force = false)
    }

    val articles = newsState.articles
    val currentArticle = articles.getOrNull(storyIdx % articles.size.coerceAtLeast(1))
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { onOpenNewsSpace() }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Newspaper, null, tint = palette.accent, modifier = Modifier.size(15.dp))
                Text(
                    text = "${newsState.activeCategory.sourceBadge}  •  ${if (newsState.isLoading) "SYNCING..." else "NEWS PULSE"}",
                    style = titleStyle.copy(color = palette.accent, fontSize = 10.sp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                        .clickable {
                            if (articles.isNotEmpty()) {
                                storyIdx = (storyIdx + 1) % articles.size
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "NEXT ›",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 9.sp,
                        color = palette.textPrimary
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh News",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { scope.launch { NewsRepository.refresh(force = true) } }
                )
            }
        }

        if (currentArticle != null) {
            Text(
                text = currentArticle.title,
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = (13.5f * config.fontSizeScale).sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentArticle.pubDate}  •  TAP FOR NEWS SPACE",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 9.5.sp,
                    color = palette.textSecondary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Read Article",
                    tint = palette.accent,
                    modifier = Modifier
                        .size(15.dp)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(currentArticle.link)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        }
                )
            }
        }
    }
}

// 13. QUICK NOTES WIDGET + BUILT-IN STICKY NOTE DIALOG
@Composable
fun QuickNotesWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onSaveNote: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { showEditor = true }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "QUICK NOTES",
                style = titleStyle.copy(color = palette.accent, fontSize = 10.5.sp)
            )
            Icon(Icons.Rounded.Edit, "Edit Note", tint = palette.textSecondary, modifier = Modifier.size(15.dp))
        }

        Text(
            text = settings.quickNoteText.ifBlank { "Tap to write a quick note..." },
            fontFamily = NothingTypographyEngine.ntype82Family,
            fontSize = (12.5f * config.fontSizeScale).sp,
            color = palette.textPrimary,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(vertical = 6.dp)
        )

        Text(
            text = "${settings.quickNoteText.length} CHARS  •  TAP TO EDIT",
            fontFamily = NothingTypographyEngine.ntype82Family,
            fontSize = 9.sp,
            color = palette.textSecondary
        )
    }

    if (showEditor) {
        QuickNotesEditorDialog(
            initialText = settings.quickNoteText,
            onSave = {
                onSaveNote(it)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
fun QuickNotesEditorDialog(
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNothingPalette.current
    var draft by remember { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismiss) {
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DOTOS QUICK NOTES",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 15.sp,
                        color = palette.accent
                    )
                    TextButton(onClick = { draft = "" }) {
                        Text("Clear", color = palette.textSecondary, fontSize = 11.sp)
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Write notes, checklists, or ideas...") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(draft) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Save Note", color = Color.White)
                    }
                }
            }
        }
    }
}

// 14. SYSTEM MATRIX WIDGET (Live RAM, Storage & Display Hz)
@Composable
fun SystemMatrixWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    telemetry: DeviceTelemetrySnapshot,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    var refreshTick by remember { mutableIntStateOf(0) }

    val stats = remember(refreshTick) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamGb = (memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(1.0)
        val usedRamGb = ((memInfo.totalMem - memInfo.availMem) / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(0.1)
        val ramPct = ((usedRamGb / totalRamGb) * 100.0).roundToInt().coerceIn(1, 99)

        val statFs = StatFs(Environment.getDataDirectory().path)
        val freeStorageGb = (statFs.availableBytes / (1024.0 * 1024.0 * 1024.0))
        val totalStorageGb = (statFs.totalBytes / (1024.0 * 1024.0 * 1024.0)).coerceAtLeast(1.0)
        val storageUsedPct = (((totalStorageGb - freeStorageGb) / totalStorageGb) * 100.0).roundToInt().coerceIn(1, 99)

        val hz = runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val rate = wm?.defaultDisplay?.refreshRate ?: 60f
            rate.roundToInt()
        }.getOrDefault(120)

        Triple(ramPct to String.format(Locale.US, "%.1f/%.0f GB", usedRamGb, totalRamGb), storageUsedPct to String.format(Locale.US, "%.0f GB FREE", freeStorageGb), hz)
    }

    val (ramPair, storagePair, hz) = stats

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { refreshTick++ }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SYSTEM MATRIX",
                style = titleStyle.copy(color = palette.textSecondary, fontSize = 10.sp)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${hz}HZ  •  BAT ${telemetry.batteryPercent}%",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 9.5.sp,
                    color = palette.accent
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DotTelemetryBar(
                label = "RAM ${ramPair.first}%",
                detail = ramPair.second,
                percent = ramPair.first,
                accent = palette.accent,
                primary = palette.textPrimary,
                subtle = palette.borderSubtle
            )
            DotTelemetryBar(
                label = "ROM ${storagePair.first}%",
                detail = storagePair.second,
                percent = storagePair.first,
                accent = palette.textPrimary,
                primary = palette.textPrimary,
                subtle = palette.borderSubtle
            )
        }

        Text(
            text = "ANDROID ${Build.VERSION.RELEASE}  •  TAP TO REFRESH",
            fontFamily = NothingTypographyEngine.ntype82Family,
            fontSize = 9.sp,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun DotTelemetryBar(
    label: String,
    detail: String,
    percent: Int,
    accent: Color,
    primary: Color,
    subtle: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 11.sp, color = primary)
            Text(detail, fontFamily = NothingTypographyEngine.ntype82Family, fontSize = 10.sp, color = accent)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val dots = 22
            val active = ((percent / 100f) * dots).roundToInt().coerceIn(1, dots)
            val step = size.width / dots
            for (i in 0 until dots) {
                drawCircle(
                    color = if (i < active) accent else subtle,
                    radius = 2.8f,
                    center = Offset(i * step + step * 0.5f, size.height * 0.5f)
                )
            }
        }
    }
}

// 15. EVENT COUNTDOWN MATRIX WIDGET
@Composable
fun EventCountdownWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onUpdateCountdown: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val dataStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData)
    var showEditor by remember { mutableStateOf(false) }

    val days = config.countdownDaysFromNow.coerceIn(0, 999)

    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable { showEditor = true }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = config.countdownTitle.uppercase(),
                style = titleStyle.copy(color = palette.accent, fontSize = 10.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$days DAYS",
                style = dataStyle.copy(
                    color = palette.textPrimary,
                    fontSize = (26f * config.fontSizeScale).sp
                )
            )
            Text(
                text = "${days * 24} HOURS LEFT  •  TAP TO SET",
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 9.5.sp,
                color = palette.textSecondary
            )
        }

        Canvas(modifier = Modifier.size(72.dp)) {
            val cols = 6
            val rows = 5
            val total = cols * rows
            val lit = (total - (days % total)).coerceIn(3, total)
            val stepX = size.width / cols
            val stepY = size.height / rows
            var idx = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val active = idx < lit
                    drawCircle(
                        color = if (active) palette.accent else palette.borderSubtle,
                        radius = if (active) 3.4f else 2.0f,
                        center = Offset((c + 0.5f) * stepX, (r + 0.5f) * stepY)
                    )
                    idx++
                }
            }
        }
    }

    if (showEditor) {
        var titleDraft by remember { mutableStateOf(config.countdownTitle) }
        var daysDraft by remember { mutableStateOf(days.toString()) }
        Dialog(onDismissRequest = { showEditor = false }) {
            NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "SET COUNTDOWN TARGET",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 14.sp,
                        color = palette.accent
                    )
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        label = { Text("Event Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = daysDraft,
                        onValueChange = { if (it.all { ch -> ch.isDigit() } && it.length <= 3) daysDraft = it },
                        label = { Text("Days Remaining") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showEditor = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onUpdateCountdown(
                                    titleDraft.ifBlank { "MILESTONE" },
                                    daysDraft.toIntOrNull()?.coerceIn(0, 999) ?: 30
                                )
                                showEditor = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
