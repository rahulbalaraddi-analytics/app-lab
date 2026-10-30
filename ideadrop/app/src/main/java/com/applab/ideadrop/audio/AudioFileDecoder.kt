package com.applab.ideadrop.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.content.Context
import android.net.Uri
import java.nio.ByteOrder

/**
 * Decodes any audio file the phone can play (m4a/AAC, mp3, amr, ogg/opus, wav…) into
 * 16 kHz mono 16-bit PCM — the format the speech engine expects.
 */
class AudioFileDecoder(context: Context, uri: Uri) {

    /** Length of the audio in ms, or 0 if unknown. */
    val durationMs: Long

    private val extractor = MediaExtractor()
    private val trackIndex: Int
    private val format: MediaFormat

    init {
        extractor.setDataSource(context, uri, null)
        trackIndex = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); throw IllegalArgumentException("No audio found in this file") }
        format = extractor.getTrackFormat(trackIndex)
        durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0
    }

    /**
     * Streams decoded audio to [onPcm] (16 kHz mono PCM chunks). [onProgress] gets 0..1.
     * Return false from [keepGoing] to cancel.
     */
    fun decode(keepGoing: () -> Boolean, onProgress: (Float) -> Unit, onPcm: (ByteArray) -> Unit) {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        extractor.selectTrack(trackIndex)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var inRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val resampler = Resampler()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        try {
            while (!outputDone && keepGoing()) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            if (durationMs > 0) onProgress((extractor.sampleTime / 1000f / durationMs).coerceIn(0f, 1f))
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        inRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }
                    outIdx >= 0 -> {
                        if (info.size > 0) {
                            val out = codec.getOutputBuffer(outIdx)!!.order(ByteOrder.nativeOrder())
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val mono = toMono(out, info.size, channels, floatPcm)
                            val pcm = resampler.process(mono, inRate)
                            if (pcm.isNotEmpty()) onPcm(pcm)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            onProgress(1f)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }

    /** Interleaved PCM (16-bit or float) → mono samples in -1..1. */
    private fun toMono(buf: java.nio.ByteBuffer, size: Int, channels: Int, isFloat: Boolean): FloatArray {
        val ch = channels.coerceAtLeast(1)
        return if (isFloat) {
            val fb = buf.asFloatBuffer()
            val frames = (size / 4) / ch
            FloatArray(frames) { i -> var sum = 0f; for (c in 0 until ch) sum += fb.get(i * ch + c); sum / ch }
        } else {
            val sb = buf.asShortBuffer()
            val frames = (size / 2) / ch
            FloatArray(frames) { i -> var sum = 0f; for (c in 0 until ch) sum += sb.get(i * ch + c); sum / ch / 32768f }
        }
    }

    /** Simple linear resampler to 16 kHz, 16-bit little-endian output. Keeps state between chunks. */
    private class Resampler {
        private var pos = 0.0
        private var last = 0f

        fun process(input: FloatArray, inRate: Int): ByteArray {
            if (input.isEmpty()) return ByteArray(0)
            val step = inRate.toDouble() / OUT_RATE
            val out = ArrayList<Short>(((input.size / step) + 2).toInt())
            // pos 0 = previous chunk's last sample, pos k = input[k - 1]
            while (pos < input.size) {
                val i = pos.toInt()
                val frac = (pos - i).toFloat()
                val a = if (i - 1 < 0) last else input[i - 1]
                val b = input[minOf(i, input.size - 1)]
                val v = (a + (b - a) * frac).coerceIn(-1f, 1f)
                out += (v * 32767).toInt().toShort()
                pos += step
            }
            pos -= input.size
            last = input.last()
            val bytes = ByteArray(out.size * 2)
            out.forEachIndexed { k, sh ->
                bytes[2 * k] = (sh.toInt() and 0xFF).toByte()
                bytes[2 * k + 1] = ((sh.toInt() shr 8) and 0xFF).toByte()
            }
            return bytes
        }

        companion object { const val OUT_RATE = 16_000 }
    }
}
