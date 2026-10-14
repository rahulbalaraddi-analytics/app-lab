package com.applab.batterypilot.collect

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.applab.batterypilot.data.BatterySample

/** Battery health facts that don't change minute to minute. */
data class HealthInfo(
    /** Estimated full capacity right now, mAh (from the charge counter). */
    val estFullMah: Int?,
    /** Factory capacity, mAh (if the phone exposes it). */
    val designMah: Int?,
    /** State of health %, from Android 14+ when allowed, else estimated. */
    val healthPct: Int?,
    val cycleCount: Int?,
    /** Android's coarse health flag: "Good", "Overheat", ... */
    val healthLabel: String,
)

object BatteryReader {

    private fun sticky(context: Context): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun read(context: Context, source: String): BatterySample? {
        val i = sticky(context) ?: return null
        val bm = context.getSystemService(BatteryManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return null
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = plugged != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val current = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
        return BatterySample(
            time = System.currentTimeMillis(),
            level = (level * 100 / scale).coerceIn(0, 100),
            charging = charging,
            plugged = plugged,
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentUa = current,
            chargeCounterUah = counter,
            screenOn = pm?.isInteractive ?: false,
            powerSave = pm?.isPowerSaveMode ?: false,
            source = source,
        )
    }

    fun health(context: Context): HealthInfo {
        val i = sticky(context)
        val bm = context.getSystemService(BatteryManager::class.java)
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val counter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
        // Charge counter is µAh on almost all phones; a few report mAh.
        val estFull = if (counter != null && level in 20..100) {
            val uah = if (counter < 20_000) counter * 1000L else counter.toLong()
            (uah / 1000.0 / (level / 100.0)).toInt()
        } else null
        val design = designCapacity(context)
        var soh: Int? = null
        if (Build.VERSION.SDK_INT >= 34) {
            soh = runCatching {
                bm?.getIntProperty(10 /* BATTERY_PROPERTY_STATE_OF_HEALTH */)
            }.getOrNull()?.takeIf { it in 1..100 }
        }
        if (soh == null && estFull != null && design != null && design > 0) {
            soh = (estFull * 100 / design).coerceIn(1, 100)
        }
        val cycles = if (Build.VERSION.SDK_INT >= 34) {
            i?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else null
        val label = when (i?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        return HealthInfo(estFull, design, soh, cycles, label)
    }

    /** Factory battery size from the phone's power profile (not exposed on every phone). */
    private fun designCapacity(context: Context): Int? = runCatching {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context)
        val mah = cls.getMethod("getBatteryCapacity").invoke(profile) as Double
        mah.toInt().takeIf { it in 1000..20000 }
    }.getOrNull()
}
