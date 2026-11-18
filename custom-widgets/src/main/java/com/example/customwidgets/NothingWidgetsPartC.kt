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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.contextautomation.ActiveMediaState
import com.example.contextautomation.ContextAutomationEngine
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.contextautomation.LiveNotificationBridge
import com.example.settingsstore.model.DockMiniChipType
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.QuickToggleItem
import com.example.settingsstore.model.WidgetAppearanceConfig
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// 9. MEDIA VINYL & DOT-BAR AUDIO VISUALIZER WIDGET
@Composable
fun MediaVinylWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    mediaState: ActiveMediaState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)
    val listenerEnabled = remember(mediaState) {
        LiveNotificationBridge.isNotificationAccessEnabled(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vinyl_deg"
    )
    val effectiveAngle = if (mediaState.isPlaying) spinAngle else 0f

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Spinning Dot-Matrix Vinyl Turntable
        Canvas(modifier = Modifier.size(92.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.46f

            drawCircle(
                color = palette.surface,
                radius = maxR,
                center = c
            )
            drawCircle(
                color = palette.borderSubtle,
                radius = maxR,
                center = c,
                style = Stroke(1.5f)
            )

            // Concentric dot grooves rotating smoothly
            val ringFractions = listOf(0.86f, 0.68f, 0.50f)
            ringFractions.forEachIndexed { rIdx, frac ->
                val ringR = maxR * frac
                val dotCount = (10 + rIdx * 4)
                for (i in 0 until dotCount) {
                    val deg = effectiveAngle + (360f * i / dotCount) + rIdx * 15f
                    val rad = Math.toRadians(deg.toDouble())
                    val isAccentDot = (i == 0 && rIdx == 0)
                    drawCircle(
                        color = if (isAccentDot) palette.accent else palette.textSecondary.copy(alpha = 0.65f),
                        radius = if (isAccentDot) 3.6f else 1.8f,
                        center = Offset(
                            c.x + ringR * cos(rad).toFloat(),
                            c.y + ringR * sin(rad).toFloat()
                        )
                    )
                }
            }

            // Center spindle
            drawCircle(
                color = if (mediaState.isPlaying) palette.accent else palette.textSecondary,
                radius = maxR * 0.18f,
                center = c
            )
            drawCircle(
                color = palette.background,
                radius = maxR * 0.06f,
                center = c
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = NothingTypographyEngine.formatText("MEDIA VINYL", settings.typographyWidgetTitle.textCase),
                    style = titleStyle.copy(color = palette.textSecondary, fontSize = 10.sp)
                )
                if (config.mediaShowVisualizer && mediaState.isPlaying) {
                    Canvas(modifier = Modifier.width(34.dp).height(12.dp)) {
                        val bars = 5
                        val bw = size.width / bars
                        for (b in 0 until bars) {
                            val wave = (0.35f + 0.65f * kotlin.math.abs(
                                sin(Math.toRadians((effectiveAngle * 2.0 + b * 48.0))).toFloat()
                            ))
                            val barH = size.height * wave
                            drawCircle(
                                color = if (b % 2 == 0) palette.accent else palette.textPrimary,
                                radius = 2.2f,
                                center = Offset(b * bw + bw / 2f, size.height - barH / 2f)
                            )
                        }
                    }
                }
            }

            Column {
                Text(
                    text = mediaState.title,
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = (14f * config.fontSizeScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mediaState.artist,
                    fontSize = 11.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!listenerEnabled && !mediaState.hasSession) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, palette.accent, RoundedCornerShape(999.dp))
                        .clickable { LiveNotificationBridge.openNotificationAccessSettings(context) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "SYNC MEDIA ACCESS",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 10.sp,
                        color = palette.accent
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { LiveNotificationBridge.skipPrevious() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous Track", tint = palette.textPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                            .clickable { LiveNotificationBridge.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (mediaState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { LiveNotificationBridge.skipNext() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.SkipNext, "Next Track", tint = palette.textPrimary)
                    }
                }
            }
        }
    }
}

