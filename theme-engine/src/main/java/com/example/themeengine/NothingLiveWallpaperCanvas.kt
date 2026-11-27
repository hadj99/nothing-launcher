package com.example.themeengine

import android.content.Context
import android.graphics.Paint
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
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.example.settingsstore.model.SurfaceThemeConfig
import com.example.settingsstore.model.WallpaperMode
import com.example.settingsstore.model.WallpaperWavePattern
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private class DotBatchScratch {
    var lowPts = FloatArray(1024)
    var midPts = FloatArray(1024)
    var highPts = FloatArray(1024)
    var accentPts = FloatArray(256)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        style = Paint.Style.STROKE
    }

    fun ensureCapacity(maxDots: Int) {
        val needed = maxDots * 2
        if (lowPts.size < needed) {
            lowPts = FloatArray(needed)
            midPts = FloatArray(needed)
            highPts = FloatArray(needed)
        }
        val accentNeeded = (maxDots / 2).coerceAtLeast(128) * 2
        if (accentPts.size < accentNeeded) {
            accentPts = FloatArray(accentNeeded)
        }
    }
}

@Composable
fun NothingLiveWallpaperCanvas(
    theme: SurfaceThemeConfig,
    deepFocusActive: Boolean,
    paused: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current

    if (paused) {
        Canvas(modifier = modifier.fillMaxSize()) {
            drawRect(color = palette.background)
        }
        return
    }

    val tiltXState = remember { mutableFloatStateOf(0f) }
    val tiltYState = remember { mutableFloatStateOf(0f) }

    DisposableEffect(theme.wallpaperTiltParallax) {
        if (!theme.wallpaperTiltParallax) {
            tiltXState.floatValue = 0f
            tiltYState.floatValue = 0f
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val nx = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val ny = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    val nextX = tiltXState.floatValue * 0.84f + nx * 0.16f
                    val nextY = tiltYState.floatValue * 0.84f + ny * 0.16f
                    if (abs(nextX - tiltXState.floatValue) > 0.028f || abs(nextY - tiltYState.floatValue) > 0.028f) {
                        tiltXState.floatValue = nextX
                        tiltYState.floatValue = nextY
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (accel != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_NORMAL)
            }
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        }
    }

    val animateActive = theme.wallpaperAnimationEnabled && theme.wallpaperWavePattern != WallpaperWavePattern.STATIC
    val durationMs = (8000f / theme.wallpaperAnimSpeed.coerceIn(0.2f, 3.0f)).roundToInt().coerceIn(1800, 35000)

    // Read animated phase ONLY inside Canvas draw scope — ZERO @Composable recompositions!
    val phaseState: State<Float>? = if (animateActive) {
        val transition = rememberInfiniteTransition(label = "wallpaper_pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        null
    }

    val scratch = remember { DotBatchScratch() }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = palette.background)

        val phase = phaseState?.value ?: 0f
        val tiltX = tiltXState.floatValue
        val tiltY = tiltYState.floatValue

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
                val step = (66f - density * 22f).coerceAtLeast(40f)
                val cols = (size.width / step).toInt() + 2
                val rows = (size.height / step).toInt() + 2
                val maxDots = (cols + 1) * (rows + 1)
                scratch.ensureCapacity(maxDots)

                val centerCol = cols * 0.5f
                val centerRow = rows * 0.45f
                val offsetX = parallaxOffsetX * 0.4f
                val offsetY = parallaxOffsetY * 0.4f

                var lowIdx = 0
                var midIdx = 0
                var highIdx = 0
                var accIdx = 0

                val lowArr = scratch.lowPts
                val midArr = scratch.midPts
                val highArr = scratch.highPts
                val accArr = scratch.accentPts

                for (r in 0..rows) {
                    val y = r * step + offsetY
                    for (c in 0..cols) {
                        val x = c * step + offsetX
                        val accentThreshold = when {
                            accentFreq <= 0.05f -> false
                            accentFreq >= 1.5f -> (c % 6 == 2 && r % 7 == 3)
                            accentFreq >= 0.7f -> (c % 9 == 4 && r % 11 == 5)
                            else -> (c % 13 == 6 && r % 15 == 7)
                        }
                        if (accentThreshold && !deepFocusActive && accIdx + 1 < accArr.size) {
                            accArr[accIdx++] = x
                            accArr[accIdx++] = y
                            continue
                        }

                        val rawWave = when (theme.wallpaperWavePattern) {
                            WallpaperWavePattern.DIAGONAL_WAVE -> sin((c * 0.35f) + (r * 0.25f) + phase)
                            WallpaperWavePattern.RADIAL_RIPPLE -> {
                                val dc = c - centerCol
                                val dr = r - centerRow
                                sin(hypot(dc, dr) * 0.65f - phase * 2f)
                            }
                            WallpaperWavePattern.MATRIX_RAIN -> sin((r * 0.45f) - phase * 2f + (c % 5) * 1.1f)
                            WallpaperWavePattern.BREATHING_PULSE -> sin(phase)
                            WallpaperWavePattern.STATIC -> 0f
                        }
                        val wave = rawWave * intensity
                        when {
                            wave > 0.35f -> {
                                highArr[highIdx++] = x
                                highArr[highIdx++] = y
                            }
                            wave > -0.35f -> {
                                midArr[midIdx++] = x
                                midArr[midIdx++] = y
                            }
                            else -> {
                                lowArr[lowIdx++] = x
                                lowArr[lowIdx++] = y
                            }
                        }
                    }
                }

                // 4 batched native drawPoints calls instead of ~2,000 individual drawCircle JNI calls!
                val nc = drawContext.canvas.nativeCanvas
                val baseRgb = if (palette.isLightSurface) 0x001A1A1E else 0x00FFFFFF
                val p = scratch.paint

                if (lowIdx > 0) {
                    val a = ((0.045f * brightness).coerceIn(0.015f, 0.35f) * 255f).toInt()
                    p.color = (a shl 24) or baseRgb
                    p.strokeWidth = 3.2f * sizeScale
                    nc.drawPoints(lowArr, 0, lowIdx, p)
                }
                if (midIdx > 0) {
                    val a = ((0.09f * brightness).coerceIn(0.025f, 0.42f) * 255f).toInt()
                    p.color = (a shl 24) or baseRgb
                    p.strokeWidth = 4.2f * sizeScale
                    nc.drawPoints(midArr, 0, midIdx, p)
                }
                if (highIdx > 0) {
                    val a = ((0.14f * brightness).coerceIn(0.035f, 0.48f) * 255f).toInt()
                    p.color = (a shl 24) or baseRgb
                    p.strokeWidth = 5.2f * sizeScale
                    nc.drawPoints(highArr, 0, highIdx, p)
                }
                if (accIdx > 0) {
                    p.color = palette.accent.copy(alpha = (0.42f * brightness).coerceIn(0.1f, 0.9f)).toArgb()
                    p.strokeWidth = 6.4f * sizeScale
                    nc.drawPoints(accArr, 0, accIdx, p)
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
