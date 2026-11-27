package com.example.customwidgets

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coreui.NothingPillChip
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.TimeFormatMode
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 1. MOON PHASE WIDGET
@Composable
fun MoonPhaseWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onTapAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val dataStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData)

    val lunar = remember { computeLunarState(System.currentTimeMillis()) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable { onTapAction() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = NothingTypographyEngine.formatText("LUNAR PHASE", settings.typographyWidgetTitle.textCase),
                style = titleStyle.copy(
                    color = palette.textSecondary,
                    fontSize = (titleStyle.fontSize.value * config.fontSizeScale).sp
                )
            )
            Text(
                text = "${lunar.illuminationPercent}%",
                style = dataStyle.copy(
                    color = palette.textPrimary,
                    fontSize = (dataStyle.fontSize.value * config.fontSizeScale).sp
                )
            )
            Text(
                text = lunar.phaseTitle,
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = (13f * config.fontSizeScale).sp,
                color = palette.accent
            )
            if (config.showMoonTimes) {
                Text(
                    text = "RISE ${lunar.moonrise}  •  SET ${lunar.moonset}",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 10.5.sp,
                    color = palette.textSecondary
                )
            }
        }

        Canvas(modifier = Modifier.size(96.dp)) {
            val r = size.minDimension * 0.44f
            val c = Offset(size.width / 2f, size.height / 2f)
            // Outer orbital dot ring
            val ringDots = (16 + (config.dotDensity * 20).toInt()).coerceIn(12, 36)
            for (i in 0 until ringDots) {
                val angle = (2.0 * PI * i / ringDots)
                val dotPos = Offset(
                    c.x + (r * 1.12f) * cos(angle).toFloat(),
                    c.y + (r * 1.12f) * sin(angle).toFloat()
                )
                drawCircle(
                    color = palette.borderSubtle,
                    radius = 1.6f,
                    center = dotPos
                )
            }
            // Dark side
            drawCircle(
                color = palette.surface,
                radius = r,
                center = c
            )
            drawCircle(
                color = palette.borderSubtle,
                radius = r,
                center = c,
                style = Stroke(width = 2f)
            )
            // Illuminated dots clipped by lunar phase
            val grid = 13
            val step = (r * 2f) / grid
            val phaseFrac = lunar.ageDays / 29.530588853
            for (gx in 0 until grid) {
                for (gy in 0 until grid) {
                    val px = c.x - r + (gx + 0.5f) * step
                    val py = c.y - r + (gy + 0.5f) * step
                    val dx = (px - c.x) / r
                    val dy = (py - c.y) / r
                    if (dx * dx + dy * dy <= 0.92f) {
                        val lit = isMoonPixelLit(dx, dy, phaseFrac)
                        if (lit) {
                            drawCircle(
                                color = palette.textPrimary,
                                radius = step * 0.34f,
                                center = Offset(px, py)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class LunarState(
    val ageDays: Double,
    val illuminationPercent: Int,
    val phaseTitle: String,
    val moonrise: String,
    val moonset: String
)

private fun computeLunarState(nowMillis: Long): LunarState {
    // Known New Moon epoch: 2000-01-06 18:14 UTC
    val epochMillis = 947182440000L
    val synodicMonth = 29.530588853
    val daysSince = (nowMillis - epochMillis) / 86_400_000.0
    val age = ((daysSince % synodicMonth) + synodicMonth) % synodicMonth
    val illumination = (((1.0 - cos(2.0 * PI * age / synodicMonth)) / 2.0) * 100.0).roundToInt()

    val title = when {
        age < 1.84566 -> "NEW MOON"
        age < 5.53699 -> "WAXING CRESCENT"
        age < 9.22831 -> "FIRST QUARTER"
        age < 12.91963 -> "WAXING GIBBOUS"
        age < 16.61096 -> "FULL MOON"
        age < 20.30228 -> "WANING GIBBOUS"
        age < 23.99361 -> "LAST QUARTER"
        age < 27.68493 -> "WANING CRESCENT"
        else -> "NEW MOON"
    }
    val riseHour = ((6 + (age * 0.81)).toInt()) % 24
    val setHour = ((18 + (age * 0.81)).toInt()) % 24
    return LunarState(
        ageDays = age,
        illuminationPercent = illumination,
        phaseTitle = title,
        moonrise = String.format(Locale.US, "%02d:18", riseHour),
        moonset = String.format(Locale.US, "%02d:42", setHour)
    )
}

private fun isMoonPixelLit(nx: Float, ny: Float, phaseFrac: Double): Boolean {
    val terminatorX = cos(2.0 * PI * phaseFrac).toFloat() * kotlin.math.sqrt((1f - ny * ny).coerceAtLeast(0f))
    return if (phaseFrac <= 0.5) {
        nx >= terminatorX
    } else {
        nx <= -terminatorX
    }
}

// 2. STEP COUNTER WIDGET
@Composable
fun StepCounterWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    stepsToday: Int,
    sensorAvailable: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onTapAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val dataStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData)
    var showCalories by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onRequestPermission()
    }

    val goal = config.dailyStepGoal.coerceAtLeast(1000)
    val progress = (stepsToday.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
    val distanceKm = String.format(Locale.US, "%.2f km", stepsToday * 0.00076)
    val kcal = (stepsToday * 0.04).roundToInt()

    Row(
        modifier = modifier
            .fillMaxSize()
            .clickable {
                showCalories = !showCalories
                onTapAction()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = NothingTypographyEngine.formatText("PEDOMETER", settings.typographyWidgetTitle.textCase),
                style = titleStyle.copy(color = palette.textSecondary)
            )
            Text(
                text = "$stepsToday",
                style = dataStyle.copy(
                    color = palette.textPrimary,
                    fontSize = (dataStyle.fontSize.value * config.fontSizeScale).sp
                )
            )
            Text(
                text = if (showCalories) "$kcal KCAL  •  GOAL $goal" else "$distanceKm  •  GOAL $goal",
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 11.sp,
                color = palette.accent
            )
            if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                NothingPillChip(
                    label = "Grant Step Sensor",
                    selected = false,
                    onClick = { permLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION) }
                )
            } else if (!sensorAvailable) {
                Text(
                    text = "Hardware step sensor unavailable on this device",
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
            }
        }

        Canvas(modifier = Modifier.size(92.dp)) {
            val totalDots = (18 + (config.dotDensity * 18).toInt()).coerceIn(14, 36)
            val activeDots = (totalDots * progress).roundToInt()
            val c = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.40f
            for (i in 0 until totalDots) {
                val angle = -PI / 2.0 + (2.0 * PI * i / totalDots)
                val pos = Offset(
                    c.x + radius * cos(angle).toFloat(),
                    c.y + radius * sin(angle).toFloat()
                )
                val isActive = i < activeDots
                drawCircle(
                    color = if (isActive) palette.accent else palette.borderSubtle,
                    radius = if (isActive) 4.2f else 2.4f,
                    center = pos
                )
            }
            drawCircle(
                color = palette.borderSubtle,
                radius = radius * 0.56f,
                center = c,
                style = Stroke(width = 1.2f)
            )
        }
    }
}

// 3. TIME & DATE WIDGET
@Composable
fun TimeDateWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onCycleFormat: () -> Unit,
    animationsPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val needsSecondTick = config.timeFormatMode == TimeFormatMode.VERTICAL_DOT
    LaunchedEffect(animationsPaused, needsSecondTick) {
        if (animationsPaused) return@LaunchedEffect
        while (true) {
            val current = System.currentTimeMillis()
            nowMillis = current
            val sleepMs = if (needsSecondTick) {
                1000L - (current % 1000L)
            } else {
                (60_000L - (current % 60_000L)).coerceIn(1_000L, 60_000L)
            }
            delay(sleepMs.coerceAtLeast(250L))
        }
    }

    val hhFmt = remember { SimpleDateFormat("HH", Locale.getDefault()) }
    val mmFmt = remember { SimpleDateFormat("mm", Locale.getDefault()) }
    val ssFmt = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, MMM dd", Locale.getDefault()) }

    val bucketKey = if (needsSecondTick) nowMillis / 1000L else nowMillis / 60_000L
    val date = remember(bucketKey) { Date(nowMillis) }
    val hourStr = remember(bucketKey) { hhFmt.format(date) }
    val minStr = remember(bucketKey) { mmFmt.format(date) }
    val secStr = remember(bucketKey) { if (needsSecondTick) ssFmt.format(date) else "00" }
    val dateLabel = remember(bucketKey) { dateFmt.format(date).uppercase() }

    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val dataStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onCycleFormat() }
            .padding(16.dp)
    ) {
        when (config.timeFormatMode) {
            TimeFormatMode.VERTICAL_DOT -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = hourStr,
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = (40f * config.fontSizeScale).sp,
                            color = palette.textPrimary,
                            lineHeight = (42f * config.fontSizeScale).sp
                        )
                        Text(
                            text = minStr,
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = (40f * config.fontSizeScale).sp,
                            color = palette.accent,
                            lineHeight = (42f * config.fontSizeScale).sp
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = dateLabel, style = titleStyle.copy(color = palette.textSecondary))
                        Text(
                            text = ":$secStr",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 18.sp,
                            color = palette.textPrimary
                        )
                        Text(
                            text = "TAP FORMAT",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 9.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }

            TimeFormatMode.ANALOG_DOT_HYBRID -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val cal = remember(nowMillis / 1000L) { Calendar.getInstance().apply { timeInMillis = nowMillis } }
                    Canvas(modifier = Modifier.size(96.dp)) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension * 0.44f
                        for (i in 0 until 12) {
                            val a = -PI / 2.0 + (2.0 * PI * i / 12.0)
                            drawCircle(
                                color = if (i % 3 == 0) palette.textPrimary else palette.borderSubtle,
                                radius = if (i % 3 == 0) 3.2f else 1.8f,
                                center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
                            )
                        }
                        val minVal = cal.get(Calendar.MINUTE)
                        val hrVal = cal.get(Calendar.HOUR) + minVal / 60f
                        val hrAngle = -PI / 2.0 + (2.0 * PI * hrVal / 12.0)
                        val minAngle = -PI / 2.0 + (2.0 * PI * minVal / 60.0)
                        drawLine(
                            color = palette.textPrimary,
                            start = c,
                            end = Offset(c.x + r * 0.55f * cos(hrAngle).toFloat(), c.y + r * 0.55f * sin(hrAngle).toFloat()),
                            strokeWidth = 5f
                        )
                        drawLine(
                            color = palette.accent,
                            start = c,
                            end = Offset(c.x + r * 0.82f * cos(minAngle).toFloat(), c.y + r * 0.82f * sin(minAngle).toFloat()),
                            strokeWidth = 3f
                        )
                        drawCircle(color = palette.accent, radius = 4.5f, center = c)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "$hourStr:$minStr", style = dataStyle.copy(color = palette.textPrimary))
                        Text(text = dateLabel, style = titleStyle.copy(color = palette.textSecondary))
                    }
                }
            }

            TimeFormatMode.DUAL_TIMEZONE -> {
                val utcFmt = remember {
                    SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                }
                val tokyoFmt = remember {
                    SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("LOCAL", style = titleStyle.copy(color = palette.accent))
                        Text("$hourStr:$minStr", style = dataStyle.copy(color = palette.textPrimary))
                        Text(dateLabel, fontSize = 11.sp, color = palette.textSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("UTC", fontSize = 10.sp, color = palette.textSecondary)
                            Text(utcFmt.format(date), fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 18.sp, color = palette.textPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("TOKYO", fontSize = 10.sp, color = palette.textSecondary)
                            Text(tokyoFmt.format(date), fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 18.sp, color = palette.textPrimary)
                        }
                    }
                }
            }

            TimeFormatMode.SINGLE_LINE -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(dateLabel, style = titleStyle.copy(color = palette.accent))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "$hourStr:$minStr:$secStr",
                        style = dataStyle.copy(
                            color = palette.textPrimary,
                            fontSize = (34f * config.fontSizeScale).sp
                        )
                    )
                }
            }
        }
    }
}