// 10. BATTERY MATRIX (PHONE + BLUETOOTH PERIPHERALS) WIDGET
@Composable
fun BatteryMatrixWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    telemetry: DeviceTelemetrySnapshot,
    hasBluetoothPermission: Boolean,
    onRequestBluetoothPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)

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
                text = NothingTypographyEngine.formatText("BATTERY MATRIX", settings.typographyWidgetTitle.textCase),
                style = titleStyle.copy(color = palette.textSecondary)
            )
            Text(
                text = if (telemetry.isCharging) "${telemetry.batteryPercent}% ⚡" else "${telemetry.batteryPercent}%",
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 14.sp,
                color = if (telemetry.batteryPercent <= 20) palette.accent else palette.textPrimary
            )
        }

        // Phone Dot-Matrix Charge Gauge (20 dots)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val totalDots = 20
            val step = size.width / totalDots
            val litDots = ((telemetry.batteryPercent / 100f) * totalDots).roundToInt().coerceIn(0, totalDots)
            for (i in 0 until totalDots) {
                val lit = i < litDots
                val color = when {
                    lit && telemetry.batteryPercent <= 20 -> palette.accent
                    lit && i >= totalDots - 3 && telemetry.isCharging -> palette.accent
                    lit -> palette.textPrimary
                    else -> palette.borderSubtle
                }
                drawCircle(
                    color = color,
                    radius = if (lit) 4.4f else 2.2f,
                    center = Offset((i + 0.5f) * step, size.height / 2f)
                )
            }
        }

        if (!hasBluetoothPermission) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, palette.accent, RoundedCornerShape(10.dp))
                    .clickable { onRequestBluetoothPermission() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Grant BT Connect to read earbuds & watch battery",
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "ALLOW",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 10.sp,
                    color = palette.accent
                )
            }
        } else if (telemetry.bluetoothBatteries.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bluetooth, null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (telemetry.isBluetoothOn) {
                            "${telemetry.connectedBluetoothNames.size} BT device(s) paired"
                        } else {
                            "Bluetooth off • Phone gauge active"
                        },
                        fontSize = 11.sp,
                        color = palette.textSecondary
                    )
                }
                Text(
                    text = if (telemetry.isBatterySaverOn) "SAVER ON" else "NOMINAL",
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 10.sp,
                    color = if (telemetry.isBatterySaverOn) palette.accent else palette.textSecondary
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                telemetry.bluetoothBatteries.take(2).forEach { bt ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bt.name,
                            fontSize = 11.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${bt.batteryPercent}%",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 11.sp,
                            color = if (bt.batteryPercent <= 20) palette.accent else palette.textSecondary
                        )
                    }
                }
            }
        }
    }
}

// 11. FOCUS POMODORO WIDGET
@Composable
fun PomodoroFocusWidget(
    config: WidgetAppearanceConfig,
    settings: LauncherSettings,
    onToggleDeepFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val titleStyle = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle)

    val focusTotalSec = (config.pomodoroFocusMinutes.coerceIn(5, 90)) * 60
    val breakTotalSec = (config.pomodoroBreakMinutes.coerceIn(1, 30)) * 60

    var isFocusPhase by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var remainingSec by remember(focusTotalSec, breakTotalSec) {
        mutableIntStateOf(if (isFocusPhase) focusTotalSec else breakTotalSec)
    }
    var sessionsCompleted by remember { mutableIntStateOf(0) }

    LaunchedEffect(running, isFocusPhase) {
        while (running) {
            delay(1000L)
            if (remainingSec > 0) {
                remainingSec--
            } else {
                if (isFocusPhase) sessionsCompleted++
                isFocusPhase = !isFocusPhase
                remainingSec = if (isFocusPhase) focusTotalSec else breakTotalSec
                running = false
            }
        }
    }

    val phaseTotal = if (isFocusPhase) focusTotalSec else breakTotalSec
    val progress = (remainingSec.toFloat() / phaseTotal.coerceAtLeast(1)).coerceIn(0f, 1f)
    val mm = remainingSec / 60
    val ss = remainingSec % 60
    val timeText = String.format(Locale.US, "%02d:%02d", mm, ss)

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = NothingTypographyEngine.formatText(
                    if (isFocusPhase) "POMODORO FOCUS" else "POMODORO BREAK",
                    settings.typographyWidgetTitle.textCase
                ),
                style = titleStyle.copy(color = palette.textSecondary, fontSize = 10.sp)
            )
            Text(
                text = timeText,
                fontFamily = NothingTypographyEngine.ndot77Family,
                fontSize = (28f * config.fontSizeScale).sp,
                color = palette.textPrimary
            )
            Text(
                text = "SESSIONS $sessionsCompleted  •  ${if (settings.deepFocusActive) "LOCK ON" else "OPEN"}",
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 10.sp,
                color = palette.accent
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (running) palette.accent else palette.surface)
                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                        .clickable {
                            val nextRunning = !running
                            running = nextRunning
                            if (nextRunning && isFocusPhase && !settings.deepFocusActive) {
                                onToggleDeepFocus()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (running) "PAUSE" else "START",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (running) Color.White else palette.textPrimary
                    )
                }
                IconButton(
                    onClick = {
                        running = false
                        remainingSec = if (isFocusPhase) focusTotalSec else breakTotalSec
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, "Reset Timer", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Dot countdown ring
        Canvas(modifier = Modifier.size(86.dp)) {
            val totalDots = 24
            val activeDots = (progress * totalDots).roundToInt().coerceIn(0, totalDots)
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.41f
            for (i in 0 until totalDots) {
                val angle = -PI / 2.0 + (2.0 * PI * i / totalDots)
                val lit = i < activeDots
                drawCircle(
                    color = if (lit) {
                        if (isFocusPhase) palette.accent else palette.textPrimary
                    } else palette.borderSubtle,
                    radius = if (lit) 3.4f else 1.8f,
                    center = Offset(c.x + r * cos(angle).toFloat(), c.y + r * sin(angle).toFloat())
                )
            }
            drawCircle(color = palette.borderSubtle, radius = r * 0.65f, center = c, style = Stroke(1f))
        }
    }
}

