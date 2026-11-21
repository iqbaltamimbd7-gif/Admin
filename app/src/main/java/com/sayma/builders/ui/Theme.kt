package com.sayma.builders.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same palette as the website's mobile admin portal (styles.css).
val Teal = Color(0xFF176E78)
val Orchid = Color(0xFFB77FBA)
val Rose = Color(0xFFD68FA9)
val Mint = Color(0xFF78CBC5)
val Ink = Color(0xFF24464B)
val Muted = Color(0xFF70878A)
val Bg = Color(0xFFEDF4F2)
val CardBg = Color(0xFFFFFFFF)
val Line = Color(0xFFE2ECEA)
val Good = Color(0xFF2E9E6B)
val Bad = Color(0xFFD9534F)
val Warn = Color(0xFFE59A2F)
val HeroBrush = Brush.linearGradient(listOf(Color(0xFF16838A), Color(0xFF648EAA), Color(0xFFD58EAF)))
val ScreenBrush = Brush.linearGradient(listOf(Color(0xFFEAF4F2), Color(0xFFF9EEF5), Color(0xFFF2EFF8)))

private val Scheme = lightColorScheme(
    primary = Teal, onPrimary = Color.White, secondary = Orchid, tertiary = Rose,
    background = Bg, surface = CardBg, onBackground = Ink, onSurface = Ink,
    surfaceVariant = Color(0xFFE8F3F1), outline = Line, error = Bad
)

@Composable
fun SaymaTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val dark = isSystemInDarkTheme() // app is light-only like the website
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography(
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            bodyMedium = TextStyle(fontSize = 14.sp),
            bodySmall = TextStyle(fontSize = 12.sp)
        ),
        content = content
    )
}
