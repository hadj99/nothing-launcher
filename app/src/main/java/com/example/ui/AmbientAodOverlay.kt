package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appindexer.ScreenTimeVelocity
import com.example.contextautomation.ActiveMediaState
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.contextautomation.LiveNotificationBridge
import com.example.settingsstore.model.LauncherSettings
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AmbientAodOverlay(
    settings: LauncherSettings,
    telemetry: DeviceTelemetrySnapshot,
    mediaState: ActiveMediaState,
    velocity: ScreenTimeVelocity,
    totalNotificationCount: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }
    val palette = LocalNothingPalette.current

    var timeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    // Periodic burn-in protection shift every 20 seconds
    var burnInStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            timeMillis = System.currentTimeMillis()
            delay(1000L)
            if ((timeMillis / 1000L) % 20L == 0L) {
                burnInStep = (burnInStep + 1) % 6
            }
        }
    }

    val shiftX = when (burnInStep) {
        0 -> 0.dp; 1 -> 6.dp; 2 -> 8.dp; 3 -> 0.dp; 4 -> (-6).dp; else -> (-8).dp
    }
    val shiftY = when (burnInStep) {
        0 -> (-6).dp; 1 -> (-2).dp; 2 -> 6.dp; 3 -> 8.dp; 4 -> 2.dp; else -> (-4).dp
    }

    val infiniteTransition = rememberInfiniteTransition(label = "aod_aura")
    val auraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aod_phase"
    )

    val breathPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aod_breath"
    )
    val tickPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aod_tick"
    )

    val clockStr = remember(timeMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMillis))
    }
    val secondsStr = remember(timeMillis) {
        SimpleDateFormat("ss", Locale.getDefault()).format(Date(timeMillis))
    }
    val dateStr = remember(timeMillis) {
        SimpleDateFormat("EEE  •  dd MMM", Locale.getDefault()).format(Date(timeMillis)).uppercase()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("ambient_aod_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .offset(x = shiftX, y = shiftY)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Subtle dot-matrix Digital Aura ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = center
                    val r = size.minDimension * 0.44f

                    // Breathing accent halo — a soft radial glow that expands with each "breath"
                    val haloRadius = r * (1.18f + 0.10f * breathPhase)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.accent.copy(alpha = 0.16f + 0.08f * breathPhase),
                                Color.Transparent
                            ),
                            center = c,
                            radius = haloRadius
                        ),
                        radius = haloRadius,
                        center = c
                    )

                    // Orbiting accent "comet" dots (two speeds for organic motion)
                    val orbitA = auraPhase * 0.55f
                    val orbitB = -auraPhase * 0.85f
                    drawCircle(
                        color = palette.accent,
                        radius = 3.6f,
                        center = Offset(
                            c.x + r * 1.22f * cos(orbitA).toFloat(),
                            c.y + r * 1.22f * sin(orbitA).toFloat()
                        )
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f),
                        radius = 2.0f,
                        center = Offset(
                            c.x + r * 1.34f * cos(orbitB).toFloat(),
                            c.y + r * 1.34f * sin(orbitB).toFloat()
                        )
                    )

                    // Morphing dot-matrix ring (the Digital Aura waveform)
                    val dots = 36
                    for (i in 0 until dots) {
                        val theta = 2.0 * PI * i / dots
                        val wave = sin(theta * 3.0 + auraPhase).toFloat()
                        val rad = r + wave * (3f + velocity.velocityScore * 5f)
                        val isAccent = i % 6 == 0
                        drawCircle(
                            color = if (isAccent) {
                                palette.accent.copy(alpha = 0.65f + 0.35f * breathPhase)
                            } else {
                                Color.White.copy(alpha = 0.30f + 0.28f * ((wave + 1f) / 2f))
                            },
                            radius = if (isAccent) 3.4f + 1.2f * breathPhase else 1.8f,
                            center = Offset(c.x + rad * cos(theta).toFloat(), c.y + rad * sin(theta).toFloat())
                        )
                    }

                    // Inner ticking glyph arcs — 4 quarter arcs sweeping at different paces
                    val arcR = r * 0.78f
                    for (q in 0 until 4) {
                        val sweepStart = auraPhase * (0.6f + q * 0.22f) + q * (PI.toFloat() / 2f)
                        drawArc(
                            color = if (q % 2 == 0) {
                                Color.White.copy(alpha = 0.16f + 0.10f * tickPhase)
                            } else {
                                palette.accent.copy(alpha = 0.22f)
                            },
                            startAngle = Math.toDegrees(sweepStart.toDouble()).toFloat(),
                            sweepAngle = 42f,
                            useCenter = false,
                            topLeft = Offset(c.x - arcR, c.y - arcR),
                            size = androidx.compose.ui.geometry.Size(arcR * 2f, arcR * 2f),
                            style = Stroke(width = 1.6f)
                        )
                    }

                    drawCircle(
                        color = Color.White.copy(alpha = 0.12f),
                        radius = r * 0.72f,
                        center = c,
                        style = Stroke(1f)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = clockStr,
                        fontFamily = NothingTypographyEngine.ndot77Family,
                        fontSize = 46.sp,
                        color = Color.White
                    )
                    Text(
                        text = "$secondsStr SEC",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        color = palette.accent
                    )
                }
            }

            Text(
                text = dateStr,
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 13.sp,
                letterSpacing = 1.6.sp,
                color = Color(0xFFB4B4BC)
            )

            // Animated dot-matrix glyph equalizer (pure draw scope — no recompositions)
            Canvas(
                modifier = Modifier
                    .width(132.dp)
                    .height(20.dp)
            ) {
                val bars = 9
                val stepX = size.width / bars
                val stepY = size.height / 5f
                for (b in 0 until bars) {
                    val level = (sin(tickPhase * 2.0 * PI + b * 0.7).toFloat() + 1f) / 2f
                    val lit = (level * 5f).toInt().coerceIn(1, 5)
                    for (d in 0 until 5) {
                        val on = d < lit
                        drawCircle(
                            color = when {
                                on && d >= 3 -> palette.accent
                                on -> Color.White.copy(alpha = 0.78f)
                                else -> Color.White.copy(alpha = 0.10f)
                            },
                            radius = if (on) 1.9f else 1.2f,
                            center = Offset(
                                stepX * b + stepX / 2f,
                                size.height - stepY * d - stepY / 2f
                            )
                        )
                    }
                }
            }

            // Status pills row (Battery + Unread Alerts)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (telemetry.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                        contentDescription = "Battery",
                        tint = if (telemetry.batteryPercent <= 20) palette.accent else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${telemetry.batteryPercent}%",
                        fontFamily = NothingTypographyEngine.ntype82Family,
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }

                if (totalNotificationCount > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, palette.accent, RoundedCornerShape(999.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Notifications, "Unread Alerts", tint = palette.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$totalNotificationCount ALERTS",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Active Media AOD Bar
            if (mediaState.hasSession) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0E0E11))
                        .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(18.dp))
                        .clickable { LiveNotificationBridge.togglePlayPause() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(palette.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (mediaState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play or Pause",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = mediaState.title,
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 12.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                        Text(
                            text = mediaState.artist,
                            fontSize = 10.sp,
                            color = Color(0xFF9A9AA2),
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (settings.deepFocusActive) "DEEP FOCUS LOCKED  •  TAP TO EXIT" else "TAP ANYWHERE TO WAKE LAUNCHER",
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                color = Color(0xFF6B6B74)
            )
        }
    }
}
