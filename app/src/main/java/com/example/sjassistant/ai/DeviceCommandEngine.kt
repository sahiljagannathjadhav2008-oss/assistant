package com.example.sjassistant.ai

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings

data class InstalledAppInfo(
    val label: String,
    val packageName: String
)

data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean
)

/**
 * Executes real zero-bloat offline Android commands:
 * - Installed app discovery & launch via <queries> (no QUERY_ALL_PACKAGES)
 * - Torch / Flashlight toggle via CameraManager
 * - System Settings shortcuts (Wi-Fi, Bluetooth, Battery)
 * - Dialer & Alarm Intents
 * - Real Battery & RAM telemetry
 */
object DeviceCommandEngine {

    fun getLaunchableApps(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty()
                if (label.isEmpty()) return@mapNotNull null
                InstalledAppInfo(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun findMatchingApp(context: Context, query: String): InstalledAppInfo? {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return null
        val apps = getLaunchableApps(context)
        return apps.firstOrNull { it.label.lowercase() == q }
            ?: apps.firstOrNull { it.label.lowercase().contains(q) }
            ?: apps.firstOrNull { app ->
                q.split(Regex("\\s+")).any { word ->
                    word.length >= 3 && app.label.lowercase().contains(word)
                }
            }
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return runCatching {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun setFlashlight(context: Context, enable: Boolean): Result<Boolean> {
        return runCatching {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: error("No hardware flash available")
            cm.setTorchMode(cameraId, enable)
            enable
        }
    }

    fun openSettingsScreen(context: Context, action: String): Boolean {
        return runCatching {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openWifiSettings(context: Context): Boolean =
        openSettingsScreen(context, Settings.ACTION_WIFI_SETTINGS)

    fun openBluetoothSettings(context: Context): Boolean =
        openSettingsScreen(context, Settings.ACTION_BLUETOOTH_SETTINGS)

    fun openBatterySettings(context: Context): Boolean =
        openSettingsScreen(context, Intent.ACTION_POWER_USAGE_SUMMARY)
            || openSettingsScreen(context, Settings.ACTION_SETTINGS)

    fun openDialer(context: Context, phoneNumber: String = ""): Boolean {
        return runCatching {
            val uri = if (phoneNumber.isNotBlank()) Uri.parse("tel:$phoneNumber") else Uri.parse("tel:")
            val intent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun openClockAlarm(context: Context, hour: Int? = null, minute: Int? = null, label: String = ""): Boolean {
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (hour != null) putExtra(AlarmClock.EXTRA_HOUR, hour)
                if (minute != null) putExtra(AlarmClock.EXTRA_MINUTES, minute)
                if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    fun getBatteryStatus(context: Context): BatteryStatus {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryStatus(levelPercent = pct, isCharging = charging)
    }
}
