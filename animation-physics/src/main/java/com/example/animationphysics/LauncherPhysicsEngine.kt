package com.example.animationphysics

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.example.settingsstore.model.LaunchTransitionStyle
import com.example.settingsstore.model.PageTransitionStyle
import com.example.settingsstore.model.PhysicsConfig
import kotlin.math.absoluteValue
import kotlin.math.sqrt

object LauncherPhysicsEngine {

    fun <T> physicsSpring(physics: PhysicsConfig): SpringSpec<T> {
        val speedFactor = physics.animationSpeed.coerceIn(0.25f, 2.0f)
        val adjustedStiffness = (physics.springStiffness * speedFactor).coerceIn(80f, 2400f)
        val adjustedDamping = physics.dampingBounce.coerceIn(0.3f, 1.0f)
        return spring(
            dampingRatio = adjustedDamping,
            stiffness = adjustedStiffness
        )
    }

    fun <T> launchTween(physics: PhysicsConfig, baseDurationMs: Int = 220): TweenSpec<T> {
        val duration = when (physics.launchTransition) {
            LaunchTransitionStyle.INSTANT -> 1
            else -> (baseDurationMs / physics.animationSpeed.coerceIn(0.25f, 2.0f)).toInt().coerceIn(30, 800)
        }
        return tween(durationMillis = duration)
    }

    fun Modifier.pagerTransition(
        pageOffset: Float,
        style: PageTransitionStyle,
        intensity: Float = 1.0f
    ): Modifier = pagerTransitionDeferred(
        pageOffsetProvider = { pageOffset },
        style = style,
        intensity = intensity
    )

    fun Modifier.pagerTransitionDeferred(
        pageOffsetProvider: () -> Float,
        style: PageTransitionStyle,
        intensity: Float = 1.0f
    ): Modifier {
        if (style == PageTransitionStyle.FLAT) return this
        val factor = intensity.coerceIn(0.2f, 2.0f)
        return graphicsLayer {
            val pageOffset = pageOffsetProvider()
            val absOffset = (pageOffset.absoluteValue * factor).coerceIn(0f, 1.5f)
            when (style) {
                PageTransitionStyle.FLAT -> Unit

                PageTransitionStyle.DEPTH -> {
                    val scale = (1f - (absOffset * 0.14f)).coerceIn(0.6f, 1f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - absOffset * 0.55f).coerceIn(0f, 1f)
                }

                PageTransitionStyle.CUBE -> {
                    cameraDistance = 18f * density
                    transformOrigin = TransformOrigin(if (pageOffset < 0f) 1f else 0f, 0.5f)
                    rotationY = (-36f * pageOffset * factor).coerceIn(-68f, 68f)
                    alpha = (1f - absOffset * 0.3f).coerceIn(0.2f, 1f)
                }

                PageTransitionStyle.PARALLAX -> {
                    translationX = -pageOffset * size.width * 0.18f * factor
                    val scale = (1f - absOffset * 0.06f).coerceIn(0.75f, 1f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - absOffset * 0.25f).coerceIn(0.4f, 1f)
                }

                PageTransitionStyle.ACCORDION -> {
                    transformOrigin = TransformOrigin(if (pageOffset < 0f) 0f else 1f, 0.5f)
                    scaleX = (1f - absOffset * 0.65f).coerceIn(0.2f, 1f)
                    alpha = (1f - absOffset * 0.4f).coerceIn(0.2f, 1f)
                }

                PageTransitionStyle.STACK -> {
                    if (pageOffset > 0f) {
                        translationX = -pageOffset * size.width * 0.5f * factor
                        val scale = (1f - absOffset * 0.12f).coerceIn(0.65f, 1f)
                        scaleX = scale
                        scaleY = scale
                        alpha = (1f - absOffset * 0.7f).coerceIn(0f, 1f)
                    }
                }
            }
        }
    }
}

object NothingHaptics {
    @Suppress("DEPRECATION")
    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun performTick(context: Context, physics: PhysicsConfig) {
        if (!physics.hapticOnScrollTick || physics.hapticIntensity <= 0.05f) return
        triggerPulse(context, durationMs = 10L, amplitude = (physics.hapticIntensity * 160).toInt().coerceIn(20, 255))
    }

    fun performConfirm(context: Context, physics: PhysicsConfig) {
        if (physics.hapticIntensity <= 0.05f) return
        triggerPulse(context, durationMs = 22L, amplitude = (physics.hapticIntensity * 230).toInt().coerceIn(40, 255))
    }

    @Suppress("DEPRECATION")
    private fun triggerPulse(context: Context, durationMs: Long, amplitude: Int) {
        runCatching {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                vibrator.vibrate(durationMs)
            }
        }
    }
}

@Composable
fun ShakeGestureObserver(
    enabled: Boolean,
    onShakeDetected: () -> Unit
) {
    val context = LocalContext.current
    val latestCallback by rememberUpdatedState(onShakeDetected)
    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var lastShakeTime = 0L
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val gForce = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
                    val now = System.currentTimeMillis()
                    if (gForce > 2.35 && now - lastShakeTime > 1200L) {
                        lastShakeTime = now
                        latestCallback()
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
}
