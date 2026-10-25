package com.lukeneedham.apphub.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.lukeneedham.common.ui.DebugTheme
import com.lukeneedham.common.ui.ProvideDebugSettings

/**
 * [ProvideDebugSettings] with the shared library's debug pages themed to match the app's
 * theme. Place it inside the app's theme, since it reads the colours and font from there.
 */
@Composable
fun ProvideAppDebugSettings(content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    ProvideDebugSettings(
        theme = DebugTheme(
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.card,
            onSurface = colors.textSecondary,
            primary = colors.accent,
            onPrimary = colors.onAccent,
            outline = colors.border,
            error = colors.failed,
            warning = colors.stale,
            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily ?: FontFamily.Default,
        ),
        content = content,
    )
}
