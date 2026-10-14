package com.applab.batterypilot.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Status colours: fixed (not dynamic) so green/amber/red always mean the same thing. */
object Status {
    val Good = Color(0xFF1E8E3E)
    val Warn = Color(0xFFE37400)
    val Bad = Color(0xFFD93025)
}

private val Light = lightColorScheme(
    primary = Color(0xFF1E6B3A),
    secondary = Color(0xFF52634F),
    background = Color(0xFFF7FBF2),
    surface = Color(0xFFF7FBF2),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF8FD8A0),
    secondary = Color(0xFFB9CCB4),
)

@Composable
fun BatteryPilotTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
