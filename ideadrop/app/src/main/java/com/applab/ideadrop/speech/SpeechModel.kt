package com.applab.ideadrop.speech

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val progress: Float) : ModelState
    data object Unpacking : ModelState
    data object Ready : ModelState
    data class Failed(val message: String) : ModelState
}

/**
 * Offline English speech model (Vosk small, ~40 MB). Downloaded once, then
 * everything runs on the phone with no internet.
 */
class SpeechModel(private val context: Context) {

    private val modelsDir = File(context.filesDir, "models")
    private val modelDir = File(modelsDir, MODEL_NAME)

    private val _state = MutableStateFlow<ModelState>(if (isInstalled()) ModelState.Ready else ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val loadLock = Mutex()
    private var model: Model? = null

    init {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
    }

    private fun isInstalled() = File(modelDir, "conf/mfcc.conf").exists()

    val isReady: Boolean get() = _state.value == ModelState.Ready

    suspend fun download() = withContext(Dispatchers.IO) {
        if (_state.value is ModelState.Downloading || _state.value == ModelState.Unpacking) return@withContext
        val zip = File(context.cacheDir, "$MODEL_NAME.zip")
        try {
            _state.value = ModelState.Downloading(0f)
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: APPROX_SIZE
            conn.inputStream.use { input ->
                FileOutputStream(zip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        _state.value = ModelState.Downloading((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            _state.value = ModelState.Unpacking
            unzip(zip, modelsDir)
            check(isInstalled()) { "Model files missing after unpacking" }
            _state.value = ModelState.Ready
        } catch (e: Exception) {
            modelDir.deleteRecursively()
            _state.value = ModelState.Failed(e.message ?: "Download failed. Check internet and retry.")
        } finally {
            zip.delete()
        }
    }

    suspend fun remove() = loadLock.withLock {
        model?.close()
        model = null
        withContext(Dispatchers.IO) { modelDir.deleteRecursively() }
        _state.value = ModelState.Missing
    }

    /** Loads the model into memory once (takes ~1–2 s the first time). */
    suspend fun load(): Model? = loadLock.withLock {
        if (!isInstalled()) return@withLock null
        model ?: withContext(Dispatchers.IO) { Model(modelDir.absolutePath) }.also { model = it }
    }

    private fun unzip(zip: File, target: File) {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(target, entry.name)
                // Guard against malicious paths ("zip slip")
                if (!out.canonicalPath.startsWith(root)) continue
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
            }
        }
    }

    companion object {
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
        private const val MODEL_URL = "https://alphacephei.com/vosk/models/$MODEL_NAME.zip"
        private const val APPROX_SIZE = 41_000_000L
        const val SAMPLE_RATE = 16_000
    }
}

/** Live transcription for one recording. Feed audio chunks, read text as it forms. */
class LiveTranscriber(model: Model) : AutoCloseable {
    private val recognizer = Recognizer(model, SpeechModel.SAMPLE_RATE.toFloat())
    private val finished = StringBuilder()

    /** Returns the text so far (final segments + current partial). */
    fun accept(buffer: ByteArray, length: Int): String {
        return if (recognizer.acceptWaveForm(buffer, length)) {
            appendSegment(textOf(recognizer.result, "text"))
            finished.toString()
        } else {
            val partial = textOf(recognizer.partialResult, "partial")
            if (partial.isBlank()) finished.toString() else join(finished.toString(), partial)
        }
    }

    fun finish(): String {
        appendSegment(textOf(recognizer.finalResult, "text"))
        return finished.toString().trim()
    }

    private fun appendSegment(text: String) {
        if (text.isBlank()) return
        if (finished.isNotEmpty()) finished.append(' ')
        finished.append(text.trim())
    }

    override fun close() = recognizer.close()

    companion object {
        fun textOf(json: String, key: String): String =
            runCatching { JSONObject(json).optString(key) }.getOrDefault("")

        private fun join(a: String, b: String) = if (a.isBlank()) b else "$a $b"
    }
}

/** Transcribes a saved 16 kHz mono WAV file (for clips recorded before the model was ready). */
suspend fun transcribeWav(model: Model, wav: File): String = withContext(Dispatchers.IO) {
    LiveTranscriber(model).use { t ->
        wav.inputStream().buffered().use { input ->
            input.skip(44) // WAV header
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                t.accept(buf, n)
            }
        }
        t.finish()
    }
}
