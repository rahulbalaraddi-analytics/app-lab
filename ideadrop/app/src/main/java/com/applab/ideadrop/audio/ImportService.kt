package com.applab.ideadrop.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.applab.ideadrop.IdeaDropApp
import com.applab.ideadrop.R
import com.applab.ideadrop.speech.PhoneSpeech
import com.applab.ideadrop.ui.MainActivity
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val name: String, val progress: Float, val liveText: String) : ImportState
    data class Done(val name: String, val noteId: Long?, val message: String) : ImportState
}

/** App-wide state of the "Import audio" job, observed by the UI. */
object ImportController {
    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()
    internal fun set(s: ImportState) { _state.value = s }
    fun clear() { if (_state.value is ImportState.Done) _state.value = ImportState.Idle }

    /** [file] is a private copy inside the app; it becomes the note's audio clip. */
    fun start(context: Context, file: File, displayName: String, language: String) {
        if (_state.value is ImportState.Working) return
        val intent = Intent(context, ImportService::class.java)
            .setAction(ImportService.ACTION_START)
            .putExtra(ImportService.EXTRA_PATH, file.absolutePath)
            .putExtra(ImportService.EXTRA_NAME, displayName)
            .putExtra(ImportService.EXTRA_LANGUAGE, language)
        ContextCompat.startForegroundService(context, intent)
    }

    fun cancel(context: Context) {
        context.startService(Intent(context, ImportService::class.java).setAction(ImportService.ACTION_CANCEL))
    }
}

/**
 * Turns an audio file (e.g. a call recording) into a text note: decodes it and streams it
 * to the phone's speech engine. Runs as a foreground service so long calls keep going
 * with the screen off.
 */
class ImportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val cancelled = AtomicBoolean(false)
    @Volatile private var stream: java.io.OutputStream? = null
    private lateinit var notifications: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (job == null) {
                val path = intent.getStringExtra(EXTRA_PATH) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Audio"
                val language = intent.getStringExtra(EXTRA_LANGUAGE) ?: PhoneSpeech.MIXED
                goForeground(name)
                job = scope.launch { run(File(path), name, language) }
            }
            ACTION_CANCEL -> {
                cancelled.set(true)
                runCatching { stream?.close() } // unblocks a write waiting on the engine
                if (job == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(name: String, progress: Int): android.app.Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(
            this, 2, Intent(this, ImportService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("Converting audio to text")
            .setContentText(name)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(open)
            .addAction(0, "Cancel", cancel)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun goForeground(name: String) {
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Audio import", NotificationManager.IMPORTANCE_LOW)
        )
        val type = if (Build.VERSION.SDK_INT >= 35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(name, 0), type)
    }

    private suspend fun run(file: File, name: String, language: String) {
        val app = application as IdeaDropApp
        ImportController.set(ImportState.Working(name, 0f, ""))
        var progress = 0f
        var lastShown = -1

        val speech = PhoneSpeech(this, language, app.settings.state.value.myWordsList()) { live ->
            ImportController.set(ImportState.Working(name, progress, live))
        }
        val engineRefused = AtomicBoolean(false)
        speech.onAudioSourceFailed = { engineRefused.set(true) }

        val result: ImportState.Done = try {
            val decoder = AudioFileDecoder(file)
            val stream = speech.startWithAudioSource()
                ?: throw UnsupportedOperationException("Needs Android 13 or newer")
            this.stream = stream
            try {
                decoder.decode(
                    keepGoing = { scope.isActive && !cancelled.get() && !engineRefused.get() },
                    onProgress = { p ->
                        progress = p
                        val pct = (p * 100).toInt()
                        if (pct != lastShown) {
                            lastShown = pct
                            notifications.notify(NOTIFICATION_ID, notification(name, pct))
                            ImportController.set(ImportState.Working(name, p, speech.text()))
                        }
                    },
                    // Blocks while the engine catches up, so the file is read at the engine's pace
                    onPcm = { pcm -> stream.write(pcm) },
                )
            } catch (_: IOException) {
                // Pipe closed: the engine stopped reading (refused or cancelled)
            } finally {
                runCatching { stream.close() }
            }
            val text = speech.finish(waitMs = 30_000)
            when {
                cancelled.get() -> {
                    file.delete()
                    ImportState.Done(name, null, "Cancelled")
                }
                engineRefused.get() && text.isBlank() -> {
                    file.delete()
                    ImportState.Done(
                        name, null,
                        "Your phone's speech engine can't read audio files. " +
                            "Try installing \"Speech Recognition & Synthesis from Google\" and set it as default."
                    )
                }
                else -> {
                    val noteId = app.repository.createFromImport(
                        transcript = text,
                        audio = file,
                        durationMs = decoder.durationMs,
                        sourceName = name,
                    )
                    ImportState.Done(
                        name, noteId,
                        if (text.isBlank()) "No speech recognised. Audio saved; try another language in Settings."
                        else "Converted to text"
                    )
                }
            }
        } catch (e: Exception) {
            speech.finish(waitMs = 1_000)
            file.delete()
            ImportState.Done(name, null, "Couldn't read this file: ${e.message ?: "unknown error"}")
        }

        ImportController.set(result)
        finishService()
    }

    private fun finishService() {
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancelled.set(true)
        scope.cancel()
        if (ImportController.state.value is ImportState.Working) {
            ImportController.set(ImportState.Done("", null, "Stopped"))
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.applab.ideadrop.IMPORT"
        const val ACTION_CANCEL = "com.applab.ideadrop.IMPORT_CANCEL"
        const val EXTRA_PATH = "path"
        const val EXTRA_NAME = "name"
        const val EXTRA_LANGUAGE = "language"
        private const val CHANNEL_ID = "import"
        private const val NOTIFICATION_ID = 2
    }
}
