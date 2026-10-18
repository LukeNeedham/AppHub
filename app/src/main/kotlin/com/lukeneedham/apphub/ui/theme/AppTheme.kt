package com.lukeneedham.apphub.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF1E1470),
    secondaryContainer = Color(0xFFEAE8F6),
    onSecondaryContainer = Color(0xFF26233A),
    background = Color(0xFFF6F5FB),
    onBackground = Color(0xFF1B1A22),
    surface = Color(0xFFF6F5FB),
    onSurface = Color(0xFF1B1A22),
    onSurfaceVariant = Color(0xFF66637C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEFEDF7),
    surfaceContainerHigh = Color(0xFFE9E7F3),
    surfaceContainerHighest = Color(0xFFE3E1EF),
    outlineVariant = Color(0xFFE6E4F1),
    error = Color(0xFFD93F57),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B0FF),
    onPrimary = Color(0xFF2A1C9C),
    primaryContainer = Color(0xFF3F31B8),
    onPrimaryContainer = Color(0xFFE7E3FF),
    secondaryContainer = Color(0xFF2B293A),
    onSecondaryContainer = Color(0xFFE3E1F2),
    background = Color(0xFF0F0E15),
    onBackground = Color(0xFFE8E6F2),
    surface = Color(0xFF0F0E15),
    onSurface = Color(0xFFE8E6F2),
    onSurfaceVariant = Color(0xFFA9A6BE),
    surfaceContainerLowest = Color(0xFF0B0A10),
    surfaceContainerLow = Color(0xFF17161F),
    surfaceContainer = Color(0xFF1D1B27),
    surfaceContainerHigh = Color(0xFF242232),
    surfaceContainerHighest = Color(0xFF2B2939),
    outlineVariant = Color(0xFF2C2A3B),
    error = Color(0xFFFF8A9B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** Meaning colours that the Material scheme has no slot for. Used on a tinted background of the same hue. */
object StatusColors {
    val success = Color(0xFF2FA36B)
    val warning = Color(0xFFE29A1B)
    val info = Color(0xFF3F8CF0)
}

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
