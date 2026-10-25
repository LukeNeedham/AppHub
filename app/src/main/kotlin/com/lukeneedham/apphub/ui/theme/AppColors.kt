package com.lukeneedham.apphub.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour the app uses, named for what it is used for rather than for a Material role.
 * Read them with `AppTheme.colors`; [AppTheme] hands Material components their own scheme built from these.
 */
@Immutable
data class AppColors(
    /** Behind every page. */
    val background: Color,
    /** Cards and sheets sitting on the [background]. */
    val card: Color,
    /** Outlines of cards and buttons, and dividers. */
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** The brand colour (cardboard brown): highlights that are not an action, and filled buttons. */
    val accent: Color,
    /** Text and icons on the [accent]. */
    val onAccent: Color,
    /** An APK is ready and you can act on it (Ready, Update ready). Purple. See docs/status-colors.md. */
    val action: Color,
    /** Something is running. */
    val building: Color,
    /** Done. */
    val installed: Color,
    /** What is on offer is out of date. */
    val stale: Color,
    /** Broken, and error text. */
    val failed: Color,
    /** Nothing to do. */
    val idle: Color,
)

internal val LightAppColors = AppColors(
    background = Color(0xFFFAF7F3),
    card = Color.White,
    border = Color(0xFFEAE3DA),
    textPrimary = Color(0xFF211C17),
    textSecondary = Color(0xFF756A5F),
    accent = Color(0xFF9A6334),
    onAccent = Color.White,
    action = Color(0xFF5B4BDB),
    building = Color(0xFF3F8CF0),
    installed = Color(0xFF2FA36B),
    stale = Color(0xFFE29A1B),
    failed = Color(0xFFD93F57),
    idle = Color(0xFF756A5F),
)

internal val DarkAppColors = AppColors(
    background = Color(0xFF16120E),
    card = Color(0xFF1D1914),
    border = Color(0xFF373028),
    textPrimary = Color(0xFFEDE6DF),
    textSecondary = Color(0xFFB3A89C),
    accent = Color(0xFFE0B88C),
    onAccent = Color(0xFF3E2308),
    action = Color(0xFFB9B0FF),
    building = Color(0xFF3F8CF0),
    installed = Color(0xFF2FA36B),
    stale = Color(0xFFE29A1B),
    failed = Color(0xFFFF8A9B),
    idle = Color(0xFFB3A89C),
)

internal val LocalAppColors = staticCompositionLocalOf { LightAppColors }
