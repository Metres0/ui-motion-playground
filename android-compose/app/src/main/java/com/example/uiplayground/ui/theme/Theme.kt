package com.example.uiplayground.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// SaltUI 风格参考：蓝白灰主色 + 极低阴影，清新稳定
private val SaltBlue = Color(0xFF3A7EF2)
private val SaltBgLight = Color(0xFFF5F6FA)
private val SaltBgDark = Color(0xFF14151A)

private val LightColors = lightColorScheme(
    primary = SaltBlue,
    background = SaltBgLight,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = SaltBlue,
    background = SaltBgDark,
    surface = Color(0xFF1E2027),
)

@Composable
fun UIPlaygroundTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
