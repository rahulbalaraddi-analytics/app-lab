package com.applab.batterypilot.collect

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.applab.batterypilot.R
import com.applab.batterypilot.analysis.Engine
import com.applab.batterypilot.data.AppDatabase
import com.applab.batterypilot.data.FixLog
import com.applab.batterypilot.data.Prefs
import com.applab.batterypilot.fixes.Fixes
import com.applab.batterypilot.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Every ~15 min: take a reading, update the forecast, alert / smart-save if needed. Very light. */
class SampleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val source = inputData.getString("source") ?: "worker"
        Engine.recordNow(applicationContext, source)
        runCatching { Guard.check(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "battery-sampler"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SampleWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runOnce(context: Context, source: String) {
            val req = OneTimeWorkRequestBuilder<SampleWorker>()
                .setInputData(androidx.work.workDataOf("source" to source))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

/** Plug / unplug / boot / app update. */
class PowerEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val source = when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> "plug"
            Intent.ACTION_POWER_DISCONNECTED -> "unplug"
            Intent.ACTION_BOOT_COMPLETED -> "boot"
            else -> "update"
        }
        SampleWorker.schedule(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Engine.recordNow(context.applicationContext, source)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Notification button: "Turn on Power saving". */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SAVER) return
        val ok = Fixes.setPowerSaving(context, true)
        context.getSystemService(NotificationManager::class.java)?.cancel(Guard.NOTIF_ID)
        if (ok) {
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    AppDatabase.get(context).fixes().insert(
                        FixLog(time = System.currentTimeMillis(), fixId = "power_saving", title = "Power saving (from alert)", action = "applied"),
                    )
                } finally { pending.finish() }
            }
        } else {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    companion object { const val ACTION_SAVER = "com.applab.batterypilot.TURN_ON_SAVER" }
}

/** Decides when to warn you or switch on Power saving. */
object Guard {
    const val CHANNEL = "forecast"
    const val NOTIF_ID = 11

    suspend fun check(context: Context) {
        val prefs = Prefs(context)
        val latest = AppDatabase.get(context).samples().latest() ?: return
        if (latest.charging) return
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val today = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        val access = Access.state(context)

        // Rule 1: fixed threshold ("Power saving below X%").
        if (prefs.saverBelowPct > 0 && latest.level <= prefs.saverBelowPct && !latest.powerSave &&
            access.secureSettings && prefs.lastAutoSaverDay != today
        ) {
            if (Fixes.setPowerSaving(context, true)) {
                prefs.lastAutoSaverDay = today
                logAuto(context, "Power saving at ${latest.level}% (your rule)")
                notify(context, "Power saving turned on", "Battery at ${latest.level}%. Turned on to help you reach ${Fixes.hourLabel(prefs.targetHour)}.", false)
            }
            return
        }

        if (hour < 10 || hour >= prefs.targetHour) return
        val (_, f) = Engine.quickForecast(context)
        f ?: return
        if (f.makesIt && f.levelAtTarget >= prefs.bufferPct / 2.0) return
        val emptyText = f.emptyAt?.let { "0% around ${timeFmt(it)}" } ?: "only ~${f.levelAtTarget.toInt().coerceAtLeast(0)}% at ${Fixes.hourLabel(prefs.targetHour)}"

        // Rule 2: Smart saver — only when it really won't make it, and not too early in the day.
        if (prefs.smartSaver && access.secureSettings && !latest.powerSave && !f.makesIt &&
            (hour >= 15 || latest.level <= 40) && prefs.lastAutoSaverDay != today
        ) {
            if (Fixes.setPowerSaving(context, true)) {
                prefs.lastAutoSaverDay = today
                logAuto(context, "Smart saver: Power saving on")
                notify(context, "Smart saver: Power saving on", "Forecast was $emptyText. Power saving should stretch it.", false)
                return
            }
        }

        // Rule 3: one heads-up per day.
        if (prefs.alertsOn && prefs.lastAlertDay != today && !latest.powerSave) {
            prefs.lastAlertDay = today
            notify(context, "Heading for $emptyText", "You're draining ${"%.1f".format(f.recentRate ?: f.sinceUnplugRate ?: 0.0)}%/hr. Allowed: ${"%.1f".format(f.allowedRate)}%/hr. Tap for quick fixes.", access.secureSettings)
        }
    }

    private suspend fun logAuto(context: Context, title: String) {
        AppDatabase.get(context).fixes().insert(
            FixLog(time = System.currentTimeMillis(), fixId = "power_saving", title = title, action = "applied"),
        )
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Battery forecast", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Heads-up when your battery won't last till your target time"
            },
        )
    }

    private fun notify(context: Context, title: String, text: String, saverButton: Boolean) {
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (saverButton) {
            val saver = PendingIntent.getBroadcast(
                context, 1,
                Intent(context, AlertActionReceiver::class.java).setAction(AlertActionReceiver.ACTION_SAVER),
                PendingIntent.FLAG_IMMUTABLE,
            )
            b.addAction(0, "Turn on Power saving", saver)
        }
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, b.build()) }
    }

    fun timeFmt(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
}