// DOCK MINI-WIDGET CHIPS (Live telemetry pills embedded inside the swipeable Dock)
@Composable
fun DockMiniWidgetChip(
    chipType: DockMiniChipType,
    settings: LauncherSettings,
    telemetry: DeviceTelemetrySnapshot,
    stepsToday: Int,
    totalNotificationCount: Int,
    contextEngine: ContextAutomationEngine,
    onToggleDeepFocus: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val shape = RoundedCornerShape(14.dp)

    val (icon, label, highlighted) = when (chipType) {
        DockMiniChipType.BATTERY_DOT -> Triple(
            if (telemetry.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
            "${telemetry.batteryPercent}%",
            telemetry.batteryPercent <= 20 || telemetry.isCharging
        )
        DockMiniChipType.STEPS_MINI -> Triple(
            Icons.Rounded.DirectionsWalk,
            if (stepsToday >= 1000) String.format(Locale.US, "%.1fk", stepsToday / 1000f) else "$stepsToday",
            false
        )
        DockMiniChipType.CLOCK_MINI -> Triple(
            Icons.Rounded.Schedule,
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
            false
        )
        DockMiniChipType.TORCH_MINI -> Triple(
            Icons.Rounded.FlashlightOn,
            if (telemetry.isTorchOn) "ON" else "TORCH",
            telemetry.isTorchOn
        )
        DockMiniChipType.FOCUS_MINI -> Triple(
            Icons.Rounded.SelfImprovement,
            if (settings.deepFocusActive) "LOCK" else "FOCUS",
            settings.deepFocusActive
        )
        DockMiniChipType.NOTIF_MINI -> Triple(
            Icons.Rounded.Notifications,
            "$totalNotificationCount",
            totalNotificationCount > 0
        )
    }

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(if (highlighted) palette.accent else palette.background.copy(alpha = 0.7f))
            .border(1.dp, if (highlighted) palette.accent else palette.borderSubtle, shape)
            .clickable {
                when (chipType) {
                    DockMiniChipType.TORCH_MINI -> contextEngine.triggerQuickToggle(QuickToggleItem.FLASHLIGHT)
                    DockMiniChipType.FOCUS_MINI -> onToggleDeepFocus()
                    else -> onOpenSettings()
                }
            }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .testTag("dock_mini_${chipType.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = chipType.displayName,
            tint = if (highlighted) Color.White else palette.textPrimary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontFamily = NothingTypographyEngine.ntype82Family,
            fontSize = 9.sp,
            color = if (highlighted) Color.White else palette.textSecondary,
            maxLines = 1
        )
    }
}
