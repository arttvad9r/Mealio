package com.arttvad9r.mealio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Radii, spacing and icon sizes carried over verbatim from Rutina Tokens.kt.
 * Rutina defines no Material3 `Shapes` object — every control uses Radius.field
 * (12dp), cards/sheets/dialogs 16dp, the bottom sheet top 26dp.
 */
object Radius {
    val card = RoundedCornerShape(16.dp)
    val menu = RoundedCornerShape(12.dp)
    val field = RoundedCornerShape(12.dp)
    val fab = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val cell = RoundedCornerShape(4.dp)
    val segment = RoundedCornerShape(3.dp)
    val gridGap: Dp = 4.dp
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screen = 16.dp
}

object IconSize {
    val action = 20.dp
    val caption = 12.dp
    val status = 22.dp
}

/** Switch colors, carried over from Rutina. */
@androidx.compose.runtime.Composable
fun mealioSwitchColors(): SwitchColors = SwitchDefaults.colors(
    uncheckedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = Color.Transparent,
    uncheckedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
    uncheckedIconColor = Color.Transparent,
    checkedTrackColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
    checkedBorderColor = Color.Transparent,
    checkedThumbColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
    checkedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
)
