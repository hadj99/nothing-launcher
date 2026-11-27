package com.example.customwidgets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appindexer.ScreenTimeVelocity
import com.example.contextautomation.ActiveMediaState
import com.example.contextautomation.ContextAutomationEngine
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.coreui.NothingSurfaceCard
import com.example.settingsstore.db.StepHistoryEntity
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.QuickToggleItem
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.example.settingsstore.model.YearViewMode
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 5. STEPS COUNTER PROGRESS (ANALYTICS TRACKER)
@Composable
fun StepAnalyticsWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    history: List<StepHistoryEntity>,
    todaySteps: Int,
    onTapAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val goal = config.dailyStepGoal.coerceAtLeast(1000)

    val days = (history.take(7).reversed().ifEmpty {
        listOf(StepHistoryEntity("Today", todaySteps, goal))
    })
    val streak = history.takeWhile { it.steps >= it.goal }.count()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { onTapAction() }
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = NothingTypographyEngine.formatText("7D STEP ANALYTICS", settings.typographyWidgetTitle.textCase),
                style = titleStyle.copy(color = palette.textSecondary)
            )
            Text(
                text = "STREAK ${streak}D",
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 12.sp,
                color = palette.accent
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            val cols = 7
            val colWidth = size.width / cols
            val rows = 6
            val dotGap = size.height / rows
            for (c in 0 until cols) {
                val item = days.getOrNull(c)
                val ratio = if (item != null) (item.steps.toFloat() / item.goal.coerceAtLeast(1)).coerceIn(0f, 1.2f) else 0f
                val litRows = (ratio * rows).roundToInt().coerceIn(0, rows)
                val cx = c * colWidth + colWidth / 2f
                for (r in 0 until rows) {
                    val cy = size.height - (r + 0.5f) * dotGap
                    val isLit = r < litRows
                    val isGoalHit = ratio >= 1f
                    drawCircle(
                        color = when {
                            isLit && isGoalHit -> palette.accent
                            isLit -> palette.textPrimary
                            else -> palette.borderSubtle
                        },
                        radius = if (isLit) 4.2f else 2.2f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }

        Text(
            text = "TODAY $todaySteps / $goal STEPS",
            fontFamily = NothingTypographyEngine.ntype82Family,
            fontSize = 11.sp,
            color = palette.textSecondary
        )
    }
}

// 6. QUICK SETTINGS BAR
@Composable
fun QuickSettingsWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    telemetry: DeviceTelemetrySnapshot,
    contextEngine: ContextAutomationEngine,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val toggles = config.quickSettingToggles.take(6).ifEmpty {
        listOf(QuickToggleItem.WIFI, QuickToggleItem.BLUETOOTH, QuickToggleItem.FLASHLIGHT, QuickToggleItem.DND)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = NothingTypographyEngine.formatText("QUICK CONTROL", settings.typographyWidgetTitle.textCase),
                style = titleStyle.copy(color = palette.textSecondary)
            )
            Text(
                text = "BAT ${telemetry.batteryPercent}%",
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 11.sp,
                color = palette.accent
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            toggles.forEach { item ->
                val active = contextEngine.isToggleActive(item, telemetry)
                val icon = when (item) {
                    QuickToggleItem.WIFI -> Icons.Rounded.Wifi
                    QuickToggleItem.BLUETOOTH -> Icons.Rounded.Bluetooth
                    QuickToggleItem.FLASHLIGHT -> Icons.Rounded.FlashlightOn
                    QuickToggleItem.DND -> Icons.Rounded.DoNotDisturbOn
                    QuickToggleItem.BATTERY_SAVER -> Icons.Rounded.BatterySaver
                    QuickToggleItem.MOBILE_DATA -> Icons.Rounded.CellTower
                    QuickToggleItem.HOTSPOT -> Icons.Rounded.WifiTethering
                    QuickToggleItem.ROTATION_LOCK -> Icons.Rounded.ScreenRotation
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) palette.accent else palette.surface)
                        .border(1.dp, if (active) palette.accent else palette.borderSubtle, RoundedCornerShape(14.dp))
                        .clickable { contextEngine.triggerQuickToggle(item) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = item.label,
                        tint = if (active) Color.White else palette.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 9.sp,
                        color = if (active) Color.White else palette.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// 7. YEAR COUNT PROGRESS WIDGET
@Composable
fun YearProgressWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onCycleViewMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val dataStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData)

    val cal = Calendar.getInstance()
    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
    val remaining = (totalDays - dayOfYear).coerceAtLeast(0)
    val pct = ((dayOfYear.toFloat() / totalDays.toFloat()) * 100f).roundToInt()
    val year = cal.get(Calendar.YEAR)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable { onCycleViewMode() }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$year PROGRESS  •  $dayOfYear/$totalDays",
                style = titleStyle.copy(color = palette.textSecondary)
            )
            Text(
                text = "$pct%",
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 14.sp,
                color = palette.accent
            )
        }

        when (config.yearViewMode) {
            YearViewMode.DOT_MATRIX_365 -> {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                ) {
                    val cols = 31
                    val rows = (totalDays + cols - 1) / cols
                    val dx = size.width / cols
                    val dy = size.height / rows
                    for (i in 0 until totalDays) {
                        val c = i % cols
                        val r = i / cols
                        val elapsed = i < dayOfYear
                        drawCircle(
                            color = when {
                                i == dayOfYear - 1 -> palette.accent
                                elapsed -> palette.textPrimary.copy(alpha = 0.85f)
                                else -> palette.borderSubtle
                            },
                            radius = if (i == dayOfYear - 1) 3.2f else 1.8f,
                            center = Offset((c + 0.5f) * dx, (r + 0.5f) * dy)
                        )
                    }
                }
            }

            YearViewMode.PERCENT_BAR -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(11.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((dayOfYear.toFloat() / totalDays).coerceIn(0f, 1f))
                            .height(22.dp)
                            .background(palette.accent)
                    )
                }
            }

            YearViewMode.COUNTDOWN -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$remaining DAYS LEFT",
                        style = dataStyle.copy(
                            color = palette.textPrimary,
                            fontSize = (24f * config.fontSizeScale).sp
                        )
                    )
                    Text(
                        text = "IN $year",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 12.sp,
                        color = palette.accent
                    )
                }
            }
        }
    }
}

