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
    primary = Color(0xFF9A6334),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF4E3D0),
    onPrimaryContainer = Color(0xFF3E2308),
    secondaryContainer = Color(0xFFF1E9E1),
    onSecondaryContainer = Color(0xFF33291F),
    background = Color(0xFFFAF7F3),
    onBackground = Color(0xFF211C17),
    surface = Color(0xFFFAF7F3),
    onSurface = Color(0xFF211C17),
    onSurfaceVariant = Color(0xFF756A5F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF3EEE8),
    surfaceContainerHigh = Color(0xFFEDE6DE),
    surfaceContainerHighest = Color(0xFFE7DFD5),
    outlineVariant = Color(0xFFEAE3DA),
    error = Color(0xFFD93F57),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE0B88C),
    onPrimary = Color(0xFF3E2308),
    primaryContainer = Color(0xFF6E4524),
    onPrimaryContainer = Color(0xFFF4E3D0),
    secondaryContainer = Color(0xFF332B24),
    onSecondaryContainer = Color(0xFFEADFD4),
    background = Color(0xFF16120E),
    onBackground = Color(0xFFEDE6DF),
    surface = Color(0xFF16120E),
    onSurface = Color(0xFFEDE6DF),
    onSurfaceVariant = Color(0xFFB3A89C),
    surfaceContainerLowest = Color(0xFF110E0B),
    surfaceContainerLow = Color(0xFF1D1914),
    surfaceContainer = Color(0xFF231E19),
    surfaceContainerHigh = Color(0xFF2A241E),
    surfaceContainerHighest = Color(0xFF312A23),
    outlineVariant = Color(0xFF373028),
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
