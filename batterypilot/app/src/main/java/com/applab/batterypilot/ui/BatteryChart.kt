package com.applab.batterypilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applab.batterypilot.analysis.Analysis
import com.applab.batterypilot.data.BatterySample
import java.util.Calendar

/**
 * Battery % over time (solid), forecast to your target time (dashed),
 * your safety buffer (dotted line) and the target time (vertical line).
 */
@Composable
fun BatteryChart(
    samples: List<BatterySample>,
    forecast: Analysis.Forecast?,
    bufferPct: Int,
    lineColor: Color,
    forecastColor: Color,
    modifier: Modifier = Modifier,
) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val charge = Color(0xFF34A853)
    val measurer = rememberTextMeasurer()
    val now = System.currentTimeMillis()
    val end = forecast?.target ?: now
    // Show from this morning (or the last unplug) to the target.
    val start = run {
        val c = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0)
        }
        val morning = if (c.timeInMillis < now) c.timeInMillis else c.timeInMillis - 24 * 3_600_000L
        val first = samples.firstOrNull { it.time >= morning - 3 * 3_600_000L }?.time ?: morning
        minOf(morning, first).coerceAtLeast(now - 20 * 3_600_000L)
    }
    val pts = samples.filter { it.time >= start }

    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val left = 34.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        val top = 6.dp.toPx()
        val w = size.width - left - 6.dp.toPx()
        val h = bottom - top
        fun x(t: Long) = left + w * ((t - start).toFloat() / (end - start).coerceAtLeast(1L))
        fun y(l: Double) = top + h * (1f - (l / 100.0).toFloat().coerceIn(0f, 1f))
        val small = TextStyle(fontSize = 10.sp, color = label)

        // Grid: 0 / 50 / 100 %
        listOf(0, 50, 100).forEach { p ->
            drawLine(grid, Offset(left, y(p.toDouble())), Offset(left + w, y(p.toDouble())), 1f)
            drawText(measurer, "$p%", Offset(0f, y(p.toDouble()) - 7.sp.toPx()), small)
        }
        // Buffer line
        drawLine(
            Color(0xFFE37400), Offset(left, y(bufferPct.toDouble())), Offset(left + w, y(bufferPct.toDouble())), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)),
        )
        // Hour ticks every 3 h
        val c = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
        while (c.timeInMillis <= end) {
            val hr = c.get(Calendar.HOUR_OF_DAY)
            if (c.timeInMillis >= start && hr % 3 == 0) {
                val tx = x(c.timeInMillis)
                drawLine(grid.copy(alpha = 0.5f), Offset(tx, top), Offset(tx, bottom), 1f)
                val txt = when { hr == 0 -> "12a"; hr < 12 -> "${hr}a"; hr == 12 -> "12p"; else -> "${hr - 12}p" }
                drawText(measurer, txt, Offset(tx - 8.dp.toPx(), bottom + 3.dp.toPx()), small)
            }
            c.add(Calendar.HOUR_OF_DAY, 1)
        }

        // Actual battery line (green while charging)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]; val b = pts[i]
            if (b.time - a.time > 3 * 3_600_000L) continue
            drawLine(
                if (b.charging) charge else lineColor,
                Offset(x(a.time), y(a.level.toDouble())), Offset(x(b.time), y(b.level.toDouble())),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
            )
        }

        // Forecast (dashed)
        if (forecast != null && forecast.path.size > 1 && pts.lastOrNull()?.charging != true) {
            val p = Path()
            forecast.path.forEachIndexed { i, (t, l) ->
                if (i == 0) p.moveTo(x(t), y(l)) else p.lineTo(x(t), y(l))
            }
            drawPath(p, forecastColor, style = Stroke(width = 2.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            forecast.emptyAt?.let { e ->
                drawCircle(forecastColor, 5.dp.toPx(), Offset(x(e), y(0.0)))
            }
        }
        // Now marker
        drawLine(label.copy(alpha = 0.6f), Offset(x(now), top), Offset(x(now), bottom), 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 5f)))
    }
}

@Composable
fun ChartLegend(lineColor: Color, forecastColor: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("━ actual", color = lineColor, style = MaterialTheme.typography.labelSmall)
        HGap(12)
        Text("╍ forecast", color = forecastColor, style = MaterialTheme.typography.labelSmall)
        HGap(12)
        Text("┄ safety buffer", color = Color(0xFFE37400), style = MaterialTheme.typography.labelSmall)
        HGap(12)
        Text("━ charging", color = Color(0xFF34A853), style = MaterialTheme.typography.labelSmall)
    }
}
