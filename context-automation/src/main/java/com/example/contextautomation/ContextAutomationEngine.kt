package com.example.contextautomation

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.example.settingsstore.model.ContextRule
import com.example.settingsstore.model.ContextTriggerType
import com.example.settingsstore.model.QuickToggleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

@Immutable
data class BluetoothPeripheralBattery(
    val name: String,
    val batteryPercent: Int
)

@Immutable
data class DeviceTelemetrySnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val isWifiConnected: Boolean = false,
    val wifiSsid: String = "",
    val isBluetoothOn: Boolean = false,
    val connectedBluetoothNames: List<String> = emptyList(),
    val bluetoothBatteries: List<BluetoothPeripheralBattery> = emptyList(),
    val isTorchOn: Boolean = false,
    val isDndOn: Boolean = false,
    val isBatterySaverOn: Boolean = false,
    val isAutoRotateOn: Boolean = true,
    val currentHour: Int = 12
)

class ContextAutomationEngine(private val context: Context) {
    private val _telemetry = MutableStateFlow(readSnapshot())
    val telemetry: StateFlow<DeviceTelemetrySnapshot> = _telemetry.asStateFlow()

    private var torchEnabled = false

    fun refreshTelemetry(): DeviceTelemetrySnapshot {
        val snap = readSnapshot()
        _telemetry.value = snap
        return snap
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun evaluateMatchingRule(rules: List<ContextRule>): ContextRule? {
        val snap = refreshTelemetry()
        return rules.firstOrNull { rule ->
            if (!rule.enabled) return@firstOrNull false
            when (rule.triggerType) {
                ContextTriggerType.MANUAL -> false
                ContextTriggerType.TIME_WINDOW -> {
                    val h = snap.currentHour
                    if (rule.startHour <= rule.endHour) {
                        h in rule.startHour until rule.endHour
                    } else {
                        h >= rule.startHour || h < rule.endHour
                    }
                }
                ContextTriggerType.WIFI_CONNECTED -> {
                    if (rule.wifiSsid.isBlank()) snap.isWifiConnected
                    else snap.isWifiConnected && snap.wifiSsid.contains(rule.wifiSsid, ignoreCase = true)
                }
                ContextTriggerType.BLUETOOTH_CONNECTED -> {
                    if (rule.bluetoothDeviceName.isBlank()) snap.isBluetoothOn
                    else snap.isBluetoothOn && snap.connectedBluetoothNames.any {
                        it.contains(rule.bluetoothDeviceName, ignoreCase = true)
                    }
                }
                ContextTriggerType.LOW_BATTERY -> snap.batteryPercent <= rule.batteryThreshold
            }
        }
    }

    fun isToggleActive(item: QuickToggleItem, snap: DeviceTelemetrySnapshot): Boolean = when (item) {
        QuickToggleItem.WIFI -> snap.isWifiConnected
        QuickToggleItem.BLUETOOTH -> snap.isBluetoothOn
        QuickToggleItem.FLASHLIGHT -> snap.isTorchOn
        QuickToggleItem.DND -> snap.isDndOn
        QuickToggleItem.BATTERY_SAVER -> snap.isBatterySaverOn
        QuickToggleItem.MOBILE_DATA -> true
        QuickToggleItem.HOTSPOT -> false
        QuickToggleItem.ROTATION_LOCK -> !snap.isAutoRotateOn
    }

    fun triggerQuickToggle(item: QuickToggleItem) {
        when (item) {
            QuickToggleItem.FLASHLIGHT -> toggleFlashlight()
            QuickToggleItem.DND -> toggleDnd()
            QuickToggleItem.WIFI -> {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_WIFI
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                openSettingsAction(action)
            }
            QuickToggleItem.BLUETOOTH -> openSettingsAction(Settings.ACTION_BLUETOOTH_SETTINGS)
            QuickToggleItem.BATTERY_SAVER -> openSettingsAction(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            QuickToggleItem.MOBILE_DATA -> {
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_DATA_ROAMING_SETTINGS
                }
                openSettingsAction(action)
            }
            QuickToggleItem.HOTSPOT -> openSettingsAction(Settings.ACTION_WIRELESS_SETTINGS)
            QuickToggleItem.ROTATION_LOCK -> openSettingsAction(Settings.ACTION_DISPLAY_SETTINGS)
        }
        refreshTelemetry()
    }

    private fun toggleFlashlight() {
        runCatching {
            val camMgr = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            val cameraId = camMgr.cameraIdList.firstOrNull { id ->
                camMgr.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchEnabled = !torchEnabled
            camMgr.setTorchMode(cameraId, torchEnabled)
            _telemetry.value = _telemetry.value.copy(isTorchOn = torchEnabled)
        }
    }

    private fun toggleDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.isNotificationPolicyAccessGranted) {
            val currentlyDnd = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(
                if (currentlyDnd) NotificationManager.INTERRUPTION_FILTER_ALL
                else NotificationManager.INTERRUPTION_FILTER_PRIORITY
            )
            refreshTelemetry()
        } else {
            openSettingsAction(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }

    private fun openSettingsAction(action: String) {
        val intent = Intent(action).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun readSnapshot(): DeviceTelemetrySnapshot {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val pct = ((level / scale.toFloat()) * 100f).toInt().coerceIn(0, 100)
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = if (activeNet != null) cm.getNetworkCapabilities(activeNet) else null
        val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val ssid = runCatching {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.connectionInfo?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" }.orEmpty()
        }.getOrDefault("")

        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bm?.adapter
        val btOn = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val btNames = mutableListOf<String>()
        val btBatteries = mutableListOf<BluetoothPeripheralBattery>()

        if (btOn && hasBluetoothConnectPermission()) {
            runCatching {
                adapter?.bondedDevices?.forEach { device ->
                    val name = device.name ?: return@forEach
                    btNames.add(name)
                    val batLevel = readDeviceBatteryLevel(device)
                    if (batLevel in 0..100) {
                        btBatteries.add(BluetoothPeripheralBattery(name, batLevel))
                    }
                }
            }
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val dnd = (nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL) !=
            NotificationManager.INTERRUPTION_FILTER_ALL

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val saver = pm?.isPowerSaveMode == true

        val autoRotate = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        }.getOrDefault(true)

        return DeviceTelemetrySnapshot(
            batteryPercent = pct,
            isCharging = charging,
            isWifiConnected = wifi,
            wifiSsid = ssid,
            isBluetoothOn = btOn,
            connectedBluetoothNames = btNames,
            bluetoothBatteries = btBatteries,
            isTorchOn = torchEnabled,
            isDndOn = dnd,
            isBatterySaverOn = saver,
            isAutoRotateOn = autoRotate,
            currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        )
    }

    private fun readDeviceBatteryLevel(device: BluetoothDevice): Int {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int) ?: -1
        }.getOrDefault(-1)
    }
}