// 8. THE DIGITAL AURA / FOCUS PULSE WIDGET
@Composable
fun DigitalAuraFocusWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    velocity: ScreenTimeVelocity,
    onToggleDeepFocus: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)

    val effectiveVelocity = (velocity.velocityScore * config.auraSensitivity).coerceIn(0.08f, 1f)
    val pulseDuration = (2600 - (effectiveVelocity * 1700)).toInt().coerceIn(650, 2800)

    val infiniteTransition = rememberInfiniteTransition(label = "digital_aura")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aura_phase"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = NothingTypographyEngine.formatText("DIGITAL AURA", settings.typographyWidgetTitle.textCase),
                style = titleStyle.copy(color = palette.textSecondary)
            )
            Text(
                text = "${velocity.totalMinutesToday}m TODAY",
                fontFamily = NothingTypographyEngine.ndot77Family,
                fontSize = (22f * config.fontSizeScale).sp,
                color = palette.textPrimary
            )
            Text(
                text = if (settings.deepFocusActive) {
                    "DEEP FOCUS LOCKED"
                } else {
                    "VELOCITY ${(effectiveVelocity * 100).roundToInt()}%  •  ${velocity.recentHourMinutes}m/HR"
                },
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 10.sp,
                color = if (settings.deepFocusActive) palette.textSecondary else palette.accent
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (settings.deepFocusActive) palette.accent else palette.surface)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                        .clickable { onToggleDeepFocus() }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("deep_focus_toggle_button")
                ) {
                    Text(
                        text = if (settings.deepFocusActive) "EXIT FOCUS" else "DEEP FOCUS",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (settings.deepFocusActive) Color.White else palette.textPrimary
                    )
                }

                if (!velocity.usagePermissionGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                            .clickable { onOpenUsageAccess() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "SYNC",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 10.sp,
                            color = palette.accent
                        )
                    }
                }
            }
        }

        // Organic pulsating dot-waveform ring
        Canvas(modifier = Modifier.size(96.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension * 0.34f
            val dotCount = (20 + (config.dotDensity * 24).toInt()).coerceIn(16, 44)
            val rings = 2
            for (ring in 0 until rings) {
                val ringScale = if (ring == 0) 1f else 0.68f
                for (i in 0 until dotCount) {
                    val theta = 2.0 * PI * i / dotCount
                    val wave = sin(theta * 3.0 + pulsePhase + ring * 1.4).toFloat()
                    val rOffset = wave * (4f + effectiveVelocity * 9f)
                    val radius = baseR * ringScale + rOffset
                    val px = c.x + radius * cos(theta).toFloat()
                    val py = c.y + radius * sin(theta).toFloat()
                    val dotColor = when {
                        settings.deepFocusActive -> palette.textSecondary.copy(alpha = 0.65f)
                        ring == 0 && wave > 0.3f -> palette.accent
                        else -> palette.textPrimary.copy(alpha = 0.78f)
                    }
                    drawCircle(
                        color = dotColor,
                        radius = (2.1f + (wave + 1f) * 1.1f),
                        center = Offset(px, py)
                    )
                }
            }
            drawCircle(
                color = if (settings.deepFocusActive) palette.textSecondary else palette.accent,
                radius = 5f + sin(pulsePhase) * 2f,
                center = c
            )
            drawCircle(
                color = palette.borderSubtle,
                radius = baseR * 0.34f,
                center = c,
                style = Stroke(width = 1f)
            )
        }
    }
}

