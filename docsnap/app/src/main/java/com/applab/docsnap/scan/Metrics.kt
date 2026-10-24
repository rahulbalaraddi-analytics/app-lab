package com.applab.docsnap.scan

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import org.json.JSONObject
import java.io.File
import kotlin.math.max

enum class Quality { GOOD, OK, LOW }

data class PageMetric(
    val widthPx: Int,
    val heightPx: Int,
    val bytes: Long,
) {
    val megapixels: Double get() = widthPx.toDouble() * heightPx / 1_000_000.0
    /** Dots per inch if the page is A4 (long side 11.69 in). */
    val estDpi: Int get() = (max(widthPx, heightPx) / 11.69).toInt()
    val quality: Quality
        get() = when {
            megapixels >= GOOD_MP -> Quality.GOOD
            megapixels >= OK_MP -> Quality.OK
            else -> Quality.LOW
        }

    companion object {
        const val GOOD_MP = 8.0
        const val OK_MP = 5.0
    }
}

object Metrics {

    fun page(file: File): PageMetric {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        return PageMetric(opts.outWidth.coerceAtLeast(0), opts.outHeight.coerceAtLeast(0), file.length())
    }

    /** Largest JPEG size the back camera can produce, in megapixels (0 if unknown). */
    fun backCameraMaxMp(context: Context): Double = runCatching {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.mapNotNull { id ->
            val ch = cm.getCameraCharacteristics(id)
            if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return@mapNotNull null
            map.getOutputSizes(ImageFormat.JPEG)?.maxOfOrNull { it.width.toLong() * it.height }
        }.maxOrNull()?.let { it / 1_000_000.0 } ?: 0.0
    }.getOrDefault(0.0)

    fun device(context: Context, initials: String, appVersion: String): JSONObject {
        val stat = StatFs(Environment.getDataDirectory().path)
        return JSONObject().apply {
            put("initials", initials)
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("android_version", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("app_version", appVersion)
            put("back_camera_max_mp", "%.1f".format(backCameraMaxMp(context)))
            put("free_storage_gb", "%.1f".format(stat.availableBytes / 1e9))
            put("updated_at", System.currentTimeMillis())
        }
    }
}
