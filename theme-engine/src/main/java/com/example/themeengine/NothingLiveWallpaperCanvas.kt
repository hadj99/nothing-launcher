package com.example.themeengine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.example.settingsstore.model.SurfaceThemeConfig
import com.example.settingsstore.model.WallpaperMode
import com.example.settingsstore.model.WallpaperWavePattern
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun NothingLiveWallpaperCanvas(
    theme: SurfaceThemeConfig,
    deepFocusActive: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(theme.wallpaperTiltParallax) {
        if (!theme.wallpaperTiltParallax) {
            tiltX = 0f
            tiltY = 0f
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val nx = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val ny = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    tiltX = tiltX * 0.85f + nx * 0.15f
                    tiltY = tiltY * 0.85f + ny * 0.15f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (accel != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        }
    }

    val animateActive = theme.wallpaperAnimationEnabled && theme.wallpaperWavePattern != WallpaperWavePattern.STATIC
    val durationMs = (8000f / theme.wallpaperAnimSpeed.coerceIn(0.2f, 3.0f)).roundToInt().coerceIn(1800, 35000)

    val phase = if (animateActive) {
        val transition = rememberInfiniteTransition(label = "wallpaper_pulse")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
        animatedPhase
    } else {
        0f
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = palette.background)

        val parallaxScale = theme.wallpaperParallaxStrength.coerceIn(0f, 2.5f)
        val parallaxOffsetX = tiltX * 26f * parallaxScale
        val parallaxOffsetY = tiltY * 26f * parallaxScale
        val intensity = if (animateActive) theme.wallpaperAnimIntensity.coerceIn(0f, 2.0f) else 0f
        val brightness = theme.wallpaperDotBrightness.coerceIn(0.25f, 2.2f)
        val sizeScale = theme.wallpaperDotSizeScale.coerceIn(0.4f, 2.2f)
        val accentFreq = theme.wallpaperAccentFrequency.coerceIn(0f, 2.0f)

        if (theme.oledPureBlack && theme.wallpaperMode == WallpaperMode.OLED_PURE_BLACK) {
            val pulse = if (animateActive) (0.20f + 0.15f * ((sin(phase) + 1f) * 0.5f) * intensity) else 0.25f
            drawCircle(
                color = palette.accent.copy(alpha = (pulse * brightness).coerceIn(0.05f, 0.9f)),
                radius = 3.2f * sizeScale,
                center = Offset(size.width * 0.5f + parallaxOffsetX * 0.3f, size.height * 0.12f + parallaxOffsetY * 0.3f)
            )
            return@Canvas
        }

        when (theme.wallpaperMode) {
            WallpaperMode.DOT_MATRIX_GRID -> {
                val density = theme.wallpaperDotDensity.coerceIn(0.25f, 1.0f)
                val step = (54f - density * 24f).coerceAtLeast(22f)
                val cols = (size.width / step).toInt() + 2
                val rows = (size.height / step).toInt() + 2
                val dotBaseColor = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color(0xFFFFFFFF)
                val centerCol = cols * 0.5f
                val centerRow = rows * 0.45f

                for (r in 0..rows) {
                    for (c in 0..cols) {
                        val x = c * step + (parallaxOffsetX * 0.4f)
                        val y = r * step + (parallaxOffsetY * 0.4f)
                        val rawWave = when (theme.wallpaperWavePattern) {
                            WallpaperWavePattern.DIAGONAL_WAVE -> sin((c * 0.35f) + (r * 0.25f) + phase)
                            WallpaperWavePattern.RADIAL_RIPPLE -> {
                                val dist = hypot((c - centerCol).toDouble(), (r - centerRow).toDouble()).toFloat()
                                sin(dist * 0.65f - phase * 2f)
                            }
                            WallpaperWavePattern.MATRIX_RAIN -> sin((r * 0.45f) - phase * 2f + (c % 5) * 1.1f)
                            WallpaperWavePattern.BREATHING_PULSE -> sin(phase)
                            WallpaperWavePattern.STATIC -> 0f
                        }
                        val wave = rawWave * intensity
                        val alpha = ((0.045f + ((wave + 1f) * 0.045f)) * brightness).coerceIn(0.015f, 0.45f)
                        val accentThreshold = when {
                            accentFreq <= 0.05f -> false
                            accentFreq >= 1.5f -> (c % 6 == 2 && r % 7 == 3)
                            accentFreq >= 0.7f -> (c % 9 == 4 && r % 11 == 5)
                            else -> (c % 13 == 6 && r % 15 == 7)
                        }
                        val isGlyphNode = accentThreshold && !deepFocusActive
                        drawCircle(
                            color = if (isGlyphNode) {
                                palette.accent.copy(alpha = (0.38f * brightness).coerceIn(0.1f, 0.9f))
                            } else {
                                dotBaseColor.copy(alpha = alpha)
                            },
                            radius = (if (isGlyphNode) 3.2f else (1.5f + (wave + 1f) * 0.65f)) * sizeScale,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            WallpaperMode.TOPOGRAPHIC_CONTOURS -> {
                val center = Offset(
                    size.width * 0.55f + parallaxOffsetX,
                    size.height * 0.38f + parallaxOffsetY
                )
                val baseAlpha = (0.12f * brightness).coerceIn(0.03f, 0.55f)
                val lineColor = if (palette.isLightSurface) {
                    Color.Black.copy(alpha = baseAlpha)
                } else {
                    Color.White.copy(alpha = baseAlpha)
                }
                for (i in 1..11) {
                    val baseR = i * (size.minDimension * 0.09f)
                    val waveArg = when (theme.wallpaperWavePattern) {
                        WallpaperWavePattern.RADIAL_RIPPLE -> phase - i * 0.55f
                        WallpaperWavePattern.BREATHING_PULSE -> phase
                        WallpaperWavePattern.STATIC -> 0f
                        else -> phase + i * 0.4f
                    }
                    val breath = if (animateActive) sin(waveArg) * 9f * intensity else 0f
                    val isAccentRing = !deepFocusActive && accentFreq > 0.1f &&
                        (i == 4 || (accentFreq > 1.3f && i == 8))
                    drawCircle(
                        color = if (isAccentRing) palette.accent.copy(alpha = (0.22f * brightness).coerceIn(0.06f, 0.75f)) else lineColor,
                        radius = (baseR + breath).coerceAtLeast(4f),
                        center = center,
                        style = Stroke(width = (if (i % 3 == 0) 2.2f else 1f) * sizeScale)
                    )
                }
            }

            WallpaperMode.FROSTED_GLASS -> {
                val driftX = if (animateActive) cos(phase) * 32f * intensity else 0f
                val driftY = if (animateActive) sin(phase) * 28f * intensity else 0f
                val glowCenter = Offset(
                    size.width * 0.35f + parallaxOffsetX * 1.4f + driftX,
                    size.height * 0.28f + parallaxOffsetY * 1.4f + driftY
                )
                val glowAlpha = ((if (deepFocusActive) 0.06f else 0.16f) * brightness).coerceIn(0.03f, 0.5f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = glowAlpha),
                            palette.surface.copy(alpha = 0.38f),
                            palette.background
                        ),
                        center = glowCenter,
                        radius = (size.maxDimension * 0.85f) * sizeScale.coerceIn(0.7f, 1.5f)
                    )
                )
            }

            WallpaperMode.OLED_PURE_BLACK -> {
                val breath = if (animateActive) sin(phase) * 2.5f * intensity else 0f
                drawCircle(
                    color = palette.accent.copy(alpha = (0.30f * brightness).coerceIn(0.06f, 0.9f)),
                    radius = (4f + breath).coerceAtLeast(1.5f) * sizeScale,
                    center = Offset(size.width * 0.5f + parallaxOffsetX * 0.3f, size.height * 0.10f)
                )
            }
        }
    }
}

