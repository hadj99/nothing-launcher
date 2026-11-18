package com.example.customwidgets

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.settingsstore.db.LauncherDatabase
import com.example.settingsstore.db.StepHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StepCounterRepository(private val context: Context) {
    private val stepDao = LauncherDatabase.getInstance(context).stepHistoryDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("dotos_steps", Context.MODE_PRIVATE)

    private val _todaySteps = MutableStateFlow(loadStoredTodaySteps())
    val todaySteps: StateFlow<Int> = _todaySteps.asStateFlow()

    private val _sensorAvailable = MutableStateFlow(false)
    val sensorAvailable: StateFlow<Boolean> = _sensorAvailable.asStateFlow()

    val last7Days: Flow<List<StepHistoryEntity>> = stepDao.observeLast7Days()

    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun startListening(dailyGoal: Int) {
        val mgr = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = mgr
        val stepSensor = mgr?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        _sensorAvailable.value = stepSensor != null
        if (stepSensor == null || !hasActivityRecognitionPermission()) return

        if (listener != null) return
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val cumulative = event.values.firstOrNull()?.toInt() ?: return
                val today = currentDateIso()
                val storedDate = prefs.getString("date_iso", null)
                var baseline = prefs.getInt("baseline_steps", -1)
                if (storedDate != today || baseline < 0 || cumulative < baseline) {
                    baseline = cumulative
                    prefs.edit()
                        .putString("date_iso", today)
                        .putInt("baseline_steps", baseline)
                        .apply()
                }
                val stepsToday = (cumulative - baseline).coerceAtLeast(0)
                prefs.edit().putInt("today_steps", stepsToday).apply()
                _todaySteps.value = stepsToday
                scope.launch {
                    stepDao.upsertDay(StepHistoryEntity(today, stepsToday, dailyGoal))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        mgr.registerListener(newListener, stepSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun stopListening() {
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
    }

    suspend fun syncTodayGoal(goal: Int) {
        stepDao.upsertDay(StepHistoryEntity(currentDateIso(), _todaySteps.value, goal))
    }

    private fun loadStoredTodaySteps(): Int {
        val today = currentDateIso()
        val storedDate = prefs.getString("date_iso", null)
        return if (storedDate == today) prefs.getInt("today_steps", 0) else 0
    }

    private fun currentDateIso(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