@Composable
fun NothingCustomWidgetCard(
    widgetType: CustomWidgetType,
    settings: LauncherSettings,
    stepsToday: Int,
    stepSensorAvailable: Boolean,
    hasStepPermission: Boolean,
    stepHistory: List<StepHistoryEntity>,
    weatherState: WeatherState,
    telemetry: DeviceTelemetrySnapshot,
    screenTimeVelocity: ScreenTimeVelocity,
    contextEngine: ContextAutomationEngine,
    onRequestStepSync: () -> Unit,
    onCycleTimeFormat: () -> Unit,
    onRefreshWeather: () -> Unit,
    onCycleYearMode: () -> Unit,
    onToggleDeepFocus: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenWidgetSettings: (CustomWidgetType) -> Unit,
    mediaState: ActiveMediaState = ActiveMediaState(),
    hasBluetoothPermission: Boolean = true,
    onRequestBluetoothPermission: () -> Unit = {},
    cardHeightDp: Int = 152,
    contentScale: Float = 1f,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val baseConfig = settings.widgetConfigFor(widgetType)
    val wConfig = if (contentScale == 1f) {
        baseConfig
    } else {
        baseConfig.copy(fontSizeScale = (baseConfig.fontSizeScale * contentScale).coerceIn(0.6f, 1.75f))
    }
    val effectiveOpacity = (wConfig.opacity * settings.theme.widgetOpacity).coerceIn(0.15f, 1f)
    val borderDp = ((wConfig.borderWidthDp + settings.theme.widgetBorderDp) * 0.5f).coerceIn(0f, 4f).dp

    NothingSurfaceCard(
        modifier = modifier
            .fillMaxWidth()
            .height(cardHeightDp.coerceIn(92, 360).dp),
        cornerRadius = 22.dp,
        borderWidth = borderDp,
        backgroundColor = palette.widgetBg.copy(alpha = effectiveOpacity),
        borderColor = palette.borderSubtle
    ) {
        when (widgetType) {
            CustomWidgetType.MOON_PHASE -> MoonPhaseWidget(
                config = wConfig,
                settings = settings,
                onTapAction = { onOpenWidgetSettings(widgetType) }
            )
            CustomWidgetType.STEP_COUNTER -> StepCounterWidget(
                config = wConfig,
                settings = settings,
                stepsToday = stepsToday,
                sensorAvailable = stepSensorAvailable,
                hasPermission = hasStepPermission,
                onRequestPermission = onRequestStepSync,
                onTapAction = { onOpenWidgetSettings(widgetType) }
            )
            CustomWidgetType.TIME_DATE -> TimeDateWidget(
                config = wConfig,
                settings = settings,
                onCycleFormat = onCycleTimeFormat
            )
            CustomWidgetType.WEATHER -> WeatherWidget(
                config = wConfig,
                settings = settings,
                weatherState = weatherState,
                onRefreshWeather = onRefreshWeather
            )
            CustomWidgetType.STEP_ANALYTICS -> StepAnalyticsWidget(
                config = wConfig,
                settings = settings,
                history = stepHistory,
                todaySteps = stepsToday,
                onTapAction = { onOpenWidgetSettings(widgetType) }
            )
            CustomWidgetType.QUICK_SETTINGS -> QuickSettingsWidget(
                config = wConfig,
                settings = settings,
                telemetry = telemetry,
                contextEngine = contextEngine
            )
            CustomWidgetType.YEAR_PROGRESS -> YearProgressWidget(
                config = wConfig,
                settings = settings,
                onCycleViewMode = onCycleYearMode
            )
            CustomWidgetType.DIGITAL_AURA -> DigitalAuraFocusWidget(
                config = wConfig,
                settings = settings,
                velocity = screenTimeVelocity,
                onToggleDeepFocus = onToggleDeepFocus,
                onOpenUsageAccess = onOpenUsageAccess
            )
            CustomWidgetType.MEDIA_VINYL -> MediaVinylWidget(
                config = wConfig,
                settings = settings,
                mediaState = mediaState
            )
            CustomWidgetType.BATTERY_MATRIX -> BatteryMatrixWidget(
                config = wConfig,
                settings = settings,
                telemetry = telemetry,
                hasBluetoothPermission = hasBluetoothPermission,
                onRequestBluetoothPermission = onRequestBluetoothPermission
            )
            CustomWidgetType.POMODORO_FOCUS -> PomodoroFocusWidget(
                config = wConfig,
                settings = settings,
                onToggleDeepFocus = onToggleDeepFocus
            )
        }
    }
}