// 4. WEATHER WIDGET
@Composable
fun WeatherWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    weatherState: WeatherState,
    onRefreshWeather: () -> Unit,
    weatherRepo: WeatherRepository? = null,
    onUpdateWeatherConfig: ((WidgetAppearanceConfig) -> WidgetAppearanceConfig) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val scope = rememberCoroutineScope()
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val dataStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData)
    var showLocationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(config.weatherCity, config.weatherUsePreciseGps, config.weatherCustomLat, config.weatherCustomLon) {
        if (weatherState.temperatureC == null && !weatherState.isLoading) {
            onRefreshWeather()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { scope.launch { onRefreshWeather() } }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${weatherState.cityLabel.uppercase()} • ${weatherState.conditionText}",
                    style = titleStyle.copy(color = palette.textSecondary),
                    modifier = Modifier.clickable { showLocationDialog = true }
                )
                val tempLabel = weatherState.temperatureC?.let { "${it.roundToInt()}°C" }
                    ?: if (weatherState.isLoading) "..." else "--°C"
                Text(
                    text = tempLabel,
                    style = dataStyle.copy(
                        color = palette.textPrimary,
                        fontSize = (dataStyle.fontSize.value * config.fontSizeScale).sp
                    )
                )
            }

            // Custom glyph weather vector on Canvas
            Canvas(modifier = Modifier.size(56.dp)) {
                val c = Offset(size.width / 2f, size.height / 2f)
                val isClear = weatherState.weatherCode <= 1
                if (isClear) {
                    drawCircle(color = palette.accent, radius = size.minDimension * 0.22f, center = c)
                    for (i in 0 until 8) {
                        val a = 2.0 * PI * i / 8.0
                        drawCircle(
                            color = palette.textPrimary,
                            radius = 2.4f,
                            center = Offset(
                                c.x + size.minDimension * 0.38f * cos(a).toFloat(),
                                c.y + size.minDimension * 0.38f * sin(a).toFloat()
                            )
                        )
                    }
                } else {
                    // Dot-matrix cloud glyph
                    val offsets = listOf(
                        Offset(-0.22f, 0.1f), Offset(0f, 0.1f), Offset(0.22f, 0.1f),
                        Offset(-0.12f, -0.08f), Offset(0.10f, -0.10f)
                    )
                    offsets.forEach { o ->
                        drawCircle(
                            color = palette.textPrimary,
                            radius = size.minDimension * 0.12f,
                            center = Offset(c.x + o.x * size.width, c.y + o.y * size.height)
                        )
                    }
                    if (weatherState.weatherCode >= 51) {
                        for (r in -1..1) {
                            drawCircle(
                                color = palette.accent,
                                radius = 2.8f,
                                center = Offset(c.x + r * 12f, c.y + size.height * 0.32f)
                            )
                        }
                    }
                }
            }
        }

        if (weatherState.hourlyTemps.isNotEmpty()) {
            // Hourly dot-matrix temperature graph
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                val temps = weatherState.hourlyTemps
                val minT = temps.minOrNull() ?: 0.0
                val maxT = (temps.maxOrNull() ?: 1.0).let { if (it == minT) minT + 1.0 else it }
                val stepX = size.width / (temps.size - 1).coerceAtLeast(1)
                temps.forEachIndexed { idx, t ->
                    val norm = ((t - minT) / (maxT - minT)).toFloat()
                    val x = idx * stepX
                    val y = size.height - (norm * (size.height - 10f)) - 5f
                    drawCircle(
                        color = if (idx == 0) palette.accent else palette.textPrimary,
                        radius = if (idx == 0) 4.2f else 2.6f,
                        center = Offset(x, y)
                    )
                }
            }
        } else if (weatherState.errorMessage != null) {
            Text(
                text = "Tap to retry weather (${weatherState.errorMessage})",
                fontSize = 10.sp,
                color = palette.textSecondary
            )
        }

        if (config.showWeatherExtras) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HUM ${weatherState.humidityPercent}%  •  UV ${weatherState.uvIndex.roundToInt()}  •  ${weatherState.windKmh.roundToInt()} KM/H",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 10.sp,
                    color = palette.textSecondary
                )
                Text(
                    text = "SET LOCATION",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 10.sp,
                    color = palette.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.accent.copy(alpha = 0.16f))
                        .clickable { showLocationDialog = true }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }

    if (showLocationDialog) {
        PreciseWeatherLocationDialog(
            config = config,
            weatherRepo = weatherRepo,
            onApplyLocation = { updatedCfg ->
                onUpdateWeatherConfig { updatedCfg }
                onRefreshWeather()
            },
            onDismiss = { showLocationDialog = false }
        )
    }
}
