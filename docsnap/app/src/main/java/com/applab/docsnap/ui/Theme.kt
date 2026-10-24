package com.applab.docsnap.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Paper = Color(0xFFF7F6F2)
    val Card = Color(0xFFFFFFFF)
    val Line = Color(0xFFE2E0D8)
    val Ink = Color(0xFF1B1F24)
    val Quiet = Color(0xFF5A6169)
    val Teal = Color(0xFF0F6E6E)
    val TealSoft = Color(0xFFDCEEEC)
    val Amber = Color(0xFFA35512)
    val AmberSoft = Color(0xFFF3E6D8)
    val Red = Color(0xFFB42318)
    val Dark = Color(0xFF111417)
}

@Composable
fun DocSnapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Teal,
            onPrimary = Color.White,
            background = Palette.Paper,
            surface = Palette.Card,
            onBackground = Palette.Ink,
            onSurface = Palette.Ink,
            error = Palette.Red,
        ),
        content = content,
    )
}
