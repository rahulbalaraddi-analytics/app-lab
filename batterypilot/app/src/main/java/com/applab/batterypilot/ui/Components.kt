package com.applab.batterypilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

/** Small stat tile: label on top, value big. */
@Composable
fun RowScope.Stat(label: String, value: String, sub: String? = null, color: Color = Color.Unspecified) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun StatRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Horizontal bar with label and percent. */
@Composable
fun ShareBar(label: String, pct: Double, trailing: String? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(trailing ?: "${pct.roundToInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Box(
                Modifier.fillMaxWidth((pct / 100.0).toFloat().coerceIn(0.01f, 1f)).height(8.dp)
                    .clip(RoundedCornerShape(4.dp)).background(color),
            )
        }
    }
}

/** Simple table: header + rows; [weights] keeps columns balanced. */
@Composable
fun SimpleTable(header: List<String>, rows: List<List<String>>, weights: List<Float>, highlightCol: Int? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            header.forEachIndexed { i, h ->
                Text(h, Modifier.weight(weights[i]), style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                r.forEachIndexed { i, c ->
                    Text(c, Modifier.weight(weights[i]), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == 0 || i == highlightCol) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp),
        color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int = 8) = Spacer(Modifier.width(w.dp))

// ---------- formatting ----------

fun time(t: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
fun dayLabel(t: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(t))
fun rate(r: Double?): String = if (r == null) "—" else "%.1f%%/hr".format(r)

fun duration(ms: Long): String {
    val m = (abs(ms) / 60_000).toInt()
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}

fun minutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
