package com.applab.ideadrop.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.applab.ideadrop.IdeaDropApp
import com.applab.ideadrop.R
import com.applab.ideadrop.speech.LiveTranscriber
import com.applab.ideadrop.speech.SpeechModel
import com.applab.ideadrop.ui.MainActivity
import java.io.File
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

sealed interface RecState {
    data object Idle : RecState
    /** [noteId] is set when adding a clip to an existing note. */
    data class Recording(val startedAt: Long, val liveText: String, val noteId: Long?) : RecState
    data object Saving : RecState
}

/** App-wide recording state the UI observes. */
object RecordingController {
    private val _state = MutableStateFlow<RecState>(RecState.Idle)
    val state: StateFlow<RecState> = _state.asStateFlow()
    internal fun set(s: RecState) { _state.value = s }

    fun start(context: Context, noteId: Long? = null) {
        if (_state.value != RecState.Idle) return
        val intent = Intent(context, RecordingService::class.java)
            .setAction(RecordingService.ACTION_START)
            .putExtra(RecordingService.EXTRA_NOTE_ID, noteId ?: -1L)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }
}

/**
 * Foreground service so recording keeps going with the screen locked (driving, bedtime).
 * Saves the note automatically when it stops — no taps needed.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (job == null) {
                    val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 }
                    goForeground()
                    job = scope.launch { record(noteId) }
                }
            }
            ACTION_STOP -> {
                stopRequested = true
                if (job == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle("IdeaDrop is listening")
            .setContentText("Speak your idea. Tap Stop to save.")
            .setContentIntent(open)
            .addAction(0, "Stop & save", stop)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun record(noteId: Long?) {
        val app = application as IdeaDropApp
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            finish(); return
        }

        val startedAt = System.currentTimeMillis()
        RecordingController.set(RecState.Recording(startedAt, "", noteId))

        val dir = File(filesDir, "audio").apply { mkdirs() }
        val file = File(dir, "idea_$startedAt.wav")
        val minBuf = AudioRecord.getMinBufferSize(
            SpeechModel.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SpeechModel.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, 8192) * 2,
        )
        val model = app.speechModel.load()
        val transcriber = model?.let { LiveTranscriber(it) }
        val writer = WavWriter(file, SpeechModel.SAMPLE_RATE)
        var text = ""

        try {
            recorder.startRecording()
            val buf = ByteArray(4096)
            while (scope.isActive && !stopRequested) {
                val n = recorder.read(buf, 0, buf.size)
                if (n <= 0) continue
                writer.write(buf, n)
                if (transcriber != null) {
                    text = transcriber.accept(buf, n)
                    RecordingController.set(RecState.Recording(startedAt, text, noteId))
                }
                if (System.currentTimeMillis() - startedAt > MAX_DURATION_MS) break
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            writer.close()
        }

        RecordingController.set(RecState.Saving)
        text = transcriber?.finish() ?: ""
        transcriber?.close()
        val durationMs = System.currentTimeMillis() - startedAt
        val needsTranscription = transcriber == null

        if (durationMs < MIN_DURATION_MS && text.isBlank()) {
            file.delete() // accidental tap
        } else if (noteId != null) {
            app.repository.appendRecording(noteId, text, file, durationMs, needsTranscription)
        } else {
            app.repository.createFromRecording(text, file, durationMs, needsTranscription)
        }
        finish()
    }

    private fun finish() {
        RecordingController.set(RecState.Idle)
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopRequested = true
        scope.cancel()
        if (RecordingController.state.value !is RecState.Idle) RecordingController.set(RecState.Idle)
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.applab.ideadrop.START"
        const val ACTION_STOP = "com.applab.ideadrop.STOP"
        const val EXTRA_NOTE_ID = "noteId"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1
        private const val MAX_DURATION_MS = 10 * 60 * 1000L
        private const val MIN_DURATION_MS = 800L
    }
}
