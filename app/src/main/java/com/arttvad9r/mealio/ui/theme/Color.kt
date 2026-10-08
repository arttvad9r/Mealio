package com.arttvad9r.mealio.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Brand seeds carried over verbatim from Rutina so Mealio belongs to the same
 * visual family.
 */
val Moss = Color(0xFF4C7A5A)
val MossDark = Color(0xFF9CC7A7)
val Clay = Color(0xFFB4715A)
val Ink = Color(0xFF23262B)
val Paper = Color(0xFFFBFBF9)

// Light palette (Rutina Theme.kt, exact values).
val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF4C7A5A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBDF),
    onPrimaryContainer = Color(0xFF1F3325),
    inversePrimary = Color(0xFF9CC7A7),
    secondary = Color(0xFFB4715A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEADFD8),
    onSecondaryContainer = Color(0xFF3A241C),
    tertiary = Color(0xFF5B6B7A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDFE6EC),
    onTertiaryContainer = Color(0xFF1E2830),
    background = Color(0xFFFBFBF9),
    onBackground = Color(0xFF23262B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF23262B),
    surfaceVariant = Color(0xFFEBEBE5),
    onSurfaceVariant = Color(0xFF4C5158),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F4),
    surfaceContainer = Color(0xFFF3F3EF),
    surfaceContainerHigh = Color(0xFFEDEDE8),
    surfaceContainerHighest = Color(0xFFE7E7E2),
    outline = Color(0xFF7A7F7C),
    outlineVariant = Color(0xFFD6D9D3),
    error = Color(0xFF9E4B3E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF5DDD8),
    onErrorContainer = Color(0xFF3E1A14),
    inverseSurface = Color(0xFF2F3336),
    inverseOnSurface = Color(0xFFF1F1ED),
    scrim = Color(0xFF000000),
)

// Dark palette (Rutina Theme.kt, exact values).
val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFF9CC7A7),
    onPrimary = Color(0xFF13251A),
    primaryContainer = Color(0xFF2C4434),
    onPrimaryContainer = Color(0xFFDCEBDF),
    inversePrimary = Color(0xFF4C7A5A),
    secondary = Color(0xFFD9A08C),
    onSecondary = Color(0xFF3A241C),
    secondaryContainer = Color(0xFF4A3A33),
    onSecondaryContainer = Color(0xFFEADFD8),
    tertiary = Color(0xFFAEBECD),
    onTertiary = Color(0xFF1E2830),
    tertiaryContainer = Color(0xFF343F49),
    onTertiaryContainer = Color(0xFFDFE6EC),
    background = Color(0xFF15171A),
    onBackground = Color(0xFFE8E9E6),
    surface = Color(0xFF1C1F23),
    onSurface = Color(0xFFE8E9E6),
    surfaceVariant = Color(0xFF272B30),
    onSurfaceVariant = Color(0xFFC2C6C2),
    surfaceContainerLowest = Color(0xFF16191C),
    surfaceContainerLow = Color(0xFF1C1F23),
    surfaceContainer = Color(0xFF212529),
    surfaceContainerHigh = Color(0xFF272B30),
    surfaceContainerHighest = Color(0xFF2E3338),
    outline = Color(0xFF6E7370),
    outlineVariant = Color(0xFF2B3034),
    error = Color(0xFFE0A199),
    onError = Color(0xFF3E1A14),
    errorContainer = Color(0xFF5A2A22),
    onErrorContainer = Color(0xFFF5DDD8),
    inverseSurface = Color(0xFFE8E9E6),
    inverseOnSurface = Color(0xFF2F3336),
    scrim = Color(0xFF000000),
)

/** Typography, values carried over from Rutina. */
val AppTypography = Typography(
    displaySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = androidx.compose.ui.text.TextStyle(
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = androidx.compose.ui.text.TextStyle(
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = androidx.compose.ui.text.TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelSmall = androidx.compose.ui.text.TextStyle(
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)
