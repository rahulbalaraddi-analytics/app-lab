package com.applab.ideadrop.ui.theme

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

val RecordRed = Color(0xFFE5484D)

private val Light = lightColorScheme(
    primary = Color(0xFF6E4BD8),
    secondary = Color(0xFF8A7FB8),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC8B6FF),
    secondary = Color(0xFFCBC0EE),
)

@Composable
fun IdeaDropTheme(content: @Composable () -> Unit) {
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
