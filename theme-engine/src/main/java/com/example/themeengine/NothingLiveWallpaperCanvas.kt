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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

    // User photo wallpaper: decoded once, dimmed by the configured scrim.
    val photoUri: String? = theme.wallpaperImageUri
    if (theme.wallpaperMode == WallpaperMode.CUSTOM_PHOTO && photoUri != null) {
        PhotoWallpaperLayer(
            uri = photoUri,
            dim = theme.wallpaperImageDim,
            modifier = modifier.fillMaxSize()
        )
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

            WallpaperMode.STARFIELD_WARP -> {
                val cx = size.width * 0.5f + parallaxOffsetX * 0.8f
                val cy = size.height * 0.44f + parallaxOffsetY * 0.8f
                val maxR = size.maxDimension * 0.65f
                val starCount = (48 + theme.wallpaperDotDensity * 48f).toInt().coerceIn(40, 96)
                val progress = phase / (Math.PI * 2.0).toFloat()
                val ink = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color.White

                for (i in 0 until starCount) {
                    val angle = (i * 2.399963f) // golden angle distribution
                    val speedMul = 1f + (i % 3) * 0.5f
                    val z = ((i * 0.173f + progress * speedMul) % 1f)
                    val dist = z * z * maxR
                    val tailDist = (dist - (10f + z * 28f * intensity) * sizeScale).coerceAtLeast(0f)
                    val sx = cx + cos(angle) * dist
                    val sy = cy + sin(angle) * dist
                    val tx = cx + cos(angle) * tailDist
                    val ty = cy + sin(angle) * tailDist
                    val alpha = (z * 0.52f * brightness).coerceIn(0.03f, 0.85f)
                    val isAccent = !deepFocusActive && accentFreq > 0.1f && (i % 11 == 0)
                    val starColor = if (isAccent) palette.accent.copy(alpha = (alpha * 1.3f).coerceIn(0.1f, 0.95f)) else ink.copy(alpha = alpha)
                    drawLine(
                        color = starColor,
                        start = Offset(tx, ty),
                        end = Offset(sx, sy),
                        strokeWidth = (1.2f + z * 2.4f) * sizeScale
                    )
                }
            }

            WallpaperMode.CYBER_HORIZON -> {
                val horizonY = size.height * 0.48f + parallaxOffsetY * 0.5f
                val vanishX = size.width * 0.5f + parallaxOffsetX * 0.9f
                val ink = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color.White
                val sunR = size.minDimension * 0.22f * sizeScale.coerceIn(0.6f, 1.4f)

                // Pulsing horizon glyph sun
                val sunPulse = if (animateActive) sin(phase) * 5f * intensity else 0f
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = (0.36f * brightness).coerceIn(0.08f, 0.8f)),
                            palette.accent.copy(alpha = 0.03f)
                        ),
                        startY = horizonY - sunR * 1.4f,
                        endY = horizonY
                    ),
                    radius = sunR + sunPulse,
                    center = Offset(vanishX, horizonY - sunR * 0.25f)
                )
                // Perspective radial rays
                val rayCount = 13
                for (i in -rayCount..rayCount) {
                    val endX = vanishX + i * (size.width * 0.16f)
                    drawLine(
                        color = ink.copy(alpha = (0.11f * brightness).coerceIn(0.03f, 0.4f)),
                        start = Offset(vanishX, horizonY),
                        end = Offset(endX, size.height),
                        strokeWidth = 1.2f * sizeScale
                    )
                }
                // Scrolling perspective horizontal depth lines
                val scroll = (phase / (Math.PI * 2.0).toFloat())
                val bands = 11
                for (b in 0 until bands) {
                    val t = ((b + scroll) / bands).coerceIn(0.01f, 1f)
                    val y = horizonY + (t * t) * (size.height - horizonY)
                    val isAccent = !deepFocusActive && (b % 4 == 0)
                    drawLine(
                        color = if (isAccent) {
                            palette.accent.copy(alpha = (t * 0.38f * brightness).coerceIn(0.05f, 0.75f))
                        } else {
                            ink.copy(alpha = (t * 0.18f * brightness).coerceIn(0.03f, 0.5f))
                        },
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = (0.9f + t * 1.8f) * sizeScale
                    )
                }
            }

            WallpaperMode.AURORA_RIBBONS -> {
                val ribbonCount = 5
                val path = Path()
                val ink = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color.White
                for (r in 0 until ribbonCount) {
                    path.reset()
                    val baseY = size.height * (0.24f + r * 0.13f) + parallaxOffsetY * (0.4f + r * 0.15f)
                    val segments = 18
                    val dx = size.width / segments
                    for (s in 0..segments) {
                        val x = s * dx
                        val wave = sin(s * 0.42f + phase * (if (r % 2 == 0) 1f else -1f) + r * 1.2f) *
                            (28f + r * 9f) * intensity.coerceAtLeast(0.35f)
                        val y = baseY + wave
                        if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    val isAccent = !deepFocusActive && (r == 1 || r == 3)
                    drawPath(
                        path = path,
                        color = if (isAccent) {
                            palette.accent.copy(alpha = (0.28f * brightness).coerceIn(0.06f, 0.75f))
                        } else {
                            ink.copy(alpha = (0.12f * brightness).coerceIn(0.03f, 0.45f))
                        },
                        style = Stroke(width = (3.5f + r * 1.6f) * sizeScale)
                    )
                }
            }

            WallpaperMode.ORBITAL_CONSTELLATION -> {
                val nodeCount = 24
                val ink = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color.White
                val xs = FloatArray(nodeCount)
                val ys = FloatArray(nodeCount)
                for (i in 0 until nodeCount) {
                    val baseX = ((i * 137) % 100) / 100f * size.width
                    val baseY = ((i * 293 + 37) % 100) / 100f * size.height
                    val driftX = sin(phase + i * 0.9f) * 26f * intensity.coerceAtLeast(0.25f)
                    val driftY = cos(phase * 1.0f + i * 1.3f) * 26f * intensity.coerceAtLeast(0.25f)
                    xs[i] = baseX + driftX + parallaxOffsetX * 0.6f
                    ys[i] = baseY + driftY + parallaxOffsetY * 0.6f
                }
                val linkThresh = size.minDimension * 0.30f
                val linkThreshSq = linkThresh * linkThresh
                for (i in 0 until nodeCount) {
                    for (j in i + 1 until nodeCount) {
                        val dx = xs[i] - xs[j]
                        val dy = ys[i] - ys[j]
                        val distSq = dx * dx + dy * dy
                        if (distSq < linkThreshSq) {
                            val ratio = 1f - (distSq / linkThreshSq)
                            drawLine(
                                color = ink.copy(alpha = (ratio * 0.18f * brightness).coerceIn(0.02f, 0.45f)),
                                start = Offset(xs[i], ys[i]),
                                end = Offset(xs[j], ys[j]),
                                strokeWidth = 1.1f * sizeScale
                            )
                        }
                    }
                    val isAccent = !deepFocusActive && (i % 5 == 0)
                    drawCircle(
                        color = if (isAccent) palette.accent.copy(alpha = (0.65f * brightness).coerceIn(0.15f, 0.95f))
                        else ink.copy(alpha = (0.34f * brightness).coerceIn(0.08f, 0.8f)),
                        radius = (if (isAccent) 3.8f else 2.4f) * sizeScale,
                        center = Offset(xs[i], ys[i])
                    )
                }
            }

            WallpaperMode.PLASMA_RINGS -> {
                val cx = size.width * 0.5f + parallaxOffsetX * 0.7f
                val cy = size.height * 0.42f + parallaxOffsetY * 0.7f
                val ink = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color.White
                val degBase = Math.toDegrees(phase.toDouble()).toFloat()
                val radiiFracs = floatArrayOf(0.14f, 0.23f, 0.32f, 0.41f, 0.50f)
                radiiFracs.forEachIndexed { idx, frac ->
                    val r = size.minDimension * frac
                    val dir = if (idx % 2 == 0) 1f else -1f
                    val start = degBase * dir + idx * 54f
                    val isAccent = !deepFocusActive && (idx == 0 || idx == 2)
                    val arcColor = if (isAccent) {
                        palette.accent.copy(alpha = (0.42f * brightness).coerceIn(0.1f, 0.85f))
                    } else {
                        ink.copy(alpha = (0.18f * brightness).coerceIn(0.04f, 0.55f))
                    }
                    for (seg in 0 until 3) {
                        drawArc(
                            color = arcColor,
                            startAngle = start + seg * 120f,
                            sweepAngle = 68f,
                            useCenter = false,
                            topLeft = Offset(cx - r, cy - r),
                            size = Size(r * 2f, r * 2f),
                            style = Stroke(width = (if (isAccent) 3.8f else 2.0f) * sizeScale)
                        )
                    }
                }
            }

            WallpaperMode.METEOR_RAIN -> {
                val cols = (14 + theme.wallpaperDotDensity * 12f).toInt().coerceIn(12, 26)
                val colW = size.width / cols
                val dropStep = 28f
                val ink = if (palette.isLightSurface) Color(0xFF1A1A1E) else Color.White
                val normPhase = phase / (Math.PI * 2.0).toFloat()

                for (c in 0 until cols) {
                    val x = (c + 0.5f) * colW + parallaxOffsetX * 0.35f
                    val speedMul = 1 + (c % 2)
                    val offsetFrac = ((c * 0.31f) + normPhase * speedMul) % 1f
                    val headY = offsetFrac * (size.height + 220f) - 110f
                    val isAccentCol = !deepFocusActive && (c % 5 == 2)
                    val trailDots = 9
                    for (t in 0 until trailDots) {
                        val y = headY - t * dropStep
                        if (y < -20f || y > size.height + 20f) continue
                        val fade = (1f - t.toFloat() / trailDots)
                        val color = if (t == 0 && isAccentCol) {
                            palette.accent.copy(alpha = (0.78f * brightness).coerceIn(0.15f, 0.95f))
                        } else if (t == 0) {
                            ink.copy(alpha = (0.48f * brightness).coerceIn(0.1f, 0.85f))
                        } else {
                            (if (isAccentCol) palette.accent else ink).copy(
                                alpha = (fade * 0.22f * brightness).coerceIn(0.02f, 0.55f)
                            )
                        }
                        drawCircle(
                            color = color,
                            radius = (if (t == 0) 3.2f else 2.1f) * sizeScale,
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

            WallpaperMode.CUSTOM_PHOTO -> {
                // Handled by PhotoWallpaperLayer above (no photo selected → fall through to dots).
                drawRect(color = palette.background)
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


/**
 * Draws the user's own photo as the wallpaper, centre-cropped, with an optional dim scrim so
 * white icons and dot-matrix labels stay readable.
 */
@Composable
fun PhotoWallpaperLayer(
    uri: String,
    dim: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(uri) { loadWallpaperBitmap(context, uri, 1440) }
    if (bitmap == null) {
        Canvas(modifier = modifier) { drawRect(color = Color.Black) }
        return
    }
    Canvas(modifier = modifier) {
        val scale = maxOf(size.width / bitmap.width, size.height / bitmap.height)
        val drawW = bitmap.width * scale
        val drawH = bitmap.height * scale
        drawImage(
            image = bitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(
                ((size.width - drawW) / 2f).toInt(),
                ((size.height - drawH) / 2f).toInt()
            ),
            dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt())
        )
        val scrim = dim.coerceIn(0f, 0.9f)
        if (scrim > 0f) drawRect(color = Color.Black.copy(alpha = scrim))
    }
}

fun loadWallpaperBitmap(context: Context, uri: String, maxWidth: Int): androidx.compose.ui.graphics.ImageBitmap? =
    runCatching {
        val parsed = android.net.Uri.parse(uri)
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(parsed)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, opts)
        } ?: return null
        bmp.asImageBitmap()
    }.getOrNull()

/**
 * Extracts a Material-You-ish palette from the user's wallpaper: the most saturated dominant colour
 * becomes the accent, a darkened copy becomes the surface, and a near-black tint the background.
 */
data class ExtractedWallpaperPalette(
    val accentHex: String,
    val surfaceHex: String,
    val backgroundHex: String,
    val textSecondaryHex: String
)

fun extractWallpaperPalette(context: Context, uri: String): ExtractedWallpaperPalette? = runCatching {
    val bitmap = loadWallpaperBitmap(context, uri, 256)?.asAndroidBitmap() ?: return null
    val colors = android.graphics.Bitmap.createScaledBitmap(bitmap, 48, 96, true)
    val buckets = HashMap<Int, IntArray>() // key: quantised rgb
    var totalSaturation = 0.0
    for (x in 0 until colors.width) {
        for (y in 0 until colors.height) {
            val px = colors.getPixel(x, y)
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(px, hsv)
            val key = (hsv[0].toInt() / 15) // 24 hue buckets
            val entry = buckets.getOrPut(key) { IntArray(3) }
            entry[0] += 1
            entry[1] += px
            entry[2] = if (hsv[1] > entry[2] / 100f) (hsv[1] * 100).toInt() else entry[2]
            totalSaturation += hsv[1]
        }
    }
    val dominant = buckets.entries.maxByOrNull { it.value[0] } ?: return null
    val count = dominant.value[0]
    val avg = dominant.value[1] / count.coerceAtLeast(1)
    var r = (android.graphics.Color.red(avg) + 40).coerceAtMost(255)
    var g = (android.graphics.Color.green(avg) + 40).coerceAtMost(255)
    var b = (android.graphics.Color.blue(avg) + 40).coerceAtMost(255)
    // Guarantee a lively accent: if the dominant colour is grey-ish, push towards its hue.
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(avg, hsv)
    if (hsv[1] < 0.35f) {
        hsv[1] = 0.72f
        hsv[2] = 0.95f
        val boosted = android.graphics.Color.HSVToColor(hsv)
        r = android.graphics.Color.red(boosted)
        g = android.graphics.Color.green(boosted)
        b = android.graphics.Color.blue(boosted)
    }
    val accent = String.format("#%02X%02X%02X", r, g, b)
    val surface = String.format("#%02X%02X%02X", (r * 0.13f).toInt(), (g * 0.13f).toInt(), (b * 0.13f).toInt())
    val background = String.format("#%02X%02X%02X", (r * 0.05f).toInt(), (g * 0.05f).toInt(), (b * 0.06f).toInt())
    ExtractedWallpaperPalette(
        accentHex = accent,
        surfaceHex = surface,
        backgroundHex = background,
        textSecondaryHex = "#9A9AA2"
    )
}.getOrNull()
