package com.example.ui

import androidx.activity.compose.BackHandler
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
                    val dots = 36
                    for (i in 0 until dots) {
                        val theta = 2.0 * PI * i / dots
                        val wave = sin(theta * 3.0 + auraPhase).toFloat()
                        val rad = r + wave * (3f + velocity.velocityScore * 5f)
                        drawCircle(
                            color = if (i % 6 == 0) palette.accent else Color.White.copy(alpha = 0.45f),
                            radius = if (i % 6 == 0) 3.4f else 1.8f,
                            center = Offset(c.x + rad * cos(theta).toFloat(), c.y + rad * sin(theta).toFloat())
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
