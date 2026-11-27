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
import androidx.compose.runtime.State
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
import com.example.settingsstore.model.DigitalAuraShape
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.QuickToggleItem
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.example.settingsstore.model.YearViewMode
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
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
    onCycleAuraShape: () -> Unit = {},
    animationsPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)

    val effectiveVelocity = (velocity.velocityScore * config.auraSensitivity).coerceIn(0.08f, 1f)
    val pulseDuration = (4200 - (effectiveVelocity * 2000)).toInt().coerceIn(1400, 4800)

    val animateRing = !animationsPaused && settings.theme.wallpaperAnimationEnabled && !settings.deepFocusActive
    val pulsePhaseState: State<Float>? = if (animateRing) {
        val infiniteTransition = rememberInfiniteTransition(label = "digital_aura")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "aura_phase"
        )
    } else {
        null
    }

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

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                        .clickable { onCycleAuraShape() }
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = config.auraShape.displayName.uppercase(),
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 9.sp,
                        color = palette.accent
                    )
                }

                if (!velocity.usagePermissionGranted) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                            .clickable { onOpenUsageAccess() }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "SYNC",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 9.sp,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        }

        // Multi-shape Digital Aura visualizer (8 selectable shapes, 0-recomposition draw scope)
        Canvas(
            modifier = Modifier
                .size(96.dp)
                .clickable { onCycleAuraShape() }
        ) {
            val pulsePhase = pulsePhaseState?.value ?: 0f
            val c = Offset(size.width / 2f, size.height / 2f)
            val baseR = size.minDimension * 0.38f
            val dotCount = (16 + (config.dotDensity * 14).toInt()).coerceIn(14, 28)
            val activeAccent = if (settings.deepFocusActive) palette.textSecondary else palette.accent
            val primaryInk = palette.textPrimary.copy(alpha = 0.80f)

            when (config.auraShape) {
                DigitalAuraShape.ORGANIC_RING -> {
                    for (ring in 0 until 2) {
                        val ringScale = if (ring == 0) 1f else 0.66f
                        for (i in 0 until dotCount) {
                            val theta = 2.0 * PI * i / dotCount
                            val wave = sin(theta * 3.0 + pulsePhase + ring * 1.4).toFloat()
                            val radius = baseR * ringScale + wave * (3.5f + effectiveVelocity * 7f)
                            drawCircle(
                                color = if (ring == 0 && wave > 0.3f) activeAccent else primaryInk,
                                radius = 2.1f + (wave + 1f) * 1.0f,
                                center = Offset(c.x + radius * cos(theta).toFloat(), c.y + radius * sin(theta).toFloat())
                            )
                        }
                    }
                    drawCircle(color = activeAccent, radius = 4.8f + sin(pulsePhase) * 1.8f, center = c)
                }

                DigitalAuraShape.SACRED_HEXAGON -> {
                    for (layer in 0 until 2) {
                        val r = baseR * (if (layer == 0) 1.05f else 0.62f)
                        val rot = pulsePhase * (if (layer == 0) 0.45f else -0.6f)
                        for (side in 0 until 6) {
                            val a1 = rot + side * (PI / 3.0)
                            val a2 = rot + (side + 1) * (PI / 3.0)
                            val p1 = Offset(c.x + r * cos(a1).toFloat(), c.y + r * sin(a1).toFloat())
                            val p2 = Offset(c.x + r * cos(a2).toFloat(), c.y + r * sin(a2).toFloat())
                            for (s in 0..3) {
                                val t = s / 4f
                                drawCircle(
                                    color = if (s == 0 && layer == 0) activeAccent else primaryInk,
                                    radius = if (s == 0) 3.4f else 2.2f,
                                    center = Offset(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t)
                                )
                            }
                        }
                    }
                    drawCircle(color = activeAccent, radius = 4.5f, center = c)
                }

                DigitalAuraShape.PULSAR_STAR -> {
                    val points = 12
                    val total = points * 2
                    for (i in 0 until total) {
                        val angle = (PI * i / points) + pulsePhase * 0.35
                        val isOuter = i % 2 == 0
                        val breath = 1f + 0.16f * sin(pulsePhase + i).toFloat()
                        val r = baseR * (if (isOuter) 1.12f * breath else 0.46f)
                        drawCircle(
                            color = if (isOuter) activeAccent else primaryInk,
                            radius = if (isOuter) 3.4f else 2.2f,
                            center = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())
                        )
                    }
                    drawCircle(color = primaryInk, radius = 4f, center = c)
                }

                DigitalAuraShape.VORTEX_SPIRAL -> {
                    val spiralDots = 28
                    for (i in 1..spiralDots) {
                        val frac = i.toFloat() / spiralDots
                        val angle = i * 0.72 + pulsePhase * 1.4
                        val r = frac * baseR * 1.15f
                        drawCircle(
                            color = if (i % 4 == 0) activeAccent else primaryInk.copy(alpha = 0.35f + frac * 0.6f),
                            radius = 1.6f + frac * 2.4f,
                            center = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())
                        )
                    }
                }

                DigitalAuraShape.INFINITY_LOOP -> {
                    val steps = 28
                    val rx = baseR * 1.15f
                    val ry = baseR * 0.68f
                    for (i in 0 until steps) {
                        val t = (2.0 * PI * i / steps) + pulsePhase
                        val x = c.x + rx * cos(t).toFloat()
                        val y = c.y + ry * sin(2.0 * t).toFloat()
                        val highlight = (i % 7 == 0)
                        drawCircle(
                            color = if (highlight) activeAccent else primaryInk,
                            radius = if (highlight) 3.6f else 2.3f,
                            center = Offset(x, y)
                        )
                    }
                }

                DigitalAuraShape.EQUALIZER_MATRIX -> {
                    val bars = 7
                    val maxDots = 7
                    val stepX = (size.width * 0.82f) / bars
                    val startX = size.width * 0.09f + stepX * 0.5f
                    val stepY = (size.height * 0.76f) / maxDots
                    for (b in 0 until bars) {
                        val levelFrac = (0.3f + 0.7f * abs(sin(pulsePhase + b * 0.75))).toFloat()
                        val litDots = (levelFrac * maxDots).roundToInt().coerceIn(1, maxDots)
                        for (d in 0 until maxDots) {
                            val y = size.height * 0.86f - d * stepY
                            val lit = d < litDots
                            drawCircle(
                                color = when {
                                    lit && d >= maxDots - 2 -> activeAccent
                                    lit -> primaryInk
                                    else -> palette.borderSubtle
                                },
                                radius = if (lit) 2.9f else 1.7f,
                                center = Offset(startX + b * stepX, y)
                            )
                        }
                    }
                }

                DigitalAuraShape.DIAMOND_CORE -> {
                    for (ring in 1..3) {
                        val span = baseR * (0.38f * ring) * (1f + 0.08f * sin(pulsePhase + ring).toFloat())
                        val corners = listOf(
                            Offset(c.x, c.y - span),
                            Offset(c.x + span, c.y),
                            Offset(c.x, c.y + span),
                            Offset(c.x - span, c.y)
                        )
                        for (edge in 0 until 4) {
                            val a = corners[edge]
                            val b = corners[(edge + 1) % 4]
                            for (s in 0..2) {
                                val t = s / 3f
                                drawCircle(
                                    color = if (ring == 3 && s == 0) activeAccent else primaryInk,
                                    radius = if (s == 0) 3.2f else 2.1f,
                                    center = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                                )
                            }
                        }
                    }
                    drawCircle(color = activeAccent, radius = 4.5f, center = c)
                }

                DigitalAuraShape.RADAR_SWEEP -> {
                    drawCircle(color = palette.borderSubtle, radius = baseR * 1.05f, center = c, style = Stroke(1.4f))
                    drawCircle(color = palette.borderSubtle, radius = baseR * 0.65f, center = c, style = Stroke(1.1f))
                    for (i in 0 until 16) {
                        val a = 2.0 * PI * i / 16.0
                        drawCircle(
                            color = primaryInk,
                            radius = 2.1f,
                            center = Offset(c.x + baseR * 1.05f * cos(a).toFloat(), c.y + baseR * 1.05f * sin(a).toFloat())
                        )
                    }
                    val beamEnd = Offset(
                        c.x + baseR * 1.05f * cos(pulsePhase.toDouble()).toFloat(),
                        c.y + baseR * 1.05f * sin(pulsePhase.toDouble()).toFloat()
                    )
                    drawLine(color = activeAccent, start = c, end = beamEnd, strokeWidth = 2.8f)
                    drawCircle(color = activeAccent, radius = 4.2f, center = beamEnd)
                    drawCircle(color = activeAccent, radius = 4.2f, center = c)
                }

                DigitalAuraShape.WIREFRAME_SPHERE -> {
                    // Latitude / longitude dot sphere slowly rotating around its axis.
                    val rings = 7
                    val rot = pulsePhase * 0.5
                    for (lat in 0 until rings) {
                        val phi = PI * (lat + 1) / (rings + 1) - PI / 2.0
                        val ringR = baseR * cos(phi).toFloat()
                        val y = c.y + baseR * sin(phi).toFloat()
                        if (ringR <= 0f) continue
                        val dots = (14 * (ringR / baseR)).toInt().coerceAtLeast(4)
                        for (d in 0 until dots) {
                            val theta = 2.0 * PI * d / dots + rot
                            val depth = sin(theta).toFloat() // -1 (back) .. 1 (front)
                            drawCircle(
                                color = if (depth > 0.55f && lat == rings / 2) activeAccent
                                else primaryInk.copy(alpha = 0.35f + 0.55f * ((depth + 1f) / 2f)),
                                radius = 1.5f + 1.1f * ((depth + 1f) / 2f),
                                center = Offset(c.x + ringR * cos(theta).toFloat(), y)
                            )
                        }
                    }
                    val equatorDots = 26
                    for (d in 0 until equatorDots) {
                        val theta = 2.0 * PI * d / equatorDots + rot
                        drawCircle(
                            color = if (d % 9 == 0) activeAccent else palette.borderSubtle,
                            radius = if (d % 9 == 0) 2.6f else 1.6f,
                            center = Offset(c.x + baseR * cos(theta).toFloat(), c.y + baseR * sin(theta).toFloat())
                        )
                    }
                }

                DigitalAuraShape.GALAXY_ORBIT -> {
                    // Three tilted elliptical orbits with trailing comet dots.
                    val orbits = 3
                    for (o in 0 until orbits) {
                        val tilt = PI / 3.0 + o * PI / 7.0
                        val radius = baseR * (0.72f + o * 0.14f)
                        val speed = 1.0 + o * 0.45
                        val dots = 22
                        for (d in 0 until dots) {
                            val theta = 2.0 * PI * d / dots
                            val ex = radius * cos(theta)
                            val ey = radius * 0.42f * sin(theta)
                            val x = c.x + (ex * cos(tilt) - ey * sin(tilt)).toFloat()
                            val y = c.y + (ex * sin(tilt) + ey * cos(tilt)).toFloat()
                            drawCircle(
                                color = primaryInk.copy(alpha = 0.18f + 0.22f * o),
                                radius = 1.5f,
                                center = Offset(x, y)
                            )
                        }
                        val head = pulsePhase * speed
                        repeat(4) { trail ->
                            val t = head - trail * 0.16
                            val ex = radius * cos(t)
                            val ey = radius * 0.42f * sin(t)
                            val x = c.x + (ex * cos(tilt) - ey * sin(tilt)).toFloat()
                            val y = c.y + (ex * sin(tilt) + ey * cos(tilt)).toFloat()
                            drawCircle(
                                color = activeAccent.copy(alpha = 1f - trail * 0.22f),
                                radius = if (trail == 0) 4.2f else 2.4f,
                                center = Offset(x, y)
                            )
                        }
                    }
                    drawCircle(color = activeAccent, radius = 5f, center = c)
                }

                DigitalAuraShape.TWIN_HELIX -> {
                    // Two counter-phased dot strands twisting vertically.
                    val strands = 22
                    val height = baseR * 1.7f
                    for (i in 0 until strands) {
                        val t = i / (strands - 1f)
                        val y = c.y - height / 2f + height * t
                        val phase = pulsePhase + t * PI * 3.0
                        val spread = baseR * 0.62f * sin(t * PI).toFloat().coerceAtLeast(0.12f)
                        val x1 = c.x + spread * cos(phase).toFloat()
                        val x2 = c.x + spread * cos(phase + PI).toFloat()
                        val front = cos(phase) > 0f
                        drawCircle(
                            color = if (front) activeAccent else primaryInk.copy(alpha = 0.45f),
                            radius = if (front) 3.2f else 2.2f,
                            center = Offset(x1, y)
                        )
                        drawCircle(
                            color = if (!front) activeAccent else primaryInk.copy(alpha = 0.45f),
                            radius = if (!front) 3.2f else 2.2f,
                            center = Offset(x2, y)
                        )
                        if (i % 4 == 0) {
                            drawLine(
                                color = palette.borderSubtle.copy(alpha = 0.5f),
                                start = Offset(x1, y),
                                end = Offset(x2, y),
                                strokeWidth = 1f
                            )
                        }
                    }
                }

                DigitalAuraShape.SIGNAL_PULSE -> {
                    // Equalizer-style concentric pulse rings radiating from the core.
                    for (ring in 0 until 4) {
                        val frac = ((pulsePhase / (2.0 * PI)) + ring * 0.25).toFloat() % 1f
                        val r = baseR * (0.25f + 0.85f * frac)
                        drawCircle(
                            color = activeAccent.copy(alpha = (1f - frac) * 0.85f),
                            radius = r,
                            center = c,
                            style = Stroke(width = 2.2f - frac * 1.2f)
                        )
                    }
                    val bars = 28
                    for (i in 0 until bars) {
                        val a = 2.0 * PI * i / bars
                        val amp = (sin(a * 5.0 + pulsePhase * 2.0).toFloat() + 1f) / 2f
                        val inner = baseR * 0.55f
                        val outer = inner + baseR * 0.30f * amp
                        drawLine(
                            color = if (amp > 0.72f) activeAccent else primaryInk.copy(alpha = 0.55f),
                            start = Offset(c.x + inner * cos(a).toFloat(), c.y + inner * sin(a).toFloat()),
                            end = Offset(c.x + outer * cos(a).toFloat(), c.y + outer * sin(a).toFloat()),
                            strokeWidth = if (amp > 0.72f) 3f else 1.8f
                        )
                    }
                    drawCircle(color = activeAccent, radius = 3.6f + sin(pulsePhase * 1.6).toFloat() * 1.4f, center = c)
                }
            }
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
    weatherRepo: WeatherRepository? = null,
    onUpdateWidgetConfig: (CustomWidgetType, (WidgetAppearanceConfig) -> WidgetAppearanceConfig) -> Unit = { _, _ -> },
    onSaveQuickNote: (String) -> Unit = {},
    onOpenNewsSpace: () -> Unit = {},
    cardHeightDp: Int = 152,
    contentScale: Float = 1f,
    animationsPaused: Boolean = false,
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
                onCycleFormat = onCycleTimeFormat,
                animationsPaused = animationsPaused
            )
            CustomWidgetType.WEATHER -> WeatherWidget(
                config = wConfig,
                settings = settings,
                weatherState = weatherState,
                onRefreshWeather = onRefreshWeather,
                weatherRepo = weatherRepo,
                onUpdateWeatherConfig = { tr -> onUpdateWidgetConfig(CustomWidgetType.WEATHER, tr) }
            )
            CustomWidgetType.NEWS_HEADLINES -> NewsPulseWidget(
                config = wConfig,
                settings = settings,
                onOpenNewsSpace = onOpenNewsSpace
            )
            CustomWidgetType.QUICK_NOTES -> QuickNotesWidget(
                config = wConfig,
                settings = settings,
                onSaveNote = onSaveQuickNote
            )
            CustomWidgetType.SYSTEM_MONITOR -> SystemMatrixWidget(
                config = wConfig,
                settings = settings,
                telemetry = telemetry
            )
            CustomWidgetType.COUNTDOWN_DECK -> EventCountdownWidget(
                config = wConfig,
                settings = settings,
                onUpdateCountdown = { title, days ->
                    onUpdateWidgetConfig(CustomWidgetType.COUNTDOWN_DECK) {
                        it.copy(countdownTitle = title, countdownDaysFromNow = days)
                    }
                }
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
                onOpenUsageAccess = onOpenUsageAccess,
                onCycleAuraShape = {
                    val shapes = DigitalAuraShape.entries
                    val next = shapes[(shapes.indexOf(baseConfig.auraShape) + 1) % shapes.size]
                    onUpdateWidgetConfig(CustomWidgetType.DIGITAL_AURA) { it.copy(auraShape = next) }
                },
                animationsPaused = animationsPaused
            )
            CustomWidgetType.MEDIA_VINYL -> MediaVinylWidget(
                config = wConfig,
                settings = settings,
                mediaState = mediaState,
                animationsPaused = animationsPaused
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
