package com.yadavard.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Violet = Color(0xFF644EE9)
val DeepViolet = Color(0xFF2C2386)
val Ink = Color(0xFF242341)
val Muted = Color(0xFF77768E)
val Canvas = Color(0xFFF7F6FC)
val Lilac = Color(0xFFECE8FF)
val Peach = Color(0xFFFFEDE4)
val Mint = Color(0xFFE7F7F3)

private val palette = lightColorScheme(
    primary = Violet, onPrimary = Color.White, primaryContainer = Lilac, onPrimaryContainer = DeepViolet,
    secondary = DeepViolet, background = Canvas, onBackground = Ink,
    surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF0EEFA),
    outline = Color(0xFFDAD7E7)
)

@Composable
fun YadavardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = palette, shapes = Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(34.dp)
    ), typography = Typography(), content = content)
}

val heroBrush = Brush.linearGradient(listOf(DeepViolet, Violet, Color(0xFF988AFF)))
