package com.applab.docsnap

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.applab.docsnap.ui.MainActivity

/**
 * Phone notifications. Uploads report through here (Drive build):
 *  - uploadDone:    "5 of 5 uploaded to Land Properties"
 *  - uploadFailed:  "2 could not upload — will retry" / "needs you" after many tries
 *  - waiting:       "12 documents waiting — connect to Wi-Fi and tap Upload all"
 *  - unfinished:    "Unfinished document: 7 pages not named"
 *  - lowStorage:    "Phone storage almost full — upload and free space"
 */
class Notifier(private val context: Context) {

    private val nm = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(NotificationChannel(CH_UPLOAD, "Uploads", NotificationManager.IMPORTANCE_DEFAULT))
            mgr.createNotificationChannel(NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun uploadDone(done: Int, total: Int, folder: String) =
        post(ID_UPLOAD, CH_UPLOAD, "$done of $total uploaded", "All safe in Drive › $folder")

    fun uploadFailed(failed: Int, needsYou: Boolean) =
        post(ID_UPLOAD_FAIL, CH_UPLOAD, "$failed could not upload",
            if (needsYou) "Open DocSnap to check" else "Will try again by itself")

    fun waiting(count: Int) =
        post(ID_WAITING, CH_REMIND, "$count documents waiting", "Connect to Wi-Fi and tap Upload all")

    fun unfinished(pages: Int) =
        post(ID_UNFINISHED, CH_REMIND, "Unfinished document", "$pages pages saved but not named yet")

    fun lowStorage(freeGb: Double) =
        post(ID_STORAGE, CH_REMIND, "Phone storage almost full", "%.1f GB left — upload and free space".format(freeGb))

    private fun post(id: Int, channel: String, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(id, n) }
    }

    companion object {
        private const val CH_UPLOAD = "uploads"
        private const val CH_REMIND = "reminders"
        private const val ID_UPLOAD = 1
        private const val ID_UPLOAD_FAIL = 2
        private const val ID_WAITING = 3
        private const val ID_UNFINISHED = 4
        private const val ID_STORAGE = 5
    }
}
