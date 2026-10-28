package com.bloo.bluelink.perf

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.bloo.bluelink.BuildConfig
import java.util.Locale

/**
 * What the phone is, and what state it is in, at the moment it is asked: the header of every performance log and report, so a line
 * can be read against the device that produced it (a 60 Hz panel and a 120 Hz one budget frames differently; a phone in battery saver
 * or hot throttles, and its frames say so).
 */
internal object PerfDevice {
    fun header(context: Context, budgetMs: Float): List<String> {
        val am = context.getSystemService(ActivityManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.let { b ->
            val l = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (l >= 0 && scale > 0) l * 100 / scale else -1
        } ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val metrics = context.resources.displayMetrics
        @Suppress("DEPRECATION")
        val refresh = context.getSystemService(android.view.WindowManager::class.java)?.defaultDisplay?.refreshRate ?: 0f
        val animatorScale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        return listOf(
            "device ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}, product ${Build.PRODUCT})",
            "os Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), patch ${Build.VERSION.SECURITY_PATCH}",
            "build ${BuildConfig.BUILD_RUN_NUMBER}, fingerprint ${Build.FINGERPRINT}",
            "display ${metrics.widthPixels}x${metrics.heightPixels}px, ${metrics.densityDpi} dpi, ${"%.0f".format(refresh)} Hz " +
                "(frame budget ${"%.1f".format(budgetMs)} ms), font scale ${"%.2f".format(context.resources.configuration.fontScale)}",
            "cpu ${Runtime.getRuntime().availableProcessors()} cores, memory class ${am?.memoryClass} MB " +
                "(large ${am?.largeMemoryClass} MB), low-ram ${am?.isLowRamDevice}, max heap ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB",
            "power save ${power?.isPowerSaveMode}, thermal ${thermalName(power)}, battery $level% ${if (charging) "charging" else "on battery"}",
            "animator scale $animatorScale, locale ${Locale.getDefault()}, abi ${Build.SUPPORTED_ABIS.firstOrNull()}",
        )
    }

    /** The phone's thermal state, by name. */
    fun thermalName(power: PowerManager?): String {
        if (power == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "unknown"
        return when (power.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "none"
            PowerManager.THERMAL_STATUS_LIGHT -> "light"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "shutdown"
            else -> "unknown"
        }
    }
}
