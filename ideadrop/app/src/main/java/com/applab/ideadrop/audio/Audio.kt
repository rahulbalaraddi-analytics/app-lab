package com.applab.ideadrop.audio

import android.media.MediaPlayer
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Writes 16-bit mono PCM into a standard WAV file (playable anywhere). */
class WavWriter(file: File, private val sampleRate: Int) {
    private val raf = RandomAccessFile(file, "rw").apply {
        setLength(0)
        write(ByteArray(HEADER_SIZE)) // placeholder, filled in on close
    }
    private var dataBytes = 0L

    fun write(buffer: ByteArray, length: Int) {
        raf.write(buffer, 0, length)
        dataBytes += length
    }

    fun close() {
        raf.seek(0)
        raf.write(header(dataBytes))
        raf.close()
    }

    private fun header(dataLen: Long): ByteArray {
        val byteRate = sampleRate * 2
        fun le32(v: Long) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
        fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
        return "RIFF".toByteArray() + le32(36 + dataLen) + "WAVE".toByteArray() +
            "fmt ".toByteArray() + le32(16) + le16(1) + le16(1) +
            le32(sampleRate.toLong()) + le32(byteRate.toLong()) + le16(2) + le16(16) +
            "data".toByteArray() + le32(dataLen)
    }

    companion object { const val HEADER_SIZE = 44 }
}

/** Plays one clip at a time: IdeaDrop's own files or call recordings (content:// links). */
class AudioPlayer(private val context: android.content.Context) {
    private var player: MediaPlayer? = null
    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath.asStateFlow()

    fun toggle(path: String) {
        if (_playingPath.value == path) { stop(); return }
        stop()
        runCatching { start(path) }.onFailure { stop() } // e.g. the original recording was deleted
    }

    private fun start(path: String) {
        player = MediaPlayer().apply {
            if (path.startsWith("content://")) setDataSource(context, android.net.Uri.parse(path)) else setDataSource(path)
            setOnCompletionListener { this@AudioPlayer.stop() }
            prepare()
            start()
        }
        _playingPath.value = path
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        _playingPath.value = null
    }
}
